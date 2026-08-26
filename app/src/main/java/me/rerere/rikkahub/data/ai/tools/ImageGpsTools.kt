/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import androidx.exifinterface.media.ExifInterface
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File

/**
 * 图片 GPS 位置修改器：读取/写入/清除图片 EXIF 中的 GPS 位置信息。
 *
 * 适用于任何带 EXIF 的图片（JPEG/HEIF 等）。传入图片的绝对文件路径。
 */
fun createImageGpsTools(): List<Tool> = listOf(
    Tool(
        name = "image_gps_get",
        description = "读取图片的 GPS 位置信息（拍摄地经纬度）。Params: path(图片绝对路径)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "图片的绝对文件路径") })
                },
                required = listOf("path")
            )
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
            val file = File(path)
            if (!file.exists()) return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "文件不存在"); put("path", path)
            }.toString()))
            val exif = runCatching { ExifInterface(file) }.getOrNull()
                ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "无法解析 EXIF（可能不是支持格式）"); put("path", path)
                }.toString()))
            val latLng = exif.latLong
            val altitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE)?.let { r ->
                runCatching { r.toDouble() }.getOrNull()
            }
            val altitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("path", path)
                put("has_gps", latLng != null)
                if (latLng != null) {
                    put("latitude", latLng[0])
                    put("longitude", latLng[1])
                }
                if (altitude != null) {
                    put("altitude", altitude)
                    put("altitude_ref", altitudeRef)
                }
                put("tip", if (latLng == null) "该图片未包含 GPS 位置信息，可用 image_gps_set 添加" else "可用 image_gps_set 修改，或 image_gps_clear 清除")
            }.toString()))
        }
    ),
    Tool(
        name = "image_gps_set",
        description = "写入/修改图片的 GPS 位置信息（拍摄地经纬度）。Params: path(图片绝对路径), latitude(纬度 -90~90), longitude(经度 -180~180), altitude(可选，海拔米)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "图片的绝对文件路径") })
                    put("latitude", buildJsonObject { put("type", "number"); put("description", "纬度，-90~90，北纬为正") })
                    put("longitude", buildJsonObject { put("type", "number"); put("description", "经度，-180~180，东经为正") })
                    put("altitude", buildJsonObject { put("type", "number"); put("description", "可选：海拔（米），正为海平面上") })
                },
                required = listOf("path", "latitude", "longitude")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["path"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
            val lat = o["latitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"invalid latitude"}"""))
            val lng = o["longitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"invalid longitude"}"""))
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                return@Tool listOf(UIMessagePart.Text("""{"error":"latitude must be -90~90, longitude -180~180"}"""))
            }
            val file = File(path)
            if (!file.exists()) return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "文件不存在"); put("path", path)
            }.toString()))
            val ok = runCatching {
                val exif = ExifInterface(file)
                exif.setLatLong(lat, lng)
                // 海拔
                o["altitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.let { alt ->
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, formatAltitude(alt))
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt < 0) "1" else "0")
                }
                exif.saveAttributes()
            }.isSuccess
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", ok)
                put("path", path)
                put("latitude", lat)
                put("longitude", lng)
                put("message", if (ok) "GPS 位置已写入" else "写入失败（只读/不支持格式）")
            }.toString()))
        }
    ),
    Tool(
        name = "image_gps_clear",
        description = "清除图片的 GPS 位置信息（移除 EXIF 中的地理位置），保护隐私。Params: path(图片绝对路径)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "图片的绝对文件路径") })
                },
                required = listOf("path")
            )
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
            val file = File(path)
            if (!file.exists()) return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "文件不存在"); put("path", path)
            }.toString()))
            val ok = runCatching {
                val exif = ExifInterface(file)
                // 移除全部 GPS 相关标签
                listOf(
                    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD,
                ).forEach { exif.setAttribute(it, null) }
                exif.saveAttributes()
            }.isSuccess
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", ok)
                put("path", path)
                put("message", if (ok) "GPS 位置信息已清除" else "清除失败")
            }.toString()))
        }
    ),
)

/** 把小数海拔转成 ExifInterface 需要的分数字符串（API 26 ExifInterface 无 setAltitude，用 setAttribute 以"数字/1"写入） */
private fun formatAltitude(alt: Double): String {
    // ExifInterface 的 TAG_GPS_ALTITUDE 期望 "小数/整数" 形式（如 "12.5/1"）
    val value = if (alt == alt.toLong().toDouble()) "${alt.toLong()}/1" else "$alt/1"
    return value
}

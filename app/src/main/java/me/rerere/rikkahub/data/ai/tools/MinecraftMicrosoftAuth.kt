/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 微软账号认证（Minecraft online-mode 登录）：
 * 设备码登录 → Microsoft token → Xbox Live → XSTS → Minecraft token
 *
 * 纯 HTTP（OkHttp），不用工作区/外部依赖。
 * 设备码流程：用户打开验证页输入代码确认（微软安全机制，无法跳过）。
 */
class MinecraftMicrosoftAuth {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 微软 OAuth client_id（Xbox app） */
    private val CLIENT_ID = "00000000402b5328"
    private val SCOPE = "XboxLive.signin offline_access"

    data class DeviceCodeResult(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val interval: Long,
    )

    data class MinecraftAuthResult(
        val accessToken: String,
        val uuid: String,
        val username: String,
    )

    /** 1. 获取设备码（显示给用户确认） */
    fun requestDeviceCode(): DeviceCodeResult {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPE)
            .build()
        val req = Request.Builder()
            .url("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
            .header("User-Agent", "RikkaHub")
            .post(form)
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw Exception("devicecode 失败: ${resp.code}")
            val obj = json.parseToJsonElement(body).jsonObject
            return DeviceCodeResult(
                deviceCode = obj["device_code"]?.jsonPrimitive?.contentOrNull ?: throw Exception("无 device_code"),
                userCode = obj["user_code"]?.jsonPrimitive?.contentOrNull ?: "",
                verificationUri = obj["verification_uri"]?.jsonPrimitive?.contentOrNull ?: "https://microsoft.com/link",
                interval = obj["interval"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 5,
            )
        }
    }

    /** 2. 轮询等待用户确认，返回微软 access_token */
    fun pollToken(deviceCode: String, interval: Long): String {
        var waitMs = interval * 1000
        repeat(30) {
            Thread.sleep(waitMs)
            val form = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", CLIENT_ID)
                .add("device_code", deviceCode)
                .build()
            val req = Request.Builder()
                .url("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
                .header("User-Agent", "RikkaHub")
                .post(form)
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use
                val obj = json.parseToJsonElement(body).jsonObject
                val accessToken = obj["access_token"]?.jsonPrimitive?.contentOrNull
                if (accessToken != null) {
                    return accessToken
                }
                val error = obj["error"]?.jsonPrimitive?.contentOrNull
                if (error == "authorization_pending") {
                    waitMs = interval * 1000
                } else if (error == "authorization_declined") {
                    throw Exception("用户拒绝了登录")
                } else if (error == "expired_token") {
                    throw Exception("设备码已过期，请重试")
                }
            }
        }
        throw Exception("等待用户确认超时")
    }

    /** 3. Xbox Live 认证：Microsoft token → Xbox token */
    private fun authenticateXbox(microsoftToken: String): String {
        val payload = """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$microsoftToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        val req = Request.Builder()
            .url("https://user.auth.xboxlive.com/user/authenticate")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "RikkaHub")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw Exception("Xbox 认证失败: ${resp.code}")
            val obj = json.parseToJsonElement(body).jsonObject
            return obj["Token"]?.jsonPrimitive?.contentOrNull ?: throw Exception("无 Xbox token")
        }
    }

    /** 4. XSTS 认证：Xbox token → XSTS token */
    private fun authenticateXsts(xboxToken: String): Pair<String, String> {
        val payload = """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xboxToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        val req = Request.Builder()
            .url("https://xsts.auth.xboxlive.com/xsts/authorize")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "RikkaHub")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw Exception("XSTS 认证失败: ${resp.code}")
            val obj = json.parseToJsonElement(body).jsonObject
            val token = obj["Token"]?.jsonPrimitive?.contentOrNull ?: throw Exception("无 XSTS token")
            val uhs = obj["DisplayClaims"]?.jsonObject
                ?.get("xui")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("uhs")?.jsonPrimitive?.contentOrNull ?: ""
            return token to uhs
        }
    }

    /** 5. Minecraft 登录：XSTS token → Minecraft token + UUID + 用户名 */
    private fun loginMinecraft(xstsToken: String, uhs: String): MinecraftAuthResult {
        val payload = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val req = Request.Builder()
            .url("https://api.minecraftservices.com/authentication/login_with_xbox")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "RikkaHub")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw Exception("Minecraft 登录失败: ${resp.code}")
            val obj = json.parseToJsonElement(body).jsonObject
            val accessToken = obj["access_token"]?.jsonPrimitive?.contentOrNull ?: throw Exception("无 Minecraft token")
            val username = obj["username"]?.jsonPrimitive?.contentOrNull ?: ""
            // 获取 UUID
            val profile = fetchProfile(accessToken)
            return MinecraftAuthResult(
                accessToken = accessToken,
                uuid = profile.first,
                username = username,
            )
        }
    }

    /** 获取 Minecraft 用户 UUID */
    private fun fetchProfile(accessToken: String): Pair<String, String> {
        val req = Request.Builder()
            .url("https://api.minecraftservices.com/minecraft/profile")
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", "RikkaHub")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return "" to ""
            val obj = json.parseToJsonElement(body).jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            return id to name
        }
    }

    /** 完整认证：设备码确认后获取 Minecraft access_token + UUID + 用户名 */
    fun authenticate(deviceCode: String, interval: Long): MinecraftAuthResult {
        val msToken = pollToken(deviceCode, interval)
        val xboxToken = authenticateXbox(msToken)
        val (xstsToken, uhs) = authenticateXsts(xboxToken)
        return loginMinecraft(xstsToken, uhs)
    }
}

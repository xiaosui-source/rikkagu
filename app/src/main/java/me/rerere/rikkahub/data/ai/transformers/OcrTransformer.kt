/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.common.cache.LruCache
import me.rerere.common.cache.SingleFileCacheStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import kotlin.time.Duration.Companion.days

private const val TAG = "OcrTransformer"

object OcrTransformer : InputMessageTransformer, KoinComponent {
    private val cache by lazy {
        val context = get<Context>()
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) {
            return messages
        }

        // 检测消息中是否包含图片: 既检查最外层 parts, 也检查 Tool.output 里的图片
        // (camera_capture 等工具返回的图片存放在 Tool.output 中, 不在最外层 parts)
        val imageParts = messages.flatMap { message ->
            message.parts.flatMap { part ->
                when (part) {
                    is UIMessagePart.Image -> listOf(part)
                    is UIMessagePart.Tool -> part.output.filterIsInstance<UIMessagePart.Image>()
                    else -> emptyList()
                }
            }
        }.filter { it.url.startsWith("file:") }
        if (imageParts.isEmpty()) return messages

        // 所有图片都已识别过（命中缓存）：静默用缓存结果替换，不进入识别流程。
        // 这样历史消息带图、后续纯文字对话时不会每次都强制"正在识别图片..."
        if (imageParts.all { cache.get(it.url) != null }) {
            return messages.map { message ->
                message.copy(
                    parts = message.parts.map { part ->
                        when {
                            part is UIMessagePart.Image && part.url.startsWith("file:") -> {
                                UIMessagePart.Text(cache.get(part.url) ?: performOcr(part))
                            }
                            part is UIMessagePart.Tool -> {
                                part.copy(
                                    output = part.output.map { outputPart ->
                                        if (outputPart is UIMessagePart.Image && outputPart.url.startsWith("file:")) {
                                            UIMessagePart.Text(cache.get(outputPart.url) ?: performOcr(outputPart))
                                        } else {
                                            outputPart
                                        }
                                    }
                                )
                            }
                            else -> part
                        }
                    }
                )
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                ctx.processingStatus.value = "正在识别图片..."
                messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            when {
                                // 最外层图片: OCR 转文字
                                part is UIMessagePart.Image && part.url.startsWith("file:") -> {
                                    UIMessagePart.Text(performOcr(part))
                                }

                                // Tool.output 里的图片: 递归扫描, 把图片替换成 OCR 文字
                                part is UIMessagePart.Tool -> {
                                    part.copy(
                                        output = part.output.map { outputPart ->
                                            when {
                                                outputPart is UIMessagePart.Image && outputPart.url.startsWith("file:") -> {
                                                    UIMessagePart.Text(performOcr(outputPart))
                                                }
                                                else -> outputPart
                                            }
                                        }
                                    )
                                }

                                else -> part
                            }
                        }
                    )
                }
            } finally {
                ctx.processingStatus.value = null
            }
        }
    }

    suspend fun performOcr(part: UIMessagePart.Image): String = runCatching {
        // Check cache first
        cache.get(part.url)?.let { cachedResult ->
            Log.i(TAG, "performOcr: Using cached result for ${part.url}")
            return cachedResult
        }

        val settings = get<SettingsStore>().settingsFlow.value

        // 若配置了 AI 视觉识别模型（ocrModelId），优先用模型识别图片
        if (settings.ocrModelId != null) {
            val visionText = recognizeWithVisionModel(part, settings.ocrPrompt)
            if (visionText.isNotBlank()) {
                val wrapped = wrapOcrText(visionText)
                cache.put(part.url, wrapped)
                return wrapped
            }
            Log.w(TAG, "performOcr: AI 视觉模型未返回结果, 回退本地")
        }

        // 纯本地：同时识别「图片样子」（物体/场景标签）和「图片文字」（OCR），
        // 弱模型也能完整理解图片内容，不依赖外部 API
        if (settings.offlineOcrEnabled) {
            // 1. 识别图片样子（ML Kit 图像标签：物体/场景/内容描述）
            val labelResult = performImageLabeling(part)
            // 2. OCR 文字（ML Kit 中文识别）
            val offlineText = performOfflineOcr(part)
            // 组合：样子 + 文字（都有时都返回，让模型既看到内容也看到文字）
            val combined = buildString {
                if (labelResult.isNotBlank()) {
                    Log.i(TAG, "performOcr: image labeling result: $labelResult")
                    appendLine(labelResult)
                }
                if (offlineText.isNotBlank()) {
                    Log.i(TAG, "performOcr: offline ML Kit result len=${offlineText.length}")
                    append(wrapOcrText(offlineText))
                }
            }
            if (combined.isNotBlank()) {
                cache.put(part.url, combined)
                return combined
            }
            // 样子和文字都识别不出 → 兜底：本地图像分析（尺寸/色调/亮度/条码）
            Log.w(TAG, "performOcr: 标签与OCR均无结果, 使用本地图像分析")
            // ImageLabeling 失败 → 兜底：本地图像分析
            val fallback = analyzeImageLocal(part)
            if (fallback.isNotBlank()) {
                val fbWrapped = wrapOcrText(fallback)
                cache.put(part.url, fbWrapped)
                return fbWrapped
            }
            Log.w(TAG, "performOcr: 本地识图无结果")
        }

        return "[Image]"
        }.getOrElse {
        "[ERROR, OCR failed: $it]"
    }

    /**
     * 用配置的 AI 视觉模型识别图片（ocrModelId）。
     * 使用 settings.ocrPrompt，其中 {images} 替换为图片 URL。
     */
    private suspend fun recognizeWithVisionModel(part: UIMessagePart.Image, ocrPrompt: String): String {
        return runCatching {
            val settings = get<SettingsStore>().settingsFlow.value
            val model = settings.findModelById(settings.ocrModelId ?: return@runCatching "")
                ?: return@runCatching ""
            val provider = model.findProvider(settings.providers) ?: return@runCatching ""
            val providerManager: me.rerere.ai.provider.ProviderManager =
                org.koin.java.KoinJavaComponent.getKoin().get()
            val providerImpl = providerManager.getProviderByType(provider)

            val prompt = ocrPrompt.replace("{images}", part.url)
                .ifBlank { "请识别这张图片里的文字和内容：${part.url}" }

            val messages = listOf(
                UIMessage.system("你是图像识别助手。请识别图片中的文字与内容并返回结果，用简体中文。"),
                UIMessage.user(prompt),
            )

            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(model = model, reasoningLevel = me.rerere.ai.core.ReasoningLevel.AUTO),
            )
            val result = messages.handleMessageChunk(chunk = chunk, model = model)
            result.lastOrNull()?.toText()?.trim().orEmpty()
        }.getOrElse { e ->
            Log.w(TAG, "recognizeWithVisionModel 失败: ${e.message}", e)
            ""
        }
    }

    /** 免费离线 OCR: Google ML Kit 中文文字识别 (本地运行, 免费, 无需联网/API Key). */
    private fun performOfflineOcr(part: UIMessagePart.Image): String {
        val context = get<Context>()
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        return try {
            val uri = android.net.Uri.parse(part.url)
            val image = InputImage.fromFilePath(context, uri)
            val result = Tasks.await(recognizer.process(image))
            result.text?.trim().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "performOfflineOcr 失败: ${e.message}", e)
            ""
        } finally {
            try { recognizer.close() } catch (_: Exception) {}
        }
    }

    /** 本地图像理解（ML Kit Image Labeling）：像"真视觉模型"一样识别图中物体/场景/内容，纯本地小内存
     *  生成自然语言描述而非单纯标签罗列，让弱模型也能"看到"图片里有什么。 */
    private fun performImageLabeling(part: UIMessagePart.Image): String {
        val context = get<Context>()
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        return try {
            val uri = android.net.Uri.parse(part.url)
            val image = InputImage.fromFilePath(context, uri)
            val result = Tasks.await(labeler.process(image))
            if (result.isEmpty()) return ""

            val top = result.sortedByDescending { it.confidence }.take(6)
            val sceneWords = top.filter { it.confidence >= 0.5 }.map { it.text }
            if (sceneWords.isEmpty()) return ""

            // 归类：主体/场景/属性，组装成自然语言描述
            val subjects = sceneWords.take(3)
            val scene = sceneWords.drop(0).joinToString("、")
            buildString {
                append("我看到了这张图片，它")
                append("描绘")
                append(scene)
                append("的画面")
                if (subjects.size > 1 && subjects.isNotEmpty()) {
                    append("，其中包含")
                    append(subjects.joinToString("、"))
                }
                append("。")
                append("（图片关键视觉元素：")
                append(top.joinToString("、") { "${it.text}(${"%.0f%%".format(it.confidence * 100)})" })
                append("）")
            }
        } catch (e: Exception) {
            Log.w(TAG, "performImageLabeling 失败: ${e.message}", e)
            ""
        } finally {
            try { labeler.close() } catch (_: Exception) {}
        }
    }

    /** 本地图片分析：无文字图片 → 提取图片信息（尺寸/平均色/亮度 + 条码识别），纯本地零依赖 */
    private fun analyzeImageLocal(part: UIMessagePart.Image): String {
        val context = get<Context>()
        return try {
            val uri = android.net.Uri.parse(part.url)
            val bitmap = me.rerere.rikkahub.utils.ImageUtils.loadOptimizedBitmap(context, uri, maxSize = 512) ?: return ""
            try {
                val w = bitmap.width
                val h = bitmap.height
                // 平均色 + 亮度（采样统计）
                var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
                val step = maxOf(1, (w * h) / 2000)
                var x = 0
                while (x < w) {
                    var y = 0
                    while (y < h) {
                        val p = bitmap.getPixel(x, y)
                        rSum += android.graphics.Color.red(p)
                        gSum += android.graphics.Color.green(p)
                        bSum += android.graphics.Color.blue(p)
                        count++
                        y += step
                    }
                    x += step
                }
                if (count == 0) return ""
                val avgR = (rSum / count).toInt()
                val avgG = (gSum / count).toInt()
                val avgB = (bSum / count).toInt()
                val hex = String.format("#%02X%02X%02X", avgR, avgG, avgB)
                val brightness = (avgR * 0.299 + avgG * 0.587 + avgB * 0.114).toInt()
                buildString {
                    append("图片视觉信息：尺寸 ${w}x${h}px，平均色调 $hex，亮度 $brightness/255")

                    // 亮度分区统计（亮/暗占比），判断照片明暗氛围
                    var darkCount = 0; var brightCount = 0
                    val step2 = maxOf(1, (w * h) / 800)
                    var xx = 0
                    while (xx < w) {
                        var yy = 0
                        while (yy < h) {
                            val p = bitmap.getPixel(xx, yy)
                            val lum = (android.graphics.Color.red(p) * 0.299 + android.graphics.Color.green(p) * 0.587 + android.graphics.Color.blue(p) * 0.114).toInt()
                            if (lum < 64) darkCount++ else if (lum > 192) brightCount++
                            yy += step2
                        }
                        xx += step2
                    }
                    val total = maxOf(1, (h / step2) * (w / step2))
                    val darkPct = (darkCount * 100.0 / total).toInt()
                    val brightPct = (brightCount * 100.0 / total).toInt()
                    append("，暗部占比${darkPct}%，亮部占比${brightPct}%")
                    append(if (brightPct > 70) "（画面偏亮/明亮）" else if (darkPct > 70) "（画面偏暗/昏暗）" else "（明暗均衡）")

                    // 饱和度：从平均 RGB 与灰度值的偏差估算
                    val saturation = (((maxOf(avgR, avgG, avgB) - minOf(avgR, avgG, avgB)) * 100) / 255).toInt()
                    append("，饱和度${saturation}%")
                    append(if (saturation > 60) "（色彩鲜艳）" else if (saturation < 20) "（接近黑白/灰阶）" else "（色彩适中）")

                    // 条码/二维码识别（已有依赖 barcode-scanning）
                    try {
                        val barcode = scanBarcode(uri)
                        if (barcode.isNotBlank()) append("，条码内容：$barcode")
                    } catch (_: Exception) {}
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "analyzeImageLocal 失败: ${e.message}", e)
            ""
        }
    }

    /** 本地条码/二维码识别（ML Kit barcode-scanning，纯本地） */
    private fun scanBarcode(uri: android.net.Uri): String {
        val context = get<Context>()
        val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder().build()
        )
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val result = Tasks.await(scanner.process(image))
            result.firstOrNull()?.rawValue?.take(200) ?: ""
        } catch (e: Exception) {
            ""
        } finally {
            try { scanner.close() } catch (_: Exception) {}
        }
    }

    private fun wrapOcrText(content: String): String =
        """
            <image_visual_description>
               $content
            </image_visual_description>
            * The image_visual_description tag contains a visual understanding (what the image looks like and any text inside it) that was produced by a local vision analyzer. Use it to answer the user. It is not part of the user's own prompt.
        """.trimIndent()
}

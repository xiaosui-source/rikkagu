/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.asr.providers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus

/**
 * 免费系统语音识别 (Android SpeechRecognizer).
 *
 * 使用设备上的系统语音识别服务, 无需 API Key, 离线可用 (取决于系统引擎是否装离线包).
 */
class SystemASRController(
    private val context: Context,
    private val setting: ASRProviderSetting.SystemAsr,
) : ASRController {

    private val _state = MutableStateFlow(ASRState())
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private val pendingResults = StringBuilder()

    override fun start(onTranscriptChange: (String) -> Unit) {
        this.onTranscriptChange = onTranscriptChange
        pendingResults.setLength(0)
        try {
            recognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.update { it.copy(status = ASRStatus.Listening, isAvailable = true) }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.update { it.copy(status = ASRStatus.Stopping) }
                }
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "网络错误 (离线包未装时在线识别需要网络)"
                        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER -> "识别服务异常"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            "当前识别引擎不支持所选语言（若设置了自定义语言请改为系统默认）"
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                            "识别服务已断开，请稍后重试"
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                            "识别请求过于频繁，请稍后重试"
                        else -> "识别错误 ($error)"
                    }
                    Log.w(TAG, "onError: $error -> $msg")
                    _state.update { it.copy(status = ASRStatus.Error, errorMessage = msg) }
                    stop()
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        pendingResults.append(text)
                        _state.update { it.copy(transcript = pendingResults.toString()) }
                        onTranscriptChange?.invoke(text)
                    }
                    _state.update { it.copy(status = ASRStatus.Idle) }
                    stop()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onTranscriptChange?.invoke(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (setting.language.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, setting.language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (setting.prompt.isNotBlank()) putExtra(RecognizerIntent.EXTRA_PROMPT, setting.prompt)
            }
            sr.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "start 失败", e)
            _state.update {
                it.copy(status = ASRStatus.Error, errorMessage = "系统识别不可用: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    override fun stop() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun dispose() {
        stop()
        onTranscriptChange = null
    }

    companion object {
        private const val TAG = "SystemASRController"
    }
}

/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.providers.SystemTTSProvider
import me.rerere.tts.provider.providers.EdgeTTSProvider
import me.rerere.tts.provider.providers.BaiduTTSProvider
import me.rerere.tts.provider.providers.YoudaoTTSProvider
import me.rerere.tts.provider.providers.GoogleFreeTTSProvider
import me.rerere.tts.provider.providers.ElevenLabsTTSProvider

class TTSManager(private val context: Context) {
    private val systemProvider = SystemTTSProvider()
    private val edgeProvider = EdgeTTSProvider()
    private val baiduProvider = BaiduTTSProvider()
    private val youdaoProvider = YoudaoTTSProvider()
    private val googleProvider = GoogleFreeTTSProvider()
    private val elevenLabsProvider = ElevenLabsTTSProvider()

    fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest
    ): Flow<AudioChunk> {
        return when (providerSetting) {
            is TTSProviderSetting.SystemTTS -> systemProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.EdgeTTS -> edgeProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.BaiduTTS -> baiduProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.YoudaoTTS -> youdaoProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.GoogleFreeTTS -> googleProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.generateSpeech(context, providerSetting, request)
        }
    }
}

/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class TTSProviderSetting {
    abstract val id: Uuid
    abstract val name: String

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): TTSProviderSetting

    @Serializable
    @SerialName("system")
    data class SystemTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "System TTS",
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("elevenlabs")
    data class ElevenLabs(
        override var id: Uuid = Uuid.random(),
        override var name: String = "ElevenLabs TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.elevenlabs.io/v1",
        val voiceId: String = "21m00Tcm4TlvDq8ikWAM",
        val modelId: String = "eleven_multilingual_v2",
        val stability: Float = 0.5f,
        val similarityBoost: Float = 0.75f,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("edge_tts")
    data class EdgeTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Edge TTS",
        var voice: String = "zh-CN-XiaoxiaoNeural",
    ) : TTSProviderSetting() {
        override fun copyProvider(id: Uuid, name: String) = copy(id = id, name = name)
    }

    @Serializable
    @SerialName("baidu_tts")
    data class BaiduTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "百度 TTS",
        var speed: Int = 5,
    ) : TTSProviderSetting() {
        override fun copyProvider(id: Uuid, name: String) = copy(id = id, name = name)
    }

    @Serializable
    @SerialName("youdao_tts")
    data class YoudaoTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "有道 TTS",
        var speed: Int = 50,
    ) : TTSProviderSetting() {
        override fun copyProvider(id: Uuid, name: String) = copy(id = id, name = name)
    }

    @Serializable
    @SerialName("google_free_tts")
    data class GoogleFreeTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Google TTS",
        var lang: String = "zh-CN",
    ) : TTSProviderSetting() {
        override fun copyProvider(id: Uuid, name: String) = copy(id = id, name = name)
    }

    companion object {
        val Types by lazy {
            listOf(
                SystemTTS::class,
                ElevenLabs::class,
                EdgeTTS::class,
                BaiduTTS::class,
                YoudaoTTS::class,
                GoogleFreeTTS::class,
            )
        }
    }
}

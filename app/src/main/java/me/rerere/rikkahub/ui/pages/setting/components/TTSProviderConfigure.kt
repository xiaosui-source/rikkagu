/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.tts.provider.TTSProviderSetting

@Composable
fun TTSProviderConfigure(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        // Provider type selector
        var expanded by remember { mutableStateOf(false) }

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_provider_type)) },
            description = { Text(stringResource(R.string.setting_tts_page_provider_type_description)) },
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = when (setting) {
                        is TTSProviderSetting.SystemTTS -> "System TTS"
                        is TTSProviderSetting.EdgeTTS -> "Edge"
                    is TTSProviderSetting.BaiduTTS -> "百度"
                    is TTSProviderSetting.YoudaoTTS -> "有道"
                    is TTSProviderSetting.GoogleFreeTTS -> "Google"
                    is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val freeProviders = listOf(
                    TTSProviderSetting.SystemTTS::class,
                    TTSProviderSetting.EdgeTTS::class,
                    TTSProviderSetting.BaiduTTS::class,
                    TTSProviderSetting.YoudaoTTS::class,
                    TTSProviderSetting.GoogleFreeTTS::class,
                    TTSProviderSetting.ElevenLabs::class,
                )
                    val paidProviders = listOf(
                        TTSProviderSetting.ElevenLabs::class,
                    )
                freeProviders.forEach { providerClass ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (providerClass) {
                                    TTSProviderSetting.SystemTTS::class -> "系统 TTS (免费)"
                                    TTSProviderSetting.EdgeTTS::class -> "Edge TTS (免费)"
                                    TTSProviderSetting.BaiduTTS::class -> "百度 TTS (免费)"
                                    TTSProviderSetting.YoudaoTTS::class -> "有道 TTS (免费)"
                                    TTSProviderSetting.GoogleFreeTTS::class -> "Google TTS (免费)"
                                    TTSProviderSetting.ElevenLabs::class -> "ElevenLabs (需 API Key)"
                                    else -> providerClass.simpleName ?: "Unknown"
                                }
                            )
                        },
                        onClick = {
                            expanded = false
                            val newSetting = when (providerClass) {
                                TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS(
                                    id = setting.id,
                                    name = "系统 TTS"
                                )

                                TTSProviderSetting.EdgeTTS::class -> TTSProviderSetting.EdgeTTS(
                                    id = setting.id,
                                    name = "Edge TTS"
                                )

                                TTSProviderSetting.BaiduTTS::class -> TTSProviderSetting.BaiduTTS(
                                    id = setting.id,
                                    name = "百度 TTS"
                                )

                                TTSProviderSetting.YoudaoTTS::class -> TTSProviderSetting.YoudaoTTS(
                                    id = setting.id,
                                    name = "有道 TTS"
                                )

                                TTSProviderSetting.GoogleFreeTTS::class -> TTSProviderSetting.GoogleFreeTTS(
                                    id = setting.id,
                                    name = "Google TTS"
                                )

                                TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs(
                                    id = setting.id,
                                    name = "ElevenLabs"
                                )

                                else -> setting
                            }
                            onValueChange(newSetting)
                        }
                    )
                }
                    paidProviders.forEach { providerClass ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (providerClass) {
                                        TTSProviderSetting.ElevenLabs::class -> "ElevenLabs TTS (付费)"
                                        else -> providerClass.simpleName ?: "Unknown"
                                    }
                                )
                            },
                            onClick = {
                                expanded = false
                                val newSetting = when (providerClass) {
                                    TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs(
                                        id = setting.id,
                                        name = "ElevenLabs TTS"
                                    )

                                    else -> setting
                                }
                                onValueChange(newSetting)
                            }
                        )
                    }
                }
            }
        }

        // Name
        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_name)) },
            description = { Text(stringResource(R.string.setting_tts_page_name_description)) }
        ) {
            OutlinedTextField(
                value = setting.name,
                onValueChange = { newName ->
                    onValueChange(setting.copyProvider(name = newName))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_name_placeholder)) }
            )
        }

        // Provider-specific fields
        when (setting) {
            is TTSProviderSetting.SystemTTS -> SystemTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.EdgeTTS -> FreeTTSConfiguration("Edge TTS", "Microsoft 免费接口，无需填写任何参数，开箱即用")
            is TTSProviderSetting.BaiduTTS -> FreeTTSConfiguration("百度 TTS", "免费接口，无需填写任何参数，开箱即用")
            is TTSProviderSetting.YoudaoTTS -> FreeTTSConfiguration("有道 TTS", "免费接口，无需填写任何参数，开箱即用")
            is TTSProviderSetting.GoogleFreeTTS -> FreeTTSConfiguration("Google TTS", "免费接口，无需填写任何参数，开箱即用")
            is TTSProviderSetting.ElevenLabs -> ElevenLabsTTSConfiguration(setting, onValueChange)
        }
    }
}

@Composable
private fun FreeTTSConfiguration(title: String, description: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Text(
            text = description,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SystemTTSConfiguration(
    setting: TTSProviderSetting.SystemTTS,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // Speech Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speech_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_speech_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speechRate,
            onValueChange = { newRate ->
                if (newRate in 0.1f..3.0f) {
                    onValueChange(setting.copy(speechRate = newRate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speech_rate)
        )
    }

    // Pitch
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_pitch)) },
        description = { Text(stringResource(R.string.setting_tts_page_pitch_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.pitch,
            onValueChange = { newPitch ->
                if (newPitch in 0.1f..2.0f) {
                    onValueChange(setting.copy(pitch = newPitch))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_pitch)
        )
    }
}

@Composable
private fun ElevenLabsTTSConfiguration(
    setting: TTSProviderSetting.ElevenLabs,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk_xxx") },
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.elevenlabs.io/v1") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_id_description)) }
    ) {
        OutlinedTextField(
            value = setting.voiceId,
            onValueChange = { newVoiceId ->
                onValueChange(setting.copy(voiceId = newVoiceId))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("21m00Tcm4TlvDq8ikWAM") }
        )
    }

    var modelExpanded by remember { mutableStateOf(false) }
    val models = listOf("eleven_multilingual_v2", "eleven_flash_v2_5", "eleven_turbo_v2_5", "eleven_v3")

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = !modelExpanded }
        ) {
            OutlinedTextField(
                value = setting.modelId,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(modelId = newModel))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            modelExpanded = false
                            onValueChange(setting.copy(modelId = model))
                        }
                    )
                }
            }
        }
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_stability)) },
        description = { Text(stringResource(R.string.setting_tts_page_stability_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.stability,
            onValueChange = { newVal ->
                if (newVal in 0f..1f) {
                    onValueChange(setting.copy(stability = newVal))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_stability)
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_similarity_boost)) },
        description = { Text(stringResource(R.string.setting_tts_page_similarity_boost_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.similarityBoost,
            onValueChange = { newVal ->
                if (newVal in 0f..1f) {
                    onValueChange(setting.copy(similarityBoost = newVal))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_similarity_boost)
        )
    }
}

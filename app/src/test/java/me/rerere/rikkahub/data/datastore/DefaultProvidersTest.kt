/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersTest {
    @Test
    fun `default providers should include 讯飞星火 with expected config`() {
        val providers = DEFAULT_PROVIDERS
            .filterIsInstance<ProviderSetting.OpenAI>()
            .filter { it.name == "讯飞星火" }

        assertEquals(1, providers.size)

        val provider = providers.single()
        assertEquals("https://spark-api-open.xf-yun.com/v1", provider.baseUrl)
        assertFalse(provider.enabled)
        assertTrue(provider.builtIn)
    }

    @Test
    fun `default providers should include NVIDIA as default enabled provider`() {
        val nvidia = DEFAULT_PROVIDERS
            .filterIsInstance<ProviderSetting.OpenAI>()
            .filter { it.name == "NVIDIA" }

        assertEquals(1, nvidia.size)
        assertTrue(nvidia.single().enabled)
        assertTrue(nvidia.single().builtIn)
    }

    @Test
    fun `default providers should have 10 providers`() {
        assertEquals(10, DEFAULT_PROVIDERS.size)
    }
}

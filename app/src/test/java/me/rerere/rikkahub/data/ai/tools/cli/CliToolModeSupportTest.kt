/*
 * 灵犀 Lingxi
 * 测试 CliToolModeSupport
 */

package me.rerere.rikkahub.data.ai.tools.cli

import me.rerere.ai.core.Tool
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliToolModeSupportTest {
    private val cliSupport = CliToolModeSupport()

    private val allTools = listOf(
        Tool(name = "calculator", description = "计算器"),
        Tool(name = "browser_navigate", description = "浏览器导航"),
        Tool(name = "app_lock", description = "应用锁定"),
        Tool(name = "bluetooth_pair", description = "蓝牙配对"),
        Tool(name = "send_sms", description = "发送短信"),
        Tool(name = "find_files", description = "查找文件"),
    )

    @Test
    fun testFullModeReturnsAll() {
        val result = cliSupport.filterByMode(allTools, ToolExposureMode.FULL)
        assertEquals(6, result.size)
    }

    @Test
    fun testCliModeFiltersDangerousTools() {
        val result = cliSupport.filterByMode(allTools, ToolExposureMode.CLI)
        
        val toolNames = result.map { it.name }
        assertFalse(toolNames.contains("app_lock"))
        assertFalse(toolNames.contains("bluetooth_pair"))
        assertFalse(toolNames.contains("send_sms"))
        
        assertTrue(toolNames.contains("calculator"))
        assertTrue(toolNames.contains("browser_navigate"))
        assertTrue(toolNames.contains("find_files"))
    }

    @Test
    fun testHiddenCatalog() {
        val hidden = cliSupport.getHiddenCatalog(allTools)
        assertEquals(3, hidden.size)
        assertEquals("app_lock", hidden[0].targetToolName)
        assertEquals("bluetooth_pair", hidden[1].targetToolName)
        assertEquals("send_sms", hidden[2].targetToolName)
    }
}

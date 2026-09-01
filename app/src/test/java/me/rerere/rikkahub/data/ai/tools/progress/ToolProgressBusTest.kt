/*
 * 灵犀 Lingxi
 * 测试 ToolProgressBus
 */

package me.rerere.rikkahub.data.ai.tools.progress

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolProgressBusTest {
    @Before
    fun setUp() {
        ToolProgressBus.reset()
    }

    @Test
    fun testInitialState() = runBlocking {
        val current = ToolProgressBus.progress.first()
        assertNull(current)
    }

    @Test
    fun testUpdateAndRead() = runBlocking {
        ToolProgressBus.update("test_tool", 0.5f, "50%")
        val event = ToolProgressBus.progress.first()
        assertEquals("test_tool", event?.toolName)
        assertEquals(0.5f, event?.progress)
        assertEquals("50%", event?.message)
    }

    @Test
    fun testPriorityFiltering() = runBlocking {
        // 低优先级事件
        ToolProgressBus.update("low_priority", 0.3f, "low", priority = 0)
        // 高优先级事件应该覆盖
        ToolProgressBus.update("high_priority", 0.7f, "high", priority = 100)
        val event = ToolProgressBus.progress.first()
        assertEquals("high_priority", event?.toolName)
        assertEquals(100, event?.priority)
    }

    @Test
    fun testClear() = runBlocking {
        ToolProgressBus.update("test", 0.5f)
        ToolProgressBus.clear()
        val event = ToolProgressBus.progress.first()
        assertNull(event)
    }

    @Test
    fun testProgressCoercion() = runBlocking {
        // 超过 1.0 应该被截断为 1.0
        ToolProgressBus.update("test", 1.5f)
        val event = ToolProgressBus.progress.first()
        assertEquals(1.0f, event?.progress)

        // 负数应该被截断为 0.0
        ToolProgressBus.reset()
        ToolProgressBus.update("test", -0.5f)
        val event2 = ToolProgressBus.progress.first()
        assertEquals(0.0f, event2?.progress)
    }

    @Test
    fun testDefaultPriority() = runBlocking {
        // grep 工具应该有高优先级
        ToolProgressBus.update("grep_code", 0.5f)
        val event = ToolProgressBus.progress.first()
        assertEquals(100, event?.priority)

        // 普通工具优先级为 0
        ToolProgressBus.reset()
        ToolProgressBus.update("random_tool", 0.5f)
        val event2 = ToolProgressBus.progress.first()
        assertEquals(0, event2?.priority)
    }
}

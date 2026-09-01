/*
 * 灵犀 Lingxi
 * 测试 ChatMemoryWindowPlanner
 */

package me.rerere.rikkahub.data.memory.scheduler

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatMemoryWindowPlannerTest {
    // 简化的消息模型用于测试
    data class TestMessage(
        val id: Long,
        val role: String,
        val content: String,
        val timestamp: Long,
    )

    @Test
    fun testPlanBasic() {
        val messages = listOf(
            TestMessage(1, "user", "Hello", 1L),
            TestMessage(2, "assistant", "Hi there!", 2L),
            TestMessage(3, "user", "How are you?", 3L),
            TestMessage(4, "assistant", "I'm fine!", 4L),
        )

        val windows = ChatMemoryWindowPlanner.plan(
            messages.map { me.rerere.rikkahub.data.model.MessageNode(
                id = it.id,
                role = it.role,
                content = it.content,
                timestamp = it.timestamp,
            ) },
            windowMessageCount = 4
        )

        assertEquals(1, windows.size)
    }

    @Test
    fun testPlanMultipleWindows() {
        val messages = (1..20).map { i ->
            TestMessage(
                id = i.toLong(),
                role = if (i % 2 == 1) "user" else "assistant",
                content = "Message $i",
                timestamp = i.toLong(),
            )
        }

        val windows = ChatMemoryWindowPlanner.plan(
            messages.map { me.rerere.rikkahub.data.model.MessageNode(
                id = it.id,
                role = it.role,
                content = it.content,
                timestamp = it.timestamp,
            ) },
            windowMessageCount = 8
        )

        assertTrue(windows.size >= 2)
    }

    @Test
    fun testWindowBounds() {
        assertEquals(8, ChatMemoryWindowPlanner.MIN_WINDOW_MESSAGE_COUNT)
        assertEquals(32, ChatMemoryWindowPlanner.DEFAULT_WINDOW_MESSAGE_COUNT)
        assertEquals(48, ChatMemoryWindowPlanner.MAX_WINDOW_MESSAGE_COUNT)
    }
}

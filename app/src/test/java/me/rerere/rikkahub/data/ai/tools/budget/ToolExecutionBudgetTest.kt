/*
 * 灵犀 Lingxi
 * 测试 ToolExecutionBudget
 */

package me.rerere.rikkahub.data.ai.tools.budget

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolExecutionBudgetTest {
    private lateinit var budget: ToolExecutionBudget

    @Before
    fun setUp() {
        budget = ToolExecutionBudget(
            maxConcurrentExecutions = 3,
            maxTotalExecutions = 10,
            maxExecutionsPerTool = 5,
        )
    }

    @Test
    fun testCanExecute() = runBlocking {
        assertTrue(budget.canExecute("test_tool"))
    }

    @Test
    fun testStartExecution() = runBlocking {
        val result = budget.startExecution("test_tool")
        assertTrue(result)
        
        val stats = budget.getStats()
        assertEquals(1, stats.current)
        assertEquals(1, stats.total)
        assertEquals(1, stats.perTool["test_tool"])
    }

    @Test
    fun testMaxConcurrentLimit() = runBlocking {
        budget.startExecution("tool_a")
        budget.startExecution("tool_b")
        budget.startExecution("tool_c")
        
        // 已达并发上限
        assertFalse(budget.canExecute("tool_d"))
    }

    @Test
    fun testEndExecution() = runBlocking {
        budget.startExecution("test_tool")
        budget.endExecution()
        
        val stats = budget.getStats()
        assertEquals(0, stats.current)
    }

    @Test
    fun testMaxPerToolLimit() = runBlocking {
        repeat(5) { budget.startExecution("limited_tool") }
        
        // 已达每工具上限
        assertFalse(budget.canExecute("limited_tool"))
    }

    @Test
    fun testReset() = runBlocking {
        budget.startExecution("test_tool")
        budget.reset()
        
        val stats = budget.getStats()
        assertEquals(0, stats.current)
        assertEquals(0, stats.total)
        assertTrue(stats.perTool.isEmpty())
    }
}

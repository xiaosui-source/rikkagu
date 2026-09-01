/*
 * 灵犀 Lingxi
 * 测试 UserscriptManager
 */

package me.rerere.rikkahub.data.ai.tools.userscript

import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserscriptManagerTest {
    private lateinit var manager: UserscriptManager
    private lateinit var scriptsDir: File

    @Before
    fun setUp() {
        scriptsDir = File("/tmp/test_scripts_${System.currentTimeMillis()}")
        scriptsDir.mkdirs()
        manager = UserscriptManager(scriptsDir)
    }

    @Test
    fun testAddAndRemoveScript() {
        val scriptFile = File(scriptsDir, "test.user.js").apply {
            writeText("""
                // ==UserScript==
                // @name         Test Script
                // @namespace    http://test.com
                // @version      1.0
                // @description  A test script
                // @match        https://example.com/*
                // @run-at       document-idle
                // ==/UserScript==
                
                (function() {
                    console.log('Hello World');
                })();
            """.trimIndent())
        }

        val result = manager.addScript(scriptFile)
        assertTrue(result)

        val scripts = manager.getInstalledScripts()
        assertEquals(1, scripts.size)
        assertEquals("Test Script", scripts[0].name)
        assertEquals("http://test.com", scripts[0].namespace)

        val removed = manager.removeScript("test")
        assertTrue(removed)
        assertTrue(manager.getInstalledScripts().isEmpty())
    }

    @Test
    fun testMatchScript() {
        val scriptFile = File(scriptsDir, "example.user.js").apply {
            writeText("""
                // ==UserScript==
                // @name         Example Script
                // @match        https://example.com/*
                // ==/UserScript==
            """.trimIndent())
        }
        manager.addScript(scriptFile)

        assertNotNull(manager.matchScript("https://example.com/page"))
        assertNull(manager.matchScript("https://other.com/page"))
    }

    @Test
    fun testExcludePatterns() {
        val scriptFile = File(scriptsDir, "exclude.user.js").apply {
            writeText("""
                // ==UserScript==
                // @name         Exclude Script
                // @match        https://example.com/*
                // @exclude      https://example.com/admin/*
                // ==/UserScript==
            """.trimIndent())
        }
        manager.addScript(scriptFile)

        assertNotNull(manager.matchScript("https://example.com/page"))
        assertNull(manager.matchScript("https://example.com/admin/panel"))
    }

    @Test
    fun testClearScripts() {
        manager.addScript(File(scriptsDir, "s1.user.js").apply {
            writeText("// @name S1\n// @match https://a.com/*\n(function(){})()")
        })
        manager.addScript(File(scriptsDir, "s2.user.js").apply {
            writeText("// @name S2\n// @match https://b.com/*\n(function(){})()")
        })

        manager.clearScripts()
        assertTrue(manager.getInstalledScripts().isEmpty())
    }

    @Test
    fun testDuplicateScriptRejected() {
        val content = """
            // ==UserScript==
            // @name         Duplicate Test
            // @match        https://test.com/*
            // ==/UserScript==
        """.trimIndent()

        val file1 = File(scriptsDir, "dup.user.js").apply { writeText(content) }
        val file2 = File(scriptsDir, "dup_copy.user.js").apply { writeText(content) }

        manager.addScript(file1)
        assertFalse(manager.addScript(file2))
        assertEquals(1, manager.getInstalledScripts().size)
    }
}

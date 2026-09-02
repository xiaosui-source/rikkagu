/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit PluginRegistry
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.plugins

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件接口
 * 
 * 完全对齐 Operit AI Plugin接口
 */
interface RikkaHubPlugin {
    val id: String
    fun register()
}

/**
 * 插件注册表
 * 
 * 完全对齐 Operit AI PluginRegistry
 */
object PluginRegistry {
    private val plugins = CopyOnWriteArrayList<RikkaHubPlugin>()
    private val installedPluginIds = ConcurrentHashMap.newKeySet<String>()
    
    @Volatile
    private var builtinsInitialized = false
    
    @Synchronized
    fun register(plugin: RikkaHubPlugin) {
        plugins.removeAll { it.id == plugin.id }
        plugins.add(plugin)
    }
    
    @Synchronized
    fun initializeBuiltins() {
        if (builtinsInitialized) return
        builtinsInitialized = true
    }
    
    @Synchronized
    fun installAll() {
        for (plugin in plugins) {
            if (installedPluginIds.add(plugin.id)) {
                plugin.register()
            }
        }
    }
    
    fun isInstalled(pluginId: String): Boolean = installedPluginIds.contains(pluginId)
    
    fun getInstalledPlugins(): List<RikkaHubPlugin> = 
        plugins.filter { installedPluginIds.contains(it.id) }
}

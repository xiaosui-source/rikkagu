/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools.local

/**
 * Bridge the workflow engine's `notification_received` trigger pre-flight check to the
 * notification listener connection state. The engine calls [isBound] before firing a
 * notification-triggered workflow so a missing listener surfaces as a clear FAILED row
 * ("notification_listener_not_enabled") instead of silently never matching.
 *
 * [me.rerere.rikkahub.data.service.RikkaNotificationListenerService] flips [connected] from
 * its onListenerConnected / onListenerDisconnected callbacks.
 */
object NotificationListenerHandle {
    @Volatile
    var connected: Boolean = false

    fun isBound(): Boolean = connected
}

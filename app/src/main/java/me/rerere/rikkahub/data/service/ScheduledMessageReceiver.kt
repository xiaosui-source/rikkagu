/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（ScheduledMessageReceiver：定时消息广播接收器）
 */

package me.rerere.rikkahub.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 定时消息广播接收器：收到广播后启动 [ScheduledMessageService] 发送消息.
 */
class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val serviceIntent = ScheduledMessageService.buildIntent(
                context = context,
                conversationId = intent.getStringExtra(ScheduledMessageService.EXTRA_CONVERSATION_ID) ?: return,
                messageText = intent.getStringExtra(ScheduledMessageService.EXTRA_MESSAGE_TEXT) ?: return,
                assistantId = intent.getStringExtra(ScheduledMessageService.EXTRA_ASSISTANT_ID),
            )
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "start scheduled message service failed", e)
        }
    }

    companion object {
        private const val TAG = "ScheduledMsgReceiver"
    }
}

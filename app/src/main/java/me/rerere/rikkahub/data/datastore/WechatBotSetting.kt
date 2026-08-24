/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

/**
 * 微信 Bot (iLink 协议) 配置. 支持多个 bot.
 *
 * 设计理念: 微信 bot 是给某个助手多开一个"微信消息通道".
 * 每个 bot 独立 token/助手, 服务为每个 bot 单独长轮询.
 *
 * 字段:
 *  - [id]: 唯一标识 (添加时生成 UUID), 多 bot 管理与轮询任务对应.
 *  - [enabled]: 总开关.
 *  - [assistantId]: 关联的助手. 留空 = 用当前助手.
 *  - [botToken]: 扫码登录后拿到的 Bearer token.
 *  - [baseUrl]: iLink 服务器地址.
 *  - [botId]: 本机微信号对应的 ilink_bot_id (登录后下发).
 */
@Serializable
data class WechatBotSetting(
    val id: String = "",
    val enabled: Boolean = false,
    val assistantId: String = "",
    val botToken: String = "",
    val baseUrl: String = "https://ilinkai.weixin.qq.com",
    val botId: String = "",
    val pollIntervalSec: Int = 1,
)

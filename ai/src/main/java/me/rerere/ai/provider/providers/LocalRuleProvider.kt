/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

/**
 * 本地规则模型 Provider（离线小助手）
 *
 * 完全本地运行，不依赖网络、API Key 或任何模型文件。
 * 直接内置规则引擎，可进行智能对话，并能在工具链路(GenerationHandler 兜底)中
 * 读取已执行工具的结果，用自然语言组织成回答，实现"能调用正常工具的小 AI"。
 */
class LocalRuleProvider : Provider<ProviderSetting.LocalRule> {

    override suspend fun listModels(providerSetting: ProviderSetting.LocalRule): List<Model> {
        return listOf(
            Model(modelId = "local-rule", displayName = "离线小助手")
        )
    }

    /** 提取用户最后一条纯文本内容 */
    private fun lastUserText(messages: List<UIMessage>): String {
        return messages.lastOrNull { it.role == MessageRole.USER }
            ?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            ?.trim()
            .orEmpty()
    }

    /** 从消息中读取最近一次已执行工具的结果文字 */
    private fun lastToolOutputText(messages: List<UIMessage>): String? {
        // 从后往前找第一条包含已执行工具的 assistant 消息
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            val tool = m.parts
                .filterIsInstance<UIMessagePart.Tool>()
                .firstOrNull { it.isExecuted }
            if (tool != null) {
                val text = tool.output
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("") { it.text }
                    .trim()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }

    /** 生成回复：优先基于工具结果，否则走智能对话规则 */
    private fun buildReply(messages: List<UIMessage>): String {
        val userText = lastUserText(messages)

        // 1. 如果有工具结果，把它组织成自然的回答（承接工具执行链）
        val toolResult = lastToolOutputText(messages)
        if (toolResult != null) {
            return summarizeToolResult(toolResult, userText)
        }

        // 2. 空输入问候
        if (userText.isBlank()) {
            return "我是完全离线的本地小助手，随时可以陪你聊天。你可以问问天气、算个数学题，或让我记住一些事情。"
        }

        // 3. 智能对话规则
        val lower = userText.lowercase()

        // 问候
        if (Regex("你好|您好|hello|hi[!？]?$|嗨|哈喽|在吗|早|早上好|中午好|下午好|晚上好|晚安").containsMatchIn(lower)) {
            return when {
                lower.contains("早") -> "早上好！新的一天，元气满满。想聊点什么？"
                lower.contains("晚安") -> "晚安！愿你做个好梦，明天见 🌙"
                lower.contains("晚上") -> "晚上好呀！有需要帮忙的吗？"
                else -> "你好呀！我是本地小助手，完全离线也能陪你聊天。有什么想聊的？"
            }
        }

        // 感谢
        if (lower.contains("谢谢") || lower.contains("感谢") || lower.contains("thank")) {
            return "不客气，能帮到你就好！"
        }

        // 告别
        if (lower.contains("再见") || lower.contains("拜拜") || lower.contains("bye")) {
            return "再见，照顾好自己，随时找我！"
        }

        // 计算器
        val calc = tryCalc(lower)
        if (calc != null) return calc

        // 简单数学/数字
        if (lower.contains("等于") && lower.contains("+") && lower.contains("?")) {
            // 已由 tryCalc 处理
        }

        // 面积/单位换算
        val unit = convertUnit(lower)
        if (unit != null) return unit

        // 自我介绍
        if (lower.contains("你是谁") || lower.contains("你叫什么") || lower.contains("介绍一下你")) {
            return "我是一个完全本地运行的离线小助手，不需要网络、API 或模型文件，直接在设备内用内置规则工作，保护你的隐私。我知道怎么帮你做计算、换算、简单问答，还能通过内置工具帮你搜索/查火车票/记事等（需要能用配套工具时）。"
        }

        // 能不能离线
        if (lower.contains("离线") || lower.contains("不需要网络") || lower.contains("不用网")) {
            return "是的，我完全离线运行，不联网、不传数据、也不需要任何 API Key。所有对话都在手机内本地完成。"
        }

        // 会什么
        if (lower.contains("你会什么") || lower.contains("能做什么") || lower.contains("会做什么") || lower.contains("功能")) {
            return "我可以做这些：\n• 简单聊天和问候\n• 数学计算（如 12×7+3 等于多少）\n• 单位换算（如 5公里等于多少米）\n• 通过内置工具：搜索/查火车票/记事情/处理 APK 逆向等（命中相关指令时自动调用）\n当然也可以提醒你我是离线小助手。"
        }

        // 隐私
        if (lower.contains("隐私") || lower.contains("数据") || lower.contains("上传") || lower.contains("泄露")) {
            return "别担心，我是离线小助手，你的对话不会上传到任何服务器，全部在设备本地处理，隐私安全有保障。"
        }

        // 情绪
        if (lower.contains("心情") || lower.contains("开心") || lower.contains("难过")) {
            return "不管心情如何，都欢迎和我说说！我是你的本地小助手，会一直在你身边。😊"
        }

        // 地址查询类，引导用工具
        if (lower.contains("搜索") || lower.contains("查") || lower.contains("帮我看") || lower.contains("帮我查")) {
            return "好的，我来帮你查 ~ （正在调用本地工具处理，请稍等）"
        }

        // 讲笑话 / 冷笑话
        if (lower.contains("笑话") || lower.contains("幽默") || lower.contains("讲一个")) {
            return "好，来一个冷笑话：\n" + jokes.random()
        }

        // 心情/状态接话
        if (lower.contains("今天") || lower.contains("最近") || lower.contains("怎么样")) {
            return pickOne(
                "最近怎么样呀？我这边一切正常，就等你来聊天呢 😄",
                "每天都一样，不过能陪你聊天就很开心！",
                "哈哈，生活就是起起落落，跟我说说怎么了？",
            )
        }

        // 什么事/疑问类
        if (lower.contains("？") || lower.contains("吗") || lower.contains("什么") ||
            lower.contains("怎么") || lower.contains("为什么") || lower.contains("能不能")
        ) {
            return pickOne(
                "嗯，这个问题让我想想……作为离线小助手，我能帮你算数、换算单位，或聊聊家常。你想深入了解哪方面？",
                "好问题！不过我得老实说，我是本地小助手，太专业的知识要联网的模型才知道。但我可以陪你聊聊别的 😄",
                "这个嘛……虽然我不太确定正确答案，但我知道你是在认真问我问题，这点就很棒！有什么我能帮上的？",
            )
        }

        // 陈述/闲聊类通用接话
        if (lower.contains("喜欢") || lower.contains("爱") || lower.contains("讨厌")) {
            return pickOne(
                "哈哈，每个人都有自己的一偏好呀！说起来，我也喜欢像你这样愿意聊天的朋友。",
                "喜欢的东西就要多留一份注意力！愿你一直能追到自己喜欢的事。",
            )
        }

        if (lower.contains("无聊") || lower.contains("没意思") || lower.contains("发呆")) {
            return "无聊啊？那我给你讲个冷笑话解闷吧：\n" + jokes.random()
        }

        if (lower.contains("开心") || lower.contains("高兴") || lower.contains("太好了")) {
            return "为你开心！好心情会传染，我被你感染到了 😄 继续保持！"
        }

        // 通用兜底：随机自然接话（不固定复读）
        return pickOne(
            "哈，你这么说也很有意思。其实我对这个话题挺好奇的，能再多说一点吗？",
            "收到收到！虽然我本事不大，但陪你聊聊天、算个数、讲个笑话还是没问题的。还想聊点什么？",
            "嗯嗯，我听你说着呢。对了，你想不想听个笑话放松一下？",
            "有意思！作为离线小助手，我可能给不了太深奥的答案，但陪着你说说话是绰绰有余 ~",
            "嘿嘿，你这话说得有点意思。想继续这个话题，还是换个轻松的聊聊？",
        )
    }

    private val jokes = listOf(
        "为什么打架不能说四遍打架？\n因为打架、打架、打架、打架 = 四打！",
        "海绵宝宝为什么总乐呵呵的？\n因为“身在水下”不用付水费！",
        "为什么鱼只有七秒记忆？\n因为第八秒它就忘了自己是个鱼。",
        "今天太阳很大，我劝它别太热，\n它说：你管得着吗，我又不是你晒黑的。",
        "数学老师问：10 的一半是多少？\n小明抢答：5！老师说不对，是 3（因为我分了 10 的一半给你）……",
        "为什么星星会眨眼？\n因为它们在假装看不见地上的秃头（开玩笑的！）。",
        "科学家说: 热水和冷水不能混。\n我说: 那洗澡怎么办？科学家: 混成温水啊，笨蛋。",
        "为什么鸡要过马路？\n因为那边有肯德基的优惠券（不是，是因为它想去对面啄米）。",
        "有个包子走在路上觉得自己的馅太油，\n后来它就上吊了——变成了烧麦。",
        "为什么警察叔叔站在斑马线上？\n因为他在等红灯，顺便贴条。",
        "小明问: 为什么你那么黑？\n小明妈: 因为我不想白活这一生。",
        "为什么键盘那么难伺候？\n因为一按它就弹，还自带脾气（的一半是空格）。",
        "我问手机: 你为什么不理我？\n手机: 你把我静音了，墨镜戴上我也看不见你。",
        "为什么好人更容易受伤？\n因为坏人跑得快，好人不舍得躲。",
        "有一杯水掉进海里哭了，\n海水说: 别哭，我们都是一体的。",
    )

    private fun pickOne(vararg choices: String): String = choices.random()



    private fun buildChunk(text: String): MessageChunk {
        return MessageChunk(
            id = Uuid.random().toString(),
            model = "local-rule",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(text))
                    ),
                    finishReason = "stop"
                )
            )
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalRule,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = buildChunk(buildReply(messages))

    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalRule,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        emit(buildChunk(buildReply(messages)))
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.LocalRule,
        params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult = error("离线小助手不支持向量嵌入")

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult = error("离线小助手不支持图像生成")

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams,
    ): ImageGenerationResult = error("离线小助手不支持图像编辑")


    /** 尝试把数字表达式计算出来 */
    private fun tryCalc(text: String): String? {
        // 支持 + - × * ÷ / % ^ 混合，括号，小数
        val cleaned = text
            .replace("等于", "")
            .replace("多少", "")
            .replace("=?？".toRegex(), "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("X", "*")
            .replace("x", "*")
            .trim()
        if (!containsCalcOp(cleaned)) return null
        val expr = cleaned.filter { c -> c.isDigit() || c in "+-*/().%^ " }
        if (expr.length != cleaned.length) {
            // 表达式混入了非数字文字（如"公里"），交给其他规则
            return null
        }
        return runCatching {
            val result = evalExpression(expr)
            if (result.isFinite() && result % 1.0 != 0.0) {
                "结果是：${String.format("%.6f", result).trimEnd('0').trimEnd('.')}"
            } else {
                "结果是：${result.toLong()}"
            }
        }.getOrNull()
    }

    private fun containsCalcOp(s: String): Boolean {
        return s.contains("+") || s.contains("-") || s.contains("*") ||
            s.contains("/") || s.contains("%") || containsMulticharOp(s)
    }

    private fun containsMulticharOp(s: String): Boolean {
        // 取幂等；简单判断
        return s.contains("**") || s.contains("^")
    }

    /** 简易表达式求值（仅 + - * / % 与括号、小数） */
    private fun evalExpression(expr: String): Double {
        // 用递归下降实现，避免依赖任何库
        return ParseEval(expr).parse()
    }

    /** 单位换算 */
    private fun convertUnit(text: String): String? {
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*(公里|千米|米|厘米|毫米|斤|公斤|千克|克|英里|英尺|英寸|升|毫升|斤|磅)").find(text)
        if (m == null) return null
        val value = m.groupValues[1].toDouble()
        val unit = m.groupValues[2]
        val result = when (unit) {
            "公里", "千米" -> "$value 公里 = ${value * 1000} 米 = ${String.format("%.4f", value * 0.621371)} 英里"
            "米" -> if (value >= 1000) "$value 米 = ${String.format("%.3f", value / 1000)} 公里" else "$value 米 = ${value * 100} 厘米"
            "厘米" -> "$value 厘米 = ${value / 100} 米"
            "毫米" -> "$value 毫米 = ${value / 1000} 米"
            "公斤", "千克" -> "$value 公斤 = $value 千克 = ${value * 2} 斤 = ${String.format("%.4f", value * 2.20462)} 磅"
            "斤" -> "$value 斤 = ${value / 2} 公斤 = ${String.format("%.4f", value * 1.10231)} 磅"
            "克" -> "$value 克 = ${value / 1000} 千克"
            "磅" -> "$value 磅 = ${String.format("%.4f", value * 0.453592)} 千克"
            "英里" -> "$value 英里 = ${String.format("%.4f", value * 1.60934)} 公里"
            "英尺" -> "$value 英尺 = ${String.format("%.4f", value * 0.3048)} 米"
            "英寸" -> "$value 英寸 = ${String.format("%.4f", value * 2.54)} 厘米"
            "升" -> "$value 升 = ${value * 1000} 毫升"
            "毫升" -> "$value 毫升 = ${value / 1000} 升"
            else -> null
        }
        return "换算结果：$result"
    }

    /** 把工具结果组织成自然语言回答 */
    private fun summarizeToolResult(result: String, userText: String): String {
        val short = if (result.length > 400) result.take(400) + "…" else result.take(600)
        val prefix = when {
            userText.contains("火车") || userText.contains("车票") || userText.contains("12306") -> "查票结果如下："
            userText.contains("记忆") || userText.contains("记住") -> "好，已经记下了："
            userText.contains("搜索") || userText.contains("搜") -> "搜索结果："
            userText.contains("apk") || userText.contains("逆向") -> "APK 解析结果："
            else -> "处理结果："
        }
        return "$prefix\n$short\n\n（以上由本地工具在设备上计算得到）"
    }
}

/**
 * 极简递归下降四则运算求值器（无外部依赖）
 */
private class ParseEval(private val s: String) {
    private var pos = 0

    fun parse(): Double {
        val v = expr()
        return v
    }

    private fun expr(): Double {
        var v = term()
        while (true) {
            if (peek('+')) { advance(); v += term() }
            else if (peek('-')) { advance(); v -= term() }
            else break
        }
        return v
    }

    private fun term(): Double {
        var v = factor()
        while (true) {
            if (peek('*')) { advance(); v *= factor() }
            else if (peek('/')) { advance(); v /= factor() }
            else break
        }
        return v
    }

    private fun factor(): Double {
        if (peek('(')) {
            advance()
            val v = expr()
            if (peek(')')) advance()
            return v
        }
        return number()
    }

    private fun number(): Double {
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
        if (start == pos) pos++
        return s.substring(start, pos).toDoubleOrNull() ?: 0.0
    }

    private fun peek(c: Char): Boolean = pos < s.length && s[pos] == c

    private fun advance() { pos++ }
}
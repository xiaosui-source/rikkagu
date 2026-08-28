/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.prompts

/**
 * 图片识别（AI 视觉模型）默认提示词。
 * 让视觉模型"看懂图片"，而不只是 OCR 文字。
 * 变量：{images} 由调用方替换为图片数据。
 */
internal val DEFAULT_OCR_PROMPT = """
    请仔细观察这张图片，告诉我图片里"是什么、有什么、正在发生什么"。请全面描述：
    - 主要物体/物品：图中有什么东西（例如"一只猫"、"一杯咖啡"、"一辆汽车"）。
    - 场景/环境：这是在哪里（室内/户外、餐厅/街道等）。
    - 人物与动作：如果有人，他们在做什么、表情如何。
    - 文字内容：如果图片里有任何文字/标志/截图内容，请完整提取出来。
    - 品牌/型号/细节：如果能看到，指出品牌或型号。
    用简体中文，按上面顺序条理清楚但简洁地回答。
    图片数据：{images}
""".trimIndent()
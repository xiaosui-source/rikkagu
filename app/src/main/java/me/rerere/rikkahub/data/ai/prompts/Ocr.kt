/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.prompts

/**
 * 图片识别（AI 视觉模型）默认提示词。
 * 变量：{images} 由调用方替换为图片数据。
 */
internal val DEFAULT_OCR_PROMPT = """
    请识别这张图片里的内容，尤其是所有可见的文字（OCR）。
    - 如果图片包含文字，请准确、完整地提取文字内容（保留换行与格式）。
    - 如果图片没有文字，请简要描述图片内容（物体、场景、人物）。
    - 用简体中文回答。
    图片数据：{images}
""".trimIndent()
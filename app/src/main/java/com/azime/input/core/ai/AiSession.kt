package com.azime.input.core.ai

import android.content.Context

/**
 * 轮19.195：**AI 会话状态 + 实时翻译管道**
 *
 * - 模式：翻译 / 续写 / 润色 / 解释 / 对话（`OFF` = 关闭）
 * - 实时翻译：候选上屏的中文**先入队** → 后台翻译 → 按序写入文本框 ✓
 *   · 失败 ⇒ 原样写入中文（不吞字 ✓）
 *   · 标点：译成目标语言时做一次本地规范化（中→英 = 半角 + 英文标点 ✓）
 * - 状态回调给 UI（AI 栏显示「翻译中…」/队列长度 ✓）
 */
object AiSession {

    enum class Mode(val label: String) {
        OFF("关闭"),
        TRANSLATE("翻译"),
        CONTINUE("续写"),
        POLISH("润色"),
        EXPLAIN("解释"),
        CHAT("对话"),
    }

    /** 翻译目标语言（可扩展 ✓）。 */
    val LANGUAGES = listOf("英文", "日文", "韩文", "繁体中文", "法文", "德文", "俄文", "西班牙文")

    @Volatile var mode: Mode = Mode.OFF
    @Volatile var targetLang: String = "英文"
    @Volatile var reverse: Boolean = false          // 英→中 ✓（目标改为中文）
    @Volatile var polishStyle: Int = 0              // 0=正式 1=口语 2=简短
    val polishStyles = listOf("正式", "口语", "简短")

    /** 条内显示的**原文**（用户输入的中文 ✓ 累积 ✓）。 */
    @Volatile var buffer: String = ""
    /** 最近一次译好的**结果**（确定时上屏 ✓）。 */
    @Volatile var result: String = ""

    /** 取结果上屏 ✓（没有结果就用原文 ✓）并清空 ✓。 */
    fun takeResult(): String {
        val out = result.ifBlank { buffer }
        buffer = ""
        result = ""
        return out
    }

    /** UI 用的状态文本（空 = 空闲）。 */
    @Volatile var status: String = ""
    /** 队列里还有多少段没译完（AI 栏显示「翻译中…」用 ✓）。 */
    @Volatile var pending: Int = 0

    fun isActive(): Boolean = mode != Mode.OFF

    fun turnOff() {
        mode = Mode.OFF
        buffer = ""
        result = ""
        status = ""
        pending = 0
    }

    /** 翻译方向的简写（AI 栏/语言键显示 ✓）。 */
    fun translateDirection(): String =
        if (reverse) "英 → 中" else "中 → ${targetLang.take(1)}"

    /** 当前模式在 AI 栏显示的完整文案 ✓。 */
    fun statusText(): String = when (mode) {
        Mode.OFF -> ""
        Mode.TRANSLATE -> "翻译 · ${if (pending > 0) "翻译中…" else "待输入"}"
        Mode.POLISH -> "润色 · ${polishStyles[polishStyle]}"
        Mode.CONTINUE -> "续写"
        Mode.EXPLAIN -> "解释"
        Mode.CHAT -> "对话"
    }

    /** 该模式对应的 system prompt ✓。 */
    fun systemPrompt(): String = when (mode) {
        Mode.TRANSLATE -> AiManager.SYS_LIVE_TRANSLATE.format(if (reverse) "简体中文" else targetLang)
        Mode.POLISH -> AiManager.SYS_REWRITE.format(polishStyles[polishStyle])
        Mode.CONTINUE -> AiManager.SYS_CONTINUE
        Mode.EXPLAIN -> AiManager.SYS_EXPLAIN
        Mode.CHAT -> AiManager.SYS_CHAT
        Mode.OFF -> ""
    }

    /**
     * **本地标点规范化**（不依赖模型也能保证观感 ✓）。
     * 中→英：全角 -> 半角 + 中文标点 -> 英文标点 ✓；其它语言交给模型 ✓
     */
    fun normalizePunctuation(text: String, target: String): String {
        if (target != "英文") return text
        val map = mapOf(
            '，' to ',', '。' to '.', '！' to '!', '？' to '?', '：' to ':',
            '；' to ';', '（' to '(', '）' to ')', '【' to '[', '】' to ']',
            '“' to '"', '”' to '"', '‘' to '\'', '’' to '\'', '、' to ',',
            '《' to '"', '》' to '"', '～' to '~', '　' to ' ',
        )
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(map[ch] ?: ch)
        var s = sb.toString()
        // 英文标点后补空格 ✓（避免 "a,b" 粘在一起）
        s = s.replace(Regex("([,.;:!?])(\\S)"), "$1 $2")
        // 连续空格压缩 ✓
        s = s.replace(Regex(" {2,}"), " ")
        return s.trim()
    }

    /** 是否值得送翻译（纯数字/纯英文/纯空格 ⇒ 直通 ✓）。 */
    fun shouldTranslate(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.all { it.isDigit() || it.isWhitespace() }) return false
        // 全 ASCII 且无中文 ⇒ 直通（已经是英文了 ✓）
        if (t.all { it.code < 128 }) return false
        return true
    }

    /** 给 AI 功能（续写/润色/解释/对话）取输入：选中文本优先 ✓ 不读剪贴板 ✗ */
    fun inputFrom(ic: android.view.inputmethod.InputConnection?): String {
        if (ic == null) return ""
        val sel = runCatching {
            ic.getSelectedText(0)?.toString().orEmpty()
        }.getOrDefault("")
        if (sel.isNotBlank()) return sel
        // 没有选中 ⇒ 取光标前的整段（到换行为止 ✓ 最多 300 字 ✓）
        return runCatching {
            val before = ic.getTextBeforeCursor(300, 0)?.toString().orEmpty()
            val line = before.substringAfterLast('\n')
            line.takeLast(120).trim()
        }.getOrDefault("")
    }
}

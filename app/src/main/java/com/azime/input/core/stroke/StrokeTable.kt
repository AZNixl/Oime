package com.azime.input.core.stroke

import android.content.Context

/**
 * 轮19.162：**笔顺表**（本地按笔划筛选候选 ✓ **不修改输入** ✓）
 *
 * 用户要求（对齐 trime2「候选面板显示优化.lua」的语义 ✓）：
 * "输入 wang 出了 王/旺 ⇒ 点'横'筛出王" —— 即**筛选候选列表里的字**，
 * 而不是把笔划打进输入框（那样会变成"打字"✗ 实测反馈 ✓）。
 *
 * 数据：RIME「五筆畫」库（`h/s/p/n/z` = 横/竖/撇/捺/折 ✓）转成的紧凑表
 * （`assets/stroke/stroke_table.txt`，11.5 万条 / 1.7MB ✓ 每字保留前 10 笔 ✓）。
 * 解析在**后台线程**完成（11 万行 ✗ 不能卡主线程 ✓），就绪前 UI 不做筛选 ✓。
 */
object StrokeTable {

    private const val ASSET = "stroke/stroke_table.txt"

    /** UI 侧最多允许叠加的笔划数（与表内保留长度一致 ✓） */
    const val MAX_STROKES = 10

    @Volatile
    private var table: Map<Char, String>? = null

    @Volatile
    private var loading = false

    /** 笔顺表是否已解析就绪（未就绪时 UI 不筛选，避免"点了没反应"✗） */
    val ready: Boolean get() = table != null

    /** 首次调用时后台解析（重复调用安全 ✓） */
    fun ensureLoaded(context: Context) {
        if (table != null || loading) return
        loading = true
        runCatching {
            val map = HashMap<Char, String>(200_000)
            context.assets.open(ASSET).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val i = line.indexOf('\t')
                    if (i <= 0) continue
                    val code = line.substring(i + 1)
                    if (code.isEmpty()) continue
                    map[line[0]] = code
                }
            }
            table = map
        }.onFailure {
            loading = false
        }
    }

    /**
     * [ch] 的笔顺是否以 [seq] 开头（seq 为 h/s/p/n/z 串 ✓）。
     * 表里查不到的字（生僻字 / 非汉字）返回 false ✓（筛选时被排除 ✓）。
     */
    fun matches(ch: Char?, seq: String): Boolean {
        if (seq.isEmpty()) return true
        if (ch == null) return false
        val code = table?.get(ch) ?: return false
        return code.startsWith(seq)
    }
}

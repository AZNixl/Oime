package com.azime.input.utils

/**
 * 轮19.145：「按一次退格键该删多少个 UTF-16 码元」—— 按**扩展字素簇**（UAX #29 的实用子集）算 ✓
 *
 * ## 为什么需要（用户反馈：微信表情要按多次退格才能删掉 ✗）
 * 老代码只判了「代理对」一种情况（`Character.isSurrogatePair` ✓）⇒ 下面这些「视觉上就一个字符」
 * 的东西会被拆成**多次**退格 ✗，而且前几次**屏幕上看不出任何变化** ✗ ⇒ 用户以为没生效 ⇒ 连按 ✓：
 *
 * | 例子 | 码元序列 | 码元数 | 老代码表现 |
 * |---|---|---|---|
 * | 😀 | D83D DE00 | 2 | ✓ 已处理 |
 * | ❤️ | 2764 FE0F | 2 | ✗ **不是代理对** ⇒ 只删掉末尾的变体选择符（不可见 ✗）⇒ 看着"没反应" ⇒ 要按 2 次 ✗ |
 * | 👍🏻 | D83D DC4D D83C DFFB | 4 | ✗ 要按 2 次 |
 * | 1️⃣ | 0031 FE0F 20E3 | 3 | ✗ 要按 3 次 |
 * | é | 0065 0301 | 2 | ✗ 要按 2 次 |
 * | 👨‍👩‍👧 | …1F468 200D 1F469 200D 1F467 | 8 | ✗ 要按 3 次 |
 * | 🇨🇳 | D83C DDE8 D83C DDF3 | 4 | ✗ 要按 2 次 |
 *
 * ## 用法
 * `Grapheme.lastClusterLength(光标前文本)` ⇒ 返回**码元数** ✓
 * 直接喂 `InputConnection.deleteSurroundingText(n, 0)`（该 API 按**码元**计 ✓）即可一次删净 ✓
 *
 * ## 算法（自右向左累积，PC 端 19 个用例已全绿 ✓ 见 grapheme_selftest.py）
 * 维护 `first`：本轮退格要删的那个「基字符」是否还没拿到 ✓
 *  · 尾部「组合类」（组合记号 / 变体选择符 / 肤色修饰 / 键帽符 / 标签字符）且还没拿到基字符 ⇒ **吞** ✓
 *  · ZWJ（U+200D）⇒ 吞掉它，并把状态重置为"还没拿到基字符"（左边又是一个完整簇 ✓ 👨‍👩‍👧 ✓）
 *  · 组合类但**已经**拿到基字符 ⇒ 它属于**左边**那个字 ⇒ **停** ✗（否则会把「é😀」的 é 一起删掉 ✗）
 *  · 普通基字符且还没拿到 ⇒ 吞下它；若它是区域指示符且左边也是 ⇒ 再吞一个（国旗 🇨🇳 ✓）
 *  · 已经拿到基字符又遇到普通字符 ⇒ **停** ✓
 */
object Grapheme {

    private const val ZWJ = 0x200D          // 零宽连接符（👨‍👩‍👧 的粘合剂 ✓）
    private const val SKIN_FIRST = 0x1F3FB  // 肤色修饰符起点（Type = Sk ✗ 不是 Mark ⇒ 必须单列 ✓）
    private const val SKIN_LAST = 0x1F3FF
    private const val RI_FIRST = 0x1F1E6    // 区域指示符（国旗 = 两个 RI 拼成 ✓）
    private const val RI_LAST = 0x1F1FF
    private const val TAG_FIRST = 0xE0020   // 标签字符（细分地区旗帜 ✓）
    private const val TAG_LAST = 0xE007F

    /**
     * 字符串**末尾**那一个显示字符占多少码元 ✓（只看尾部 ✓，从后往前吞 ✓）
     * @return 0 表示空串 ✓（返回值可直接当删除长度用 ✓）
     */
    fun lastClusterLength(s: CharSequence): Int {
        val n = s.length
        if (n == 0) return 0
        var i = n
        var first = true      // 本次退格要删的「基字符」是否还没拿到 ✓
        while (true) {
            val j = prevIndex(s, i)
            if (j < 0) break
            val cp = Character.codePointAt(s, j)
            when {
                // 尾部的组合类：属于本簇 ⇒ 吞 ✓
                first && (isExtend(cp) || isTag(cp)) -> i = j
                // ZWJ：左边又是一整个簇 ⇒ 吞掉它并重新进入"待取基字符"状态 ✓
                cp == ZWJ -> { i = j; first = true }
                // 组合类但已有基字符 ⇒ 它黏的是**左边那个字** ✗ 停 ✓
                isExtend(cp) || isTag(cp) -> break
                first -> {
                    i = j
                    first = false
                    // 国旗：两个区域指示符算一个簇 ✓（吞完就停 ✓）
                    if (isRegional(cp)) {
                        val k = prevIndex(s, i)
                        if (k >= 0 && isRegional(Character.codePointAt(s, k))) i = k
                        break
                    }
                    // 普通基字符：继续循环 ⇒ 让下一轮判断左边是不是 ZWJ ✓
                }
                // 已有基字符又遇普通字符 ⇒ 停 ✓
                else -> break
            }
        }
        return n - i
    }

    /** 索引 [end]（右开）之前的**一个码点**的起始下标 ✓；返回 -1 表示到头了 ✓ */
    private fun prevIndex(s: CharSequence, end: Int): Int {
        if (end <= 0) return -1
        val i = end - 1
        return if (i > 0 && Character.isLowSurrogate(s[i]) && Character.isHighSurrogate(s[i - 1])) i - 1 else i
    }

    /** 组合类（能"粘"在左边基字符上 ✓） */
    private fun isExtend(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(),        // Mn（含 U+FE0E/FE0F 变体选择符 ✓）
        Character.COMBINING_SPACING_MARK.toInt(),  // Mc
        Character.ENCLOSING_MARK.toInt()           // Me（含 U+20E3 键帽 ✓）
        -> true
        else -> cp in SKIN_FIRST..SKIN_LAST        // 肤色修饰符（Sk ✗ 不归上面三类 ⇒ 单列 ✓）
    }

    private fun isRegional(cp: Int): Boolean = cp in RI_FIRST..RI_LAST

    private fun isTag(cp: Int): Boolean = cp in TAG_FIRST..TAG_LAST

    /**
     * 诊断用：把**尾部**若干个码点打成十六进制串 ✓（真机定位「这个表情到底是什么编码」✓）
     * 例：`😀` → `1F600`；`[微笑]` → `5B 5FAE 7B11 5D`
     */
    fun hexTail(s: CharSequence, maxCodePoints: Int = 10): String {
        if (s.isEmpty()) return ""
        val cps = ArrayList<Int>(maxCodePoints)
        var i = 0
        while (i < s.length) {
            val cp = Character.codePointAt(s, i)
            cps.add(cp)
            i += Character.charCount(cp)
        }
        val from = maxOf(0, cps.size - maxCodePoints)
        val sb = StringBuilder()
        for (k in from until cps.size) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(Integer.toHexString(cps[k]).uppercase())
        }
        return sb.toString()
    }
}

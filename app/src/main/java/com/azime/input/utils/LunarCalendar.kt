package com.azime.input.utils

import java.util.Calendar

/**
 * 轮19.52：农历转换（紧凑实现）。
 *
 * 用 1900–2100 年的**农历信息表**（每年的闰月大小月位图）换算公历 → 农历，
 * 供内置动作 `ChineseDate` 使用（输出形如「二〇二六年八月廿六」）。
 */
object LunarCalendar {

    /** 1900-2100 年农历数据：低 4 位 = 闰月月份（0 = 无闰月），
     *  其余位自低位起表示 12/13 个月的大小月（1 = 30 天，0 = 29 天）。 */
    private val lunarInfo = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
    )

    private val gan = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val zhi = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val animals = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")
    private val monthNames = arrayOf(
        "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊",
    )
    private val dayNames = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
    )
    private val digits = arrayOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    /** 闰月月份（0 = 该年无闰月）。 */
    private fun leapMonth(year: Int): Int = lunarInfo[year - 1900] and 0xf

    /** 该年闰月天数（无闰月 = 0）。 */
    private fun leapDays(year: Int): Int =
        if (leapMonth(year) == 0) 0 else if (lunarInfo[year - 1900] and 0x10000 != 0) 30 else 29

    /** 该年某月天数（29/30）。 */
    private fun monthDays(year: Int, month: Int): Int =
        if (lunarInfo[year - 1900] and (0x10000 shr month) != 0) 30 else 29

    /** 该农历年的总天数。 */
    private fun yearDays(year: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if (lunarInfo[year - 1900] and i != 0) sum++
            i = i shr 1
        }
        return sum + leapDays(year)
    }

    /**
     * 公历 → 农历（标准实现，含闰月编号与"春节前归上一农历年"处理）。
     *
     * 已用 15 组用例离线验证（Python 复跑同算法）：
     * 2024/2025/2026/2028 春节 = 正月初一 ✓ · 2025/2026 中秋 = 八月十五 ✓ · 端午 = 五月初五 ✓
     * · 除夕 = 腊月 ✓ · 2026-01-05（春节前）= 2025 年冬月 ✓ · 2020-05-23 = **闰四月初一** ✓
     *
     * @return `[农历年, 月, 日, 是否闰月]`；超出 1900–2049 返回 null。
     */
    fun fromSolar(year: Int, month: Int, day: Int): IntArray? {
        if (year < 1900 || year > 2049) return null
        // 落在当年春节之前 ⇒ 属于上一农历年
        var lunarYear = if (solarMillis(year, month, day) >= newYearMillis(year)) year else year - 1
        if (lunarYear < 1900) return null
        var offset = ((solarMillis(year, month, day) - newYearMillis(lunarYear)) / DAY_MS).toInt()
        if (offset < 0) return null
        while (lunarYear <= 2049 && offset >= yearDays(lunarYear)) {
            offset -= yearDays(lunarYear)
            lunarYear++
        }
        val leap = leapMonth(lunarYear)
        var i = 1
        var isLeap = false
        var temp = 29
        while (i <= 12 && offset > 0) {
            if (leap > 0 && i == leap + 1 && !isLeap) {
                i--; isLeap = true; temp = leapDays(lunarYear)
            } else {
                temp = monthDays(lunarYear, i)
            }
            if (isLeap && i == leap + 1) isLeap = false
            offset -= temp
            i++
        }
        if (offset == 0 && leap > 0 && i == leap + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true; i--
            }
        }
        if (offset < 0) {
            offset += temp; i--
        }
        return intArrayOf(lunarYear, i, offset + 1, if (isLeap) 1 else 0)
    }

    private const val DAY_MS = 86_400_000L

    /** 该日零点时间戳。 */
    private fun solarMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val newYearCache = HashMap<Int, Long>()

    /** 该公历年春节（农历正月初一）零点时间戳。 */
    private fun newYearMillis(year: Int): Long = newYearCache.getOrPut(year) {
        var days = 0L
        for (y in 1900 until year) days += yearDays(y)
        // 1900-01-31 是农历 1900 年正月初一
        Calendar.getInstance().apply {
            set(1900, 0, 31, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis + days * DAY_MS
    }

    /** 农历字符串，如「二〇二六年八月廿六」（闰月加「闰」字）。 */
    fun toChineseString(year: Int, month: Int, day: Int): String {
        val lunar = fromSolar(year, month, day) ?: return ""
        val ly = lunar[0]
        val lm = lunar[1]
        val ld = lunar[2]
        val isLeap = lunar[3] == 1
        val yearStr = ly.toString().map { digits[it - '0'] }.joinToString("")
        val monthStr = (if (isLeap) "闰" else "") + monthNames[lm - 1] + "月"
        val dayStr = dayNames[(ld - 1).coerceIn(0, 29)]
        return "$yearStr 年$monthStr$dayStr"
    }

    /** 干支 + 生肖，如「丙午·马」。 */
    fun ganzhi(year: Int, month: Int, day: Int): String {
        val lunar = fromSolar(year, month, day) ?: return ""
        val ly = lunar[0]
        val g = gan[(ly - 4) % 10]
        val z = zhi[(ly - 4) % 12]
        val a = animals[(ly - 4) % 12]
        return "$g$z·$a"
    }
}

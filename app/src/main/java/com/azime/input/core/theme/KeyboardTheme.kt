package com.azime.input.core.theme

import android.content.Context
import android.content.SharedPreferences
import com.azime.input.AZimeApplication

/**
 * 键盘主题与配色（参考小企鹅输入法.fx 的主题模块）：
 * - 深浅色模式：跟随系统 / 强制亮色 / 强制暗色
 * - 强调色：亮色 / 暗色两套（accentActive 与回车键底色由此派生）
 *
 * 键盘视图在下次弹出时应用新配色（与键高设置同一生效时机）。
 */
object KeyboardTheme {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_MODE = "mode"                 // system | light | dark
    private const val KEY_ACCENT_LIGHT = "accent_light" // ARGB int
    private const val KEY_ACCENT_DARK = "accent_dark"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    const val DEFAULT_ACCENT_LIGHT = 0xFF1A73E8.toInt()
    const val DEFAULT_ACCENT_DARK = 0xFF8AB4F8.toInt()

    /** 强调色预设：(名称, 亮色, 暗色)。 */
    val accentPresets: List<Triple<String, Int, Int>> = listOf(
        Triple("默认蓝", 0xFF1A73E8.toInt(), 0xFF8AB4F8.toInt()),
        Triple("中国红", 0xFFD32F2F.toInt(), 0xFFE57373.toInt()),
        Triple("森绿", 0xFF2E7D32.toInt(), 0xFF81C784.toInt()),
        Triple("暗紫", 0xFF6A1B9A.toInt(), 0xFFBA68C8.toInt()),
        Triple("橙光", 0xFFEF6C00.toInt(), 0xFFFFB74D.toInt()),
        Triple("青碧", 0xFF00897B.toInt(), 0xFF4DB6AC.toInt()),
        Triple("樱粉", 0xFFD81B60.toInt(), 0xFFF06292.toInt()),
    )

    private val prefs: SharedPreferences
        get() = AZimeApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun mode(): String = prefs.getString(KEY_MODE, MODE_SYSTEM) ?: MODE_SYSTEM

    fun setMode(mode: String) {
        prefs.edit().putString(KEY_MODE, mode).apply()
    }

    fun accentLight(): Int = prefs.getInt(KEY_ACCENT_LIGHT, DEFAULT_ACCENT_LIGHT)

    fun accentDark(): Int = prefs.getInt(KEY_ACCENT_DARK, DEFAULT_ACCENT_DARK)

    fun setAccents(light: Int, dark: Int) {
        prefs.edit().putInt(KEY_ACCENT_LIGHT, light).putInt(KEY_ACCENT_DARK, dark).apply()
    }

    /** 解析实际深色状态。 */
    fun isDark(systemDark: Boolean): Boolean = when (mode()) {
        MODE_LIGHT -> false
        MODE_DARK -> true
        else -> systemDark
    }

    /** 是否为强调色预设值（非默认且不匹配任何预设 → 自定义）。 */
    fun isCustomAccent(): Boolean =
        accentPresets.none { it.second == accentLight() && it.third == accentDark() }
}

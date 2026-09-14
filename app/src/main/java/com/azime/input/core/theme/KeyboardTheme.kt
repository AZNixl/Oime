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

    // ── 轮19.31：界面风格 + 强调色原色 ──
    //   风格影响：键盘调色板（MIUI 中性灰阶）/ 设置页卡片与容器色 / 键圆角
    //   原色影响：强调键与强调容器是否**直接用纯色**（不再 alpha 混合冲淡）
    private const val KEY_UI_STYLE = "ui_style"
    private const val KEY_ACCENT_PURE = "accent_pure"

    const val STYLE_MATERIAL = "material"
    const val STYLE_MIUIX = "miuix"

    fun uiStyle(): String = prefs.getString(KEY_UI_STYLE, STYLE_MATERIAL) ?: STYLE_MATERIAL

    fun setUiStyle(v: String) = prefs.edit().putString(KEY_UI_STYLE, v).apply()

    fun isMiuix(): Boolean = uiStyle() == STYLE_MIUIX

    /**
     * 强调色是否用**原色**（不做半透明混合）。
     * 轮19.32：**默认改为 true**——用户要的是"选了什么色就显示什么色"，
     * 柔和混合（把颜色冲淡）改为需要主动关闭原色才会出现。
     */
    fun accentPure(): Boolean = prefs.getBoolean(KEY_ACCENT_PURE, true)

    fun setAccentPure(v: Boolean) = prefs.edit().putBoolean(KEY_ACCENT_PURE, v).apply()

    /** 实际生效：开了原色开关，或当前模式用了自定义配色（自定义就该原样呈现）。 */
    fun accentPureEffective(dark: Boolean): Boolean =
        accentPure() || (if (dark) customDarkOn() else customLightOn())

    // ── 轮19.30：亮/暗两套自定义配色 ──
    //   组A「字母键」= 26 字母键 + 逗号 + 句号（keyBg）
    //   组B「功能键」= Shift / 符号 / 退格 等（funcKeyBg）
    //   组C「强调键」= 回车/高亮（accent，沿用既有 accent_light / accent_dark）
    private const val KEY_CUSTOM_LIGHT = "custom_light_on"
    private const val KEY_CUSTOM_DARK = "custom_dark_on"
    private const val KEY_KEYBG_LIGHT = "keybg_light"
    private const val KEY_FUNCBG_LIGHT = "funcbg_light"
    private const val KEY_KEYBG_DARK = "keybg_dark"
    private const val KEY_FUNCBG_DARK = "funcbg_dark"

    /** 默认值 = 内置调色板（开启自定义但不改色时与默认外观一致）。 */
    const val DEFAULT_KEYBG_LIGHT = 0xFFFFFFFF.toInt()
    const val DEFAULT_FUNCBG_LIGHT = 0xFFD3D7DC.toInt()
    const val DEFAULT_KEYBG_DARK = 0xFF2A2D2F.toInt()
    const val DEFAULT_FUNCBG_DARK = 0xFF3C4043.toInt()

    fun customLightOn(): Boolean = prefs.getBoolean(KEY_CUSTOM_LIGHT, false)
    fun setCustomLightOn(v: Boolean) = prefs.edit().putBoolean(KEY_CUSTOM_LIGHT, v).apply()

    fun customDarkOn(): Boolean = prefs.getBoolean(KEY_CUSTOM_DARK, false)
    fun setCustomDarkOn(v: Boolean) = prefs.edit().putBoolean(KEY_CUSTOM_DARK, v).apply()

    fun anyCustomOn(): Boolean = customLightOn() || customDarkOn()

    /** 字母键底色（dark=true 取暗色套）。 */
    fun keyBgColor(dark: Boolean): Int = prefs.getInt(
        if (dark) KEY_KEYBG_DARK else KEY_KEYBG_LIGHT,
        if (dark) DEFAULT_KEYBG_DARK else DEFAULT_KEYBG_LIGHT,
    )

    fun setKeyBgColor(dark: Boolean, v: Int) =
        prefs.edit().putInt(if (dark) KEY_KEYBG_DARK else KEY_KEYBG_LIGHT, v).apply()

    /** 功能键底色。 */
    fun funcBgColor(dark: Boolean): Int = prefs.getInt(
        if (dark) KEY_FUNCBG_DARK else KEY_FUNCBG_LIGHT,
        if (dark) DEFAULT_FUNCBG_DARK else DEFAULT_FUNCBG_LIGHT,
    )

    fun setFuncBgColor(dark: Boolean, v: Int) =
        prefs.edit().putInt(if (dark) KEY_FUNCBG_DARK else KEY_FUNCBG_LIGHT, v).apply()

    /** 恢复该模式下的默认配色（含强调色）。 */
    fun resetColors(dark: Boolean) {
        prefs.edit()
            .putInt(if (dark) KEY_KEYBG_DARK else KEY_KEYBG_LIGHT,
                if (dark) DEFAULT_KEYBG_DARK else DEFAULT_KEYBG_LIGHT)
            .putInt(if (dark) KEY_FUNCBG_DARK else KEY_FUNCBG_LIGHT,
                if (dark) DEFAULT_FUNCBG_DARK else DEFAULT_FUNCBG_LIGHT)
            .putInt(if (dark) KEY_ACCENT_DARK else KEY_ACCENT_LIGHT,
                if (dark) DEFAULT_ACCENT_DARK else DEFAULT_ACCENT_LIGHT)
            .apply()
    }

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

package com.azime.input.core.theme

/**
 * 轮19.35：界面风格 token 表。
 *
 * 设计原则（见 `UI_STYLE_RESEARCH.md`）：
 * 1. **风格 = 一份数据**，不是一堆 if/else；新增风格只加一条 [StyleTokens]。
 * 2. 视觉 token（配色/边框/阴影/字重/等宽）由风格决定；
 *    **几何 token（键圆角）默认不生效**——只有用户打开「几何跟随风格」时才用风格建议值，
 *    否则一律沿用用户自己调好的参数（19.33 的教训）。
 */
enum class UiStyle(val id: String, val label: String) {
    MATERIAL("material", "Material"),
    MIUIX("miuix", "Miuix"),
    ONEUI("oneui", "One UI"),
    // 轮19.74：**删除 iOS / Nothing OS / Material You 三种风格**（用户要求：修不好的直接删）
    // 原因：它们的键面/功能键与底色对比在真机上始终不理想（尤其 Material You 取壁纸色后键面难辨），
    // 与其留着重试，不如把选择收窄到三套稳定的 ✓
    ;

    companion object {
        fun from(id: String): UiStyle = entries.firstOrNull { it.id == id } ?: MATERIAL
    }
}

/** 单套（浅色或深色）调色板。 */
data class StylePalette(
    val bg: Long,
    val barBg: Long,
    val keyBg: Long,
    val funcKeyBg: Long,
    val text: Long,
    val subText: Long,
)

/** 一种风格的完整 token。 */
data class StyleTokens(
    val style: UiStyle,
    /** 键面**建议**圆角（仅「几何跟随风格」开启时生效）。 */
    val keyCornerDp: Int,
    /** 键面描边透明度（0 = 无描边）。 */
    val keyBorderAlpha: Float,
    /** 键面阴影（dp，0 = 无）。 */
    val keyShadowDp: Int,
    /** 键面字重加粗（One UI 风格为 true）。 */
    val boldKeys: Boolean,
    /** 键面用等宽字体（Nothing OS）。 */
    val monospace: Boolean,
    /** Material You：从系统壁纸动态取色（API 31+；否则回落 [light]/[dark]）。 */
    val dynamicColor: Boolean,
    val light: StylePalette,
    val dark: StylePalette,
)

object UiStyles {

    private fun p(
        bg: Long, barBg: Long, keyBg: Long, funcKeyBg: Long, text: Long, subText: Long,
    ) = StylePalette(bg, barBg, keyBg, funcKeyBg, text, subText)

    private val all: Map<UiStyle, StyleTokens> = mapOf(
        // 既有默认：中性灰 + 白键
        UiStyle.MATERIAL to StyleTokens(
            UiStyle.MATERIAL, keyCornerDp = 8, keyBorderAlpha = 0f, keyShadowDp = 0,
            boldKeys = false, monospace = false, dynamicColor = false,
            light = p(0xFFE9EBEE, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD3D7DC, 0xFF202124, 0xFF80868B),
            dark = p(0xFF1B1D1F, 0xFF26282A, 0xFF2A2D2F, 0xFF3C4043, 0xFFE8EAED, 0xFF9AA0A6),
        ),
        // MIUI 扁平：页面浅灰 + 卡片/键面纯白 + 工具栏纯白
        UiStyle.MIUIX to StyleTokens(
            UiStyle.MIUIX, keyCornerDp = 12, keyBorderAlpha = 0f, keyShadowDp = 0,
            boldKeys = false, monospace = false, dynamicColor = false,
            light = p(0xFFF2F3F5, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE8EAED, 0xFF191919, 0xFF7A7A7A),
            dark = p(0xFF191919, 0xFF1F1F1F, 0xFF2C2C2E, 0xFF3A3A3C, 0xFFEDEDED, 0xFF9E9E9E),
        ),
        // One UI：大圆角 + 柔和阴影 + 半粗字重
        UiStyle.ONEUI to StyleTokens(
            UiStyle.ONEUI, keyCornerDp = 16, keyBorderAlpha = 0f, keyShadowDp = 2,
            boldKeys = true, monospace = false, dynamicColor = false,
            light = p(0xFFF2F4F7, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE4E7EC, 0xFF101828, 0xFF667085),
            dark = p(0xFF171C24, 0xFF1D2530, 0xFF242B35, 0xFF2E3742, 0xFFF2F4F7, 0xFF98A2B3),
        ),
    )

    fun of(style: UiStyle): StyleTokens = all[style] ?: all.getValue(UiStyle.MATERIAL)

    fun ofCurrent(): StyleTokens = of(UiStyle.from(KeyboardTheme.uiStyle()))

    val selectable: List<UiStyle> = listOf(
        UiStyle.MATERIAL, UiStyle.MIUIX, UiStyle.ONEUI,
    )
}

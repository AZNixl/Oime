package com.azime.input.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Oime 自绘图标集（轮19.4 起用方案 J，轮19.5 去掉重影层）。
 *
 * 单色 tint：路径统一用黑色填充/描边，由 Icon(tint=...) 上色，跟随主题与键面文字色。
 * 24×24 网格，stroke 默认 2.0~2.4，圆头圆角。
 * 回车键为**纸飞机**（两片机翼留缝形成折痕，单色下也能看出折线）。
 */
object OimeIcons {

    /** (pathData, 是否填充, 描边宽度) */
    private data class P(val d: String, val filled: Boolean = false, val w: Float = 2.2f)

    private fun Icon(name: String, vararg paths: P): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        // 轮19.5：去掉重影层（用户反馈「阴影看得眼花」）——现在是纯单层主体。
        paths.forEach { p ->
            b.addPath(
                pathData = PathParser().parsePathString(p.d).toNodes(),
                fill = if (p.filled) SolidColor(Color.Black) else null,
                stroke = if (p.filled) null else SolidColor(Color.Black),
                strokeLineWidth = p.w,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    // ── 剪贴板 ────────────────────────────────────────────────
    val clipboard by lazy {
        Icon(
            "oime_clipboard",
            P("M7 4.2h10a2.4 2.4 0 0 1 2.4 2.4v13a2.4 2.4 0 0 1-2.4 2.4H7A2.4 2.4 0 0 1 4.6 19.6v-13A2.4 2.4 0 0 1 7 4.2z", w = 2.0f),
            P("M9.8 1.9h4.4a1.3 1.3 0 0 1 1.3 1.3v1.5a1.3 1.3 0 0 1-1.3 1.3H9.8a1.3 1.3 0 0 1-1.3-1.3V3.2A1.3 1.3 0 0 1 9.8 1.9z", filled = true),
            P("M8.6 11.4h6.8M8.6 15.4h4.2", w = 1.8f),
        )
    }

    // ── 输入方案 ──────────────────────────────────────────────
    val schemas by lazy {
        Icon(
            "oime_schemas",
            P("M6 3.9a2.1 2.1 0 1 0 0 4.2a2.1 2.1 0 1 0 0-4.2z", filled = true),
            P("M6 9.7a2.1 2.1 0 1 0 0 4.2a2.1 2.1 0 1 0 0-4.2z", filled = true),
            P("M6 15.5a2.1 2.1 0 1 0 0 4.2a2.1 2.1 0 1 0 0-4.2z", filled = true),
            P("M11 6h7.6M11 11.8h7.6M11 17.6h5.2", w = 2.3f),
        )
    }

    // ── 九宫格数字键盘 ────────────────────────────────────────
    val numpad by lazy {
        Icon(
            "oime_numpad",
            P("M6.6 3.6h10.8a3 3 0 0 1 3 3v10.8a3 3 0 0 1-3 3H6.6a3 3 0 0 1-3-3V6.6a3 3 0 0 1 3-3z", w = 2.0f),
            P("M7.4 6.2a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M12 6.2a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M16.6 6.2a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M7.4 10.8a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M12 10.8a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M16.6 10.8a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M7.4 15.4a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M12 15.4a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M16.6 15.4a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
        )
    }

    // ── 符号（#） ─────────────────────────────────────────────
    val symbols by lazy {
        Icon(
            "oime_symbols",
            P("M9.6 4.6 7.6 19.4M16.4 4.6 14.4 19.4M5.2 9.5h13.6M4.6 14.9h13.6", w = 2.4f),
        )
    }

    // ── 设置（齿轮） ──────────────────────────────────────────
    val settings by lazy {
        Icon(
            "oime_settings",
            P("M12 4.6a7.4 7.4 0 1 0 0 14.8a7.4 7.4 0 1 0 0-14.8z", w = 2.2f),
            P("M12 9.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z", filled = true),
            P("M12 2.4v2M12 19.6v2M2.4 12h2M19.6 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4"),
        )
    }

    // ── 退格 ─────────────────────────────────────────────────
    val backspace by lazy {
        Icon(
            "oime_backspace",
            P("M9 5.4h11a2 2 0 0 1 2 2v9.2a2 2 0 0 1-2 2H9L2.4 12z", w = 2.2f),
            P("M12.8 9.8l4.4 4.4M17.2 9.8l-4.4 4.4"),
        )
    }

    // ── 回车（纸飞机） ────────────────────────────────────────
    val enter by lazy {
        Icon(
            "oime_enter",
            // 两片机翼留缝 = 折痕（单色 tint 下也能读出来）
            P("M21.4 2.8 3 10.4l7.2 2.7z", filled = true),
            P("M21.4 2.8 11 14l2.6 6.6z", filled = true),
        )
    }

    // ── 空格 ─────────────────────────────────────────────────
    val space by lazy {
        Icon(
            "oime_space",
            P("M6.4 8.6h11.2a3.2 3.2 0 0 1 3.2 3.2v0.4a3.2 3.2 0 0 1-3.2 3.2H6.4a3.2 3.2 0 0 1-3.2-3.2v-0.4a3.2 3.2 0 0 1 3.2-3.2z", w = 2.2f),
            P("M8.4 12h7.2"),
        )
    }

    // ── 换挡（上箭头） ────────────────────────────────────────
    val shift by lazy {
        Icon(
            "oime_shift",
            P("M12 3.4 4 11.2h4.2v6.4h6.8v-6.4H20z", w = 2.2f),
        )
    }

    // ── 返回 ─────────────────────────────────────────────────
    val back by lazy {
        Icon(
            "oime_back",
            P("M14.6 5.4 8 12l6.6 6.6"),
        )
    }

    // ── 语音（麦克风） ────────────────────────────────────────
    val mic by lazy {
        Icon(
            "oime_mic",
            P("M12 3.2a2.9 2.9 0 0 1 2.9 2.9v5.8a2.9 2.9 0 0 1-5.8 0V6.1A2.9 2.9 0 0 1 12 3.2z", filled = true),
            P("M5.8 11.4v0.8a6.2 6.2 0 0 0 12.4 0v-0.8M12 18.4V21M8.6 21h6.8", w = 2.0f),
        )
    }

    // ── 联网 API（云） ────────────────────────────────────────
    val cloud by lazy {
        Icon(
            "oime_cloud",
            P("M7.6 18.4a3.8 3.8 0 0 1 0.2-7.6a5 5 0 0 1 9.6-1a3.6 3.6 0 0 1-0.4 8.6z", w = 2.1f),
        )
    }

    // ── 刷新 ─────────────────────────────────────────────────
    val refresh by lazy {
        Icon(
            "oime_refresh",
            P("M19.6 12a7.6 7.6 0 1 1-2.3-5.4"),
            P("M19.8 4.2v3.8h-3.8"),
        )
    }

    // ── 关于（信息） ──────────────────────────────────────────
    val info by lazy {
        Icon(
            "oime_info",
            P("M12 3.4a8.6 8.6 0 1 0 0 17.2a8.6 8.6 0 1 0 0-17.2z", w = 2.1f),
            P("M12 10.8v5.6M12 7.4h0.02", w = 2.2f),
        )
    }

    // ── 项目地址（链接） ──────────────────────────────────────
    val link by lazy {
        Icon(
            "oime_link",
            P("M10.4 13.6a3.6 3.6 0 0 0 5.1 0l3-3a3.6 3.6 0 0 0-5.1-5.1l-1 1", w = 2.1f),
            P("M13.6 10.4a3.6 3.6 0 0 0-5.1 0l-3 3a3.6 3.6 0 0 0 5.1 5.1l1-1", w = 2.1f),
        )
    }

    // ── 字体 ─────────────────────────────────────────────────
    val font by lazy {
        Icon(
            "oime_font",
            P("M5.6 19.4 12 4.6l6.4 14.8M8 14.4h8"),
        )
    }

    // ── 主题与配色 ────────────────────────────────────────────
    val palette by lazy {
        Icon(
            "oime_palette",
            P("M12 3.6a8.4 8.4 0 1 0 0 16.8a8.4 8.4 0 1 0 0-16.8z", w = 2.0f),
            P("M8.6 8.2a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z", filled = true),
            P("M12 6.2a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z", filled = true),
            P("M15.4 8.2a1.3 1.3 0 1 0 0 2.6a1.3 1.3 0 1 0 0-2.6z", filled = true),
        )
    }

    // ── 悬浮窗 ───────────────────────────────────────────────
    val pip by lazy {
        Icon(
            "oime_pip",
            P("M6 4.6h12a2.4 2.4 0 0 1 2.4 2.4v10a2.4 2.4 0 0 1-2.4 2.4H6A2.4 2.4 0 0 1 3.6 17V7A2.4 2.4 0 0 1 6 4.6z", w = 2.0f),
            P("M13 12.4h5.4a1 1 0 0 1 1 1v3.4a1 1 0 0 1-1 1H13a1 1 0 0 1-1-1v-3.4a1 1 0 0 1 1-1z", filled = true),
        )
    }

    // ── 预设置（Lua 代码） ────────────────────────────────────
    val code by lazy {
        Icon(
            "oime_code",
            P("M9.2 8 5 12l4.2 4M14.8 8 19 12l-4.2 4"),
        )
    }

    // ── emoji ────────────────────────────────────────────────
    val emoji by lazy {
        Icon(
            "oime_emoji",
            P("M12 3.6a8.4 8.4 0 1 0 0 16.8a8.4 8.4 0 1 0 0-16.8z", w = 2.0f),
            P("M9.4 8.6a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M14.6 8.6a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
            P("M8.6 14.2a4.2 4.2 0 0 0 6.8 0", w = 2.0f),
        )
    }

    // ── 键盘 ─────────────────────────────────────────────────
    val keyboard by lazy {
        Icon(
            "oime_keyboard",
            P("M4 6.4h16a1.6 1.6 0 0 1 1.6 1.6v8a1.6 1.6 0 0 1-1.6 1.6H4A1.6 1.6 0 0 1 2.4 16V8A1.6 1.6 0 0 1 4 6.4z", w = 2.0f),
            P("M6 10h0.02M9 10h0.02M12 10h0.02M15 10h0.02M18 10h0.02M7.4 13.6h9.2", w = 1.9f),
        )
    }

    // ── 勾选 ─────────────────────────────────────────────────
    val check by lazy {
        Icon(
            "oime_check",
            P("M5.4 12.6 10 17.2 18.6 7.4", w = 2.4f),
        )
    }

    // ── 轮19.11 新增：○ 菜单 / 工具栏扩展工具用 ─────────────────

    /** 方案开关（滑块）。 */
    val tune by lazy {
        Icon(
            "oime_tune",
            P("M4 7.4h9M17.5 7.4H20M4 16.6h3M11.5 16.6H20", w = 2.1f),
            P("M15 5.2a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4z", filled = true),
            P("M9 14.4a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4z", filled = true),
        )
    }

    /** 开关（pill + 圆点）。 */
    val toggle by lazy {
        Icon(
            "oime_toggle",
            P("M8 7.6h8a4.4 4.4 0 0 1 0 8.8H8a4.4 4.4 0 0 1 0-8.8z", w = 2.1f),
            P("M15.6 12a1.9 1.9 0 1 0 0 3.8a1.9 1.9 0 1 0 0-3.8z", filled = true),
        )
    }

    /** 方案组（四宫格）。 */
    val apps by lazy {
        Icon(
            "oime_apps",
            P("M5.4 5.4h5.2v5.2H5.4zM13.4 5.4h5.2v5.2h-5.2zM5.4 13.4h5.2v5.2H5.4zM13.4 13.4h5.2v5.2h-5.2z", w = 1.9f),
        )
    }

    /** 方案管理（清单 + 勾）。 */
    val manage by lazy {
        Icon(
            "oime_manage",
            P("M4.6 6.6h9.4M4.6 11.4h9.4M4.6 16.2h5.6", w = 2.0f),
            P("M14.4 15.4 16.6 17.6 20.4 13", w = 2.2f),
        )
    }

    /** 亮色（太阳）。 */
    val sun by lazy {
        Icon(
            "oime_sun",
            P("M12 7.6a4.4 4.4 0 1 0 0 8.8a4.4 4.4 0 1 0 0-8.8z", w = 2.1f),
            P("M12 2.6v2.2M12 19.2v2.2M2.6 12h2.2M19.2 12h2.2M5.6 5.6l1.6 1.6M16.8 16.8l1.6 1.6M18.4 5.6l-1.6 1.6M7.2 16.8l-1.6 1.6"),
        )
    }

    /** 暗色（月亮）。 */
    val moon by lazy {
        Icon(
            "oime_moon",
            P("M20 14.6A8.6 8.6 0 0 1 9.4 4a8.6 8.6 0 1 0 10.6 10.6z", w = 2.0f),
        )
    }

    /** O 圆环（环 + 中心点）。 */
    val ring by lazy {
        Icon(
            "oime_ring",
            P("M12 4.4a7.6 7.6 0 1 0 0 15.2a7.6 7.6 0 1 0 0-15.2z", w = 2.3f),
            P("M12 10.8a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z", filled = true),
        )
    }

    /** 更多候选（网格列表）。 */
    val candidates by lazy {
        Icon(
            "oime_candidates",
            P("M4.4 5.6h6.4v4.4H4.4zM13.2 5.6h6.4v4.4h-6.4zM4.4 14h6.4v4.4H4.4zM13.2 14h6.4v4.4h-6.4z", w = 1.9f),
        )
    }

    /** 中英切换（"文 A" 造型：左侧笔画 + 右侧 A）。 */
    val lang by lazy {
        Icon(
            "oime_lang",
            // 左侧「文」：点 + 横 + 撇捺
            P("M3.4 7.2h5.2M6 4.4v2.8M4.2 13.4c1.6-1.2 2.8-2.8 3.4-4.6M9.4 13.4c-1.2-1.2-2.2-2.8-2.8-4.4", w = 1.9f),
            // 右侧「A」
            P("M13.2 13.6 16.6 5.2 20 13.6M14.4 11.2h4.4", w = 2.0f),
        )
    }

    /** 重做（顺时针箭头）。 */
    val redo by lazy {
        Icon(
            "oime_redo",
            P("M19.6 12a7.6 7.6 0 1 1-2.3-5.4"),
            P("M19.8 4.2v3.8h-3.8"),
        )
    }

    /** 撤回（逆时针箭头）。 */
    val undo by lazy {
        Icon(
            "oime_undo",
            P("M4.4 12a7.6 7.6 0 1 0 2.3-5.4"),
            P("M4.2 4.2v3.8h3.8"),
        )
    }

    /** 清空（垃圾桶）。 */
    val trash by lazy {
        Icon(
            "oime_trash",
            P("M5.4 7.4h13.2M9 7.4V5.6a1.2 1.2 0 0 1 1.2-1.2h3.6A1.2 1.2 0 0 1 15 5.6v1.8", w = 2.0f),
            P("M7 7.4l0.9 11.2a1.6 1.6 0 0 0 1.6 1.5h5a1.6 1.6 0 0 0 1.6-1.5L17 7.4", w = 2.0f),
        )
    }

    /** 按 string id 取图标（键面 icon 字段 / 工具栏 id 用）。 */
    fun byName(name: String): ImageVector? = when (name) {
        "clipboard" -> clipboard
        "schemas", "schema" -> schemas
        "numpad" -> numpad
        "symbols" -> symbols
        "settings" -> settings
        "backspace" -> backspace
        "enter" -> enter
        "space" -> space
        "shift" -> shift
        "back", "main", "return" -> back
        "mic" -> mic
        "cloud" -> cloud
        "refresh" -> refresh
        "info" -> info
        "link" -> link
        "font" -> font
        "palette" -> palette
        "pip" -> pip
        "code" -> code
        "emoji" -> emoji
        "keyboard" -> keyboard
        "check" -> check
        "tune", "switches" -> tune
        "toggle" -> toggle
        "apps", "groups" -> apps
        "manage" -> manage
        "sun", "light" -> sun
        "moon", "dark" -> moon
        "ring", "oring" -> ring
        "candidates" -> candidates
        "undo" -> undo
        "redo" -> redo
        "ascii", "lang" -> lang
        "trash", "deleteall" -> trash
        "voice" -> mic
        "float" -> pip
        "lua" -> code
        "theme" -> palette
        "deploy" -> refresh
        "hide" -> back
        else -> null
    }
}

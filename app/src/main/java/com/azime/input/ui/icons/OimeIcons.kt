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
        else -> null
    }
}

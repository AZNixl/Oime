package com.azime.input.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Oime 自绘图标集 —— **方案 A：细描边圆润**（轮19.64 按用户选型重画）。
 *
 * 设计约束：
 * - 24×24 网格；**统一 1.6px 圆头描边**（原来 1.8~2.4 混用，视觉重量不齐 ⇒ 这次统一）
 * - 圆角一律 round join；"实心点"用双弧闭合小圆，视觉重量与线宽一致
 * - 单色：路径只用黑色，由 `Icon(tint=…)` 上色，跟随主题与键面文字色
 * - 两处按用户指定保留个性：**回车 = 纸飞机**、**○ 菜单 = 圆环**
 */
object OimeIcons {

    /** (pathData, 是否填充, 描边宽度) */
    private data class P(val d: String, val filled: Boolean = false, val w: Float = 1.6f)

    private fun Icon(name: String, vararg paths: P): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
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

    /** 实心小圆（双弧闭合，避免 arc-to-same-point 在部分渲染器上不显示）。 */
    private fun dot(cx: Float, cy: Float, r: Float = 1.05f): P =
        P(
            "M${cx - r} $cy" +
                "a$r $r 0 1 0 ${r * 2} 0" +
                "a$r $r 0 1 0 ${-r * 2} 0z",
            filled = true,
        )

    // ── 剪贴板 ────────────────────────────────────────────────
    val clipboard by lazy {
        Icon(
            "oime_clipboard",
            P("M7.2 4.6h9.6a2.2 2.2 0 0 1 2.2 2.2v12a2.2 2.2 0 0 1-2.2 2.2H7.2A2.2 2.2 0 0 1 5 18.8v-12a2.2 2.2 0 0 1 2.2-2.2z"),
            P("M9.8 3h4.4a1.2 1.2 0 0 1 1.2 1.2v1.6a1.2 1.2 0 0 1-1.2 1.2H9.8a1.2 1.2 0 0 1-1.2-1.2V4.2A1.2 1.2 0 0 1 9.8 3z"),
            P("M8.6 11.6h6.8M8.6 15.2h4.2"),
        )
    }

    // ── 输入方案（列表 + 圆点）────────────────────────────────
    val schemas by lazy {
        Icon(
            "oime_schemas",
            P("M9.2 7.4h10.3M9.2 12h10.3M9.2 16.6h6.6"),
            dot(5.2f, 7.4f),
            dot(5.2f, 12f),
            dot(5.2f, 16.6f),
        )
    }

    // ── 数字键盘 ──────────────────────────────────────────────
    val numpad by lazy {
        Icon(
            "oime_numpad",
            P("M4.6 5.6h14.8a1.8 1.8 0 0 1 1.8 1.8v9.2a1.8 1.8 0 0 1-1.8 1.8H4.6A1.8 1.8 0 0 1 2.8 16.6V7.4a1.8 1.8 0 0 1 1.8-1.8z"),
            dot(7.6f, 9.6f, 0.95f), dot(12f, 9.6f, 0.95f), dot(16.4f, 9.6f, 0.95f),
            dot(7.6f, 14.2f, 0.95f), dot(12f, 14.2f, 0.95f), dot(16.4f, 14.2f, 0.95f),
        )
    }

    // ── 符号 ─────────────────────────────────────────────────
    val symbols by lazy {
        Icon(
            "oime_symbols",
            P("M4.4 7.2h6.2M4.4 12h4.2M4.4 16.8h6.2"),
            dot(15.4f, 8.8f), dot(19f, 12.9f), dot(14.6f, 16.8f),
        )
    }

    // ── 设置（齿轮：圆 + 八向短齿）────────────────────────────
    val settings by lazy {
        Icon(
            "oime_settings",
            P("M12 8.9a3.1 3.1 0 1 0 0 6.2 3.1 3.1 0 0 0 0-6.2z"),
            P("M12 3.4v2.1M12 18.5v2.1M20.6 12h-2.1M5.5 12H3.4M18.1 5.9l-1.5 1.5M7.4 16.6l-1.5 1.5M18.1 18.1l-1.5-1.5M7.4 7.4 5.9 5.9"),
        )
    }

    // ── 退格（左向标签 + ×）──────────────────────────────────
    val backspace by lazy {
        Icon(
            "oime_backspace",
            P("M9.2 5.2h9.4a2.2 2.2 0 0 1 2.2 2.2v9.2a2.2 2.2 0 0 1-2.2 2.2H9.2c-.6 0-1.2-.3-1.6-.7L3 12.7a1 1 0 0 1 0-1.4L7.6 5.9c.4-.4 1-.7 1.6-.7z"),
            P("M12.4 9.8l4.6 4.6M17 9.8l-4.6 4.6", w = 1.5f),
        )
    }

    // ── 回车 = **纸飞机**（保留个性）────────────────────────
    val enter by lazy {
        Icon(
            "oime_enter",
            P("M3.2 11.3 20.6 4.2l-6.1 15.6-2.6-6.1z"),
            P("M11.9 13.7 20.6 4.2", w = 1.4f),
        )
    }

    // ── 空格 = **一条直线**（轮19.65 按用户要求）──────────────
    val space by lazy {
        Icon(
            "oime_space",
            P("M5.2 12h13.6"),
        )
    }

    // ── Shift ────────────────────────────────────────────────
    val shift by lazy {
        Icon(
            "oime_shift",
            P("M12 4.4 5.4 11.2h3.2v6h6.8v-6h3.2z"),
        )
    }

    // ── 返回 ─────────────────────────────────────────────────
    val back by lazy {
        Icon(
            "oime_back",
            P("M19 12H5.4M11 5.6 4.6 12l6.4 6.4"),
        )
    }

    // ── 麦克风 ───────────────────────────────────────────────
    val mic by lazy {
        Icon(
            "oime_mic",
            P("M12 3.6a2.9 2.9 0 0 1 2.9 2.9v5.3a2.9 2.9 0 0 1-5.8 0V6.5A2.9 2.9 0 0 1 12 3.6z"),
            P("M5.6 11.4a6.4 6.4 0 0 0 12.8 0"),
            P("M12 17.8v2.6"),
        )
    }

    // ── 云端（联网语音）──────────────────────────────────────
    val cloud by lazy {
        Icon(
            "oime_cloud",
            P("M7.4 18.4h9.9a3.6 3.6 0 0 0 .3-7.2 5 5 0 0 0-9.6-1.2 3.6 3.6 0 0 0-.6 8.4z"),
        )
    }

    // ── 刷新 / 部署 ──────────────────────────────────────────
    val refresh by lazy {
        Icon(
            "oime_refresh",
            P("M19.4 12a7.4 7.4 0 1 1-2.2-5.3"),
            P("M19.4 4.4v4.3h-4.3", w = 1.5f),
        )
    }

    // ── 信息 ─────────────────────────────────────────────────
    val info by lazy {
        Icon(
            "oime_info",
            P("M12 4.4a7.6 7.6 0 1 0 0 15.2 7.6 7.6 0 0 0 0-15.2z"),
            P("M12 10.8v5.4"),
            dot(12f, 8.1f, 0.95f),
        )
    }

    // ── 链接 ─────────────────────────────────────────────────
    val link by lazy {
        Icon(
            "oime_link",
            P("M10.4 13.6a3.4 3.4 0 0 0 4.9 0l2.6-2.6a3.4 3.4 0 0 0-4.9-4.9l-1 1"),
            P("M13.6 10.4a3.4 3.4 0 0 0-4.9 0l-2.6 2.6a3.4 3.4 0 0 0 4.9 4.9l1-1"),
        )
    }

    // ── 字体（A + 基准线）────────────────────────────────────
    val font by lazy {
        Icon(
            "oime_font",
            P("M6.4 16.6 11.4 6l5 10.6M8.4 13.4h6"),
            P("M6 19.6h12", w = 1.4f),
        )
    }

    // ── 配色板 ───────────────────────────────────────────────
    val palette by lazy {
        Icon(
            "oime_palette",
            P("M12 4.4a7.6 7.6 0 0 0 0 15.2c1.3 0 2-.8 2-1.8 0-.6-.3-1-.7-1.4-.4-.4-.6-.8-.6-1.3 0-1 .8-1.7 1.8-1.7h1.6a3.5 3.5 0 0 0 3.5-3.6c0-3.1-3.4-5.4-7.6-5.4z"),
            dot(8.6f, 10.4f, 0.9f), dot(12f, 8.6f, 0.9f), dot(15.4f, 9.8f, 0.9f), dot(8f, 13.9f, 0.9f),
        )
    }

    // ── 画中画 / 悬浮 ────────────────────────────────────────
    val pip by lazy {
        Icon(
            "oime_pip",
            P("M4.6 5.4h14.8a1.8 1.8 0 0 1 1.8 1.8v9.6a1.8 1.8 0 0 1-1.8 1.8H4.6A1.8 1.8 0 0 1 2.8 16.8V7.2a1.8 1.8 0 0 1 1.8-1.8z"),
            P("M12.4 11.6h5.2a1 1 0 0 1 1 1v2.6a1 1 0 0 1-1 1h-5.2a1 1 0 0 1-1-1v-2.6a1 1 0 0 1 1-1z", w = 1.5f),
        )
    }

    // ── 代码 ─────────────────────────────────────────────────
    val code by lazy {
        Icon(
            "oime_code",
            P("M9.2 7.6 4.8 12l4.4 4.4M14.8 7.6 19.2 12l-4.4 4.4"),
        )
    }

    // ── 表情 ─────────────────────────────────────────────────
    val emoji by lazy {
        Icon(
            "oime_emoji",
            P("M12 4.4a7.6 7.6 0 1 0 0 15.2 7.6 7.6 0 0 0 0-15.2z"),
            P("M8.9 14.1a4 4 0 0 0 6.2 0"),
            dot(9.4f, 9.9f, 0.95f), dot(14.6f, 9.9f, 0.95f),
        )
    }

    // ── 键盘 ─────────────────────────────────────────────────
    val keyboard by lazy {
        Icon(
            "oime_keyboard",
            P("M4.2 6.6h15.6a1.8 1.8 0 0 1 1.8 1.8v7.2a1.8 1.8 0 0 1-1.8 1.8H4.2a1.8 1.8 0 0 1-1.8-1.8V8.4a1.8 1.8 0 0 1 1.8-1.8z"),
            dot(7f, 10.2f, 0.85f), dot(12f, 10.2f, 0.85f), dot(17f, 10.2f, 0.85f),
            P("M8 13.9h8", w = 1.5f),
        )
    }

    // ── 勾选 ─────────────────────────────────────────────────
    val check by lazy {
        Icon(
            "oime_check",
            P("M5.4 12.6 10 17.2 18.6 7"),
        )
    }

    // ── 调节 ─────────────────────────────────────────────────
    val tune by lazy {
        Icon(
            "oime_tune",
            P("M4.4 8.6h15.2M4.4 15.4h15.2"),
            dot(9.4f, 8.6f, 1.5f), dot(15f, 15.4f, 1.5f),
        )
    }

    // ── 开关 ─────────────────────────────────────────────────
    val toggle by lazy {
        Icon(
            "oime_toggle",
            P("M8.4 7.4h7.2a4.6 4.6 0 0 1 0 9.2H8.4a4.6 4.6 0 0 1 0-9.2z"),
            dot(15.6f, 12f, 1.7f),
        )
    }

    // ── 应用（四宫格）────────────────────────────────────────
    val apps by lazy {
        Icon(
            "oime_apps",
            P("M5.4 5.2h4.6a1.2 1.2 0 0 1 1.2 1.2v4.6a1.2 1.2 0 0 1-1.2 1.2H5.4a1.2 1.2 0 0 1-1.2-1.2V6.4a1.2 1.2 0 0 1 1.2-1.2z"),
            P("M14 5.2h4.6a1.2 1.2 0 0 1 1.2 1.2v4.6a1.2 1.2 0 0 1-1.2 1.2H14a1.2 1.2 0 0 1-1.2-1.2V6.4A1.2 1.2 0 0 1 14 5.2z"),
            P("M5.4 13h4.6a1.2 1.2 0 0 1 1.2 1.2v4.6a1.2 1.2 0 0 1-1.2 1.2H5.4a1.2 1.2 0 0 1-1.2-1.2v-4.6A1.2 1.2 0 0 1 5.4 13z"),
            P("M14 13h4.6a1.2 1.2 0 0 1 1.2 1.2v4.6a1.2 1.2 0 0 1-1.2 1.2H14a1.2 1.2 0 0 1-1.2-1.2v-4.6A1.2 1.2 0 0 1 14 13z"),
        )
    }

    // ── 管理（文件夹）────────────────────────────────────────
    val manage by lazy {
        Icon(
            "oime_manage",
            P("M4.2 6.6h4.4l1.6 2.2h9.6a1.6 1.6 0 0 1 1.6 1.6v7a1.6 1.6 0 0 1-1.6 1.6H4.2a1.6 1.6 0 0 1-1.6-1.6v-9.2a1.6 1.6 0 0 1 1.6-1.6z"),
        )
    }

    // ── 太阳 ─────────────────────────────────────────────────
    val sun by lazy {
        Icon(
            "oime_sun",
            P("M12 8.4a3.6 3.6 0 1 0 0 7.2 3.6 3.6 0 0 0 0-7.2z"),
            P("M12 3.4v1.8M12 18.8v1.8M20.6 12h-1.8M5.2 12H3.4M18.1 5.9l-1.3 1.3M7.2 16.8l-1.3 1.3M18.1 18.1l-1.3-1.3M7.2 7.2 5.9 5.9"),
        )
    }

    // ── 月亮 ─────────────────────────────────────────────────
    val moon by lazy {
        Icon(
            "oime_moon",
            P("M19.4 14.6A7.8 7.8 0 0 1 9.4 4.6a7.8 7.8 0 1 0 10 10z"),
        )
    }

    // ── ○ 圆环（○ 菜单 / 圆环设置；用户指定：菜单用圆环）────
    val ring by lazy {
        Icon(
            "oime_ring",
            P("M12 4.6a7.4 7.4 0 1 0 0 14.8 7.4 7.4 0 0 0 0-14.8z"),
            P("M12 9.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8z", w = 1.3f),
        )
    }

    // ── 候选 ─────────────────────────────────────────────────
    val candidates by lazy {
        Icon(
            "oime_candidates",
            P("M4.4 6.6h15.2M4.4 12h9M4.4 17.4h9"),
            P("M17.4 15.2l1.9 1.9 3-3.4", w = 1.5f),
        )
    }

    // ── 中英 ─────────────────────────────────────────────────
    val lang by lazy {
        Icon(
            "oime_lang",
            P("M4.8 16.4 9 6.6l4.2 9.8M6.4 13.4h5.2"),
            P("M16.4 11.4h3.2v5.2h-3.2z", w = 1.4f),
        )
    }

    // ── 悬浮键盘 ─────────────────────────────────────────────
    val floatKbd by lazy {
        Icon(
            "oime_floatkbd",
            P("M4.4 8.6h11.4a1.6 1.6 0 0 1 1.6 1.6v5.6a1.6 1.6 0 0 1-1.6 1.6H4.4a1.6 1.6 0 0 1-1.6-1.6v-5.6a1.6 1.6 0 0 1 1.6-1.6z", w = 1.5f),
            dot(6.6f, 11.7f, 0.8f), dot(9.6f, 11.7f, 0.8f), dot(12.6f, 11.7f, 0.8f),
            P("M7.6 14.5h4.6", w = 1.5f),
            P("M16.6 6.6h3.8M18.5 4.7v3.8", w = 1.5f),
        )
    }

    // ── 单手 ─────────────────────────────────────────────────
    val oneHand by lazy {
        Icon(
            "oime_onehand",
            P("M13.4 8.6h6.2a1.6 1.6 0 0 1 1.6 1.6v5.6a1.6 1.6 0 0 1-1.6 1.6h-6.2a1.6 1.6 0 0 1-1.6-1.6v-5.6a1.6 1.6 0 0 1 1.6-1.6z", w = 1.5f),
            P("M4.2 6.4v11.2", w = 1.5f),
            dot(16.2f, 11.7f, 0.8f), dot(19f, 11.7f, 0.8f),
            P("M16 14.5h3", w = 1.5f),
        )
    }

    // ── 重做 / 撤回 ──────────────────────────────────────────
    val redo by lazy {
        Icon(
            "oime_redo",
            P("M19.6 9.6H10a5.2 5.2 0 0 0 0 10.4h3.6", w = 1.5f),
            P("M16.2 5.8l4 3.8-4 3.8", w = 1.5f),
        )
    }

    val undo by lazy {
        Icon(
            "oime_undo",
            P("M4.4 9.6H14a5.2 5.2 0 0 1 0 10.4h-3.6", w = 1.5f),
            P("M7.8 5.8l-4 3.8 4 3.8", w = 1.5f),
        )
    }

    // ── 删除（垃圾桶）────────────────────────────────────────
    val trash by lazy {
        Icon(
            "oime_trash",
            P("M4.8 7.4h14.4"),
            P("M9.4 7.4V5.6a1.2 1.2 0 0 1 1.2-1.2h2.8a1.2 1.2 0 0 1 1.2 1.2v1.8"),
            P("M6.8 7.4l.9 10.4a1.8 1.8 0 0 0 1.8 1.6h5a1.8 1.8 0 0 0 1.8-1.6l.9-10.4", w = 1.5f),
            P("M10.4 11.2v4.6M13.6 11.2v4.6", w = 1.4f),
        )
    }

    // ── 名称映射（保持与既有调用点一致）──────────────────────
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
        "onehand", "hand" -> oneHand
        "trash", "deleteall" -> trash
        "voice" -> mic
        "float" -> pip
        "floatkbd" -> floatKbd
        "lua" -> code
        "theme" -> palette
        "deploy" -> refresh
        "hide" -> back
        else -> null
    }
}

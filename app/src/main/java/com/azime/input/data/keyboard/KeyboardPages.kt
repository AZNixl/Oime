package com.azime.input.data.keyboard

import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyboardLayout
import com.azime.input.data.model.KeyboardRow
import com.azime.input.data.model.KeyType

/**
 * 主键盘字母键长按符号（按用户规范）：
 * Q-P → 1-0；A-L → 全选/-/@/#//——/+/括号气泡/=；
 * Z-M → `/剪切/复制/粘贴/"/'/：；逗号 → ！；句号 → ？
 *
 * 取值为内置命令 identifier（select_all/cut/copy/paste）时走命令分发，
 * 其他一律字面上屏；K 键长按 = 常用括号气泡（[BracketPairs]）。
 */
val LongPressSymbols: Map<Char, List<String>> = mapOf(
    'q' to listOf("1"), 'w' to listOf("2"), 'e' to listOf("3"),
    'r' to listOf("4"), 't' to listOf("5"), 'y' to listOf("6"),
    'u' to listOf("7"), 'i' to listOf("8"), 'o' to listOf("9"),
    'p' to listOf("0"),
    'a' to listOf("select_all"), 's' to listOf("-"), 'd' to listOf("@"),
    // 轮19.19：H 长按两个候选——中文模式出「——」，英文模式出「_」（见 KeyboardKey 的 ASCII 优选）
    'f' to listOf("#"), 'g' to listOf("/"), 'h' to listOf("——", "_"),
    'j' to listOf("+"), 'k' to listOf("括号"), 'l' to listOf("="),
    'z' to listOf("`"), 'x' to listOf("cut"), 'c' to listOf("copy"),
    'v' to listOf("paste"), 'b' to listOf("\""), 'n' to listOf("'"),
    'm' to listOf("："),
    ',' to listOf("！"), '.' to listOf("？"),
)

/** K 键长按气泡：常用括号对（参考 26键.lua，上屏后光标移到括号内）。 */
val BracketPairs: List<String> = listOf(
    "{}{Left}", "〈〉{Left}", "(){Left}", "《》{Left}", "[]{Left}", "【】{Left}",
)

/**
 * 轮19.24：**英文（ASCII）模式下的长按符号变体**——键面符号与长按气泡随中英自动切换。
 * 语义对齐 trime2 `26键.lua` 的 `ascii = { long_click = ... }` 子表：
 * 中文标点 ↔ ASCII，其余（数字、- @ # + = 等命令）两模式一致。
 */
val LongPressSymbolsAscii: Map<Char, List<String>> = mapOf(
    'h' to listOf("_"),
    'm' to listOf(":"),
    ',' to listOf("!"),
    '.' to listOf("?"),
    'b' to listOf("\""),
    'n' to listOf("'"),
    // K 键：括号气泡换成 ASCII 括号（与中文一样 6 对，位置一一对应）
    'k' to listOf("{}{Left}", "<>{Left}", "(){Left}", "[]{Left}", "\"\"{Left}", "''{Left}"),
)

/** 按中英模式取长按符号列表（英文模式优先 ASCII 变体）。 */
fun longPressSymbolsFor(code: String, ascii: Boolean): List<String> {
    // 轮19.25：K 键特殊——长按应出**括号对**（BracketPairs / ASCII 变体）。
    // 19.24 重构时漏了这层特判，导致中文模式弹出的是 LongPressSymbols['k'] 里的字面量「括号」。
    if (code == "k") return if (ascii) LongPressSymbolsAscii['k'] ?: BracketPairs else BracketPairs
    val c = code.firstOrNull() ?: return emptyList()
    if (ascii) LongPressSymbolsAscii[c]?.let { return it }
    return LongPressSymbols[c] ?: emptyList()
}

/** 长按符号的键面提示文本（命令 identifier 转中文；随中英模式切换）。 */
fun longPressHint(code: String, ascii: Boolean = false): String? {
    val list = when {
        code == "k" -> if (ascii) LongPressSymbolsAscii['k'] else BracketPairs
        ascii && code.length == 1 -> LongPressSymbolsAscii[code.first()] ?: LongPressSymbols[code.first()]
        else -> LongPressSymbols[code.firstOrNull() ?: ' ']
    } ?: return null
    val first = list.firstOrNull() ?: return null
    return when (first) {
        "select_all" -> "全选"
        "cut" -> "剪切"
        "copy" -> "复制"
        "paste" -> "粘贴"
        else -> first.removeSuffix("{Left}")
    }
}

/** 内置功能键动作（trime2 identifier 约定）。 */
object KeyActions {
    const val SPACE_LONG = "toggle_ascii"
    const val ENTER_LONG = "newline"
    const val SHIFT_LONG = "caps_lock"
    const val SYMBOLS_LONG = "choose_page"
    const val BS_UP = "delete_all"
    const val BS_DOWN = "undo"
    const val BS_LEFT = "select_back"
}

/**
 * 内置键盘页。布局参考小企鹅输入法（fcitx5-android）26 键样式：
 *
 * 行1: Q  W  E  R  T  Y  U  I  O  P
 * 行2: ·5  A  S  D  F  G  H  J  K  L  ·5   （左右各 0.5 键宽偏移，G 对齐 V）
 * 行3: ⇧  Z  X  C  V  B  N  M  ⌫
 * 行4: 123 ,  ␣␣␣␣  .  ⏎
 *
 * code 为发送给 RIME 的字符（字母统一小写，大小写由 Service 按 shift/ascii 状态决定）。
 */
object KeyboardPages {

    private fun row(vararg keys: Key) = KeyboardRow(keys.toList())

    private fun charKey(label: String, width: Float = 1f): Key =
        Key(label = label, code = label.lowercase(), width = width, type = KeyType.CHARACTER)

    /** 占位空键：不渲染背景、不响应点击，用于行内对齐偏移。 */
    private fun spacerKey(width: Float): Key =
        Key(label = "", code = "spacer", width = width, type = KeyType.FUNCTION)

    private fun backspace(width: Float = 1.5f) = Key(
        label = "⌫", code = "backspace", width = width, type = KeyType.DELETE, icon = "backspace",
        swipeUp = KeyActions.BS_UP, swipeDown = KeyActions.BS_DOWN, swipeLeft = KeyActions.BS_LEFT,
    )

    private fun space(width: Float = 4f) = Key(
        label = "空格", code = "space", width = width, type = KeyType.SPACE, icon = "space",
        // 轮19.34（按用户要求）：长按不再切中英，改为**上滑**切中英
        longClick = null,
        swipeUp = KeyActions.SPACE_LONG,   // = "toggle_ascii"
    )

    private fun enter(width: Float = 1.5f) = Key(
        label = "⏎", code = "enter", width = width, type = KeyType.ENTER, icon = "enter",
        longClick = KeyActions.ENTER_LONG,
    )

    private fun shift(width: Float = 1.5f) = Key(
        label = "⇧", code = "shift", width = width, type = KeyType.MODIFIER, icon = "shift",
        longClick = KeyActions.SHIFT_LONG,
    )

    /** 符号/页面切换键：长按呼出「默认键盘」选择气泡（26键 / 九宫格数字 / emoji）。 */
    private fun pageKey(label: String, target: String = "symbols", width: Float = 1.5f, icon: String? = null) = Key(
        label = label, code = target, width = width, type = KeyType.FUNCTION, icon = icon,
        longClick = KeyActions.SYMBOLS_LONG,
    )

    /**
     * 26 键主键盘（第二行左右各偏移 0.5 键宽：G 对齐 V，第四行与上一行边缘对齐）。
     * rev=2：第四行 123/⏎ 加宽填满整行（旧版行尾留白）。
     */
    val qwerty: KeyboardLayout = KeyboardLayout(
        name = "qwerty",
        rows = listOf(
            row(
                charKey("Q"), charKey("W"), charKey("E"), charKey("R"), charKey("T"),
                charKey("Y"), charKey("U"), charKey("I"), charKey("O"), charKey("P"),
            ),
            row(
                spacerKey(0.5f),
                charKey("A"), charKey("S"), charKey("D"), charKey("F"), charKey("G"),
                charKey("H"), charKey("J"), charKey("K"), charKey("L"),
                spacerKey(0.5f),
            ),
            // 轮19.10 对齐修复：第 3 行改为与第 2 行**同为 11 个子元素、权重和同为 10**，
            // 这样两行的单位键宽完全一致、Z..M 恰好落在 S..K 正下方。
            // 原来第 3 行是 [shift 1.5][7 字母][backspace 1.5]（9 个元素、8 道行距），
            // 行距数不同 → 单位键宽比第 2 行大 0.2×rowGap，累到行尾偏出近 1 个键位。
            row(
                // 轮19.11b：填充键挪到**内侧**（⇧ 与 Z 之间、M 与 ⌫ 之间），而不是两端——
                // 这样 ⇧ 左边缘 = 行首 0（与下方 # 键左边缘齐），⌫ 右边缘 = 行尾 W（与下方 ⏎ 齐），
                // 同时仍是 11 子元素、10 道行距 ⇒ 单位键宽与第 2 行差 0.6%、
                // Z..M 相对 S..K 偏差 < 0.3dp（字母依然对齐）。
                shift(width = 1.5f),
                spacerKey(0.01f),
                charKey("Z"), charKey("X"), charKey("C"), charKey("V"),
                charKey("B"), charKey("N"), charKey("M"),
                spacerKey(0.01f),
                backspace(width = 1.5f),
            ),
            row(
                pageKey("123", width = 1.7f, icon = "symbols"), // 稍宽于 shift(1.5)，紧凑起步
                charKey(","),
                // 轮19.29：空格默认宽度 4.3 → **4.5**，回车 2.0 → **1.8**（Σ 仍为 10.0，列仍对齐）
                space(width = 4.5f),
                charKey("."),
                enter(width = 1.8f),
            ),
        ),
        rev = 7, // 轮19.15：横屏第 4 行补回回车键
    )

    /**
     * 横屏分体键盘（轮19.13，方案 L4 镜像错位分体，**B 键归右半**）。
     *
     * 用占位键表达分体结构：左半缩进（越往下越往中间缩）+ 中间分体空隙 + 右半镜像缩进。
     * 每行权重和都是 **10.9** ⇒ 各行单位键宽一致，上下按键对齐；
     * 第四行按同样的 10.9 设计（123 / 空格 ×2 / ⌫），所以底行与字母行也对齐。
     */
    val qwertyLand: KeyboardLayout = KeyboardLayout(
        name = "qwerty_land",
        rows = listOf(
            // 1：Q W E R T ‖ Y U I O P
            row(
                charKey("Q"), charKey("W"), charKey("E"), charKey("R"), charKey("T"),
                spacerKey(0.9f),
                charKey("Y"), charKey("U"), charKey("I"), charKey("O"), charKey("P"),
            ),
            // 2：左半右缩 0.4 / 右半左缩 0.4（镜像）
            row(
                spacerKey(0.4f),
                charKey("A"), charKey("S"), charKey("D"), charKey("F"), charKey("G"),
                spacerKey(0.9f),
                charKey("H"), charKey("J"), charKey("K"), charKey("L"),
                spacerKey(0.6f),
            ),
            // 3：缩进 0.8；B 归右半 → 左 Z X C V，右 B N M
            row(
                spacerKey(0.8f),
                charKey("Z"), charKey("X"), charKey("C"), charKey("V"),
                spacerKey(0.9f),
                charKey("B"), charKey("N"), charKey("M"),
                spacerKey(2.2f),
            ),
            // 4：123 ‖ 空格 ｜ 空格 ‖ ⌫ ⏎（权重和同为 10.9，与上面各列对齐）
            // 轮19.15：补回**回车键**（横屏首版漏了 ⏎，用户反馈"横屏没有回车"）
            row(
                pageKey("123", width = 1.5f, icon = "symbols"),
                space(width = 3.5f),
                spacerKey(0.9f),
                space(width = 2.2f),
                backspace(width = 1.0f),
                enter(width = 1.8f),
            ),
        ),
        rev = 1,
    )

    /** 数字/符号页（第四行首键切换）。rev=2：与 qwerty 第四行同步填满。 */
    val symbols: KeyboardLayout = KeyboardLayout(
        name = "symbols",
        rows = listOf(
            row(
                charKey("1"), charKey("2"), charKey("3"), charKey("4"), charKey("5"),
                charKey("6"), charKey("7"), charKey("8"), charKey("9"), charKey("0"),
            ),
            row(
                spacerKey(0.5f),
                charKey("@"), charKey("#"), charKey("¥"), charKey("%"), charKey("&"),
                charKey("-"), charKey("+"), charKey("("), charKey(")"),
                spacerKey(0.5f),
            ),
            row(
                pageKey("九宫格", "numpad", icon = "numpad"),
                charKey("*"), charKey("\""), charKey("'"), charKey(":"),
                charKey(";"), charKey("!"), charKey("?"),
                backspace(),
            ),
            row(
                // 轮19.2：第四行首键改为「返回」（原为切九宫格，九宫格入口已在第三行）
                pageKey("返回", "main", width = 1.7f, icon = "back"),
                charKey(","),
                // 轮19.29：与主键盘同步（空格 4.5 / 回车 1.8）
                space(width = 4.5f),
                charKey("."),
                enter(width = 1.8f),
            ),
        ),
        rev = 3,
    )

    /**
     * 九宫格数字键盘（编辑器用表达，rev=3 对齐实际 NumpadPane 渲染）：
     * 行1: 滑键  1  2  3  ⌫
     * 行2: 滑键  4  5  6  符号
     * 行3: 滑键  7  8  9  空格
     * 行4: 返回  =  0  .  ⏎
     * （实际渲染中左列滑键为跨 3 行的单键，此处以「滑键」占位对齐编辑器预览；
     *   数字横向 123/456/789 排布，0 左 = 号、右 . 号 —— 反馈轮9。）
     */
    val numpad: KeyboardLayout = KeyboardLayout(
        name = "numpad",
        rows = listOf(
            row(
                Key("滑键", code = "slider", width = 1f, type = KeyType.FUNCTION),
                charKey("1"), charKey("2"), charKey("3"),
                backspace(width = 1f),
            ),
            row(
                Key("滑键", code = "slider", width = 1f, type = KeyType.FUNCTION),
                charKey("4"), charKey("5"), charKey("6"),
                Key("符号", code = "symgrid", width = 1f, type = KeyType.FUNCTION),
            ),
            row(
                Key("滑键", code = "slider", width = 1f, type = KeyType.FUNCTION),
                charKey("7"), charKey("8"), charKey("9"),
                space(width = 1f),
            ),
            row(
                pageKey("返回", target = "main", width = 1f, icon = "back"),
                // 轮19.2：原 = 号键改为 00（= 已并入左列滑键符号带）
                charKey("00"), charKey("0"), charKey("."),
                enter(width = 1f),
            ),
        ),
        rev = 4,
    )

    /** 九宫格左列滑动选符号键的符号带（上下滑动选择，松手上屏）。 */
    val NumpadSliderSymbols: List<String> = listOf(
        "+", "-", "*", "/", "=", "？", "！",
    )
}

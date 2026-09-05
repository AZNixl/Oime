package com.azime.input.data.keyboard

import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyboardLayout
import com.azime.input.data.model.KeyboardRow
import com.azime.input.data.model.KeyType

/** 主键盘页字母键的长按符号（fcitx5 风格：q→1!、w→2@ …）。 */
val LongPressSymbols: Map<Char, List<String>> = mapOf(
    'q' to listOf("1", "!"), 'w' to listOf("2", "@"), 'e' to listOf("3", "#"),
    'r' to listOf("4", "$"), 't' to listOf("5", "%"), 'y' to listOf("6", "^"),
    'u' to listOf("7", "&"), 'i' to listOf("8", "*"), 'o' to listOf("9", "("),
    'p' to listOf("0", ")"),
    'a' to listOf("~"), 's' to listOf("`"), 'd' to listOf("\\"), 'f' to listOf("|"),
    'g' to listOf("/"), 'h' to listOf(":"), 'j' to listOf(";"), 'k' to listOf("\""),
    'l' to listOf("'"),
    'z' to listOf("·"), 'x' to listOf("×"), 'c' to listOf("《"), 'v' to listOf("》"),
    'b' to listOf("["), 'n' to listOf("]"), 'm' to listOf("—"),
)

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
 * 行2: A  S  D  F  G  H  J  K  L
 * 行3: ⇧  Z  X  C  V  B  N  M  ⌫
 * 行4: 123 ,  ␣␣␣␣  .  ⏎
 *
 * code 为发送给 RIME 的字符（字母统一小写，大小写由 Service 按 shift/ascii 状态决定）。
 */
object KeyboardPages {

    private fun row(vararg keys: Key) = KeyboardRow(keys.toList())

    private fun charKey(label: String, width: Float = 1f): Key =
        Key(label = label, code = label.lowercase(), width = width, type = KeyType.CHARACTER)

    private fun backspace(width: Float = 1.5f) = Key(
        label = "⌫", code = "backspace", width = width, type = KeyType.DELETE,
        swipeUp = KeyActions.BS_UP, swipeDown = KeyActions.BS_DOWN, swipeLeft = KeyActions.BS_LEFT,
    )

    private fun space(width: Float = 4f) = Key(
        label = "空格", code = "space", width = width, type = KeyType.SPACE,
        longClick = KeyActions.SPACE_LONG,
    )

    private fun enter(width: Float = 1.5f) = Key(
        label = "⏎", code = "enter", width = width, type = KeyType.ENTER,
        longClick = KeyActions.ENTER_LONG,
    )

    private fun shift(width: Float = 1.5f) = Key(
        label = "⇧", code = "shift", width = width, type = KeyType.MODIFIER,
        longClick = KeyActions.SHIFT_LONG,
    )

    /** 符号/页面切换键：长按呼出「默认键盘」选择气泡（26键 / 九宫格数字 / emoji）。 */
    private fun pageKey(label: String, target: String = "symbols", width: Float = 1.5f) = Key(
        label = label, code = target, width = width, type = KeyType.FUNCTION,
        longClick = KeyActions.SYMBOLS_LONG,
    )

    /** 26 键主键盘。 */
    val qwerty: KeyboardLayout = KeyboardLayout(
        name = "qwerty",
        rows = listOf(
            row(
                charKey("Q"), charKey("W"), charKey("E"), charKey("R"), charKey("T"),
                charKey("Y"), charKey("U"), charKey("I"), charKey("O"), charKey("P"),
            ),
            row(
                charKey("A"), charKey("S"), charKey("D"), charKey("F"), charKey("G"),
                charKey("H"), charKey("J"), charKey("K"), charKey("L"),
            ),
            row(
                shift(),
                charKey("Z"), charKey("X"), charKey("C"), charKey("V"),
                charKey("B"), charKey("N"), charKey("M"),
                backspace(),
            ),
            row(
                pageKey("123"),
                charKey(","),
                space(),
                charKey("."),
                enter(),
            ),
        ),
    )

    /** 数字/符号页（第四行首键切换）。 */
    val symbols: KeyboardLayout = KeyboardLayout(
        name = "symbols",
        rows = listOf(
            row(
                charKey("1"), charKey("2"), charKey("3"), charKey("4"), charKey("5"),
                charKey("6"), charKey("7"), charKey("8"), charKey("9"), charKey("0"),
            ),
            row(
                charKey("@"), charKey("#"), charKey("¥"), charKey("%"), charKey("&"),
                charKey("-"), charKey("+"), charKey("("), charKey(")"),
            ),
            row(
                pageKey("ABC"),
                charKey("*"), charKey("\""), charKey("'"), charKey(":"),
                charKey(";"), charKey("!"), charKey("?"),
                backspace(),
            ),
            row(
                pageKey("ABC"),
                charKey(","),
                space(),
                charKey("."),
                enter(),
            ),
        ),
    )

    /** 九宫格数字键盘（键宽 2.5f × 4 键铺满一行；第 3 行末为退格；第 4 行首「26」返回主键盘）。 */
    val numpad: KeyboardLayout = KeyboardLayout(
        name = "numpad",
        rows = listOf(
            row(
                charKey("1", width = 2.5f), charKey("2", width = 2.5f), charKey("3", width = 2.5f),
                Key("+", code = "+", width = 2.5f, type = KeyType.CHARACTER),
            ),
            row(
                charKey("4", width = 2.5f), charKey("5", width = 2.5f), charKey("6", width = 2.5f),
                Key("-", code = "-", width = 2.5f, type = KeyType.CHARACTER),
            ),
            row(
                charKey("7", width = 2.5f), charKey("8", width = 2.5f), charKey("9", width = 2.5f),
                backspace(width = 2.5f),
            ),
            row(
                pageKey("26", target = "main", width = 2f),
                charKey("0", width = 2f), charKey(".", width = 2f),
                space(width = 2f),
                enter(2f),
            ),
        ),
    )
}

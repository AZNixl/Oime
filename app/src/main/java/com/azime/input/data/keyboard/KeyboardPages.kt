package com.azime.input.data.keyboard

import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyboardLayout
import com.azime.input.data.model.KeyboardRow
import com.azime.input.data.model.KeyType

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
                Key("⇧", code = "shift", width = 1.5f, type = KeyType.MODIFIER),
                charKey("Z"), charKey("X"), charKey("C"), charKey("V"),
                charKey("B"), charKey("N"), charKey("M"),
                Key("⌫", code = "backspace", width = 1.5f, type = KeyType.DELETE),
            ),
            row(
                Key("123", code = "symbols", width = 1.5f, type = KeyType.FUNCTION),
                charKey(","),
                Key("空格", code = "space", width = 4f, type = KeyType.SPACE),
                charKey("."),
                Key("⏎", code = "enter", width = 1.5f, type = KeyType.ENTER),
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
                Key("ABC", code = "symbols", width = 1.5f, type = KeyType.FUNCTION),
                charKey("*"), charKey("\""), charKey("'"), charKey(":"),
                charKey(";"), charKey("!"), charKey("?"),
                Key("⌫", code = "backspace", width = 1.5f, type = KeyType.DELETE),
            ),
            row(
                Key("ABC", code = "symbols", width = 1.5f, type = KeyType.FUNCTION),
                charKey(","),
                Key("空格", code = "space", width = 4f, type = KeyType.SPACE),
                charKey("."),
                Key("⏎", code = "enter", width = 1.5f, type = KeyType.ENTER),
            ),
        ),
    )
}

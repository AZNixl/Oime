package com.azime.input.data.model

/**
 * 键盘布局。
 *
 * 动作字段（[Key.longClick]/[Key.swipeUp]…）的取值与 trime2 preset_keys 约定兼容：
 * - `select_all` / `cut` / `copy` / `paste` / `toggle_ascii` / `newline` /
 *   `caps_lock` / `delete_all` / `undo` / `page:main` / `page:symbols` /
 *   `page:numpad` / `page:emoji` / `choose_page`  等英文 identifier → 内置命令
 * - preset_keys.lua 中定义的条目名 → 引用该条目
 * - 其他任意文本 → 直接上屏（支持 trime2 的 `{text}{Left}` 光标后缀语法）
 */
data class KeyboardLayout(
    val name: String,
    val rows: List<KeyboardRow>
)

data class KeyboardRow(
    val keys: List<Key>
)

data class Key(
    val label: String,
    val code: String,
    val width: Float = 1.0f,
    val type: KeyType = KeyType.CHARACTER,
    /** 高度系数（1.0 = 标准键高），编辑器可调，渲染行高按行内最大值取。 */
    val height: Float = 1.0f,
    /** 长按动作（同 trime2 long_click）。 */
    val longClick: String? = null,
    val swipeUp: String? = null,
    val swipeDown: String? = null,
    val swipeLeft: String? = null,
    val swipeRight: String? = null,
    /** 键面右上角的小字提示（一般为长按符号）。 */
    val hint: String? = null,
)

enum class KeyType {
    CHARACTER,
    FUNCTION,
    MODIFIER,
    SPACE,
    ENTER,
    DELETE
}

package com.azime.input.data.model

/**
 * 键盘布局。
 *
 * 动作字段（[Key.longClick]/[Key.swipeUp]…）的取值：
 * - `select_all` / `cut` / `copy` / `paste` / `toggle_ascii` / `newline` /
 *   `caps_lock` / `delete_all` / `undo` / `page:main` / `page:symbols` /
 *   `page:numpad` / `page:emoji` / `choose_page`  等英文 identifier → 内置命令
 * - 内置功能键值（对齐 RIME 命名，如 `escape` / `prior` / `switch_ime`…）
 * - 其他任意文本 → 直接上屏（支持 trime2 的 `{text}{Left}` 光标后缀语法）
 */
data class KeyboardLayout(
    val name: String,
    val rows: List<KeyboardRow>,
    /**
     * 布局版本：仅作**编辑器热重载信号**与记录 ✓。
     * 轮19.158：**不再用于"旧自定义自动失效"** ✗ —— 编辑器完全接管内置布局后，
     * 用户对内置布局的自定义（键宽/键位/长按）必须跨版本保留 ✓
     * （原"rev 升级即删同名自定义"已移除 ✗ 用户自定义被静默丢弃 ✗）
     */
    val rev: Int = 1
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
    /**
     * 轮19.19：**长按气泡的符号列表**（编辑器可编辑，空格分隔存进这里）。
     * 非空时覆盖内置 LongPressSymbols / K 键括号表，实现"自定义长按 popup"。
     * 元素支持内置命令（copy/cut/…）与 `{text}{Left}` 光标后缀语法。
     */
    val popup: List<String> = emptyList(),
    /**
     * 轮19.158：**是否套用内置长按符号表** ✓（仅对无显式 longClick/popup 的键有意义 ✓）。
     * - true / null（默认）→ 长按回落内置表（26 键字母键 ✓ 随中英切换 ✓）
     * - false → 编辑器已显式处理过（自定义或**清空**）⇒ **不再回落** ✓
     *   （用户反馈"默认符号不能留空，留空就会重新写入默认的符号"✗ ⇒ 三态落库 ✓）
     * Boolean? 兼容旧 JSON：Gson 缺字段 = null = 视为 true ✓
     */
    val longPressBuiltin: Boolean? = true,
    /**
     * 键面图标名（轮19.4）：指向 OimeIcons.byName(name)；非空时键面渲染图标而非文字。
     * 功能键（换挡/退格/回车/空格/返回等）用；文字标签仍保留（无图标时回落、无障碍描述用）。
     */
    val icon: String? = null,
)

enum class KeyType {
    CHARACTER,
    FUNCTION,
    MODIFIER,
    SPACE,
    ENTER,
    DELETE
}

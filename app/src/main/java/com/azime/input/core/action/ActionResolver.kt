package com.azime.input.core.action

/**
 * 布局动作解析器（原 `LuaScriptManager`，轮19.51 改名）。
 *
 * 取值来源优先级：
 * 1. **内置功能键值**（对齐 RIME 命名：select_all / escape / prior / switch_ime …）
 * 2. 字面文本（支持 `{text}{Left}` 光标后缀语法）
 *
 * 说明：`preset_keys` 预设表与 Lua 脚本加载**已全部移除**（19.51），
 * 这里只剩纯解析逻辑，不再依赖 luaj。
 */
sealed interface ResolvedAction {
    /** 直接上屏文本；moveLeft/moveRight 为上屏后的光标移动步数。 */
    data class Commit(val text: String, val moveLeft: Int = 0, val moveRight: Int = 0) : ResolvedAction
    /** 内置命令 identifier（交给 Service 执行）。 */
    data class Command(val ident: String) : ResolvedAction
}

object ActionResolver {

    /**
     * 把布局动作字段（longClick/swipeUp/…）解析为可执行动作。
     * 识别**内置功能键值**；其余按字面文本处理（支持 `{Left}` / `{Right}` 光标后缀）。
     */
    fun resolveAction(value: String): ResolvedAction? {
        val v = value.trim()
        if (v.isEmpty()) return null

        // 1. 内置功能键值
        resolveBuiltin(v)?.let { return it }

        // 2. 字面文本
        return resolveLiteral(v)
    }

    /**
     * 内置功能键值（对齐 **RIME 的内置功能**命名，兼容大小写/下划线/连字符写法）。
     * 清单与含义会显示在键盘编辑器的「内置功能」说明里。
     */
    private val commands = setOf(
        // 编辑
        "select_all", "cut", "copy", "paste", "undo", "delete_all",
        // 上屏 / 删除
        "newline", "return", "enter", "backspace", "delete", "space", "tab", "shift_tab",
        // 中英 / 大小写
        "toggle_ascii", "ascii_mode", "caps_lock", "shift",
        // 光标
        "left", "right", "up", "down", "home", "end",
        // 翻页（RIME 约定）
        "prior", "next", "page_up", "page_down",
        // 组合
        "esc", "escape", "clear",
        // 键盘页
        "page:main", "page:symbols", "page:numpad", "page:emoji",
        "choose_page", "toggle_symbols",
        // Oime 内置
        "deploy", "switch_ime", "clipboard", "menu",
        // 轮19.52 新增：时间与重复（大小写不敏感，Date/ChineseDate/RepeatCommit 均可）
        "date", "time", "chinesedate", "repeatcommit",
    )

    private fun resolveBuiltin(v: String): ResolvedAction? =
        if (v.lowercase() in commands) ResolvedAction.Command(v.lowercase()) else null

    /** `{text}{Left}` 语法：上屏 text 后光标左移。 */
    private fun resolveLiteral(v: String): ResolvedAction {
        var text = v
        var left = 0
        var right = 0
        Regex("""\{(Left|Right)\}\s*$""").find(text)?.let { m ->
            if (m.groupValues[1] == "Left") left++ else right++
            text = text.substring(0, m.range.first)
        }
        return ResolvedAction.Commit(text, moveLeft = left, moveRight = right)
    }
}

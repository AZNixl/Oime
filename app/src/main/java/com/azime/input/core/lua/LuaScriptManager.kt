package com.azime.input.core.lua

import com.azime.input.core.storage.StorageManager
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * 解析后的动作。取值来源优先级：
 * 1. **内置功能键值**（对齐 RIME 命名：select_all / escape / prior / switch_ime …）
 * 2. 字面文本（支持 `{text}{Left}` 光标后缀语法）
 *
 * 说明：`preset_keys` 预设表功能**已移除**，动作不再查预设表。
 */
sealed interface ResolvedAction {
    /** 直接上屏文本；moveLeft/moveRight 为上屏后的光标移动步数。 */
    data class Commit(val text: String, val moveLeft: Int = 0, val moveRight: Int = 0) : ResolvedAction
    /** 内置命令 identifier（交给 Service 执行）。 */
    data class Command(val ident: String) : ResolvedAction
}

object LuaScriptManager {

    private var globals: Globals? = null

    /**
     * 加载用户 Lua 脚本（若存在）——只执行脚本自身的副作用，**不参与动作解析**
     * （预设表 preset_keys 已移除）。脚本目录如需使用请自行创建。
     */
    fun loadScript() {
        try {
            val g = JsePlatform.standardGlobals()
            globals = g
            val scriptFile = StorageManager.getLuaScriptFile()
            if (scriptFile.exists()) {
                g.loadfile(scriptFile.absolutePath)?.call()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── 动作解析 ─────────────────────────────────────────────

    /**
     * 把布局动作字段（longClick/swipeUp/…）解析为可执行动作。
     * 识别**内置功能键值**；其余按字面文本处理（支持 `{Left}` / `{Right}` 光标后缀）。
     */
    fun resolveAction(value: String): ResolvedAction? {
        val v = value.trim()
        if (v.isEmpty()) return null

        // 轮19.43：**已去除 preset_keys 预设功能** —— 动作只认「内置功能键值」或字面文本
        // （原来第 1 步是查 presetEntries 预设表，现整段移除）

        // 1. 内置命令
        resolveBuiltin(v)?.let { return it }

        // 3. 字面文本
        return resolveLiteral(v)
    }

    /**
     * 内置功能键值（轮19.43：对齐 **RIME 的内置功能**命名，兼容大小写/下划线/连字符写法）。
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
    )

    private fun resolveBuiltin(v: String): ResolvedAction? =
        if (v.lowercase() in commands) ResolvedAction.Command(v.lowercase()) else null

    /** `{text}{Left}` 语法：上屏 text 后光标左移（trime2 兼容）。 */
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

    // ── 编辑器支持 ───────────────────────────────────────────

    /** 读取当前脚本内容；文件不存在时返回空串。 */
    fun scriptText(): String =
        StorageManager.getLuaScriptFile().takeIf { it.exists() }?.readText() ?: ""

    /** 校验语法（仅编译不执行）。返回 null 表示通过，否则为错误信息。 */
    fun validate(source: String): String? = try {
        val g = globals ?: JsePlatform.standardGlobals()
        g.load(source, "azime_editor")
        null
    } catch (e: Exception) {
        e.message?.take(200) ?: "语法错误"
    }

    /** 保存并重新加载；语法错误时拒绝写入并返回错误信息。 */
    fun saveScript(source: String): String? {
        validate(source)?.let { return it }
        StorageManager.getLuaScriptFile().writeText(source)
        loadScript()
        return null
    }
}

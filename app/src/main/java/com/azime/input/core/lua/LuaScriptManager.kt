package com.azime.input.core.lua

import com.azime.input.core.storage.StorageManager
import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyType
import com.azime.input.data.model.KeyboardLayout
import com.azime.input.data.model.KeyboardRow
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

/** preset_keys 条目：trime2 表格式（label/send/commit）兼容。 */
data class PresetEntry(
    val label: String? = null,
    val send: String? = null,
    val commit: String? = null,
)

/**
 * 解析后的动作。取值来源优先级：
 * 1. preset_keys.lua 中的条目引用
 * 2. 内置命令 identifier（select_all / cut / copy / paste / …）
 * 3. 字面文本（支持 trime2 的 `{text}{Left}` 光标后缀语法）
 */
sealed interface ResolvedAction {
    /** 直接上屏文本；moveLeft/moveRight 为上屏后的光标移动步数。 */
    data class Commit(val text: String, val moveLeft: Int = 0, val moveRight: Int = 0) : ResolvedAction
    /** 内置命令 identifier（交给 Service 执行）。 */
    data class Command(val ident: String) : ResolvedAction
}

object LuaScriptManager {

    private var globals: Globals? = null
    private var presetEntries: Map<String, PresetEntry> = emptyMap()

    fun loadScript() {
        try {
            val g = JsePlatform.standardGlobals()
            globals = g
            val scriptFile = StorageManager.getLuaScriptFile()
            if (scriptFile.exists()) {
                val result = g.loadfile(scriptFile.absolutePath)?.call()
                presetEntries = when {
                    result is LuaTable -> parseEntries(result)
                    // 也支持脚本只定义全局表 preset_keys = { ... } 不返回
                    g.get("preset_keys") is LuaTable -> parseEntries(g.get("preset_keys") as LuaTable)
                    else -> emptyMap()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseEntries(table: LuaTable): Map<String, PresetEntry> {
        val map = mutableMapOf<String, PresetEntry>()
        var key = LuaValue.NIL
        while (true) {
            val next = table.next(key)
            if (next.arg1().isnil()) break
            key = next.arg1()
            val name = key.tojstring()
            val value = next.arg(2)
            map[name] = when {
                value.istable() -> PresetEntry(
                    label = value.get("label").takeIf { it.isstring() }?.tojstring(),
                    send = value.get("send").takeIf { it.isstring() }?.tojstring(),
                    commit = value.get("commit").takeIf { it.isstring() }?.tojstring()
                        ?: value.get("text").takeIf { it.isstring() }?.tojstring(),
                )
                value.isstring() -> PresetEntry(commit = value.tojstring())
                else -> PresetEntry()
            }
        }
        return map
    }

    fun getEntries(): Map<String, PresetEntry> = presetEntries

    fun getKeyAction(key: String): PresetEntry? = presetEntries[key]

    // ── 动作解析 ─────────────────────────────────────────────

    /**
     * 把布局动作字段（longClick/swipeUp/…）解析为可执行动作。
     * 识别内置命令 identifier；命中 preset_keys 条目时取其 send/commit；
     * 其余按字面文本处理（trime2 `{Left}`/`{Right}` 后缀）。
     */
    fun resolveAction(value: String): ResolvedAction? {
        val v = value.trim()
        if (v.isEmpty()) return null

        // 1. preset_keys 条目引用（含多级名）
        presetEntries[v]?.let { entry ->
            entry.send?.let { send ->
                resolveBuiltin(send)?.let { return it }
                return resolveLiteral(send)
            }
            entry.commit?.let { return ResolvedAction.Commit(it) }
            return null
        }

        // 2. 内置命令
        resolveBuiltin(v)?.let { return it }

        // 3. 字面文本
        return resolveLiteral(v)
    }

    private val commands = setOf(
        "select_all", "cut", "copy", "paste",
        "toggle_ascii", "newline", "backspace", "delete",
        "space", "tab", "esc", "left", "right", "up", "down",
        "page_up", "page_down", "home", "end",
        "caps_lock", "shift", "delete_all", "undo",
        "page:main", "page:symbols", "page:numpad", "page:emoji",
        "choose_page", "toggle_symbols",
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

    // ── trime2 键盘布局 lua 解析 ─────────────────────────────

    /**
     * 解析 trime2 风格的键盘布局 lua 文件，约定：
     * ```
     * return {
     *   name = "my_kb",
     *   rows = {
     *     { keys = { { label="q", click="q", width=1.0,
     *                 long_click="1", swipe_up="...", ... } } },
     *   },
     * }
     * ```
     * 行也支持直接写键数组（`{ {...},{...} }`，无 keys 包装）。
     * 解析失败返回 null。
     */
    fun parseKeyboardLayout(file: File): KeyboardLayout? = try {
        val g = globals ?: JsePlatform.standardGlobals()
        val result = g.loadfile(file.absolutePath)?.call()
        if (result is LuaTable) tableToLayout(result, file.nameWithoutExtension) else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun tableToLayout(table: LuaTable, fallbackName: String): KeyboardLayout? {
        val rowsTable = table.get("rows") as? LuaTable ?: return null
        val name = (table.get("name").takeIf { it.isstring() }?.tojstring() ?: fallbackName)
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_')
            .take(64)
            .ifBlank { "imported" }
        val rows = mutableListOf<KeyboardRow>()
        var ri = LuaValue.NIL
        while (true) {
            val rNext = rowsTable.next(ri)
            if (rNext.arg1().isnil()) break
            ri = rNext.arg1()
            val rowVal = rNext.arg(2)
            val keysTable = (rowVal.get("keys").takeIf { it.istable() } ?: rowVal) as? LuaTable
                ?: continue
            val keys = mutableListOf<Key>()
            var ki = LuaValue.NIL
            while (true) {
                val kNext = keysTable.next(ki)
                if (kNext.arg1().isnil()) break
                ki = kNext.arg1()
                val kv = kNext.arg(2)
                if (kv.istable()) keys += luaKeyToKey(kv as LuaTable)
            }
            if (keys.isNotEmpty()) rows.add(KeyboardRow(keys))
        }
        if (rows.isEmpty()) return null
        return KeyboardLayout(name = name, rows = rows)
    }

    /** trime2 键字段 → 内部 [Key]。type 按 click 值推断。 */
    private fun luaKeyToKey(kv: LuaTable): Key {
        val click = kv.get("click").takeIf { it.isstring() }?.tojstring()
            ?: kv.get("commit").takeIf { it.isstring() }?.tojstring()
            ?: ""
        val label = kv.get("label").takeIf { it.isstring() }?.tojstring() ?: click
        fun act(name: String): String? = kv.get(name).takeIf { it.isstring() }?.tojstring()
        val type = when {
            click.equals("BackSpace", true) -> KeyType.DELETE
            click.equals("Return", true) || click.equals("enter", true) -> KeyType.ENTER
            click.equals("space", true) -> KeyType.SPACE
            click.equals("shift", true) || click.equals("caps_lock", true) -> KeyType.MODIFIER
            click.length == 1 -> KeyType.CHARACTER
            else -> KeyType.FUNCTION
        }
        return Key(
            label = label,
            code = click,
            width = kv.get("width").takeIf { it.isnumber() }?.tofloat() ?: 1.0f,
            type = type,
            longClick = act("long_click"),
            swipeUp = act("swipe_up"),
            swipeDown = act("swipe_down"),
            swipeLeft = act("swipe_left"),
            swipeRight = act("swipe_right"),
            hint = act("hint"),
        )
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

package com.azime.input.core.lua

import com.azime.input.core.storage.StorageManager
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

object LuaScriptManager {
    
    private var globals: Globals? = null
    private var presetKeys: Map<String, String> = emptyMap()
    
    fun loadScript() {
        try {
            globals = JsePlatform.standardGlobals()
            
            val scriptFile = StorageManager.getLuaScriptFile()
            if (scriptFile.exists()) {
                val chunk = globals?.loadfile(scriptFile.absolutePath)
                val result = chunk?.call()
                
                if (result is LuaTable) {
                    presetKeys = parseLuaTable(result)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun parseLuaTable(table: LuaTable): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var key = LuaValue.NIL
        
        while (true) {
            val next = table.next(key)
            if (next.arg1().isnil()) break
            
            key = next.arg1()
            val value = next.arg(2)
            
            if (key.isstring() && value.isstring()) {
                map[key.tojstring()] = value.tojstring()
            }
        }
        
        return map
    }
    
    fun getPresetKeys(): Map<String, String> {
        return presetKeys
    }
    
    fun getKeyAction(key: String): String? {
        return presetKeys[key]
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

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
}

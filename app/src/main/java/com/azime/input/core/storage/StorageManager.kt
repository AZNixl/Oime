package com.azime.input.core.storage

import android.content.Context
import android.os.Environment
import java.io.File

object StorageManager {
    
    private const val EXTERNAL_DIR = "Documents/Oime"
    private const val SCHEMA_DIR = "schema"
    private const val FONTS_DIR = "fonts"
    private const val LUA_DIR = "lua"
    
    lateinit var externalRootDir: File
        private set
    lateinit var schemaDir: File
        private set
    lateinit var fontsDir: File
        private set
    lateinit var luaDir: File
        private set
    lateinit var keyboardLuaDir: File
        private set
    lateinit var internalDataDir: File
        private set

    fun initializeDirectories(context: Context) {
        // External storage: /storage/emulated/0/Documents/AZime
        val externalStorage = Environment.getExternalStorageDirectory()
        externalRootDir = File(externalStorage, EXTERNAL_DIR)

        // Create subdirectories
        schemaDir = File(externalRootDir, SCHEMA_DIR)
        fontsDir = File(externalRootDir, FONTS_DIR)
        luaDir = File(externalRootDir, LUA_DIR)
        keyboardLuaDir = File(luaDir, "keyboards")

        // Create all directories
        externalRootDir.mkdirs()
        schemaDir.mkdirs()
        fontsDir.mkdirs()
        luaDir.mkdirs()
        keyboardLuaDir.mkdirs()

        // Internal data directory
        internalDataDir = context.filesDir

        // Create default lua script if not exists
        createDefaultLuaScript()
        createExampleKeyboardLua()
    }
    
    private fun createDefaultLuaScript() {
        val luaFile = File(luaDir, "preset_keys.lua")
        if (!luaFile.exists()) {
            val defaultContent = """-- AZime preset_keys configuration
-- Add your preset_keys customizations here

-- Example:
-- preset_keys = {
--     ["Return"] = "commit",
--     ["space"] = "commit",
--     ["Escape"] = "clear"
-- }

preset_keys = {}

return preset_keys
"""
            luaFile.writeText(defaultContent)
        }
    }
    
    fun getSchemaSubdirectories(): List<File> {
        return schemaDir.listFiles()?.filter { it.isDirectory }?.toList() ?: emptyList()
    }
    
    fun getFontFiles(): List<File> {
        return fontsDir.listFiles()?.filter { 
            it.isFile && it.extension.lowercase() in listOf("ttf", "otf", "ttc")
        }?.toList() ?: emptyList()
    }
    
    fun getLuaScriptFile(): File {
        return File(luaDir, "preset_keys.lua")
    }

    /** 外置 trime2 风格键盘布局 lua 文件（Documents/AZime/lua/keyboards 目录）。 */
    fun getKeyboardLuaFiles(): List<File> {
        return keyboardLuaDir.listFiles()
            ?.filter { it.isFile && it.extension == "lua" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /** 首次使用时生成示例键盘布局，展示全部支持的字段。 */
    private fun createExampleKeyboardLua() {
        val f = File(keyboardLuaDir, "example.lua")
        if (f.exists()) return
        f.writeText("""-- AZime trime2 风格键盘布局示例
-- 放到本目录（Documents/AZime/lua/keyboards/）的 .lua 文件
-- 都会在「键盘编辑器 → 导入 lua 布局」中出现。
-- 支持字段：label / click / long_click / swipe_up / swipe_down /
--           swipe_left / swipe_right / width / hint
-- click 取值：单字符=输入；英文 identifier=命令（toggle_ascii、
--           page:numpad、page:emoji、select_all…）；其他文本=直接上屏。

return {
    name = "example",
    rows = {
        { keys = {
            { label = "q", click = "q", width = 1, long_click = "1" },
            { label = "w", click = "w", width = 1, long_click = "2" },
            { label = "e", click = "e", width = 1, long_click = "3" },
            { label = "r", click = "r", width = 1, long_click = "4" },
            { label = "t", click = "t", width = 1, long_click = "5" },
            { label = "y", click = "y", width = 1, long_click = "6" },
            { label = "u", click = "u", width = 1, long_click = "7" },
            { label = "i", click = "i", width = 1, long_click = "8" },
            { label = "o", click = "o", width = 1, long_click = "9" },
            { label = "p", click = "p", width = 1, long_click = "0" },
        } },
        { keys = {
            { label = "sym", click = "symbols", width = 1.5 },
            { label = "a", click = "a", width = 1 },
            { label = "s", click = "s", width = 1 },
            { label = "d", click = "d", width = 1 },
            { label = "f", click = "f", width = 1 },
            { label = "g", click = "g", width = 1 },
            { label = "h", click = "h", width = 1 },
            { label = "j", click = "j", width = 1 },
            { label = "k", click = "k", width = 1 },
            { label = "l", click = "l", width = 1 },
        } },
        { keys = {
            { label = "⇧", click = "shift", width = 1.5 },
            { label = "z", click = "z", width = 1 },
            { label = "x", click = "x", width = 1 },
            { label = "c", click = "c", width = 1 },
            { label = "v", click = "v", width = 1 },
            { label = "b", click = "b", width = 1 },
            { label = "n", click = "n", width = 1 },
            { label = "m", click = "m", width = 1 },
            { label = "⌫", click = "BackSpace", width = 1.5,
              swipe_up = "delete_all", swipe_down = "undo" },
        } },
        { keys = {
            { label = "123", click = "page:numpad", width = 1.5 },
            { label = "☺", click = "page:emoji", width = 1.5 },
            { label = "，", click = ",", width = 1 },
            { label = "空格", click = "space", width = 4 },
            { label = "。", click = ".", width = 1 },
            { label = "中/英", click = "toggle_ascii", width = 1.5 },
            { label = "⏎", click = "Return", width = 1.5 },
        } },
    },
}
""")
    }
}

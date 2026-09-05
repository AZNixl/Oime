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
    lateinit var internalDataDir: File
        private set

    fun initializeDirectories(context: Context) {
        // External storage: /storage/emulated/0/Documents/Oime
        val externalStorage = Environment.getExternalStorageDirectory()
        externalRootDir = File(externalStorage, EXTERNAL_DIR)

        // Create subdirectories
        schemaDir = File(externalRootDir, SCHEMA_DIR)
        fontsDir = File(externalRootDir, FONTS_DIR)
        luaDir = File(externalRootDir, LUA_DIR)

        // Create all directories
        externalRootDir.mkdirs()
        schemaDir.mkdirs()
        fontsDir.mkdirs()
        luaDir.mkdirs()

        // Internal data directory
        internalDataDir = context.filesDir

        // Create default lua script if not exists
        createDefaultLuaScript()
    }

    private fun createDefaultLuaScript() {
        val luaFile = File(luaDir, "preset_keys.lua")
        if (!luaFile.exists()) {
            val defaultContent = """-- Oime 预设置配置（preset_keys）
-- 语法说明见设置 → 高级 → 预设置 → 「说明」

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
}

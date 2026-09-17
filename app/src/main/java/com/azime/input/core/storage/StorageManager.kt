package com.azime.input.core.storage

import android.content.Context
import android.os.Environment
import java.io.File

object StorageManager {

    private const val EXTERNAL_DIR = "Documents/Oime"
    private const val SCHEMA_DIR = "schema"
    private const val FONTS_DIR = "fonts"
    private const val LUA_DIR = "lua"
    private const val MODELS_DIR = "models"

    lateinit var externalRootDir: File
        private set
    lateinit var schemaDir: File
        private set
    lateinit var fontsDir: File
        private set
    lateinit var luaDir: File
        private set
    lateinit var modelsDir: File
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
        // 轮19：语音本地模型侧载目录（sherpa-onnx 模型不进 APK）
        modelsDir = File(externalRootDir, MODELS_DIR)

        // Create all directories
        externalRootDir.mkdirs()
        schemaDir.mkdirs()
        fontsDir.mkdirs()
        luaDir.mkdirs()
        modelsDir.mkdirs()

        // Internal data directory
        internalDataDir = context.filesDir

        // 轮19.43：**去除「预设置（preset_keys）」功能** —— 不再生成预设模板文件。
        // 动作一律走「内置功能键值」（见键盘编辑器里的清单），不再依赖 Lua 预设表。
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

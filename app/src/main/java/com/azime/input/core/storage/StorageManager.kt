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
    private const val SOUNDS_DIR = "sounds"

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

    /** 轮19.46：打字音效目录（自动创建，并放入 APK 内置的默认音效）。 */
    lateinit var soundsDir: File
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
        soundsDir = File(externalRootDir, SOUNDS_DIR)

        // Create all directories
        externalRootDir.mkdirs()
        schemaDir.mkdirs()
        fontsDir.mkdirs()
        // 轮19.46：**不再创建 lua 目录** —— 预设置（preset_keys）已去除，该目录没有保留必要
        modelsDir.mkdirs()
        soundsDir.mkdirs()

        // 轮19.46：把内置默认音效释放到外置目录（不覆盖用户自己的文件）
        copyDefaultSounds(context)

        // Internal data directory
        internalDataDir = context.filesDir

        // 轮19.43：**去除「预设置（preset_keys）」功能** —— 不再生成预设模板文件。
        // 动作一律走「内置功能键值」（见键盘编辑器里的清单），不再依赖 Lua 预设表。
    }

    /**
     * 轮19.46：把 APK 内置的默认音效（`assets/sounds/` 下的文件）复制到 `Documents/Oime/sounds/`，
     * 作为**默认音效**。已存在的同名文件不覆盖——用户替换后不会被还原。
     */
    private fun copyDefaultSounds(context: Context) {
        runCatching {
            val names = context.assets.list("sounds") ?: return@runCatching
            for (name in names) {
                val target = File(soundsDir, name)
                if (target.exists()) continue
                context.assets.open("sounds/$name").use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
            }
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

    /** 用户 Lua 脚本路径（轮19.49：预设表已移除，改用通用脚本名）。 */
    fun getLuaScriptFile(): File {
        return File(luaDir, "script.lua")
    }
}

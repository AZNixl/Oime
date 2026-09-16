package com.azime.input.core.sound

import android.media.AudioAttributes
import android.media.SoundPool
import com.azime.input.core.keyboard.KeyboardManager
import java.io.File

/**
 * 轮19.34：打字音效。
 *
 * - 总开关 + **外置文件夹**（默认 `/sdcard/Documents/Oime/sounds`）+ 可自定义音效文件
 * - 用 SoundPool 预加载，播放零分配（热路径只做 pool.play）
 * - 关闭时 `playPress()` 直接 return，不产生任何开销
 */
object SoundManager {

    private val exts = listOf("mp3", "ogg", "wav", "m4a", "aac")

    private var pool: SoundPool? = null
    private var loadedId = 0
    private var loadedPath: String? = null

    /** 扫描音效目录，返回可用文件名（排序）。 */
    fun listFiles(): List<String> {
        val dir = File(KeyboardManager.soundDir())
        if (!dir.isDirectory) return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in exts }
            .map { it.name }
            .sorted()
    }

    /** 当前生效的音效文件绝对路径（空则取目录第一个）。 */
    fun resolvePath(): String? {
        val dir = File(KeyboardManager.soundDir())
        val picked = KeyboardManager.soundFile()
        if (picked.isNotBlank()) {
            val f = File(dir, picked)
            if (f.isFile) return f.absolutePath
        }
        return listFiles().firstOrNull()?.let { File(dir, it).absolutePath }
    }

    private fun ensurePool(): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool = p
        return p
    }

    private fun ensureLoaded(path: String): Boolean {
        if (loadedPath == path && loadedId != 0) return true
        val p = ensurePool()
        val id = runCatching { p.load(path, 1) }.getOrDefault(0)
        if (id == 0) return false
        loadedId = id
        loadedPath = path
        return true
    }

    /** 按键音（热路径：只做一次判断 + pool.play）。 */
    fun playPress() {
        if (!KeyboardManager.soundEnabled()) return
        val path = resolvePath() ?: return
        if (!ensureLoaded(path)) return
        val vol = KeyboardManager.soundVolume() / 100f
        runCatching { pool?.play(loadedId, vol, vol, 1, 0, 1f) }
    }

    /** 试听（设置页用）。 */
    fun preview(path: String) {
        if (!ensureLoaded(path)) return
        val vol = KeyboardManager.soundVolume().coerceAtLeast(20) / 100f
        runCatching { pool?.play(loadedId, vol, vol, 1, 0, 1f) }
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        loadedId = 0
        loadedPath = null
    }
}

package com.azime.input.core.font

import android.graphics.Typeface
import com.azime.input.AZimeApplication
import com.azime.input.core.storage.StorageManager
import java.io.File

/**
 * 字体管理器（外置目录版）。
 *
 * 不做导入：直接读取外置字体目录 `Documents/AZime/fonts/`，
 * 用户通过 USB / 文件管理器放入 ttf/otf/ttc 即可被扫描到。
 *
 * 多字体：两个可选角色 —— 键帽字体（keyFont）与候选字体（candidateFont），
 * 各自独立生效；空值 = 系统默认。
 */
object FontManager {

    private const val PREFS_NAME = "font_prefs"
    private const val KEY_KEY_FONT = "key_font"
    private const val KEY_CAND_FONT = "candidate_font"

    private val prefs
        get() = AZimeApplication.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val typefaceCache = HashMap<String, Typeface>()

    val supportedExtensions = setOf("ttf", "otf", "ttc")

    /** 外置字体目录（Documents/AZime/fonts）。 */
    fun fontsDir(): File = StorageManager.fontsDir

    fun fontFiles(): List<File> =
        fontsDir().listFiles { f -> f.isFile && f.extension.lowercase() in supportedExtensions }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** 删除字体文件；若正被任一角色使用则回退系统默认。 */
    fun deleteFont(name: String) {
        File(fontsDir(), name).delete()
        typefaceCache.remove(name)
        if (keyFontName() == name) setKeyFont("")
        if (candidateFontName() == name) setCandidateFont("")
    }

    // ── 角色一：键帽字体 ──
    fun keyFontName(): String = prefs.getString(KEY_KEY_FONT, "") ?: ""
    fun setKeyFont(name: String) = prefs.edit().putString(KEY_KEY_FONT, name).apply()
    fun keyTypeface(): Typeface? = typefaceFor(keyFontName())

    // ── 角色二：候选字体 ──
    fun candidateFontName(): String = prefs.getString(KEY_CAND_FONT, "") ?: ""
    fun setCandidateFont(name: String) = prefs.edit().putString(KEY_CAND_FONT, name).apply()
    fun candidateTypeface(): Typeface? = typefaceFor(candidateFontName())

    private fun typefaceFor(name: String): Typeface? {
        if (name.isBlank()) return null
        return typefaceCache.getOrPut(name) {
            val file = File(fontsDir(), name)
            if (!file.exists()) return null
            Typeface.createFromFile(file)
        }
    }
}

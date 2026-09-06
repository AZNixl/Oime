package com.azime.input.core.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
    private const val KEY_PANEL_FONT = "panel_font"

    private val prefs
        get() = AZimeApplication.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val typefaceCache = HashMap<String, Typeface>()

    val supportedExtensions = setOf("ttf", "otf", "ttc")

    /** 外置字体目录（Documents/Oime/fonts）。 */
    fun fontsDir(): File = StorageManager.fontsDir

    /**
     * 应用私有字体目录（filesDir/fonts）。
     * Android 13+ 上 File API 读 Documents 受限，SAF 导入的字体复制到这里保证可读。
     */
    fun internalFontsDir(): File =
        File(AZimeApplication.instance.filesDir, "fonts").apply { mkdirs() }

    /** 全部字体：外置目录（含子文件夹，递归）+ 私有目录（SAF 导入），同名去重、私有优先。 */
    fun fontFiles(): List<File> {
        val seen = LinkedHashMap<String, File>()
        internalFontsDir()
            .listFiles { f -> f.isFile && f.extension.lowercase() in supportedExtensions }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { seen.putIfAbsent(it.name.lowercase(), it) }
        fontsDir().walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .sortedBy { it.name.lowercase() }
            .forEach { seen.putIfAbsent(it.name.lowercase(), it) }
        return seen.values.toList()
    }

    private fun findFontFile(name: String): File? {
        File(internalFontsDir(), name).takeIf { it.exists() }?.let { return it }
        File(fontsDir(), name).takeIf { it.exists() }?.let { return it }
        return fontsDir().walkTopDown().firstOrNull { it.isFile && it.name == name }
    }

    /**
     * SAF 导入：把用户选中的文件夹（含子目录）中的字体复制到应用私有目录，
     * 绕过 Android 13+ 对 Documents 的 File 读取限制。返回成功导入的数量。
     */
    fun importFromTree(context: Context, uri: Uri): Int {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return 0
        val dir = internalFontsDir()
        var count = 0
        fun copyRecursive(df: DocumentFile) {
            for (child in df.listFiles()) {
                if (child.isDirectory) {
                    copyRecursive(child)
                    continue
                }
                val fileName = child.name ?: continue
                if (fileName.substringAfterLast('.', "").lowercase() !in supportedExtensions) continue
                runCatching {
                    val input = context.contentResolver.openInputStream(child.uri)
                        ?: return@runCatching
                    input.use { ins ->
                        File(dir, fileName).outputStream().use { output -> ins.copyTo(output) }
                    }
                    count++
                }
            }
        }
        copyRecursive(root)
        return count
    }

    /** 删除字体文件；若正被任一角色使用则回退系统默认。 */
    fun deleteFont(name: String) {
        findFontFile(name)?.delete()
        typefaceCache.remove(name)
        if (keyFontName() == name) setKeyFont("")
        if (candidateFontName() == name) setCandidateFont("")
        if (panelFontName() == name) setPanelFont("")
    }

    // ── 角色一：键帽字体 ──
    fun keyFontName(): String = prefs.getString(KEY_KEY_FONT, "") ?: ""
    fun setKeyFont(name: String) = prefs.edit().putString(KEY_KEY_FONT, name).apply()
    fun keyTypeface(): Typeface? = typefaceFor(keyFontName())

    // ── 角色二：候选栏字体 ──
    fun candidateFontName(): String = prefs.getString(KEY_CAND_FONT, "") ?: ""
    fun setCandidateFont(name: String) = prefs.edit().putString(KEY_CAND_FONT, name).apply()
    fun candidateTypeface(): Typeface? = typefaceFor(candidateFontName())

    // ── 角色三：候选面板字体（更多候选 / emoji / 符号网格） ──
    fun panelFontName(): String = prefs.getString(KEY_PANEL_FONT, "") ?: ""
    fun setPanelFont(name: String) = prefs.edit().putString(KEY_PANEL_FONT, name).apply()
    fun panelTypeface(): Typeface? = typefaceFor(panelFontName())

    private fun typefaceFor(name: String): Typeface? {
        if (name.isBlank()) return null
        return typefaceCache.getOrPut(name) {
            val file = findFontFile(name) ?: return null
            Typeface.createFromFile(file)
        }
    }
}

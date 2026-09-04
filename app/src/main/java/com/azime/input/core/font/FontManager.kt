package com.azime.input.core.font

import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.azime.input.AZimeApplication
import java.io.File

/**
 * 字体管理器。
 *
 * 字体文件存放于应用内部目录 `filesDir/fonts/`（Android 11+ 上 SAF 导入的文件
 * 通过 MediaStore 落盘，直接 File 读外部 Documents 目录不可靠，故用内部目录作为
 * 权威存储；`Documents/AZime/fonts` 保留给用户手动备份/交换）。
 *
 * 字体作用于键盘 UI（候选栏 + 键帽），在下次键盘视图重建时生效。
 */
object FontManager {

    private const val PREFS_NAME = "font_prefs"
    private const val KEY_ACTIVE = "active_font"

    private val prefs
        get() = AZimeApplication.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val typefaceCache = HashMap<String, Typeface>()

    fun fontsDir(): File = File(AZimeApplication.instance.filesDir, "fonts").apply { mkdirs() }

    val supportedExtensions = setOf("ttf", "otf", "ttc")

    fun fontFiles(): List<File> =
        fontsDir().listFiles { f -> f.isFile && f.extension.lowercase() in supportedExtensions }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /**
     * 从 SAF uri 导入字体文件。成功返回文件名（不含路径）。
     * 同名文件覆盖（视为替换）。
     */
    fun importFrom(uri: Uri): Result<String> = runCatching {
        val resolver = AZimeApplication.instance.contentResolver
        val name = queryDisplayName(uri) ?: "font_${System.currentTimeMillis()}.ttf"
        val ext = name.substringAfterLast('.', "").lowercase()
        require(ext in supportedExtensions) { "仅支持 ttf/otf/ttc，收到：.$ext" }

        val target = File(fontsDir(), name)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取所选文件")
        typefaceCache.remove(name)
        name
    }

    fun deleteFont(name: String) {
        File(fontsDir(), name).delete()
        typefaceCache.remove(name)
        if (activeName() == name) setActive("")
    }

    /** 当前生效的字体名；空串 = 系统默认。 */
    fun activeName(): String = prefs.getString(KEY_ACTIVE, "") ?: ""

    fun setActive(name: String) {
        prefs.edit().putString(KEY_ACTIVE, name).apply()
    }

    /** 当前字体的 [Typeface]；未设置/文件丢失时返回 null（= 系统默认）。 */
    fun activeTypeface(): Typeface? {
        val name = activeName()
        if (name.isBlank()) return null
        return typefaceCache.getOrPut(name) {
            val file = File(fontsDir(), name)
            if (!file.exists()) return null
            Typeface.createFromFile(file)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = AZimeApplication.instance.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }
}

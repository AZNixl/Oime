package com.azime.input.core.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.documentfile.provider.DocumentFile
import com.azime.input.AZimeApplication
import com.azime.input.core.storage.StorageManager
import java.io.File

/**
 * 字体管理器（外置目录版，参考 xime.az AppFonts 实现方式）。
 *
 * 不做导入：直接读取外置字体目录 `Documents/Oime/fonts/`，
 * 用户通过 USB / 文件管理器放入 ttf/otf/ttc 即可被扫描到。
 *
 * 多字体（xime 方式）：可多选若干字体，选择顺序即回退顺序——
 * 首选字体缺字形时依次回退后续字体，最后回退系统默认，
 * Compose 端构建为 [FontFamily] 回退链。
 */
object FontManager {

    private const val PREFS_NAME = "font_prefs"
    private const val KEY_SELECTED_FONTS = "selected_fonts"

    private val prefs
        get() = AZimeApplication.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val typefaceCache = HashMap<String, Typeface>()

    /** FontFamily 回退链缓存：签名（文件名逗号连接）→ FontFamily。 */
    @Volatile
    private var familyCache: Pair<String, FontFamily>? = null

    /** 字体设置版本号：每次选择变化自增，供键盘视图检测热加载（反馈轮9）。 */
    @Volatile
    private var revCounter: Int = 0

    fun rev(): Int = revCounter

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

    // ── 多选字体（xime AppFonts 方式：选择顺序 = 回退顺序） ──

    /** 已选字体文件名列表（顺序即回退顺序）。 */
    fun selectedFonts(): List<String> {
        val raw = prefs.getString(KEY_SELECTED_FONTS, "") ?: ""
        return raw.split('\n').filter { it.isNotBlank() }
    }

    /** 保存多选字体列表（保持传入顺序）。 */
    fun setSelectedFonts(names: List<String>) {
        prefs.edit().putString(KEY_SELECTED_FONTS, names.joinToString("\n")).apply()
        familyCache = null
        revCounter++
    }

    fun isFontSelected(name: String): Boolean = selectedFonts().contains(name)

    /**
     * 当前键盘字体回退链（键帽/候选/面板统一使用）：
     * 多个字体按选择顺序构建 FontFamily，缺字形依次回退；
     * 未选择任何字体时返回 null（调用方用系统默认字体）。
     * 按文件名列表签名缓存，变更时自动重建。
     */
    /**
     * 轮19.79：**给纯 View（系统级浮窗）用的 Typeface** —— 按已选字体顺序取第一个可用字体。
     * 系统级浮窗是纯 Android View（不是 Compose），拿不到 FontFamily ⇒ 用 Typeface 同步字体设置 ✓
     */
    fun keyboardTypeface(): android.graphics.Typeface? {
        for (n in selectedFonts()) {
            val f = findFontFile(n) ?: continue
            runCatching { return android.graphics.Typeface.createFromFile(f) }
        }
        return null
    }

    fun keyboardFontFamily(): FontFamily? {
        val names = selectedFonts()
        if (names.isEmpty()) return null
        val signature = names.joinToString(",")
        familyCache?.let { (sig, family) -> if (sig == signature) return family }
        val fonts = mutableListOf<Font?>()
        for (name in names) {
            val file = findFontFile(name) ?: continue
            try {
                fonts.add(Font(file))
            } catch (_: Exception) { /* 跳过损坏文件 */ }
        }
        if (fonts.isEmpty()) return null
        // 轮19.34：**链尾追加系统字体兜底**——用户自定义字体常常缺字形（典型的如拆字用的
        // 部首/部件字，如 ⺮ 龸 亻 等），原来 FontFamily 只含用户字体 ⇒ 缺字变豆腐块。
        // 追加系统 sans-serif 后，缺字形会自动回落到系统 CJK 字体。
        systemFallbackFont()?.let { fonts.add(it) }
        return try {
            @Suppress("UNCHECKED_CAST")
            FontFamily(fonts.filterNotNull()).also { familyCache = signature to it }
        } catch (_: Exception) {
            null
        }
    }

    /** 轮19.34：系统字体兜底项（Compose 设备字体名，API 26+；失败则返回 null 由调用方忽略）。 */
    private fun systemFallbackFont(): Font? = try {
        Font(androidx.compose.ui.text.font.DeviceFontFamilyName("sans-serif"))
    } catch (_: Throwable) {
        null
    }

    /** 字体设置变更后调用（setSelectedFonts 已自动失效，保留给外部强制刷新用）。 */
    fun invalidateCustomFont() {
        familyCache = null
        revCounter++
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

    /** 删除字体文件；若在多选列表中则移除。 */
    fun deleteFont(name: String) {
        findFontFile(name)?.delete()
        typefaceCache.remove(name)
        if (isFontSelected(name)) setSelectedFonts(selectedFonts() - name)
    }
}

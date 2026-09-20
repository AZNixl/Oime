package com.azime.input.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.azime.input.core.storage.StorageManager
import net.lingala.zip4j.ZipFile
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

/**
 * 输入方案导入器。
 *
 * 支持两种来源：
 * - ZIP 压缩包：自动探测文件名编码（UTF-8 标记位 / GBK 兜底），解决中文文件名乱码
 * - SAF 文件夹：复制其中全部 yaml/txt
 *
 * **落盘结构（轮19.50 统一）**：`Documents/Oime/schemas/<用户命名的文件夹>/<方案文件>`
 * - 文件夹名**必须由用户命名**（不再有"保持原名"选项）
 * - 压缩包**不管是什么形式**（`/文件夹/方案文件` 还是 `/方案文件`），解压后都会**拍平一层**：
 *   唯一的顶层目录会被提升，内容直接落在用户命名的文件夹里 ✓
 *
 * 由 RimeManager.syncGroup / switchSchemaGroup 在启动或切组时同步进 shared 目录并触发重新部署。
 */
class SchemaImporter {

    /**
     * 导入 ZIP 到 `schemas/<targetName>/`。
     * @param targetName 用户命名的文件夹名（调用方已校验非法字符与重名）
     */
    fun importFromZip(context: Context, uri: Uri, targetName: String): Result<String> = runCatching {
        val tempZip = File(context.cacheDir, "temp_schema.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempZip).use { output -> input.copyTo(output) }
        } ?: error("无法读取所选文件")

        val name = targetName.trim().ifBlank { "schema_${System.currentTimeMillis()}" }
        val targetDir = File(StorageManager.schemaDir, name)
        require(!targetDir.exists()) { "已存在同名方案文件夹「$name」" }
        targetDir.mkdirs()

        try {
            // 先按默认（UTF-8）打开；若存在未标记 UTF-8 的条目（多半是 Windows 压的 GBK 包），改用 GBK
            val zipFile = ZipFile(tempZip)
            val hasNonUtf8 = zipFile.fileHeaders.any { !it.isFileNameUTF8Encoded }
            if (hasNonUtf8) {
                zipFile.charset = Charset.forName("GBK")
            }
            zipFile.extractAll(targetDir.absolutePath)
        } finally {
            tempZip.delete()
        }

        // 轮19.50：**拍平一层** —— 解压结果若只有一个顶层目录（常见于「方案文件夹/方案文件…」的包），
        // 把它的内容提升到 targetDir，避免出现 schemas/<名>/<方案文件夹>/方案文件 这种多一层结构。
        flattenSingleTopFolder(targetDir)

        val count = targetDir.walkTopDown().count { it.isFile && it.extension == "yaml" }
        require(count > 0) { "压缩包中没有找到 .yaml 方案文件" }
        name
    }

    /** SAF 文件夹导入：同样落到 `schemas/<targetName>/`。 */
    fun importFromTree(context: Context, uri: Uri, targetName: String): Result<String> = runCatching {
        val name = targetName.trim().ifBlank { "schema_${System.currentTimeMillis()}" }
        val targetDir = File(StorageManager.schemaDir, name)
        require(!targetDir.exists()) { "已存在同名方案文件夹「$name」" }
        targetDir.mkdirs()

        val root = DocumentFile.fromTreeUri(context, uri) ?: error("无法读取所选文件夹")
        root.listFiles().forEach { child ->
            val n = child.name ?: return@forEach
            if (child.isDirectory) {
                val sub = File(targetDir, n)
                sub.mkdirs()
                copyTreeToDir(context, child, sub)
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    File(targetDir, n).outputStream().use { out -> input.copyTo(out) }
                }
            }
        }

        flattenSingleTopFolder(targetDir)

        val count = targetDir.walkTopDown().count { it.isFile && it.extension == "yaml" }
        require(count > 0) { "所选文件夹中没有找到 .yaml 方案文件" }
        name
    }

    private fun copyTreeToDir(context: Context, dir: DocumentFile, outDir: File) {
        dir.listFiles().forEach { child ->
            val n = child.name ?: return@forEach
            if (child.isDirectory) {
                val sub = File(outDir, n)
                sub.mkdirs()
                copyTreeToDir(context, child, sub)
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    File(outDir, n).outputStream().use { out -> input.copyTo(out) }
                }
            }
        }
    }

    /** 唯一顶层目录 → 提升其内容（连续多层单目录时递归处理）。 */
    private fun flattenSingleTopFolder(dir: File) {
        var guard = 0
        while (guard++ < 8) {
            val children = dir.listFiles() ?: return
            val dirs = children.filter { it.isDirectory }
            val files = children.filter { it.isFile }
            if (dirs.size != 1 || files.isNotEmpty()) return
            val inner = dirs.first()
            val kids = inner.listFiles().orEmpty()
            var moved = 0
            kids.forEach { k ->
                if (k.renameTo(File(dir, k.name))) moved++
            }
            if (moved == 0) return
            inner.delete()
        }
    }
}

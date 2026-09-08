package com.azime.input.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.azime.input.core.storage.StorageManager
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

/**
 * 输入方案导入器。
 *
 * 支持两种来源：
 * - ZIP 压缩包：自动探测文件名编码（UTF-8 标记位 / GBK 兜底），解决中文文件名乱码
 * - SAF 文件夹：复制其中全部 yaml/txt（含子目录拍平），文件名由系统解码，天然无乱码
 *
 * 落盘到 Documents/Oime/schema/<名>/（每个子目录即一个「方案组」，轮13 起），
 * 由 RimeManager.syncGroup / switchSchemaGroup 在启动或切组时同步进 shared 目录并触发重新部署。
 */
class SchemaImporter {

    fun importFromZip(context: Context, uri: Uri): Result<String> = runCatching {
        val tempZip = File(context.cacheDir, "temp_schema.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempZip).use { output -> input.copyTo(output) }
        } ?: error("无法读取所选文件")

        val schemaName = tempZip.nameWithoutExtension.ifBlank { "schema_${System.currentTimeMillis()}" }
        val targetDir = File(StorageManager.schemaDir, schemaName)
        targetDir.mkdirs()

        try {
            // 先按默认（UTF-8）打开；若存在未标记 UTF-8 的条目（多半是 Windows 压的 GBK 包），改用 GBK
            val zipFile = ZipFile(tempZip)
            // zip4j 2.x 构造器第二参数是密码(charset 用 setter)：检测到未标记 UTF-8 的
            // 条目（多为 Windows GBK 压缩包）时切换文件名编码，修复中文乱码
            val hasNonUtf8 = zipFile.fileHeaders.any { !it.isFileNameUTF8Encoded }
            if (hasNonUtf8) {
                zipFile.charset = Charset.forName("GBK")
            }
            zipFile.extractAll(targetDir.absolutePath)
        } finally {
            tempZip.delete()
        }

        val count = targetDir.walkTopDown().count { it.isFile && it.extension == "yaml" }
        require(count > 0) { "压缩包中没有找到 .yaml 方案文件" }
        schemaName
    }

    /** 从 SAF 目录树导入：把目录下（含子目录）的 yaml/txt 拍平复制到 schema/<名>/。 */
    fun importFromFolder(context: Context, uri: Uri): Result<String> = runCatching {
        val tree = DocumentFile.fromTreeUri(context, uri)
            ?: error("无法访问所选文件夹")
        val name = queryTreeName(context, uri) ?: "folder_${System.currentTimeMillis()}"
        val targetDir = File(StorageManager.schemaDir, name)
        targetDir.mkdirs()

        var count = 0
        fun copyRecursive(dir: DocumentFile) {
            for (child in dir.listFiles()) {
                if (child.isDirectory) {
                    copyRecursive(child)
                } else {
                    val fn = child.name ?: continue
                    if (fn.substringAfterLast('.', "").lowercase() !in setOf("yaml", "yml", "txt")) continue
                    val out = File(targetDir, fn)
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    count++
                }
            }
        }
        copyRecursive(tree)
        require(count > 0) { "文件夹中没有找到 .yaml 方案文件" }
        name
    }

    private fun queryTreeName(context: Context, uri: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        docId.substringAfter(':').ifBlank { null }
    }.getOrNull()
}

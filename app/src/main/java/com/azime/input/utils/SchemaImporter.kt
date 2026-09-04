package com.azime.input.utils

import android.content.Context
import android.net.Uri
import com.azime.input.core.storage.StorageManager
import net.lingala.zip4j.ZipFile
import java.io.File
import java.io.FileOutputStream

class SchemaImporter {
    
    fun importFromZip(context: Context, uri: Uri): Boolean {
        return try {
            // Copy ZIP to temp location
            val tempZip = File(context.cacheDir, "temp_schema.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Extract to schema directory
            val zipFile = ZipFile(tempZip)
            val schemaName = getSchemaNameFromZip(zipFile)
            val targetDir = File(StorageManager.schemaDir, schemaName)
            
            zipFile.extractAll(targetDir.absolutePath)
            
            // Clean up temp file
            tempZip.delete()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun getSchemaNameFromZip(zipFile: ZipFile): String {
        // Try to find schema name from file headers or use timestamp
        return "schema_${System.currentTimeMillis()}"
    }
}

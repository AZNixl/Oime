package com.azime.input.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.azime.input.core.storage.StorageManager
import com.azime.input.ui.editor.KeyboardEditorActivity
import com.azime.input.ui.font.FontManagerActivity
import com.azime.input.ui.lua.LuaEditorActivity
import com.azime.input.utils.SchemaImporter
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private val schemaImporter = SchemaImporter()
    
    private val zipPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importSchema(uri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(
            ComposeView(this).apply {
                setContent {
                    MaterialTheme {
                        SettingsScreen(
                            onBackClick = { finish() },
                            onImportSchema = { pickZipFile() },
                            onOpenKeyboardEditor = {
                                startActivity(Intent(this@SettingsActivity, KeyboardEditorActivity::class.java))
                            },
                            onManageFonts = {
                                startActivity(Intent(this@SettingsActivity, FontManagerActivity::class.java))
                            },
                            onEditLuaScript = {
                                startActivity(Intent(this@SettingsActivity, LuaEditorActivity::class.java))
                            }
                        )
                    }
                }
            }
        )
    }
    
    private fun pickZipFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        zipPickerLauncher.launch(intent)
    }
    
    private fun importSchema(uri: Uri) {
        try {
            val success = schemaImporter.importFromZip(this, uri)
            val message = if (success) "导入成功" else "导入失败"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onImportSchema: () -> Unit,
    onOpenKeyboardEditor: () -> Unit,
    onManageFonts: () -> Unit,
    onEditLuaScript: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SettingsSection(title = "输入方案") {
                    SettingsItem(
                        title = "导入方案",
                        subtitle = "从 ZIP 文件导入输入方案",
                        onClick = onImportSchema
                    )
                }
            }
            
            item {
                SettingsSection(title = "键盘布局") {
                    SettingsItem(
                        title = "键盘布局编辑器",
                        subtitle = "编辑键盘布局",
                        onClick = onOpenKeyboardEditor
                    )
                }
            }
            
            item {
                SettingsSection(title = "外观") {
                    SettingsItem(
                        title = "字体设置",
                        subtitle = "管理主字体和备用字体",
                        onClick = onManageFonts
                    )
                }
            }
            
            item {
                SettingsSection(title = "高级") {
                    SettingsItem(
                        title = "Lua 脚本",
                        subtitle = "编辑 preset_keys.lua",
                        onClick = onEditLuaScript
                    )
                }
            }
            
            item {
                SettingsSection(title = "关于") {
                    SettingsItem(
                        title = "版本",
                        subtitle = "0.1.0-preview",
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

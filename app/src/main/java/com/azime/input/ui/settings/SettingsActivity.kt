package com.azime.input.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.azime.input.core.rime.RimeManager
import com.azime.input.ui.editor.KeyboardEditorActivity
import com.azime.input.ui.font.FontManagerActivity
import com.azime.input.ui.lua.LuaEditorActivity
import com.azime.input.utils.SchemaImporter
import kotlinx.coroutines.launch

/**
 * 设置主页 —— UI 参考 KernelSU 主页：
 * 顶部状态卡（○ logo + 名称 + 引擎状态），下方分组卡片列表。
 */
class SettingsActivity : AppCompatActivity() {

    private val schemaImporter = SchemaImporter()

    private val zipPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { importSchema { schemaImporter.importFromZip(this, it) } }
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { importSchema { schemaImporter.importFromFolder(this, it) } }
        }
    }

    private fun importSchema(action: () -> Result<String>) {
        lifecycleScope.launch {
            action().onSuccess { name ->
                RimeManager.deployImportedSchemas(applicationContext)
                Toast.makeText(this@SettingsActivity, "已导入「$name」，方案部署中…", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@SettingsActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
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
                            onOpenKeyboardEditor = {
                                startActivity(Intent(this@SettingsActivity, KeyboardEditorActivity::class.java))
                            },
                            onManageFonts = {
                                startActivity(Intent(this@SettingsActivity, FontManagerActivity::class.java))
                            },
                            onEditLuaScript = {
                                startActivity(Intent(this@SettingsActivity, LuaEditorActivity::class.java))
                            },
                            onPickZip = {
                                zipPickerLauncher.launch(
                                    Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "application/zip"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                )
                            },
                            onPickFolder = {
                                folderPickerLauncher.launch(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                )
                            },
                        )
                    }
                }
            }
        )
    }
}

// ── KSU 风格主页 ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenKeyboardEditor: () -> Unit,
    onManageFonts: () -> Unit,
    onEditLuaScript: () -> Unit,
    onPickZip: () -> Unit,
    onPickFolder: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text("○输入法", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // 状态卡（参考 KSU 顶卡）
            item { StatusCard() }

            // 方案管理
            item {
                SectionLabel("输入方案")
                Card { Column(Modifier.padding(vertical = 4.dp)) {
                    SchemaList()
                    KsuItem(
                        icon = Icons.Default.FileDownload,
                        title = "导入方案（ZIP）",
                        subtitle = "兼容 GBK 文件名压缩包",
                        onClick = onPickZip,
                    )
                    KsuItem(
                        icon = Icons.Default.FolderOpen,
                        title = "导入方案（文件夹）",
                        subtitle = "选择含方案 yaml 的文件夹",
                        onClick = onPickFolder,
                    )
                } }
            }

            // 键盘
            item {
                SectionLabel("键盘")
                Card {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        KsuItem(
                            icon = Icons.Default.Keyboard,
                            title = "键盘布局编辑器",
                            subtitle = "可视化编辑按键与滑动手势",
                            onClick = onOpenKeyboardEditor,
                        )
                    }
                }
            }

            // 外观
            item {
                SectionLabel("外观")
                Card {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        KsuItem(
                            icon = Icons.Default.FontDownload,
                            title = "字体管理",
                            subtitle = "读取 Documents/AZime/fonts 中的字体",
                            onClick = onManageFonts,
                        )
                    }
                }
            }

            // 高级
            item {
                SectionLabel("高级")
                Card {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        KsuItem(
                            icon = Icons.Default.Code,
                            title = "Lua 脚本",
                            subtitle = "preset_keys.lua（trime2 格式兼容）",
                            onClick = onEditLuaScript,
                        )
                    }
                }
            }

            // 关于
            item {
                SectionLabel("关于")
                Card {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        KsuItem(
                            icon = Icons.Default.Info,
                            title = "版本",
                            subtitle = "0.2.0-preview · 平台 RIME",
                            onClick = {},
                            showChevron = false,
                        )
                        KsuItem(
                            icon = Icons.Default.Link,
                            title = "GitHub",
                            subtitle = "github.com/AZNixl/AZime",
                            onClick = {
                                runCatching {
                                    context.startActivity(android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/AZNixl/AZime"),
                                    ))
                                }
                            },
                            showChevron = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard() {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var schema by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            RimeManager.ensureReady(context)
        }
        ready = RimeManager.isReady()
        schema = if (ready) RimeManager.schemaDisplayName(RimeManager.currentSchema()) else ""
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ○ logo
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(cs.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("○", color = cs.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("○输入法", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (ready) Color(0xFF2E9E5B) else cs.tertiary, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            ready -> "运行正常 · $schema"
                            else -> "引擎未就绪 / 首次部署中…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SchemaList() {
    var schemas by remember { mutableStateOf(RimeManager.availableSchemas()) }
    var current by remember { mutableStateOf(RimeManager.currentSchema()) }
    var refreshed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!refreshed) {
            refreshed = true
            kotlinx.coroutines.delay(1500) // 给首次部署留出窗口
            schemas = RimeManager.availableSchemas()
            current = RimeManager.currentSchema()
        }
    }

    if (schemas.isNotEmpty()) {
        schemas.forEach { id ->
            val selected = id == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        current = id
                        RimeManager.switchSchema(id)
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = {
                    current = id
                    RimeManager.switchSchema(id)
                })
                Spacer(Modifier.width(8.dp))
                Text(RimeManager.schemaDisplayName(id), style = MaterialTheme.typography.bodyLarge)
                if (selected) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** KSU 风格卡片条目：图标 + 标题/副标题 + 右箭头。 */
@Composable
private fun KsuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

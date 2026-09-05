package com.azime.input.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import com.azime.input.core.haptic.HapticsManager
import com.azime.input.core.rime.RimeManager
import com.azime.input.core.storage.StorageManager
import com.azime.input.ui.editor.KeyboardEditorActivity
import com.azime.input.ui.font.FontManagerActivity
import com.azime.input.ui.lua.LuaEditorActivity
import com.azime.input.utils.SchemaImporter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

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
                promptRename(name)
            }.onFailure { e ->
                Toast.makeText(this@SettingsActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 导入成功后询问重命名（改写 schema 子文件夹名，内部 schema_id 不变）。 */
    private fun promptRename(importedName: String) {
        val input = EditText(this).apply {
            setText(importedName)
            setSelection(0, importedName.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名方案文件夹")
            .setMessage("可修改导入的文件夹名（Documents/Oime/schema/ 下），内部方案 ID 不变。")
            .setView(input)
            .setPositiveButton("重命名") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == importedName) return@setPositiveButton
                if (newName.contains('/') || newName.contains('\\') || newName.contains("..")) {
                    Toast.makeText(this, "名称含非法字符", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val src = File(StorageManager.schemaDir, importedName)
                val dst = File(StorageManager.schemaDir, newName)
                if (!src.exists() || dst.exists() || !src.renameTo(dst)) {
                    Toast.makeText(this, "重命名失败（目标文件夹已存在？）", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    RimeManager.deployImportedSchemas(applicationContext)
                }
                Toast.makeText(this, "已重命名为「$newName」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("保持原名", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 主题色跟随键盘回车键颜色，并跟随系统深浅色
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val scheme = if (dark) androidx.compose.material3.darkColorScheme()
            else androidx.compose.material3.lightColorScheme()
            MaterialTheme(
                colorScheme = scheme.copy(
                    primary = com.azime.input.ui.keyboard.keyboardAccentActiveColor(dark),
                    primaryContainer = com.azime.input.ui.keyboard.keyboardAccentKeyColor(dark),
                    onPrimaryContainer = if (dark) Color(0xFFD7E3F4) else Color(0xFF202124),
                ),
            ) {
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
    // 一级菜单（main）+ 二级页：schemas | keyboard | appearance | about
    var subPage by remember { mutableStateOf("main") }
    val subTitles = mapOf(
        "schemas" to "输入方案",
        "keyboard" to "键盘",
        "appearance" to "外观",
        "about" to "关于",
    )

    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(subTitles[subPage] ?: "○输入法", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { if (subPage != "main") subPage = "main" else onBackClick() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            )
        }
    ) { padding ->
        // ── 二级页：输入方案 ──
        if (subPage == "schemas") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Card { Column(Modifier.padding(vertical = 4.dp)) { SchemaList() } }
                }
                item {
                    Card { Column(Modifier.padding(vertical = 4.dp)) {
                        KsuItem(
                            icon = Icons.Default.FileDownload,
                            title = "导入方案（ZIP）",
                            subtitle = "兼容 GBK 文件名压缩包",
                            onClick = onPickZip,
                        )
                        KsuItem(
                            icon = Icons.Default.FolderOpen,
                            title = "导入方案（文件夹）",
                            subtitle = "选择含方案 yaml 的文件夹；导入后自动部署",
                            onClick = onPickFolder,
                        )
                    } }
                }
            }
            return@Scaffold
        }
        // ── 二级页：键盘 ──
        if (subPage == "keyboard") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
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
                item {
                    Card { Column { KeyHeightSliders() } }
                }
                item {
                    Card { Column { VibrationSettings() } }
                }
            }
            return@Scaffold
        }
        // ── 二级页：外观 ──
        if (subPage == "appearance") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Card {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KsuItem(
                                icon = Icons.Default.FontDownload,
                                title = "字体管理",
                                subtitle = "键帽 / 候选字体（Documents/Oime/fonts）",
                                onClick = onManageFonts,
                            )
                        }
                    }
                }
            }
            return@Scaffold
        }
        // ── 二级页：关于 ──
        if (subPage == "about") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Card {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KsuItem(
                                icon = Icons.Default.Info,
                                title = "版本",
                                subtitle = "0.4.0-oime · 包名 com.oime.input · 平台 RIME",
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
            return@Scaffold
        }
        // ── 一级菜单：纯入口 ──
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { StatusCard() }

            item {
                Card {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        KsuItem(
                            icon = Icons.Default.List,
                            title = "输入方案",
                            subtitle = "切换 / 导入 / 重命名方案",
                            onClick = { subPage = "schemas" },
                        )
                        KsuItem(
                            icon = Icons.Default.Keyboard,
                            title = "键盘",
                            subtitle = "布局编辑 · 键高 · 增高行 · 打字振动",
                            onClick = { subPage = "keyboard" },
                        )
                        KsuItem(
                            icon = Icons.Default.FontDownload,
                            title = "外观",
                            subtitle = "字体管理",
                            onClick = { subPage = "appearance" },
                        )
                        KsuItem(
                            icon = Icons.Default.Code,
                            title = "预设置",
                            subtitle = "preset_keys.lua（按键动作预设）",
                            onClick = onEditLuaScript,
                        )
                        KsuItem(
                            icon = Icons.Default.Info,
                            title = "关于",
                            subtitle = "版本 / 项目地址",
                            onClick = { subPage = "about" },
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

/** 键盘尺寸设置：键高滑杆 + 增高行开关与高度滑杆（下次键盘弹出即生效）。 */
@Composable
private fun KeyHeightSliders() {
    var keyH by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.keyHeightDp().toFloat()) }
    var barH by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.barHeightDp().toFloat()) }
    var barOn by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.barEnabled()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("键高：${keyH.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = keyH,
            onValueChange = {
                keyH = it
                com.azime.input.core.keyboard.KeyboardManager.setKeyHeightDp(it.toInt())
            },
            valueRange = 36f..64f,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("增高行（工具栏 + 候选栏）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = barOn,
                onCheckedChange = {
                    barOn = it
                    com.azime.input.core.keyboard.KeyboardManager.setBarEnabled(it)
                },
            )
        }
        if (barOn) {
            Text("增高行高度：${barH.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = barH,
                onValueChange = {
                    barH = it
                    com.azime.input.core.keyboard.KeyboardManager.setBarHeightDp(it.toInt())
                },
                valueRange = 38f..72f,
            )
        }
        Text(
            "工具栏自定义：在键盘上长按 ○ 菜单键勾选",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 打字振动设置：总开关 / 按下 / 抬起 / 系统或自定义模式（自定义时长滑杆）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VibrationSettings() {
    var enabled by remember { mutableStateOf(HapticsManager.enabled()) }
    var pressOn by remember { mutableStateOf(HapticsManager.pressEnabled()) }
    var releaseOn by remember { mutableStateOf(HapticsManager.releaseEnabled()) }
    var custom by remember { mutableStateOf(HapticsManager.mode() == "custom") }
    var ms by remember { mutableStateOf(HapticsManager.customMs().toFloat()) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        SettingSwitchRow("总开关", enabled) {
            enabled = it
            HapticsManager.setEnabled(it)
        }
        SettingSwitchRow("按下震动", pressOn) {
            pressOn = it
            HapticsManager.setPressEnabled(it)
        }
        SettingSwitchRow("抬起按键震动", releaseOn) {
            releaseOn = it
            HapticsManager.setReleaseEnabled(it)
        }
        Spacer(Modifier.height(6.dp))
        Text("震动模式", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !custom,
                onClick = { custom = false; HapticsManager.setMode("system") },
                label = { Text("系统默认") },
            )
            FilterChip(
                selected = custom,
                onClick = { custom = true; HapticsManager.setMode("custom") },
                label = { Text("自定义") },
            )
        }
        if (custom) {
            Spacer(Modifier.height(4.dp))
            Text(
                "自定义时长：${ms.toInt()}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = ms,
                onValueChange = {
                    ms = it
                    HapticsManager.setCustomMs(it.toInt())
                },
                valueRange = 5f..60f,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionLabel(text: String) {    Text(
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

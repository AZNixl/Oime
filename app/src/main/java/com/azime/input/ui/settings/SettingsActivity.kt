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
import androidx.compose.foundation.BorderStroke
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
    val scope = rememberCoroutineScope()
    // 一级菜单（main）+ 二级页：schemas | keyboard | theme | about
    var subPage by remember { mutableStateOf("main") }
    val subTitles = mapOf(
        "schemas" to "输入方案",
        "keyboard" to "键盘",
        "theme" to "主题与配色",
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
                item {
                    // 重新部署：方案 config 改动后手动触发
                    var deploying by remember { mutableStateOf(false) }
                    Card { Column(Modifier.padding(vertical = 4.dp)) {
                        KsuItem(
                            icon = Icons.Default.Build,
                            title = "重新部署",
                            subtitle = if (deploying) "部署中，请稍候…" else "方案 config 改动后重新部署全部方案",
                            onClick = {
                                if (!deploying) {
                                    deploying = true
                                    scope.launch {
                                        runCatching {
                                            com.azime.input.core.rime.RimeManager.deployImportedSchemas(context.applicationContext)
                                        }
                                        deploying = false
                                        Toast.makeText(context, "部署完成", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
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
                item {
                    Card { Column { SymbolHintSettings() } }
                }
            }
            return@Scaffold
        }
        // ── 二级页：主题与配色（参考小企鹅输入法.fx：主题卡片网格 + 自定义色） ──
        if (subPage == "theme") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item { Card { Column { ThemeColorSettings() } } }
                item {
                    // 字体管理入口：位于主题与配色下层
                    Card {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KsuItem(
                                icon = Icons.Default.FontDownload,
                                title = "字体管理",
                                subtitle = "多选字体回退链（Documents/Oime/fonts）",
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
                                subtitle = "0.8.1-oime · 包名 com.oime.input · 平台 RIME",
                                onClick = {},
                                showChevron = false,
                            )
                            KsuItem(
                                icon = Icons.Default.Link,
                                title = "GitHub",
                                subtitle = "github.com/AZNixl/Oime",
                                onClick = {
                                    runCatching {
                                        context.startActivity(android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/AZNixl/Oime"),
                                        ))
                                    }
                                },
                                showChevron = false,
                            )
                            KsuItem(
                                icon = Icons.Default.WavingHand,
                                title = "重新运行首次启动向导",
                                subtitle = "权限 / 启用 / 选择输入法引导",
                                onClick = {
                                    context.getSharedPreferences("wizard_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("wizard_done", false).apply()
                                    context.startActivity(
                                        android.content.Intent(context, com.azime.input.ui.main.MainActivity::class.java)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                            )
                        }
                    }
                }
            }
            return@Scaffold
        }
        // ── 一级菜单：KSU 布局（大方块状态卡 + 两小方块 + 设置项列表） ──
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // 大方块：引擎状态卡
            item { StatusCard() }

            // 两个小方块：版本 / 项目
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("版本", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Text("0.8.1-oime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                        modifier = Modifier.weight(1f).clickable {
                            runCatching {
                                context.startActivity(android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/AZNixl/Oime"),
                                ))
                            }
                        },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("项目", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Text("AZNixl/Oime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 各设置项
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
                            icon = Icons.Default.Palette,
                            title = "主题与配色",
                            subtitle = "主题卡片 · 强调色 · 字体管理",
                            onClick = { subPage = "theme" },
                        )
                        KsuItem(
                            icon = Icons.Default.Backup,
                            title = "备份设置",
                            subtitle = "导出全部偏好到 Download 目录",
                            onClick = {
                                scope.launch {
                                    val name = backupSettings(context)
                                    Toast.makeText(
                                        context,
                                        if (name != null) "已备份：Download/$name" else "备份失败",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
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
            Text("增高行（键盘底部空行）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                valueRange = 1f..72f,
            )
        }
        Text(
            "增高行 = 键盘最后一行下方多一个无按键的空行（1-72dp）；工具栏自定义：长按 ○ 菜单键勾选",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // 空格键显示文本：留空 = 显示当前方案名（默认）
        var spaceLabel by remember {
            mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.spaceLabel())
        }
        OutlinedTextField(
            value = spaceLabel,
            onValueChange = {
                spaceLabel = it
                com.azime.input.core.keyboard.KeyboardManager.setSpaceLabel(it)
            },
            label = { Text("空格键显示文本") },
            placeholder = { Text("留空显示当前方案名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
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

/** 符号显示开关：长按符号提示 + 四向滑动提示，各自独立（关闭时动作照常执行）。 */
@Composable
private fun SymbolHintSettings() {
    var hintLong by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.hintLong()) }
    var hintUp by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.hintUp()) }
    var hintDown by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.hintDown()) }
    var hintLeft by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.hintLeft()) }
    var hintRight by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.hintRight()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("符号显示", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        SettingSwitchRow("长按符号提示", hintLong) {
            hintLong = it; com.azime.input.core.keyboard.KeyboardManager.setHintLong(it)
        }
        SettingSwitchRow("上滑提示", hintUp) {
            hintUp = it; com.azime.input.core.keyboard.KeyboardManager.setHintUp(it)
        }
        SettingSwitchRow("下滑提示", hintDown) {
            hintDown = it; com.azime.input.core.keyboard.KeyboardManager.setHintDown(it)
        }
        SettingSwitchRow("左滑提示", hintLeft) {
            hintLeft = it; com.azime.input.core.keyboard.KeyboardManager.setHintLeft(it)
        }
        SettingSwitchRow("右滑提示", hintRight) {
            hintRight = it; com.azime.input.core.keyboard.KeyboardManager.setHintRight(it)
        }
        Text(
            "关闭后不在键面上显示提示文字，滑动 / 长按动作照常执行；下次键盘弹出生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 主题与配色（参考小企鹅输入法.fx）：色彩模式 + 主题卡片网格（迷你键盘预览）+ 自定义 RGB。 */
@Composable
private fun ThemeColorSettings() {
    val km = com.azime.input.core.theme.KeyboardTheme
    var mode by remember { mutableStateOf(km.mode()) }
    var accentRev by remember { mutableStateOf(0) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("色彩模式", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == km.MODE_SYSTEM,
                onClick = { mode = km.MODE_SYSTEM; km.setMode(km.MODE_SYSTEM) },
                label = { Text("跟随系统") },
            )
            FilterChip(
                selected = mode == km.MODE_LIGHT,
                onClick = { mode = km.MODE_LIGHT; km.setMode(km.MODE_LIGHT) },
                label = { Text("亮色") },
            )
            FilterChip(
                selected = mode == km.MODE_DARK,
                onClick = { mode = km.MODE_DARK; km.setMode(km.MODE_DARK) },
                label = { Text("暗色") },
            )
        }
        Spacer(Modifier.height(12.dp))

        Text("主题配色", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        // 主题卡片网格（2 列）：迷你键盘预览（工具栏 + 三行键 + 强调色回车键），点击应用
        km.accentPresets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPresets.forEach { (name, light, dark) ->
                    val selected = km.accentLight() == light && km.accentDark() == dark
                    ThemeCard(
                        name = name,
                        accentLight = light,
                        accentDark = dark,
                        darkMode = mode == km.MODE_DARK,
                        selected = selected,
                        onClick = { km.setAccents(light, dark); accentRev++ },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 奇数个补位
                repeat(2 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 自定义 RGB（亮 / 暗共用一个自定义色）
        key(accentRev) {
            var r by remember { mutableStateOf(((km.accentLight() shr 16) and 0xFF) / 255f) }
            var g by remember { mutableStateOf(((km.accentLight() shr 8) and 0xFF) / 255f) }
            var b by remember { mutableStateOf((km.accentLight() and 0xFF) / 255f) }
            fun apply() {
                val argb = (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
                km.setAccents(argb, argb)
            }
            Text("自定义颜色", style = MaterialTheme.typography.bodyMedium)
            Text("红", style = MaterialTheme.typography.bodySmall)
            Slider(value = r, onValueChange = { r = it; apply() }, valueRange = 0f..1f)
            Text("绿", style = MaterialTheme.typography.bodySmall)
            Slider(value = g, onValueChange = { g = it; apply() }, valueRange = 0f..1f)
            Text("蓝", style = MaterialTheme.typography.bodySmall)
            Slider(value = b, onValueChange = { b = it; apply() }, valueRange = 0f..1f)
        }
        Text(
            "主题卡片即时预览，点击应用；配色应用于键盘强调色（回车键、候选高亮等）与设置页主色，下次键盘弹出生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 迷你键盘预览主题卡片（参考小企鹅输入法的主题选择卡）。 */
@Composable
private fun ThemeCard(
    name: String,
    accentLight: Int,
    accentDark: Int,
    darkMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // 预览按当前色彩模式渲染：暗色模式用暗色 accent 预览
    val accent = Color(if (darkMode) accentDark else accentLight)
    val previewBg = if (darkMode) Color(0xFF1B1D1F) else Color(0xFFE9EBEE)
    val keyBg = if (darkMode) Color(0xFF2A2D2F) else Color.White
    val keyText = if (darkMode) Color(0xFFE8EAED) else Color(0xFF202124)
    val cardBorder = if (selected) BorderStroke(2.dp, cs.primary) else BorderStroke(1.dp, cs.outlineVariant)

    Column(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(12.dp),
            border = cardBorder,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Column(
                modifier = Modifier
                    .background(previewBg)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // 迷你工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(if (darkMode) Color(0xFF26282A) else Color.White, RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(Modifier.size(4.dp).background(keyText, CircleShape))
                    Box(Modifier.weight(1f).height(3.dp).background(keyText.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
                    Box(Modifier.size(4.dp).background(accent, CircleShape))
                }
                // 三行迷你键位：最后一键为强调色（回车）
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(4) { col ->
                            val isEnter = row == 2 && col == 3
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(11.dp)
                                    .background(if (isEnter) accent else keyBg, RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) cs.primary else cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "已选", tint = cs.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** 备份全部偏好为 JSON，写入系统 Download 目录（MediaStore）。返回文件名，失败返回 null。 */
private fun backupSettings(context: android.content.Context): String? = runCatching {
    val prefNames = listOf("keyboard_prefs", "font_prefs", "haptic_prefs", "theme_prefs", "wizard_prefs")
    val root = org.json.JSONObject()
    for (name in prefNames) {
        val p = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        root.put(name, org.json.JSONObject(p.all))
    }
    val fileName = "Oime_backup_" +
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date()) + ".json"
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
        }
        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
        } ?: return null
    } else {
        @Suppress("DEPRECATION")
        val dir = android.os.Environment
            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        java.io.File(dir, fileName).writeText(root.toString())
    }
    fileName
}.getOrNull()

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

package com.azime.input.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    // 反馈轮16：启动界面自动申请麦克风权限（语音输入长按 ○ 听写用）
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 授权结果由语音输入卡自行复查，这里不需要额外处理 */ }

    private fun importSchema(action: () -> Result<String>) {
        lifecycleScope.launch {
            action().onSuccess { name ->
                RimeManager.deployImportedSchemas(applicationContext)
                // 轮13：导入即创建方案组（Documents/Oime/schema/<名>/），不自动切换
                Toast.makeText(this@SettingsActivity, "已导入方案组「$name」，可在方案组中切换", Toast.LENGTH_SHORT).show()
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
                // 轮13：重命名的正好是当前方案组时，同步更新 current_group 指向
                if (importedName == RimeManager.currentGroupId(applicationContext)) {
                    RimeManager.setCurrentGroup(applicationContext, newName)
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
        // 反馈轮16：启动界面自动申请麦克风权限（语音输入；IME 界面无法弹权限框，统一在设置入口申请）
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
        // 反馈轮9：设置界面状态栏沉浸（edge-to-edge，状态栏随主题深浅色）
        enableEdgeToEdge()
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
    // 反馈轮16：输入方案页的「方案管理」子级页（勾选启用集）
    var showManage by remember { mutableStateOf(false) }
    val subTitles = mapOf(
        "schemas" to "输入方案",
        "keyboard" to "键盘",
        "theme" to "主题与配色",
        "float" to "悬浮窗",
        "about" to "关于",
    )
    // 反馈轮10：设置子级页支持系统返回键（原来滑动/返回直接回桌面）
    BackHandler(enabled = subPage != "main" || showManage) {
        if (showManage) showManage = false else subPage = "main"
    }
    // 反馈轮16：「部署」提到标题文本后面（原独立卡片移除）
    var deploying by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (subPage == "schemas" && showManage) "方案管理"
                            else subTitles[subPage] ?: "○输入法",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (subPage == "schemas" && !showManage) {
                            Text(
                                if (deploying) "部署中…" else "部署",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (deploying) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable(enabled = !deploying) {
                                        deploying = true
                                        scope.launch {
                                            runCatching {
                                                com.azime.input.core.rime.RimeManager.deployImportedSchemas(context.applicationContext)
                                            }
                                            deploying = false
                                            Toast.makeText(context, "部署完成", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showManage) showManage = false
                        else if (subPage != "main") subPage = "main" else onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            )
        }
    ) { padding ->
        // ── 二级页：输入方案 ──
        if (subPage == "schemas") {
            // 反馈轮16：方案管理子级页（从所选方案组行后的入口进入，勾选启用集）
            if (showManage) {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    SchemaManagePage()
                }
                return@Scaffold
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                // 父级菜单①：方案组（组单选 → 选中组后挂「方案管理」入口 → 下方只显示已选方案）
                item {
                    Card { Column(Modifier.padding(vertical = 4.dp)) { SchemaList(onOpenManage = { showManage = true }) } }
                }
                // 父级菜单②：导入方案
                item {
                    Card { Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "导入方案",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
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
                // 父级菜单③：语音输入（轮15：麦克风权限 + 识别方式；本地模型/联网 API 占位）
                item {
                    Card { Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "语音输入",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        val micGranted = remember {
                            mutableStateOf(
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            )
                        }
                        val micLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { micGranted.value = it }
                        KsuItem(
                            icon = Icons.Default.Mic,
                            title = "麦克风权限",
                            subtitle = if (micGranted.value) "已授权（长按 ○ 键开始听写）" else "语音输入需要录音权限",
                            onClick = { if (!micGranted.value) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        )
                        val voicePrefs = remember { context.getSharedPreferences("voice_prefs", android.content.Context.MODE_PRIVATE) }
                        var voiceMode by remember { mutableStateOf(voicePrefs.getString("mode", "system") ?: "system") }
                        Text(
                            "识别方式",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                        listOf(
                            "system" to "系统接口（SpeechRecognizer）",
                            "local" to "本地模型（暂未开放）",
                            "api" to "联网 API（暂未开放）",
                        ).forEach { (value, label) ->
                            val enabled = value == "system"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) {
                                        voiceMode = value
                                        voicePrefs.edit().putString("mode", value).apply()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = voiceMode == value, onClick = {
                                    if (enabled) {
                                        voiceMode = value
                                        voicePrefs.edit().putString("mode", value).apply()
                                    }
                                }, enabled = enabled)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                    Card { Column { FontSizeSettings() } }
                }
                item {
                    Card { Column { KeyAppearanceSettings() } }
                }
                item {
                    Card { Column { ResponseTimingSettings() } }
                }
                item {
                    Card { Column { GesturePositionSettings() } }
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
        // ── 二级页：悬浮窗（反馈轮9，参考 trime 悬浮窗） ──
        if (subPage == "float") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item { Card { Column { FloatingWindowSettings() } } }
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
                                subtitle = "0.9.7-oime · 包名 com.oime.input · 平台 RIME",
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
                                icon = Icons.Default.Backup,
                                title = "备份设置",
                                subtitle = "导出全部偏好到 Download 目录（反馈轮10 移入关于）",
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
            // 反馈轮9：KSU 布局——左侧一个大状态方块 + 右侧两个小方块
            // 反馈轮10：两小方块与大方块等高、图标缩小、「项目」改「部署」
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.weight(1.2f).fillMaxHeight()) { StatusCard(fillWidth = true) }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                // 反馈轮12：「版本」并到图标同行，压缩为两行
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("版本", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Text("0.9.7-oime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f).clickable {
                                runCatching {
                                    context.startActivity(android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/AZNixl/Oime"),
                                    ))
                                }
                            },
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                // 反馈轮12：去掉「部署」，改为项目地址卡（排版同上方版本卡）
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("项目", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Text(
                                    "AZNixl/Oime",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                            subtitle = "布局编辑 · 键高 · 字号 · 按键外观",
                            onClick = { subPage = "keyboard" },
                        )
                        KsuItem(
                            icon = Icons.Default.PictureInPictureAlt,
                            title = "悬浮窗",
                            subtitle = "编码预览悬浮窗 · 默认 / 自定义样式",
                            onClick = { subPage = "float" },
                        )
                        KsuItem(
                            icon = Icons.Default.Palette,
                            title = "主题与配色",
                            subtitle = "主题卡片 · 强调色 · 字体管理",
                            onClick = { subPage = "theme" },
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
private fun StatusCard(fillWidth: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var schema by remember { mutableStateOf("") }
    // 反馈轮10：输入法未启用时大方块灰色显示「未启用」，点击跳转系统启用页
    val imeEnabled = remember {
        runCatching {
            val current = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
            ) ?: ""
            current.startsWith(context.packageName)
        }.getOrDefault(false)
    }
    val notEnabled = !imeEnabled

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            RimeManager.ensureReady(context)
        }
        ready = RimeManager.isReady()
        schema = if (ready) RimeManager.schemaDisplayName(RimeManager.currentSchema()) else ""
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notEnabled) Color(0xFFB9BEC4) else cs.primaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = (
            if (notEnabled) Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            } else Modifier
        ).let { if (fillWidth) it.fillMaxHeight() else it },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ○ logo（未启用时灰色）
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(if (notEnabled) Color(0xFF8A8F94) else cs.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "○",
                    color = if (notEnabled) Color(0xFFE3E5E8) else cs.onPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                // 反馈轮12：字号调小 + 单行省略，窄方块内不换行
                Text(
                    "○输入法",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                when {
                                    notEnabled -> Color(0xFF6B7075)
                                    ready -> Color(0xFF2E9E5B)
                                    else -> cs.tertiary
                                },
                                CircleShape,
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            notEnabled -> "未启用 · 点击去启用"
                            ready -> "运行正常 · $schema"
                            else -> "引擎未就绪 / 首次部署中…"
                        },
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (notEnabled) Color(0xFF3C4043) else cs.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SchemaList(onOpenManage: () -> Unit) {
    val context = LocalContext.current
    // 反馈轮13：方案组区 —— 组 → 方案 两级（参考 trime2，一次只加载一个组）
    var groups by remember { mutableStateOf(RimeManager.schemaGroups(context)) }
    var currentGroup by remember { mutableStateOf(RimeManager.currentGroupId(context)) }
    var switchingGroup by remember { mutableStateOf(false) }
    val groupScope = rememberCoroutineScope()
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

    fun switchGroup(groupId: String) {
        if (groupId == currentGroup || switchingGroup) return
        currentGroup = groupId
        switchingGroup = true
        groupScope.launch {
            runCatching { RimeManager.switchSchemaGroup(context.applicationContext, groupId) }
            switchingGroup = false
            groups = RimeManager.schemaGroups(context)
            schemas = RimeManager.availableSchemas()
            current = RimeManager.currentSchema()
        }
    }

    Text(
        "方案组（一次加载一组）",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    groups.forEach { g ->
        val selected = g.id == currentGroup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { switchGroup(g.id) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = { switchGroup(g.id) })
            Spacer(Modifier.width(8.dp))
            Text(g.name, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "${g.schemaIds.size} 个方案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Check, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
    // 反馈轮16：选中组「后面显示方案管理」——第一次启用方案必须先进管理勾选启用集
    groups.firstOrNull { it.id == currentGroup }?.let { g ->
        val enabledIdsNow = RimeManager.groupEnabledIds(context, currentGroup)
        val enabledCount = enabledIdsNow?.size ?: g.schemaIds.size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenManage() }
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "方案管理",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (enabledIdsNow == null) "尚未选择方案，点此勾选实际使用的（组内 ${g.schemaIds.size} 个）"
                    else "已启用 $enabledCount 个，点此修改勾选",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )

    // ── 已选方案（反馈轮16：父级菜单下只显示勾选启用的方案；切换仍为单选）──
    Text(
        "已选方案（点击切换）",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    val enabledIds = remember(currentGroup, schemas) { RimeManager.groupEnabledIds(context, currentGroup) }
    val enabledList = remember(enabledIds, schemas) {
        if (enabledIds != null) schemas.filter { it in enabledIds } else emptyList()
    }
    when {
        // 未做过方案管理：引导先进「方案管理」勾选（第一次启用方案必须进入方案管理）
        enabledIds == null -> Text(
            "尚未选择使用的方案 —— 请先进入上方「方案管理」勾选实际使用的方案",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        enabledList.isEmpty() -> Text(
            "未启用任何方案 —— 请进入「方案管理」勾选",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        else -> enabledList.forEach { id ->
            val selected = id == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        current = id
                        RimeManager.switchSchema(id)
                        // 轮13：记录组内上次使用的方案（部署后回落 schema_list[0] 即回到它）
                        runCatching { RimeManager.recordGroupSchema(context.applicationContext, id) }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = {
                    current = id
                    RimeManager.switchSchema(id)
                    runCatching { RimeManager.recordGroupSchema(context.applicationContext, id) }
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
    }
}

/**
 * 方案管理子级页（反馈轮16）：从「输入方案 → 方案组 → 选中组 → 方案管理」进入。
 * 勾选当前组内实际使用的方案（启用集），应用后 schema_list（部署范围）与
 * 「已选方案」列表都只含启用方案。原平铺在输入方案页的方案管理区整体迁入此处。
 */
@Composable
private fun SchemaManagePage() {
    val context = LocalContext.current
    val groupScope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(RimeManager.schemaGroups(context)) }
    var currentGroup by remember { mutableStateOf(RimeManager.currentGroupId(context)) }
    val group = groups.firstOrNull { it.id == currentGroup }
    val manageSchemas = group?.schemaIds ?: emptyList()
    var enabledSet by remember(currentGroup, manageSchemas) {
        mutableStateOf(
            RimeManager.groupEnabledIds(context, currentGroup)?.filter { it in manageSchemas }?.toSet()
                ?: manageSchemas.toSet()
        )
    }
    var applyingManage by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "组：${group?.name ?: "（无）"}　共 ${manageSchemas.size} 个方案，已选 ${enabledSet.size} 个",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            "勾选实际使用的方案后点「应用」；应用后键盘 O 菜单「输入方案」里只出现已选方案",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(manageSchemas.size) { idx ->
                val id = manageSchemas[idx]
                val checked = id in enabledSet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            enabledSet = if (checked) enabledSet - id else enabledSet + id
                        }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = {
                        enabledSet = if (it) enabledSet + id else enabledSet - id
                    })
                    Text(RimeManager.schemaDisplayName(id), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // 全选 / 清空 快捷操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "全选",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { enabledSet = manageSchemas.toSet() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "清空",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { enabledSet = emptySet() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        // 应用：写启用集 → 重入当前组（syncGroup 按启用集重写 schema_list 并部署）
        Button(
            onClick = {
                if (applyingManage) return@Button
                applyingManage = true
                groupScope.launch {
                    runCatching {
                        RimeManager.setGroupEnabled(context.applicationContext, currentGroup, enabledSet.toList())
                        RimeManager.switchSchemaGroup(context.applicationContext, currentGroup)
                    }
                    applyingManage = false
                    Toast.makeText(
                        context,
                        "已应用方案选择（${enabledSet.size} 个）",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            enabled = !applyingManage && manageSchemas.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(if (applyingManage) "应用中，请稍候…" else "应用方案选择（${enabledSet.size} 个）")
        }
    }
}

/** 键盘尺寸设置：键高滑杆 + 增高行开关与高度滑杆（下次键盘弹出即生效）。 */
@Composable
private fun KeyHeightSliders() {
    var keyH by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.keyHeightDp().toFloat()) }
    var barH by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.barHeightDp().toFloat()) }
    var barOn by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.barEnabled()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        XimeSlider("键高", "${keyH.toInt()}dp", keyH, 36f..64f) {
            keyH = it
            com.azime.input.core.keyboard.KeyboardManager.setKeyHeightDp(it.toInt())
        }
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
            XimeSlider("增高行高度", "${barH.toInt()}dp", barH, 1f..72f) {
                barH = it
                com.azime.input.core.keyboard.KeyboardManager.setBarHeightDp(it.toInt())
            }
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
        Text(
            "提示：中文模式下按 ⇧ 切英文后，空格键可输入空格（反馈轮9已修复无编码时空格被引擎吞掉的问题）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * xime.az 风格滑条：标题左 + 当前值右 + 深色圆角轨道 + 白色竖线 thumb。
 * 反馈轮10：自绘实现（pointerInput），不依赖 material3 Slider 的 thumb/track slot API。
 */
@Composable
private fun XimeSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.bodySmall,
                color = cs.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        val span = (range.endInclusive - range.start).coerceAtLeast(0.001f)
        fun frac() = ((value - range.start) / span).coerceIn(0f, 1f)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(range) {
                    fun setFromX(x: Float) {
                        val f = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        onChange(range.start + f * span)
                    }
                    detectTapGestures { off -> setFromX(off.x) }
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures { change, _ ->
                        val f = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onChange(range.start + f * span)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 深色圆角轨道
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Color(0xFF232527), RoundedCornerShape(5.dp)),
            )
            // 强调色已填充段
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac())
                    .height(10.dp)
                    .background(cs.primary, RoundedCornerShape(5.dp)),
            )
            // 白色竖线 thumb（描边保证对比度）
            val density = LocalDensity.current
            val thumbX = with(density) {
                (((constraints.maxWidth - 8.dp.toPx()) * frac()).coerceAtLeast(0f)).toDp()
            }
            Box(
                modifier = Modifier
                    .offset(x = thumbX)
                    .size(8.dp, 22.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0x33000000), RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** 按键响应时间（反馈轮10）：长按/连发/滑动阈值微调，键盘内即时读取。 */
@Composable
private fun ResponseTimingSettings() {
    val km = com.azime.input.core.keyboard.KeyboardManager
    var longPress by remember { mutableStateOf(km.longPressMs().toFloat()) }
    var repeatStart by remember { mutableStateOf(km.repeatStartMs().toFloat()) }
    var repeatInterval by remember { mutableStateOf(km.repeatIntervalMs().toFloat()) }
    var swipe by remember { mutableStateOf(km.swipeThresholdDp().toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("按键响应", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        XimeSlider("长按触发", "${longPress.toInt()}ms", longPress, 100f..800f) {
            longPress = it; km.setLongPressMs(it.toInt())
        }
        XimeSlider("连发起动", "${repeatStart.toInt()}ms", repeatStart, 50f..500f) {
            repeatStart = it; km.setRepeatStartMs(it.toInt())
        }
        XimeSlider("连发间隔", "${repeatInterval.toInt()}ms", repeatInterval, 20f..200f) {
            repeatInterval = it; km.setRepeatIntervalMs(it.toInt())
        }
        XimeSlider("滑动触发距离", "${swipe.toInt()}dp", swipe, 10f..80f) {
            swipe = it; km.setSwipeThresholdDp(it.toInt())
        }
        Text(
            "长按 = 按住多久触发长按符号；连发 = 长按退格的起动延时与每字间隔；滑动 = 手势触发距离。下次键盘弹出即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 手势提示位置（反馈轮11）：长按气泡偏移与四向预览显示位置。 */
@Composable
private fun GesturePositionSettings() {
    val km = com.azime.input.core.keyboard.KeyboardManager
    var bubbleX by remember { mutableStateOf(km.bubbleXDp().toFloat()) }
    var bubbleY by remember { mutableStateOf(km.bubbleYExtraDp().toFloat()) }
    var previewAbove by remember { mutableStateOf(km.swipePreviewAbove()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("手势提示位置", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        XimeSlider("长按气泡水平偏移", "${bubbleX.toInt()}dp", bubbleX, 0f..24f) {
            bubbleX = it; km.setBubbleXDp(it.toInt())
        }
        XimeSlider("气泡垂直余量", "${bubbleY.toInt()}dp", bubbleY, 5f..40f) {
            bubbleY = it; km.setBubbleYExtraDp(it.toInt())
        }
        SettingSwitchRow("四向预览显示在键上方", previewAbove) { on ->
            previewAbove = on; km.setSwipePreviewAbove(on)
        }
        Text(
            "水平偏移 = 长按气泡相对按键的右移量；垂直余量 = 气泡与按键的间距。关闭「键上方」时四向预览显示在键面中央。下次键盘弹出即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 字号设置（反馈轮9）：键盘键面与工具栏/候选字号分开调整。 */
@Composable
private fun FontSizeSettings() {
    var keySize by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.fontSizeKey().toFloat()) }
    var barSize by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.fontSizeBar().toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("字号", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        XimeSlider("键盘键面字号", "${keySize.toInt()}sp", keySize, 12f..30f) {
            keySize = it
            com.azime.input.core.keyboard.KeyboardManager.setFontSizeKey(it.toInt())
        }
        XimeSlider("工具栏/候选字号", "${barSize.toInt()}sp", barSize, 12f..28f) {
            barSize = it
            com.azime.input.core.keyboard.KeyboardManager.setFontSizeBar(it.toInt())
        }
        Text(
            "下次键盘弹出即生效；候选映射键、气泡符号等随相应字号缩放。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 按键外观设置（反馈轮9）：按键圆角 / 行距 / 列距。 */
@Composable
private fun KeyAppearanceSettings() {
    var corner by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.keyCornerDp().toFloat()) }
    var rowGap by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.rowGapDp().toFloat()) }
    var colGap by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.colGapDp().toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("按键外观", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        XimeSlider("按键圆角", "${corner.toInt()}dp", corner, 0f..20f) {
            corner = it
            com.azime.input.core.keyboard.KeyboardManager.setKeyCornerDp(it.toInt())
        }
        XimeSlider("行距", "${rowGap.toInt()}dp", rowGap, 1f..10f) {
            rowGap = it
            com.azime.input.core.keyboard.KeyboardManager.setRowGapDp(it.toInt())
        }
        XimeSlider("列距", "${colGap.toInt()}dp", colGap, 1f..10f) {
            colGap = it
            com.azime.input.core.keyboard.KeyboardManager.setColGapDp(it.toInt())
        }
        Text(
            "下次键盘弹出即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 悬浮窗设置（反馈轮9，参考 trime 悬浮窗 / 悬浮窗显示优化.lua）：
 * 开关 + 默认/自定义模式；自定义模式可调水平/垂直位置、字号、背景不透明度。
 */
@Composable
private fun FloatingWindowSettings() {
    val km = com.azime.input.core.keyboard.KeyboardManager
    var enabled by remember { mutableStateOf(km.floatEnabled()) }
    var mode by remember { mutableStateOf(km.floatMode()) }
    var x by remember { mutableStateOf(km.floatXDp().toFloat()) }
    var y by remember { mutableStateOf(km.floatYDp().toFloat()) }
    var textSp by remember { mutableStateOf(km.floatTextSp().toFloat()) }
    var alpha by remember { mutableStateOf(km.floatBgAlpha().toFloat()) }
    val custom = mode == "custom"
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("悬浮窗", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        SettingSwitchRow("启用悬浮窗（输入时显示编码）", enabled) {
            enabled = it
            km.setFloatEnabled(it)
        }
        Spacer(Modifier.height(6.dp))
        Text("样式", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !custom,
                onClick = { mode = "default"; km.setFloatMode("default") },
                label = { Text("默认悬浮窗") },
            )
            FilterChip(
                selected = custom,
                onClick = { mode = "custom"; km.setFloatMode("custom") },
                label = { Text("自定义悬浮窗") },
            )
        }
        if (custom) {
            Spacer(Modifier.height(8.dp))
            XimeSlider("水平位置", "${x.toInt()}dp", x, 0f..400f) {
                x = it; km.setFloatXDp(it.toInt())
            }
            XimeSlider("垂直位置（键盘顶向上）", "${y.toInt()}dp", y, 20f..400f) {
                y = it; km.setFloatYDp(it.toInt())
            }
            XimeSlider("字号", "${textSp.toInt()}sp", textSp, 14f..40f) {
                textSp = it; km.setFloatTextSp(it.toInt())
            }
            XimeSlider("背景不透明度", "${alpha.toInt()}%", alpha, 20f..100f) {
                alpha = it; km.setFloatBgAlpha(it.toInt())
            }
        }
        Text(
            "参考 trime 悬浮窗：输入时在键盘上方悬浮显示当前输入码；默认样式固定位置与字号，自定义样式按上面参数渲染。下次键盘弹出即生效。",
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
        // 反馈轮10：选中态读取 accentRev（应用配色后触发重组，选中框实时变动）
        val currentLight = remember(accentRev) { km.accentLight() }
        val currentDark = remember(accentRev) { km.accentDark() }
        // 主题卡片网格（2 列）：迷你键盘预览（工具栏 + 三行键 + 强调色回车键），点击应用
        km.accentPresets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPresets.forEach { (name, light, dark) ->
                    val selected = currentLight == light && currentDark == dark
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

        // 反馈轮9：樱粉右侧加「自定义」卡片，点击才展开下方自定义颜色调整
        val presets = km.accentPresets
        val isCustomSelected = presets.none { (_, l, d) -> currentLight == l && currentDark == d }
        var showCustom by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeCard(
                name = "自定义",
                accentLight = if (isCustomSelected) km.accentLight() else 0xFF6750A4.toInt(),
                accentDark = if (isCustomSelected) km.accentDark() else 0xFF6750A4.toInt(),
                darkMode = mode == km.MODE_DARK,
                selected = isCustomSelected || showCustom,
                onClick = { showCustom = !showCustom },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // 自定义 RGB（亮 / 暗共用一个自定义色）——点击自定义卡片后展开
        if (showCustom) {
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

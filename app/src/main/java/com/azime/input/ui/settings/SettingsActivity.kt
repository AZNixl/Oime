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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.azime.input.core.diag.Diag as DiagLog
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
import com.azime.input.ui.icons.OimeIcons
import androidx.lifecycle.lifecycleScope
import com.azime.input.core.haptic.HapticsManager
import com.azime.input.core.rime.RimeManager
import com.azime.input.core.storage.StorageManager
import com.azime.input.ui.editor.KeyboardEditorActivity
import com.azime.input.ui.font.FontManagerActivity
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
            result.data?.data?.let { uri ->
                // 轮19.50：**先命名，再导入**（不再有"保持原名"）
                promptFolderNameThenImport(displayNameOf(uri)) { name ->
                    schemaImporter.importFromZip(this, uri, name)
                }
            }
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
                // 轮13：导入即创建方案组（Documents/Oime/schemas/<名>/），不自动切换
                Toast.makeText(this@SettingsActivity, "已导入方案组「$name」，在方案组列表点击即可切换使用", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@SettingsActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 取 SAF URI 的显示名（去掉扩展名），用作文件夹名默认值。 */
    private fun displayNameOf(uri: android.net.Uri): String = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "新方案"

    /**
     * 轮19.50：**先命名再导入** —— 文件夹名必须由用户填写。
     *
     * 原来导入后弹「重命名 / 保持原名」，选保持原名会保留压缩包里的结构
     * （`schemas/<包名>/<方案文件夹>/…`）或直接用文件名当文件夹名，落盘结构不统一 ✗
     * 现在改为导入前命名 + 解压时拍平一层 ⇒ 结果恒为 `schemas/<名字>/<方案文件>` ✓
     */
    private fun promptFolderNameThenImport(
        defaultName: String,
        doImport: (String) -> Result<String>,
    ) {
        val input = android.widget.EditText(this).apply {
            setText(defaultName)
            setSelection(0, defaultName.length)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("命名方案文件夹")
            .setMessage(
                "导入后会保存为 Documents/Oime/schemas/<你填写的名字>/方案文件。" +
                    "\n（无论压缩包内是「文件夹/方案文件」还是直接「方案文件」，都会拍平到这一层）",
            )
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() -> {
                        toast("请填写文件夹名")
                        return@setPositiveButton
                    }
                    name.contains('/') || name.contains('\\') || name.contains("..") -> {
                        toast("名称含非法字符")
                        return@setPositiveButton
                    }
                    java.io.File(
                        com.azime.input.core.storage.StorageManager.schemaDir, name,
                    ).exists() -> {
                        toast("已存在同名方案文件夹")
                        return@setPositiveButton
                    }
                }
                importSchema { doImport(name) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 轻量 Toast 封装。 */
    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
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
            // 轮19.71：界面风格版本号 —— 在「主题与配色」里改风格时 +1，触发整页主题重算 ✓
            var uiStyleRev by remember { mutableStateOf(0) }
            val scheme = if (dark) androidx.compose.material3.darkColorScheme()
            else androidx.compose.material3.lightColorScheme()
            // 轮19.15：Card 默认底色（surfaceContainerLow/surfaceContainer）统一成浅灰 →
            // 所有二级页（键盘 / 主题 / O 圆环 …）里的卡片都跟着变浅灰
            // 轮19.26：M3 默认配色是**淡紫色系**，各卡片/容器按不同角色取色（surfaceContainerLow /
            // surfaceVariant / surfaceContainerHighest …），只覆盖两个角色会漏 —— 用户看到的紫底就是漏网的角色。
            // 这里把**整套中性色**统一成灰阶（浅色/深色各一套）。
            // 轮19.31：界面风格 —— miuix 用 MIUI 扁平灰阶（页面浅灰 + 卡片纯白），material 用中性灰
            // 轮19.71：界面风格改为**读 rev 状态** —— 原来直接读 isMiuix()（非 Compose 状态 ✗）
            // ⇒ 下拉改了偏好却没有任何东西触发重组 ⇒ 看起来"改了没反应" ✗
            val miuix = remember(uiStyleRev) { com.azime.input.core.theme.KeyboardTheme.isMiuix() }
            val plainGray = when {
                miuix && dark -> Color(0xFF2C2C2E)
                miuix -> Color(0xFFFFFFFF)
                dark -> Color(0xFF26262A)
                else -> Color(0xFFF1F1F2)
            }
            val grayHigh = when {
                miuix && dark -> Color(0xFF333335)
                miuix -> Color(0xFFF7F8FA)
                dark -> Color(0xFF2C2C31)
                else -> Color(0xFFEAEAEC)
            }
            val grayHighest = when {
                miuix && dark -> Color(0xFF3A3A3C)
                miuix -> Color(0xFFE8EAED)
                dark -> Color(0xFF323238)
                else -> Color(0xFFE4E4E7)
            }
            val bg = when {
                miuix && dark -> Color(0xFF191919)
                miuix -> Color(0xFFF2F3F5)
                dark -> Color(0xFF1B1B1F)
                else -> Color(0xFFFFFFFF)
            }
            MaterialTheme(
                colorScheme = scheme.copy(
                    background = bg,
                    surface = bg,
                    surfaceVariant = plainGray,
                    surfaceContainerLowest = bg,
                    surfaceContainerLow = plainGray,
                    surfaceContainer = plainGray,
                    surfaceContainerHigh = grayHigh,
                    surfaceContainerHighest = grayHighest,
                    secondaryContainer = grayHigh,
                    tertiaryContainer = grayHigh,
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
                    onPickZip = {
                        zipPickerLauncher.launch(
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "application/zip"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                        )
                    },
                    onUiStyleChanged = { uiStyleRev++ },
                )
            }
        }
    }
}

// ── KSU 风格主页 ─────────────────────────────────────────────

/** 轮19.2：应用版本名动态读取（原来硬编码 "0.9.8-oime"，升级后不跟随）。 */
private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "unknown"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenKeyboardEditor: () -> Unit,
    onManageFonts: () -> Unit,
    onPickZip: () -> Unit,
    // 轮19.71：界面风格变更回调（用于让整页主题立即重算）
    onUiStyleChanged: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    // 轮19.11b：设置主页卡片配色——大方块=强调色(不变)、右侧两方块=按键色、其余大项=浅灰
    val plainCardColors = CardDefaults.cardColors(
        containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
            Color(0xFF26262A)
        } else {
            Color(0xFFF1F1F2)
        },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 轮19.21：恢复备份——选 JSON 文件后覆盖写回偏好
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val n = restoreSettings(context, uri)
        Toast.makeText(
            context,
            if (n >= 0) "已恢复 $n 项设置，建议重新打开键盘生效" else "恢复失败：文件格式不对",
            Toast.LENGTH_LONG,
        ).show()
    }
    // 一级菜单（main）+ 二级页：schemas | keyboard | theme | about
    var subPage by remember { mutableStateOf("main") }
    // 反馈轮16：输入方案页的「方案管理」子级页（勾选启用集）
    var showManage by remember { mutableStateOf(false) }
    // 轮19：联网 API 配置对话框（语音输入大项）
    var showVoiceApiDialog by remember { mutableStateOf(false) }
    val subTitles = mapOf(
        "schemas" to "输入方案",
        "keyboard" to "键盘",
        "theme" to "主题与配色",
        "float" to "悬浮窗及嵌入式",
        "handwriting" to "语音手写管理",
        "oring" to "O 圆环",
        "about" to "关于",
                "candkeys" to "自定义候选键",
        )
    // 反馈轮10：设置子级页支持系统返回键（原来滑动/返回直接回桌面）
    BackHandler(enabled = subPage != "main" || showManage) {
        if (showManage) showManage = false else subPage = "main"
    }
    // 反馈轮16：「部署」提到标题文本后面（原独立卡片移除）
    var deploying by remember { mutableStateOf(false) }
    // 轮19.1：方案页「刷新」——切方案组/方案管理后界面数据重新拉取（原需退出重进）
    var schemaRefreshRev by remember { mutableStateOf(0) }
    var hwRefreshRev by remember { mutableStateOf(0) }

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
                            // 轮19.1：刷新键——重新拉取方案组/方案列表（切组或方案管理后同步界面）
                            Text(
                                "刷新",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 14.dp)
                                    .clickable {
                                        schemaRefreshRev++
                                        Toast.makeText(context, "已刷新", Toast.LENGTH_SHORT).show()
                                    },
                            )
                        }
                        if (subPage == "handwriting") {
                            Text(
                                "刷新",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable { hwRefreshRev++ },
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
                    SchemaManagePage(refreshRev = schemaRefreshRev)
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
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column(Modifier.padding(vertical = 4.dp)) { SchemaList(onOpenManage = { showManage = true }, refreshRev = schemaRefreshRev) } }
                }
                // 父级菜单②：导入方案
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "导入方案",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        KsuItem(
                            icon = Icons.Default.FileDownload,
                            title = "导入方案（ZIP）",
                            subtitle = "导入时命名文件夹；兼容 GBK 文件名压缩包",
                            onClick = onPickZip,
                        )
                        Text(
                            "也可以不用导入：用文件管理器把方案文件直接复制到 " +
                                "Documents/Oime/schemas/ 下新建的文件夹里即可。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    } }
                }
            }
            // 轮19：联网 API 配置对话框（语音输入大项）
            return@Scaffold
        }
        // ── 二级页：自定义候选键（轮19.56）──
        if (subPage == "candkeys") {
            CandidateKeyEditorPage(padding) { subPage = "keyboard" }
            return@Scaffold
        }
        // ── 二级页：手写输入（轮19.114）──
        if (subPage == "handwriting") {
            HandwritingSettingsPage(padding, refreshRev = hwRefreshRev) { subPage = "main" }
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
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KsuItem(
                                icon = OimeIcons.keyboard,
                                title = "键盘布局编辑器",
                                subtitle = "可视化编辑按键与滑动手势",
                                onClick = onOpenKeyboardEditor,
                            )
                        }
                    }
                }
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { KeyHeightSliders() } }
                }
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { FontSizeSettings() } }
                }
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { KeyAppearanceSettings() } }
                }
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { CandidateKeySettings(onOpen = { subPage = "candkeys" }) } }
                }
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { VibrationSettings() } }
                }
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { SymbolHintSettings() } }
                }
                item {
                    // 轮19.34：打字音效（总开关 + 外置文件夹 + 自定义音效 + 音量）
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            var sRev by remember { mutableStateOf(0) }
                            Text("打字音效", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.runtime.key(sRev) {
                                val km2 = com.azime.input.core.keyboard.KeyboardManager
                                var on by remember { mutableStateOf(km2.soundEnabled()) }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = on, onCheckedChange = { on = it; km2.setSoundEnabled(it); sRev++ })
                                }
                                var dir by remember { mutableStateOf(km2.soundDir()) }
                                OutlinedTextField(
                                    value = dir,
                                    onValueChange = { dir = it; km2.setSoundDir(it) },
                                    label = { Text("音效文件夹") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val files = remember(dir, sRev) { com.azime.input.core.sound.SoundManager.listFiles() }
                                if (files.isEmpty()) {
                                    Text(
                                        "该目录下没有音效文件（支持 mp3 / ogg / wav / m4a）；把文件放进目录后回到这里即可选。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Text(
                                        "选择音效（当前：${km2.soundFile().ifBlank { files.first() }}）",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    files.take(12).forEach { f ->
                                        val picked = (km2.soundFile().ifBlank { files.first() }) == f
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { km2.setSoundFile(f); sRev++ }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                (if (picked) "● " else "○ ") + f,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                            )
                                            TextButton(onClick = {
                                                com.azime.input.core.sound.SoundManager.preview(
                                                    java.io.File(dir, f).absolutePath,
                                                )
                                            }) { Text("试听") }
                                        }
                                    }
                                }
                                var vol by remember { mutableStateOf(km2.soundVolume().toFloat()) }
                                XimeSlider("音量", "${vol.toInt()}%", vol, 0f..100f) {
                                    vol = it; km2.setSoundVolume(it.toInt())
                                }
                            }
                        }
                    }
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
                // 轮19.141：**三张卡由函数内部产出** ✓ 外层不要再包 Card ✗（卡套卡 ✗）
                item { FloatingWindowSettings() }
                // 轮19.142：嵌入式**单独一个方块** ✓（与悬浮窗卡并列 ✓ 中间 12dp 分隔 ✓）
                item { FloatEmbedCard() }
            }
            return@Scaffold
        }
        // ── 二级页：O 圆环（轮19.6：形状切换 + 上滑快捷启动应用） ──
        if (subPage == "oring") {
            OringSettings(padding)
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
                item { Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { ThemeColorSettings(onUiStyleChanged = onUiStyleChanged) } } }
                item {
                    // 字体管理入口：位于主题与配色下层
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KsuItem(
                                icon = OimeIcons.font,
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
            // 轮19.83：关于页**分卡片**（原来所有条目共用一个背景 ✗）+ 日志/许可/隐私
            val km = com.azime.input.core.keyboard.KeyboardManager
            var logRev by remember { mutableStateOf(0) }   // 日志列表/开关变更后强制重组 ✓
            var showLicense by remember { mutableStateOf(false) }
            var showPrivacy by remember { mutableStateOf(false) }
            // 轮19.146：本次更新（1.0.4）说明弹窗 ✓
            var showUpdateLog by remember { mutableStateOf(false) }
            // 轮19.133：备份列表直接挂在「恢复备份」项下面 ✓（不再单开版块 ✗）
            var bkRev by remember { mutableStateOf(0) }
            var pendingRestore by remember { mutableStateOf<java.io.File?>(null) }
            // 轮19.146：本次更新说明弹窗 ✓（1.0.4 的新增/修改/修复 ✓）
            if (showUpdateLog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showUpdateLog = false },
                    title = { Text("本次更新（1.0.4）") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(UPDATE_LOG_1_0_4, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { showUpdateLog = false }) { Text("知道了") } },
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                // ① 版本信息
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KsuItem(
                                icon = OimeIcons.info,
                                title = "版本",
                                subtitle = "${appVersionName(context)}" + runCatching {
                                    @Suppress("DEPRECATION")
                                    " (vc" + context.packageManager.getPackageInfo(context.packageName, 0).versionCode + ")"
                                }.getOrDefault("") + " · 包名 com.oime.input · 平台 RIME (librime)",
                                onClick = {},
                                showChevron = false,
                            )
                            KsuItem(
                                icon = OimeIcons.info,
                                title = "本次更新",
                                subtitle = "1.0.4 · 手写输入 · 启动向导 · 微信表情退格修复（点开看全部）",
                                onClick = { showUpdateLog = true },
                                showChevron = true,
                            )
                            KsuItem(
                                icon = OimeIcons.link,
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
                        }
                    }
                }

                // ② 日志（轮19.83）
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            var verbose by remember(logRev) { mutableStateOf(km.verboseLog()) }
                            SettingSwitchRow("详细日志（记录按键/候选/光标等埋点）", verbose) { on ->
                                verbose = on
                                km.setVerboseLog(on)
                                DiagLog.setVerbose(on)
                            }
                            Text(
                                "默认只记「错误 / 警告 / 关键事件」（几乎不耗电）；" +
                                    "排查问题时再打开详细日志即可。日志只存本地，按天分文件、自动保留 7 天。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            KsuItem(
                                icon = OimeIcons.manage,
                                title = "日志目录",
                                subtitle = "Documents/Oime/logs/",
                                onClick = {},
                                showChevron = false,
                            )
                            val logFiles = remember(logRev) { DiagLog.files() }
                            if (logFiles.isEmpty()) {
                                Text(
                                    "（还没有日志文件）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                            } else {
                                logFiles.take(4).forEach { f ->
                                    Text(
                                        "· ${f.name}　${f.length() / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = {
                                    DiagLog.clearAll()
                                    Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                                    logRev++
                                }) { Text("清空日志", fontSize = 12.sp) }
                                OutlinedButton(onClick = {
                                    DiagLog.info("Diag", "manual log entry from About page")
                                    Toast.makeText(context, "已写入一条测试日志", Toast.LENGTH_SHORT).show()
                                    logRev++
                                }) { Text("写入测试日志", fontSize = 12.sp) }
                            }
                        }
                    }
                }

                // ③ 数据与备份
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KsuItem(
                                icon = Icons.Default.Backup,
                                title = "备份设置",
                                subtitle = "导出全部偏好到 Documents/Oime/backup/",
                                onClick = {
                                    scope.launch {
                                        val name = backupSettings(context)
                                        Toast.makeText(
                                            context,
                                            if (name != null) "已备份：Documents/Oime/backup/$name" else "备份失败",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                                showChevron = false,
                            )
                            KsuItem(
                                icon = Icons.Default.Restore,
                                title = "恢复备份",
                                subtitle = "点下面任一备份直接恢复；或从文件选择器挑 JSON",
                                onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                                showChevron = false,
                            )
                            // 轮19.133：**直接列出 backup 目录** ✓（用户要求：做到「恢复备份」下面 ✓）
                            run {
                                val dir = com.azime.input.core.storage.StorageManager.backupDir
                                val files = remember(bkRev, dir.path) {
                                    (dir.listFiles() ?: emptyArray())
                                        .filter { it.isFile }
                                        .sortedByDescending { it.name }
                                }
                                if (files.isEmpty()) {
                                    Text(
                                        "（Documents/Oime/backup/ 里暂无备份）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    )
                                }
                                files.forEach { f ->
                                    val kb = (f.length() + 512) / 1024
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { pendingRestore = f }
                                            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(f.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        }
                                        Text("$kb KB", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("  恢复", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                pendingRestore?.let { f ->
                                    androidx.compose.material3.AlertDialog(
                                        onDismissRequest = { pendingRestore = null },
                                        title = { Text("恢复备份？") },
                                        text = { Text("将用「${f.name}」覆盖当前设置 ✓（键盘/字体/震动/主题/向导）\n恢复后建议重启输入法 ✓") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                val n = restoreSettings(context, android.net.Uri.fromFile(f))
                                                pendingRestore = null; bkRev++
                                                Toast.makeText(
                                                    context,
                                                    if (n >= 0) "已恢复 $n 项设置，建议重启输入法" else "恢复失败",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                logRev++
                                            }) { Text("恢复") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { pendingRestore = null }) { Text("取消") }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ④ 开源许可
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        KsuItem(
                            icon = OimeIcons.code,
                            title = "开源许可",
                            subtitle = "librime · sherpa-onnx · Jetpack Compose 等",
                            onClick = { showLicense = true },
                        )
                    }
                }

                // ⑤ 隐私条约
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        KsuItem(
                            icon = OimeIcons.check,
                            title = "隐私条约",
                            subtitle = "本地优先：不采集、不上传、无遥测",
                            onClick = { showPrivacy = true },
                        )
                    }
                }

                // ⑥ 致谢
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) {
                        KsuItem(
                            icon = OimeIcons.emoji,
                            title = "致谢",
                            subtitle = "RIME / librime 社区 · sherpa-onnx · 以及所有测试反馈的朋友",
                            onClick = {},
                            showChevron = false,
                        )
                    }
                }
            }

            if (showLicense) {
                LongTextDialog("开源许可", LICENSE_TEXT) { showLicense = false }
            }
            if (showPrivacy) {
                LongTextDialog("隐私条约", PRIVACY_TEXT) { showPrivacy = false }
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
                            // 轮19.16：浅白与页面底色撞了 → 改「功能键灰」（与键盘功能键同色系）
                            colors = CardDefaults.cardColors(
                                containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                    Color(0xFF3A3A3F)
                                } else {
                                    Color(0xFFE3E5E8)
                                },
                            ),
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
                                Text(appVersionName(context), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                    Color(0xFF3A3A3F)
                                } else {
                                    Color(0xFFE3E5E8)
                                },
                            ),
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
            // 轮19.10：大项**各自独立成卡**（原来 7 个条目共用一块背景，用户反馈要分开）
            item {
                Card(colors = plainCardColors) {
                    KsuItem(
                        icon = OimeIcons.schemas,
                        title = "输入方案",
                        subtitle = "切换 / 导入 / 重命名方案",
                        onClick = { subPage = "schemas" },
                    )
                }
            }
            item {
                Card(colors = plainCardColors) {
                    KsuItem(
                        icon = OimeIcons.keyboard,
                        title = "键盘",
                        subtitle = "布局编辑 · 键高 · 字号 · 按键外观",
                        onClick = { subPage = "keyboard" },
                    )
                }
            }
            item {
                Card(colors = plainCardColors) {
                    KsuItem(
                        icon = OimeIcons.pip,   // 图标不改 ✓
                        title = "悬浮窗及嵌入式",
                        subtitle = "编码预览悬浮窗 · 嵌入模式 · 样式",
                        onClick = { subPage = "float" },
                    )
                }
            }
            // 轮19.114：手写输入入口（模型由用户自行下载 ✓，放 models/handwriting/ ✓）
            item {
                Card(colors = plainCardColors) {
                    KsuItem(
                        icon = OimeIcons.voiceHandwriting,   // 轮19.139：语音+手写混合图标 ✓（只改这一个 ✓）
                        title = "语音手写管理",
                        subtitle = "语音识别 · 离线手写（含模型下载）",
                        onClick = { subPage = "handwriting" },
                    )
                }
            }
            item {
                Card(colors = plainCardColors) {
                    KsuItem(
                        icon = OimeIcons.palette,
                        title = "主题与配色",
                        subtitle = "主题卡片 · 强调色 · 字体管理",
                        onClick = { subPage = "theme" },
                    )
                }
            }
            item {
                Card(colors = plainCardColors) {
                    KsuItem(
                        icon = OimeIcons.emoji,
                        title = "O 圆环",
                        subtitle = "圆环形状 · 上滑快捷启动应用",
                        onClick = { subPage = "oring" },
                    )
                }
            }
            item {
                Card(colors = plainCardColors) {
                    KsuItem(
                        icon = OimeIcons.info,
                        title = "关于",
                        subtitle = "版本 / 项目地址",
                        onClick = { subPage = "about" },
                    )
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
        Box(Modifier.fillMaxWidth()) {
            // 轮19.47：右缘放一个**四分之一圆环**做背景装饰；
            // 已启用 = 白色，未启用 = 黑色（用户要求）
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val r = size.height * 1.15f
                val stroke = size.height * 0.13f
                val col = if (notEnabled) Color.Black.copy(alpha = 0.30f)
                else Color.White.copy(alpha = 0.38f)
                drawCircle(
                    color = col,
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(size.width + r * 0.36f, size.height / 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // 轮19.47：去掉「○输入法」标题，余下文本放大（用户要求）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
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
                    // 轮19.15：第二行只放**运行状态**（方案挪到第三行）
                    Text(
                        text = when {
                            notEnabled -> "未启用 · 点击去启用"
                            ready -> "运行正常"
                            else -> "引擎未就绪 / 首次部署中…"
                        },
                        // 轮19.71：按用户要求**改回白色 + 放大**（19.70 的回退撤销）
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (notEnabled) Color(0xFF3C4043) else Color.White,
                    )
                }
                // 轮19.15：第三行 = 当前方案
                if (ready && schema.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "方案 · $schema",
                        // 轮19.71：同步改回 15sp + 白色
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SchemaList(onOpenManage: () -> Unit, refreshRev: Int = 0) {
    val context = LocalContext.current
    // 反馈轮13：方案组区 —— 组 → 方案 两级（参考 trime2，一次只加载一个组）
    var groups by remember { mutableStateOf(RimeManager.schemaGroups(context)) }
    var currentGroup by remember { mutableStateOf(RimeManager.currentGroupId(context)) }
    var switchingGroup by remember { mutableStateOf(false) }
    val groupScope = rememberCoroutineScope()
    var schemas by remember { mutableStateOf(RimeManager.availableSchemas()) }
    var current by remember { mutableStateOf(RimeManager.currentSchema()) }
    var refreshed by remember { mutableStateOf(false) }

    // 轮19.1：refreshRev 变化（标题栏「刷新」）时重新拉取全部数据
    LaunchedEffect(refreshRev) {
        if (refreshRev > 0) {
            groups = RimeManager.schemaGroups(context)
            currentGroup = RimeManager.currentGroupId(context)
            schemas = RimeManager.availableSchemas()
            current = RimeManager.currentSchema()
            return@LaunchedEffect
        }
    }

    LaunchedEffect(Unit) {
        if (!refreshed) {
            refreshed = true
            kotlinx.coroutines.delay(1500) // 给首次部署留出窗口
            groups = RimeManager.schemaGroups(context)
            currentGroup = RimeManager.currentGroupId(context)
            schemas = RimeManager.availableSchemas()
            current = RimeManager.currentSchema()
        }
    }

    fun switchGroup(groupId: String) {
        if (groupId == currentGroup || switchingGroup) return
        switchingGroup = true
        // 轮18.2：在线切组（destroy → 新组 init → 部署），完成后回主线程刷新
        RimeManager.switchSchemaGroupOnline(context.applicationContext, groupId) {
            switchingGroup = false
            currentGroup = RimeManager.currentGroupId(context)
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
    // 轮18：选中组「后面显示方案管理」——查看组内方案/点击切换（trime2 风格，无启用集）
    groups.firstOrNull { it.id == currentGroup }?.let { g ->
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
                    "组内 ${g.schemaIds.size} 个方案，点此查看与切换",
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

    // ── 当前组方案（轮18 trime2 风格：librime 实际识别的方案，点击切换）──
    Text(
        "当前组方案（点击切换）",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    when {
        schemas.isEmpty() -> Text(
            "暂无已部署方案 —— 等待引擎部署完成，或切换方案组",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        else -> schemas.forEach { id ->
            val selected = id == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        current = id
                        RimeManager.switchSchema(id)
                        // 记录组内上次使用的方案（重启后回落）
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
/**
 * 方案管理页（轮18 trime2 架构）：方案组切换 + 当前组方案列表。
 * 组目录即 librime user 数据目录，组自带 default.custom.yaml 由 librime 自动 patch，
 * App 不碰 yaml、无「启用集」概念。方案列表 = librime 部署成功的方案（availableSchemas）。
 */
@Composable
private fun SchemaManagePage(refreshRev: Int = 0) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf(RimeManager.schemaGroups(context)) }
    var currentGroup by remember { mutableStateOf(RimeManager.currentGroupId(context)) }
    var available by remember { mutableStateOf(RimeManager.availableSchemas()) }
    var current by remember { mutableStateOf(RimeManager.currentSchema()) }
    // 轮18.2：勾选启用集 = 组目录 default.custom.yaml 的 schema_list
    var enabledIds by remember(currentGroup) {
        mutableStateOf(RimeManager.groupEnabledSchemas(context, currentGroup))
    }
    var switching by remember { mutableStateOf(false) }
    var refreshed by remember { mutableStateOf(false) }

    // 轮19.1：refreshRev 变化（标题栏「刷新」）时重新拉取组与启用集
    LaunchedEffect(refreshRev) {
        if (refreshRev > 0) {
            groups = RimeManager.schemaGroups(context)
            currentGroup = RimeManager.currentGroupId(context)
            available = RimeManager.availableSchemas()
            current = RimeManager.currentSchema()
            enabledIds = RimeManager.groupEnabledSchemas(context, currentGroup)
        }
    }

    LaunchedEffect(Unit) {
        if (!refreshed) {
            refreshed = true
            kotlinx.coroutines.delay(1500) // 给部署留出窗口
            available = RimeManager.availableSchemas()
            current = RimeManager.currentSchema()
            enabledIds = RimeManager.groupEnabledSchemas(context, currentGroup)
        }
    }

    fun applyEnabled(ids: List<String>) {
        if (ids.isEmpty()) return // 至少保留一个方案
        switching = true
        enabledIds = ids
        RimeManager.setGroupEnabledSchemas(context.applicationContext, currentGroup, ids) { ok ->
            switching = false
            if (ok) {
                available = RimeManager.availableSchemas()
                current = RimeManager.currentSchema()
            }
        }
    }

    // 轮19：去掉页内方案组区块（切组在上一级「输入方案」页），只保留组内方案；
    // LazyColumn 支持滚动（组内方案可达 20+，原 Column 固定不可滚）。
    val group = groups.firstOrNull { it.id == currentGroup }
    val allIds = group?.schemaIds ?: emptyList()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("组内方案", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "勾选启用 · 点选使用（当前组：$currentGroup）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (switching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (allIds.isEmpty()) {
            item {
                Text(
                    "暂无方案 —— 等待引擎部署完成，或在该组目录中放入 .schema.yaml",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        } else {
            items(allIds.size) { idx ->
                val id = allIds[idx]
                val inList = id in enabledIds
                val inUse = id == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (id in available) {
                                current = id
                                RimeManager.switchSchema(id)
                                runCatching { RimeManager.recordGroupSchema(context.applicationContext, id) }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = inList,
                        onCheckedChange = { on ->
                            val next = if (on) enabledIds + id else enabledIds - id
                            if (next.isNotEmpty()) applyEnabled(next)
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        // 轮18.2：显示 schema.yaml 的 name 字段，不再显示文件名
                        Text(
                            RimeManager.schemaDisplayName(id),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (inUse) {
                        Text(
                            "使用中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyHeightSliders() {
    var keyH by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.keyHeightDp().toFloat()) }
    var barH by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.barHeightDp().toFloat()) }
    var barOn by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.barEnabled()) }
    // 轮19.8：工具栏（候选行 / 工具图标行）高度可微调
    var toolH by remember { mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.toolbarHeightDp().toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        XimeSlider("键高", "${keyH.toInt()}dp", keyH, 36f..64f) {
            keyH = it
            com.azime.input.core.keyboard.KeyboardManager.setKeyHeightDp(it.toInt())
        }
        XimeSlider("工具栏高度", "${toolH.toInt()}dp", toolH, 32f..72f) {
            toolH = it
            com.azime.input.core.keyboard.KeyboardManager.setToolbarHeightDp(it.toInt())
        }
        // 轮19.30：按输入框类型自动切页（登录输账号 → 九宫格；密码/邮箱/网址 → 英文）
        var autoPage by remember {
            mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.autoPageByInput())
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "输入框类型自动切页",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = autoPage,
                onCheckedChange = {
                    autoPage = it
                    com.azime.input.core.keyboard.KeyboardManager.setAutoPageByInput(it)
                },
            )
        }
        Text(
            "数字/电话框自动切九宫格；密码、邮箱、网址框自动切英文；普通文本框回主键盘。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "工具栏 = 候选/输入码区 + ○ 菜单键与工具图标所在的那一条；改后自动重建键盘生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
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
        // 空格键显示文本（轮19.5）：留空 = 显示当前方案「名称」（schema.yaml 的 name 字段）
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
            placeholder = { Text("留空显示当前方案名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        // 轮19.30：空格自定义文本的水平位置（原来自定义文本只能居中）
        var spaceOff by remember {
            mutableStateOf(
                com.azime.input.core.keyboard.KeyboardManager.spaceLabelOffsetDp().toFloat(),
            )
        }
        XimeSlider("文本位置", "${spaceOff.toInt()}dp", spaceOff, -80f..80f) {
            spaceOff = it
            com.azime.input.core.keyboard.KeyboardManager.setSpaceLabelOffsetDp(it.toInt())
        }
        Text(
            "有文字 → 空格键显示该文字；只打空格 → 只显示空格图标；留空 → 显示当前方案名称",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
        // 轮19.52：键盘左右边距（曲面屏把键盘往中间收）
        var sideMargin by remember {
            mutableStateOf(com.azime.input.core.keyboard.KeyboardManager.keyboardSideMarginDp().toFloat())
        }
        XimeSlider("左右边距", "${sideMargin.toInt()}dp", sideMargin, 0f..48f) {
            sideMargin = it
            com.azime.input.core.keyboard.KeyboardManager.setKeyboardSideMarginDp(it.toInt())
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                com.azime.input.core.keyboard.KeyboardManager.resetKeyboardGeometry()
                // 回读一次，让滑杆跟着回到默认值
                corner = com.azime.input.core.keyboard.KeyboardManager.keyCornerDp().toFloat()
                rowGap = com.azime.input.core.keyboard.KeyboardManager.rowGapDp().toFloat()
                colGap = com.azime.input.core.keyboard.KeyboardManager.colGapDp().toFloat()
                sideMargin = com.azime.input.core.keyboard.KeyboardManager.keyboardSideMarginDp().toFloat()
                android.widget.Toast.makeText(context, "已恢复键盘默认外观", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("恢复默认") }
        Text(
            "恢复内容：键高、工具栏高度、键盘/工具栏字号、按键圆角、行距、列距、键盘左右边距、" +
                "四向与长按提示位置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "下次键盘弹出即生效（左右边距用于曲面屏，把键盘两侧往里收）。",
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember { mutableStateOf(km.floatEnabled()) }
    var mode by remember { mutableStateOf(km.floatMode()) }
    var x by remember { mutableStateOf(km.floatXDp().toFloat()) }
    var y by remember { mutableStateOf(km.floatYDp().toFloat()) }
    var textSp by remember { mutableStateOf(km.floatTextSp().toFloat()) }
    var alpha by remember { mutableStateOf(km.floatBgAlpha().toFloat()) }
    val custom = mode == "custom"
    // 轮19.141 ★ 崩溃修复：**外层 LazyColumn 本身就是滚动容器**（见调用处 item ✓）
    //   ⇒ 这里再套 `verticalScroll()` 会抛
    //   `IllegalStateException: Vertically scrollable component was measured with an
    //    infinity maximum height constraints` ✗（真机日志铁证 ✓）
    //   · `fillMaxSize()` 在 LazyColumn item 里同样拿到无限高度 ✗
    //   · 横向 padding 由外层提供（horizontal = 16.dp ✓）⇒ 此处**不能重复加** ✗
    //   ⇒ 这里只负责把三张卡竖排 + 12dp 分隔 ✓
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Card(colors = grayCardColors(), shape = settingsCardShape()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("悬浮窗", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        SettingSwitchRow("启用悬浮窗（输入时显示编码）", enabled) {
            enabled = it
            km.setFloatEnabled(it)
        }
        // 轮19.142：开关与样式**同属一张卡** ✓（用户要求 ✓）中间用细分隔线分区 ✓
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.height(12.dp))
        Text("样式", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OimeChip(
                selected = !custom,
                onClick = { mode = "default"; km.setFloatMode("default") },
                label = { Text("默认悬浮窗") },
            )
            OimeChip(
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
        Spacer(Modifier.height(8.dp))
        // 轮19.34：悬浮窗增强——背景色 / 首选强调 / 候选数量 / 排列方向
        var floatRev by remember { mutableStateOf(0) }
        androidx.compose.runtime.key(floatRev) {
            var firstAccent by remember { mutableStateOf(km.floatFirstAccent()) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("首选候选加强调底色", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = firstAccent, onCheckedChange = {
                    firstAccent = it; km.setFloatFirstAccent(it); floatRev++
                })
            }
            var candCount by remember { mutableStateOf(km.floatCandCount().toFloat()) }
            XimeSlider("候选数量", "${candCount.toInt()}", candCount, 1f..9f) {
                candCount = it; km.setFloatCandCount(it.toInt())
            }
            var orient by remember { mutableStateOf(km.floatOrientation()) }
            Text("候选排列", style = MaterialTheme.typography.bodyMedium)
            // 轮19.72：补 fillMaxWidth —— 原来没宽度约束，chips（weight 子项）可能量成 0 宽而不显示 ✗
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("h" to "横向", "v" to "竖向").forEach { (id, label) ->
                    val on = orient == id
                    // 轮19.134：**统一成 OimeChip** ✓（原来是自绘 Box + 描边 ✗ ⇒ 与其它选项不一致 ✗ 用户多次反馈）
                    OimeChip(
                        selected = on,
                        onClick = { orient = id; km.setFloatOrientation(id); floatRev++ },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 轮19.73：**补回丢失的 `}`** —— 19.71 删重复块时把 Row 的闭合括号一起删了 ✗
            // ⇒ 后面的「竖向反向」开关被并进同一个 Row（它内部 fillMaxWidth）⇒ 把两个 chips 挤成 0 宽 ✗

            // 轮19.55：竖向显示时可切换正向/反向（反向 = 第 1 个候选在最下）
            var vReverse by remember { mutableStateOf(km.floatVerticalReverse()) }
            SettingSwitchRow("竖向反向显示（1 号在最下）", vReverse) { on ->
                vReverse = on
                km.setFloatVerticalReverse(on)
            }
            var showFloatColorPick by remember { mutableStateOf(false) }
            // 轮19.72：入口做明显（与其它设置行同规格：标题 + 右侧色块/箭头）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    )
                    .border(
                        1.dp, MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    )
                    .clickable { showFloatColorPick = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    com.azime.input.ui.icons.OimeIcons.palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "悬浮窗背景色",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (km.floatBgColor() != 0) "自定义" else "跟随主题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (km.floatBgColor() != 0) Color(km.floatBgColor())
                            else MaterialTheme.colorScheme.surfaceVariant,
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        ),
                )
            }
            if (showFloatColorPick) {
                ColorPickerDialog(
                    title = "悬浮窗背景色",
                    initial = if (km.floatBgColor() != 0) km.floatBgColor() else 0xFF26282A.toInt(),
                    onDismiss = { showFloatColorPick = false },
                    onConfirm = { v -> km.setFloatBgColor(v); showFloatColorPick = false; floatRev++ },
                )
            }
            TextButton(onClick = { km.setFloatBgColor(0); floatRev++ }) { Text("背景色恢复跟随主题") }
        }
        }
      }
    }
}

/**
 * 嵌入式设置（轮19.142：**独立成单独一张卡** ✓ 用户要求「嵌入式自己一个方块」✓）。
 * 与「悬浮窗」卡并列，卡片间由外层 LazyColumn 的 12dp 分隔隔开 ✓
 */
@Composable
private fun FloatEmbedCard() {
    val km = com.azime.input.core.keyboard.KeyboardManager
    Card(colors = grayCardColors(), shape = settingsCardShape()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            var embedMode by remember { mutableStateOf(km.embedMode()) }
            Text("嵌入式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "输入码 / 首选字 显示在文本输入框，候选留在工具栏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OimeChip(
                    selected = embedMode == "off",
                    onClick = { embedMode = "off"; km.setEmbedMode("off") },
                    label = { Text("关闭") },
                )
                OimeChip(
                    selected = embedMode == "code",
                    onClick = { embedMode = "code"; km.setEmbedMode("code") },
                    label = { Text("嵌入输入码") },
                )
                OimeChip(
                    selected = embedMode == "top",
                    onClick = { embedMode = "top"; km.setEmbedMode("top") },
                    label = { Text("嵌入首选") },
                )
            }
        }
    }
}

/** 打字振动设置：总开关 / 按下 / 抬起 / 系统或自定义模式（自定义时长滑杆）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VibrationSettings() {
    var enabled by remember { mutableStateOf(HapticsManager.enabled()) }
    var pressOn by remember { mutableStateOf(HapticsManager.pressEnabled()) }
    var releaseOn by remember { mutableStateOf(HapticsManager.releaseEnabled()) }
    var mode by remember { mutableStateOf(HapticsManager.mode()) }
    val custom = mode == HapticsManager.MODE_CUSTOM
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OimeChip(
                selected = mode == HapticsManager.MODE_KEYBOARD,
                onClick = { mode = HapticsManager.MODE_KEYBOARD; HapticsManager.setMode(HapticsManager.MODE_KEYBOARD) },
                label = { Text("系统键盘触感") },
            )
            OimeChip(
                selected = mode == HapticsManager.MODE_SYSTEM,
                onClick = { mode = HapticsManager.MODE_SYSTEM; HapticsManager.setMode(HapticsManager.MODE_SYSTEM) },
                label = { Text("系统轻点") },
            )
            OimeChip(
                selected = custom,
                onClick = { mode = HapticsManager.MODE_CUSTOM; HapticsManager.setMode(HapticsManager.MODE_CUSTOM) },
                label = { Text("自定义") },
            )
        }
        if (custom) {
            Spacer(Modifier.height(4.dp))
            // 轮19.1：震动滑杆统一为 XimeSlider（原用原生 Slider，与其它设置项 UI 不一致）
            XimeSlider("自定义时长", "${ms.toInt()}ms", ms, 5f..60f) {
                ms = it
                HapticsManager.setCustomMs(it.toInt())
            }
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
private fun ThemeColorSettings(onUiStyleChanged: () -> Unit = {}) {
    val km = com.azime.input.core.theme.KeyboardTheme
    var mode by remember { mutableStateOf(km.mode()) }
    var accentRev by remember { mutableStateOf(0) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("色彩模式", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OimeChip(
                selected = mode == km.MODE_SYSTEM,
                onClick = { mode = km.MODE_SYSTEM; km.setMode(km.MODE_SYSTEM) },
                label = { Text("跟随系统") },
            )
            OimeChip(
                selected = mode == km.MODE_LIGHT,
                onClick = { mode = km.MODE_LIGHT; km.setMode(km.MODE_LIGHT) },
                label = { Text("亮色") },
            )
            OimeChip(
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
        // 轮19.31：界面风格（Material / Miuix）——键盘与设置页一起变
        var uiStyle by remember { mutableStateOf(km.uiStyle()) }
        var accentPure by remember { mutableStateOf(km.accentPure()) }
        var styleRev by remember { mutableStateOf(0) }
        androidx.compose.runtime.key(styleRev) {
            // 轮19.42：风格改为**下拉选择**（原 3 列平铺改掉）
            Text("界面风格", style = MaterialTheme.typography.bodyMedium)
            var styleMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        )
                        .clickable { styleMenu = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        com.azime.input.core.theme.UiStyle.from(uiStyle).label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾", style = MaterialTheme.typography.bodyMedium)
                }
                DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                    com.azime.input.core.theme.UiStyles.selectable.forEach { st ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    st.label + if (st.id == uiStyle) "   ✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                uiStyle = st.id
                                km.setUiStyle(st.id)
                                styleMenu = false
                                styleRev++
                                onUiStyleChanged()   // 轮19.71：立刻让设置页主题重算 ✓
                            },
                        )
                    }
                }
            }
            Text(
                "风格只改配色/边框/字重；键高、行距、圆角仍按你自己的设置。" +
                    "（Material You 在 Android 12+ 会从壁纸取色）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var geoFollow by remember { mutableStateOf(km.geometryFollowsStyle()) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("几何也跟随风格", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "开启后键面圆角用该风格的建议值（会覆盖你的圆角设置），关闭则始终用你自己的。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = geoFollow,
                    onCheckedChange = { geoFollow = it; km.setGeometryFollowsStyle(it); styleRev++ },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("强调色用原色", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "关闭时强调色会与键底做半透明混合（观感柔和，但颜色会被冲淡）；" +
                            "开启后严格按你选的颜色呈现。自定义配色开启时自动按原色。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = accentPure,
                    onCheckedChange = {
                        accentPure = it
                        km.setAccentPure(it)
                        styleRev++
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
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

        // 轮19.30：自定义配色改为**亮/暗两套 + 分组 + 调色板弹窗**（原 RGB 滑杆整段替换）
        if (showCustom) {
            key(accentRev) {
                ThemeColorGroups()
            }
        }
        Text(
            "主题卡片即时预览，点击应用；配色应用于键盘强调色（回车键、候选高亮等）与设置页主色，下次键盘弹出生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 轮19.30：亮色 / 暗色两套自定义配色编辑器。
 * 分组：字母键（26 字母 + 逗号句号 · 共享 keyBg）/ 功能键（Shift·符号·退格 · 共享 funcKeyBg）/ 强调键（回车高亮）。
 */
@Composable
private fun ThemeColorGroups() {
    val km = com.azime.input.core.theme.KeyboardTheme
    var rev by remember { mutableStateOf(0) }
    var pick by remember {
        mutableStateOf<Triple<String, Int, (Int) -> Unit>?>(null)
    }
    androidx.compose.runtime.key(rev) {
        Text("自定义配色", style = MaterialTheme.typography.bodyMedium)
        ColorGroupSection("亮色自定义", dark = false, onPick = { pick = it }, onChanged = { rev++ })
        Spacer(Modifier.height(8.dp))
        ColorGroupSection("暗色自定义", dark = true, onPick = { pick = it }, onChanged = { rev++ })
        Spacer(Modifier.height(4.dp))
        Text(
            "字母键 = 26 个字母键 + 逗号 + 句号（共享一色）；功能键 = Shift / 符号 / 退格（共享一色）；" +
                "强调键 = 回车与候选高亮。支持十六进制与透明度；下次键盘弹出生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    pick?.let { (title, initial, setter) ->
        ColorPickerDialog(
            title = title,
            initial = initial,
            onDismiss = { pick = null },
            onConfirm = { setter(it); pick = null; rev++ },
        )
    }
}

@Composable
private fun ColorGroupSection(
    title: String,
    dark: Boolean,
    onPick: (Triple<String, Int, (Int) -> Unit>) -> Unit,
    onChanged: () -> Unit,
) {
    val km = com.azime.input.core.theme.KeyboardTheme
    var on by remember { mutableStateOf(if (dark) km.customDarkOn() else km.customLightOn()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = {
                on = it
                if (dark) km.setCustomDarkOn(it) else km.setCustomLightOn(it)
                onChanged()
            },
        )
    }
    if (!on) return
    val keyBg = km.keyBgColor(dark)
    val funcBg = km.funcBgColor(dark)
    val accent = if (dark) km.accentDark() else km.accentLight()
    ColorRow("字母键（26 字母 + 逗号句号）", keyBg) {
        onPick(Triple("字母键配色", keyBg) { v -> km.setKeyBgColor(dark, v) })
    }
    ColorRow("功能键（Shift / 符号 / 退格）", funcBg) {
        onPick(Triple("功能键配色", funcBg) { v -> km.setFuncBgColor(dark, v) })
    }
    ColorRow("强调键（回车 / 高亮）", accent) {
        onPick(Triple("强调键配色", accent) { v ->
            if (dark) km.setAccents(km.accentLight(), v) else km.setAccents(v, km.accentDark())
        })
    }
    TextButton(onClick = { km.resetColors(dark); onChanged() }) { Text("恢复默认") }
}

@Composable
private fun ColorRow(label: String, color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Color(color), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "#" + String.format("%08X", color),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 轮19.30：调色板 + 十六进制 + 透明度 的取色弹窗。 */
@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var argb by remember { mutableStateOf(initial) }
    var hex by remember { mutableStateOf(String.format("%08X", initial)) }
    val palette = listOf(
        0xFFFFFFFF, 0xFFF1F1F2, 0xFFD3D7DC, 0xFF9AA0A6, 0xFF5F6368, 0xFF3C4043, 0xFF26282A, 0xFF000000,
        0xFF1A73E8, 0xFF4285F4, 0xFF8AB4F8, 0xFF34A853, 0xFF97C459, 0xFFFBBC04, 0xFFF9AB00, 0xFFEA4335,
        0xFFE57373, 0xFFEF9F27, 0xFFBA7517, 0xFF7F77DD, 0xFFD4537E, 0xFF1D9E75, 0xFF0F6E56, 0xFF042C53,
    ).map { it.toInt() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(Color(argb), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(8.dp))
                palette.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color(c), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .clickable {
                                        argb = (argb and 0xFF000000.toInt()) or (c and 0x00FFFFFF)
                                        hex = String.format("%08X", argb)
                                    },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { txt ->
                        hex = txt.filter { it.isLetterOrDigit() }.take(8).uppercase()
                        if (hex.length == 8) {
                            hex.toLongOrNull(16)?.let { argb = it.toInt() }
                        }
                    },
                    label = { Text("十六进制（AARRGGBB，前两位是透明度）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                // 轮19.32：滑杆改用设置内同款 XimeSlider（原来用的 Material Slider，样式不一致）
                XimeSlider(
                    title = "透明度",
                    valueText = "${((argb ushr 24) and 0xFF) * 100 / 255}%",
                    value = ((argb ushr 24) and 0xFF) / 255f,
                    range = 0f..1f,
                ) { f ->
                    val a = (f * 255).toInt().coerceIn(0, 255)
                    argb = (argb and 0x00FFFFFF) or (a shl 24)
                    hex = String.format("%08X", argb)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(argb) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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

/** 备份全部偏好为 JSON，写入 Documents/Oime/backup/。返回文件名，失败返回 null。 */
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
    // 轮19.55：改存到外置目录 Documents/Oime/backup/（原来写系统 Download，用户要求集中管理）
    val dir = com.azime.input.core.storage.StorageManager.backupDir
    dir.mkdirs()
    java.io.File(dir, fileName).writeText(root.toString(), Charsets.UTF_8)
    fileName
}.getOrNull()

/** 轮19.21：从备份 JSON 恢复偏好（覆盖式写回；类型按 JSON 值推断）。返回恢复项数，失败 -1。 */
private fun restoreSettings(context: android.content.Context, uri: android.net.Uri): Int = runCatching {
    val text = context.contentResolver.openInputStream(uri)?.use {
        it.readBytes().toString(Charsets.UTF_8)
    } ?: return -1
    val root = org.json.JSONObject(text)
    var count = 0
    for (name in root.keys()) {
        val obj = root.optJSONObject(name) ?: continue
        val p = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        val ed = p.edit()
        for (key in obj.keys()) {
            when (val v = obj.get(key)) {
                is Boolean -> ed.putBoolean(key, v)
                is Int -> ed.putInt(key, v)
                is Long -> ed.putLong(key, v)
                is Double -> {
                    // JSON 数字统一是 Double：无小数位且落在 Int 范围时按 Int 存（滑杆/枚举都是 Int）
                    if (v == v.toInt().toDouble()) ed.putInt(key, v.toInt()) else ed.putFloat(key, v.toFloat())
                }
                is String -> ed.putString(key, v)
                else -> Unit
            }
            count++
        }
        ed.apply()
    }
    count
}.getOrDefault(-1)

/**
 * 轮19.33：卡片形状**不再跟随风格**（风格只改配色）。
 * 保持固定 12dp，避免"一切风格版式就变"的观感断裂。
 */
@Composable
private fun settingsCardShape(): androidx.compose.foundation.shape.RoundedCornerShape =
    androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

/**
 * 轮19.26：设置页统一卡片底色（浅灰）——主页面与**所有二级页**都用它，
 * 不再依赖 M3 默认的中性色（默认偏紫）。
 */
@Composable
private fun grayCardColors(): androidx.compose.material3.CardColors {
    // 轮19.31：Miuix 风格用纯白卡（浅色）/ 深灰卡（暗色）；Material 保持浅灰
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val miuix = com.azime.input.core.theme.KeyboardTheme.isMiuix()
    val bg = when {
        miuix && dark -> Color(0xFF2C2C2E)
        miuix -> Color(0xFFFFFFFF)
        dark -> Color(0xFF26262A)
        else -> Color(0xFFF1F1F2)
    }
    return androidx.compose.material3.CardDefaults.cardColors(containerColor = bg)
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

/** 轮19：联网 API 配置对话框（OpenAI 兼容 /v1/audio/transcriptions）。 */
@Composable
private fun WebApiConfigDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("speech_prefs", android.content.Context.MODE_PRIVATE) }
    val cfg = remember { com.azime.input.core.speech.SpeechEngineManager.webApiConfig(context) }
    var url by remember { mutableStateOf(prefs.getString("web_api_url", "") ?: "") }
    var key by remember { mutableStateOf(prefs.getString("web_api_key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("web_api_model", "whisper-1") ?: "whisper-1") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("联网 API 配置", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "OpenAI 兼容接口（POST {baseUrl}/audio/transcriptions）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("模型") },
                    placeholder = { Text("whisper-1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            prefs.edit()
                                .putString("web_api_url", url.trim())
                                .putString("web_api_key", key.trim())
                                .putString("web_api_model", model.trim().ifEmpty { "whisper-1" })
                                .apply()
                            // 配置完成直接切换到联网引擎
                            prefs.edit().putString("engine", com.azime.input.core.speech.SpeechEngineManager.ENGINE_WEB_API).apply()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = url.isNotBlank() && key.isNotBlank(),
                    ) {
                        Text("保存并启用")
                    }
                }
            }
        }
    }
}

/**
 * 轮19.6：O 圆环设置页——形状三选一 + 上滑快捷启动的 5 个应用槽位。
 */
@Composable
private fun OringSettings(padding: androidx.compose.foundation.layout.PaddingValues) {
    val context = LocalContext.current
    val km = com.azime.input.core.keyboard.KeyboardManager
    var shape by remember { mutableStateOf(km.ringShape()) }
    var slots by remember { mutableStateOf(km.ringApps()) }
    var pickSlot by remember { mutableStateOf(-1) }
    val apps = remember { com.azime.input.core.apps.AppLauncher.installedApps(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Card(colors = grayCardColors(), shape = settingsCardShape()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "圆环形状",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    listOf(
                        km.RING_SHAPE_RING to ("圆形环" to "默认，白色呼吸圆环"),
                        km.RING_SHAPE_SQUARE to ("圆角方形环" to "方形描边，硬朗"),
                        km.RING_SHAPE_EYE to ("眼睛" to "环内双眼：随机眨眼 · 左右看"),
                    ).forEach { (value, pair) ->
                        val (name, desc) = pair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shape = value
                                    km.setRingShape(value)
                                }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = shape == value, onClick = {
                                shape = value
                                km.setRingShape(value)
                            })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = grayCardColors(), shape = settingsCardShape()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "上滑快捷应用（5 个槽位）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Text(
                        "在键盘上从 ○ 圆环向上滑 → 弧上出现 5 个图标 → 滑动选择、松手打开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    for (i in 0 until km.RING_APP_SLOTS) {
                        val pkg = slots.getOrElse(i) { "" }
                        KsuItem(
                            icon = OimeIcons.emoji,
                            title = "槽位 ${i + 1}：" + (
                                if (pkg.isBlank()) "未设置"
                                else com.azime.input.core.apps.AppLauncher.label(context, pkg)
                                ),
                            subtitle = if (pkg.isBlank()) "点击选择应用（共 ${apps.size} 个可启动应用）" else pkg,
                            onClick = { pickSlot = i },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "提示：读取应用列表需要 QUERY_ALL_PACKAGES 权限（已在首次向导中说明）。" +
                    "未设置槽位时该位置显示「＋」占位。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickSlot >= 0) {
        AppPickerDialog(
            apps = apps,
            onPick = { pkg ->
                km.setRingApp(pickSlot, pkg)
                slots = km.ringApps()
                pickSlot = -1
            },
            onClear = {
                km.setRingApp(pickSlot, "")
                slots = km.ringApps()
                pickSlot = -1
            },
            onDismiss = { pickSlot = -1 },
        )
    }
}

/** 应用选择对话框：图标 + 名称列表（含搜索框 + 清除槽位）。 */
@Composable
private fun AppPickerDialog(
    apps: List<com.azime.input.core.apps.AppLauncher.AppInfo>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, true) }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("选择应用", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered.size) { idx ->
                        val a = filtered[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(a.pkg) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 轮19.7：图标按需加载（列表只带包名/名称，避免一次性解码上百个图标）
                            val ic = remember(a.pkg) {
                                com.azime.input.core.apps.AppLauncher.icon(context, a.pkg)
                            }
                            if (ic != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = ic,
                                    contentDescription = a.label,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else {
                                Icon(OimeIcons.emoji, contentDescription = null, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text("清除槽位")
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                }
            }
        }
    }
}


/**
 * 候选快捷键入口卡片（轮19.61）：改到**子级菜单**里设置（参照用户建议）。
 * 子页里同时有第二 / 第三候选与自定义 code，**保存时写入 RIME 配置并热重载部署**。
 */
@Composable
private fun CandidateKeySettings(onOpen: () -> Unit) {
    val km = com.azime.input.core.keyboard.KeyboardManager
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("候选快捷键", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "当前：第二候选 = ${nameOfCandidateKey(km.candidateKey2())}，" +
                "第三候选 = ${nameOfCandidateKey(km.candidateKey3())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // 轮19.63：入口做得更明显（原来只是一行小字）——强调色描边按钮 + 图标 + 箭头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary,
                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                )
                .clickable { onOpen() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                com.azime.input.ui.icons.OimeIcons.byName("settings") ?: Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "设置候选快捷键",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "▸",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun nameOfCandidateKey(code: String): String = when (code.lowercase()) {
    "" -> "无"
    "." -> "句号"
    "," -> "逗号"
    "shift" -> "Shift"
    "symbols" -> "符号键"
    else -> "自定义（$code）"
}

/** 自定义候选键子页面（轮19.56）：分别填第二/第三候选的触发 code，保存后回到上一级下拉栏选择。 */
@Composable
private fun CandidateKeyEditorPage(padding: androidx.compose.foundation.layout.PaddingValues, onDone: () -> Unit) {
    val km = com.azime.input.core.keyboard.KeyboardManager
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var k2 by remember { mutableStateOf(km.candidateKey2()) }
    var k3 by remember { mutableStateOf(km.candidateKey3()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("自定义候选键", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "填按键的 code：单个字符直接写（如 . , ; /），功能键写 shift / symbols。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = k2, onValueChange = { k2 = it },
            label = { Text("第二候选键 code") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = k3, onValueChange = { k3 = it },
            label = { Text("第三候选键 code") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { k2 = ""; k3 = "" }) { Text("清空（都设为无）") }
            Button(onClick = {
                km.setCandidateKey2(k2)
                km.setCandidateKey3(k3)
                // 轮19.62：直接生效——组合中的候选映射每次渲染都会读设置（无需重载）
                android.widget.Toast.makeText(context, "已保存（组合中按该键即选候选）", android.widget.Toast.LENGTH_SHORT).show()
                onDone()
            }) { Text("保存") }
        }
    }
}


/** 轮19.82：语音输入使用说明（弹窗用，写得比原来底部那段更细）。 */
/**
 * 轮19.129：**语音手写管理**（版式对齐「输入方案」页 ✓ 用户要求）
 * · 用**背景色分块**（Card + grayCardColors ✓）· 选中项用**边框**（非整块强调色 ✓）
 * · **「选择模型」与「下载模型」分开**两部分 ✓ · **刷新**放在大标题后面 ✓
 */


/**
 * 轮19.135：**统一的可选按钮 = Material3 原生 `FilterChip` 默认样式** ✓
 * 用户提供了旧截图作基准 ✓：未选中 = 浅底 + **灰色描边** ✓；选中 = **灰底、无蓝框** ✓
 * ⇒ 结论：**不要做任何自定义** ✗（我前几轮加的主色描边/填充色都不是它 ✗）
 * ⇒ 这里只是一个**透传包装** ✓（保留"改一处、全项目 15 处生效"的好处 ✓）
 */
@Composable
private fun OimeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = label,
    )
}

@Composable
private fun HandwritingSettingsPage(
    padding: androidx.compose.foundation.layout.PaddingValues,
    refreshRev: Int,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelsDir = com.azime.input.core.storage.StorageManager.modelsDir
    val scope = rememberCoroutineScope()
    val speechPrefs = remember {
        context.getSharedPreferences("speech_prefs", android.content.Context.MODE_PRIVATE)
    }
    var engine by remember { mutableStateOf(speechPrefs.getString("engine", "sense_voice") ?: "sense_voice") }
    var refresh by remember { mutableStateOf(refreshRev) }
    var micOk by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micOk = it }
    // 轮19.145：联网下载两个开关 ✓（与首次向导共用 wizard_prefs ✓）
    var netAllow by remember { mutableStateOf(com.azime.input.core.net.NetPrefs.allowDownload(context)) }
    var netWifiOnly by remember { mutableStateOf(com.azime.input.core.net.NetPrefs.wifiOnly(context)) }
    // 当前网络类型（随 refresh 重读 ⇒ 用户切了 Wi-Fi 回来即刷新 ✓）
    val netNow = remember(refresh) { com.azime.input.core.net.netState(context) }
    var showVoiceHelp by remember { mutableStateOf(false) }
    var showHwHelp by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    // 轮19.145：联网下载闸门 ✓（移动网络下的待确认动作 ✓）
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    var busyId by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("") }
    var pct by remember { mutableStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    // 轮19.136：按模型 id 存提示 ✓（显示在该模型行下方 ✓）
    val msgFor = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }

    androidx.compose.runtime.LaunchedEffect(refreshRev) { refresh = refreshRev + 1 }
    val senseOk = remember(refresh) { com.azime.input.core.speech.SpeechEngineManager.hasSenseVoiceModel() }
    val zipOk = remember(refresh) { com.azime.input.core.speech.SpeechEngineManager.hasZipformerModel() }
    val apiCfg = remember(refresh) { com.azime.input.core.speech.SpeechEngineManager.webApiConfig(context) }
    val hwOk = remember(refresh) { com.azime.input.core.handwriting.HandwritingEngine.isModelPresent() }
    val accent = MaterialTheme.colorScheme.primary

    /**
     * 轮19.145：**联网下载闸门** ✓
     *
     * 背景（用户反馈"启动界面没有联网权限的索取界面，直接就可以联网下模型了"）：
     * Android 的 `INTERNET` 是 normal 权限 ⇒ 装机即授予 ✓ 系统没有运行时弹窗 ✗
     * ⇒ 只能由 App 自己给出「知情 + 选择」：
     *  · 关了「允许联网下载」⇒ 直接拦下并说明去哪儿打开 ✓
     *  · 开着「仅 Wi-Fi」但当前是**移动网络** ⇒ 弹框问一句 ✓（模型 200~240MB ✗ 别偷偷跑流量 ✗）
     */
    fun gatedDownload(msgKey: String, start: () -> Unit) {
        // ⚠️ 不能用 `val net = com.azime.input.core.net` ✗（Kotlin 不许把**包名**当值用 ✗
        //    编译报 "Expression expected, but a package name found" ✗）⇒ 一律全限定名 ✓
        when {
            !com.azime.input.core.net.NetPrefs.allowDownload(context) -> {
                msgFor[msgKey] = "⚠️ " + com.azime.input.core.net.downloadBlockReason(context)
            }
            com.azime.input.core.net.NetPrefs.wifiOnly(context) &&
                com.azime.input.core.net.netState(context) == com.azime.input.core.net.NetState.MOBILE -> {
                pendingDownload = start
            }
            else -> start()
        }
    }

    fun startVoiceDownload(model: com.azime.input.core.handwriting.ModelDownloader.VoiceModel) {
        gatedDownload(model.id) {
            // 轮19.137：**交给全局下载器** ✓（页面销毁不中断 ✓ 断点续传 ✓）
            com.azime.input.core.handwriting.ModelDownloader.launchDownload(model.id) {
                val st = com.azime.input.core.handwriting.ModelDownloader.stateOf(model.id)
                val r = com.azime.input.core.handwriting.ModelDownloader.installVoiceModel(
                    model, modelsDir,
                    onProgress = { d, tt ->
                        st.pct.value = if (tt > 0) ((d * 100) / tt).toInt() else -1
                        st.stage.value = "下载中…"
                    },
                    onStage = { v -> st.stage.value = v },
                )
                st.msg.value = if (r.ok) "✅ ${r.message}" else "❌ 失败：${r.message}"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { refresh++ }
            }
        }
    }

    fun startHwDownload() {
        gatedDownload("hw") {
            com.azime.input.core.handwriting.ModelDownloader.launchDownload("hw") {
                val st = com.azime.input.core.handwriting.ModelDownloader.stateOf("hw")
                val dir = com.azime.input.core.handwriting.HandwritingEngine.modelDir()
                val r1 = com.azime.input.core.handwriting.ModelDownloader.download(
                    com.azime.input.core.handwriting.HandwritingEngine.REMOTE_MODEL_ONNX,
                    java.io.File(dir, "model.onnx"),
                    onProgress = { d, tt ->
                        st.pct.value = if (tt > 0) ((d * 100) / tt).toInt() else -1
                        st.stage.value = "下载中…"
                    },
                )
                val r2 = if (r1.ok) com.azime.input.core.handwriting.ModelDownloader.download(
                    com.azime.input.core.handwriting.HandwritingEngine.REMOTE_LABELS,
                    java.io.File(dir, "labels.txt"),
                ) else r1
                st.msg.value = if (r1.ok && r2.ok) "✅ 手写模型已安装" else "❌ 失败：${(if (!r1.ok) r1 else r2).message}"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { refresh++ }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // ⚠️ 轮19.130：必须接 Scaffold 的 padding ✗（否则内容顶到状态栏/标题下面 ⇒ 与标题重叠 ✗ 用户反馈）
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 轮19.131：**页面里不再画标题** ✗（顶栏已有 ✓ 否则两个大标题 ✗ 用户反馈）
        Spacer(Modifier.height(4.dp))

        // ═══════ 【〇、联网下载】（轮19.145 ✓ 用户反馈"没有联网权限索取界面"✗）═══════
        Card(colors = grayCardColors(), shape = settingsCardShape()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("联网下载", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android 的联网权限是「安装即授权」，系统**没有**运行时开关可弹；" +
                        "所以这里给你两个显式开关，决定本应用能否联网下载模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SettingSwitchRow("允许联网下载模型", netAllow) {
                    netAllow = it
                    com.azime.input.core.net.NetPrefs.setAllowDownload(context, it)
                }
                SettingSwitchRow("仅 Wi-Fi 下载（推荐）", netWifiOnly) {
                    netWifiOnly = it
                    com.azime.input.core.net.NetPrefs.setWifiOnly(context, it)
                }
                Text(
                    "当前网络：${com.azime.input.core.net.netStateLabel(netNow)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ═══════ 【一、语音】（背景色分块 ✓）═══════
        Card(colors = grayCardColors(), shape = settingsCardShape()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "语音", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                // 轮19.139：与「语音」标题左对齐 ✓（用户反馈"没对齐"✗）
                Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SettingSwitchRow("麦克风权限", micOk) {
                        if (!micOk) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
                // ── 选择模型（与下载分开 ✓）──
                Text(
                    "选择模型（点一下即生效）", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                com.azime.input.core.handwriting.ModelDownloader.VOICE_MODELS.forEach { vm ->
                    val installed = if (vm.id == "sense_voice") senseOk else zipOk
                    val selected = engine == vm.id
                    // 轮19.133：**统一用 FilterChip** ✓（用户要求「设置内所有的相关项都要改」✓）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = selected,
                            enabled = installed,
                            onClick = {
                                com.azime.input.core.haptic.HapticsManager.press()
                                engine = vm.id
                                speechPrefs.edit().putString("engine", vm.id).apply()
                            },
                            label = { Text(vm.title.substringBefore("（")) },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            (if (installed) "" else "未安装 · ") + vm.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // 联网 API 行（同一「选择」区 ✓）
                val apiSel = engine == com.azime.input.core.speech.SpeechEngineManager.ENGINE_WEB_API
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    OimeChip(
                        selected = apiSel,
                        enabled = apiCfg != null,
                        onClick = {
                            engine = com.azime.input.core.speech.SpeechEngineManager.ENGINE_WEB_API
                            speechPrefs.edit().putString("engine", engine).apply()
                        },
                        label = { Text("联网 API") },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (apiCfg != null) "已配置" else "未配置（需先配置）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text("配置", fontSize = 12.sp, color = accent,
                        modifier = Modifier.clickable { showApiDialog = true }.padding(horizontal = 6.dp))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 20.dp))
                // ── 下载模型（与选择分开 ✓）──
                Text(
                    "下载模型", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                com.azime.input.core.handwriting.ModelDownloader.VOICE_MODELS.forEach { vm ->
                    val installed = if (vm.id == "sense_voice") senseOk else zipOk
                    val st = com.azime.input.core.handwriting.ModelDownloader.stateOf(vm.id)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(vm.title.substringBefore("（"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = !st.running.value,
                            onClick = { startVoiceDownload(vm) },
                        ) {
                            Text(if (installed) "重新下载" else "下载")
                        }
                    }
                    // 轮19.137：状态来自**全局下载器** ✓（切页回来也能看到 ✓）
                    val rowMsg = st.msg.value
                    if (!rowMsg.isNullOrEmpty()) {
                        Text(
                            rowMsg, style = MaterialTheme.typography.bodySmall,
                            color = if (rowMsg.startsWith("✅") || rowMsg.contains("已安装"))
                                    MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
                        )
                    }
                    if (st.running.value) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
                            val pr = if (st.pct.value >= 0) st.pct.value / 100f else 0f
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { pr },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                            Text(
                                st.stage.value + (if (st.pct.value >= 0) "  ${st.pct.value}%" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                TextButton(onClick = { showVoiceHelp = true }) { Text("语音使用说明", fontSize = 12.sp) }
            }
        }

        // ═══════ 【二、手写】（背景色分块 ✓）═══════
        Card(colors = grayCardColors(), shape = settingsCardShape()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "手写", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    "选择模型", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    OimeChip(
                        selected = hwOk,
                        enabled = true,
                        onClick = {},
                        label = { Text("手写模型") },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        (if (hwOk) "已安装 · 当前使用" else "未安装（可去下面下载）") + " · DeepHCCR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 20.dp))
                Text(
                    "下载模型", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                val hwSt = com.azime.input.core.handwriting.ModelDownloader.stateOf("hw")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("手写模型（model.onnx + labels.txt）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = !hwSt.running.value,
                        onClick = { startHwDownload() },
                    ) {
                        Text(if (hwOk) "重新下载" else "下载")
                    }
                }
                val hwRowMsg = hwSt.msg.value
                if (!hwRowMsg.isNullOrEmpty()) {
                    Text(
                        hwRowMsg, style = MaterialTheme.typography.bodySmall,
                        color = if (hwRowMsg.startsWith("✅") || hwRowMsg.contains("已安装"))
                                MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
                    )
                }
                if (hwSt.running.value) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
                        val pr = if (hwSt.pct.value >= 0) hwSt.pct.value / 100f else 0f
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { pr },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                        Text(
                            hwSt.stage.value + (if (hwSt.pct.value >= 0) "  ${hwSt.pct.value}%" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                TextButton(onClick = { showHwHelp = true }) { Text("手写使用说明", fontSize = 12.sp) }
            }
        }

        if (msg.isNotEmpty()) {
            Text(
                msg, style = MaterialTheme.typography.bodySmall,
                color = if (msg.contains("已安装")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (showVoiceHelp) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showVoiceHelp = false },
                title = { Text("语音输入使用说明") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(VOICE_HELP_TEXT, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { showVoiceHelp = false }) { Text("知道了") } },
            )
        }
        if (showHwHelp) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showHwHelp = false },
                title = { Text("手写输入使用说明") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(HANDWRITING_HELP_TEXT, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { showHwHelp = false }) { Text("知道了") } },
            )
        }
        if (showApiDialog) {
            WebApiConfigDialog(onDismiss = { showApiDialog = false; refresh++ })
        }
        // 轮19.145：**移动网络下下载前问一句** ✓（模型 200~240MB ✗ 别在用户不知情时跑流量 ✗）
        pendingDownload?.let { go ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingDownload = null },
                title = { Text("当前是移动网络") },
                text = {
                    Text(
                        "语音/手写模型约 200~240MB，用手机流量下载会消耗较多数据。是否继续？\n\n" +
                            "想以后不再询问，可在上方「联网下载」里关闭「仅 Wi-Fi 下载」。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { pendingDownload = null; go() }) { Text("继续下载") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDownload = null }) { Text("取消") }
                },
            )
        }
    }
}

/**
 * 轮19.146：**1.0.4 更新说明**（关于页「本次更新」弹窗用 ✓）。
 *
 * 只写 **1.0.3 → 1.0.4** 的差异 ✓：本轮开发中「引入后又修掉」的问题（用户从没见过 ✗）
 * 不进这份清单 ✗ 否则用户会看得莫名其妙 ✓
 */
private val UPDATE_LOG_1_0_4 = """
本次更新：1.0.4（vc146）· 相对 1.0.3

【新增】
· 手写输入（整套）：离线手写识别（DeepHCCR，量化后模型约 10MB）
  · 手写板铺满键盘区；抬笔停顿约 800ms 自动识别，无需点按钮；自带清空键
  · 识别结果进工具栏候选，点选即上屏；上屏后画布自动清空
  · 模型可在「语音手写管理」里一键下载；也可手动放入 Documents/Oime/models/handwriting/
· 「嵌入式」独立成项（悬浮窗及嵌入式页）：三态 —— 不嵌入 / 嵌入编码 / 嵌入首选
  · 「嵌入首选」会把输入码与首选候选的位置对调
· 「语音手写管理」页：语音识别设置从「输入方案」页搬进来，语音 + 手写 + 模型下载统一入口
· 启动向导扩到 7 页，新增两步：
  · 【语音输入权限】先把「为什么要麦克风」讲清楚，再由你点按钮申请（RECORD_AUDIO 运行时权限）
  · 【联网与模型下载】说明「联网权限属于安装即授权、系统没有弹窗」，并给两个开关：
    允许联网下载 / 仅 Wi-Fi 下载（模型 200~240MB，默认只在 Wi-Fi 下下）
· 关于页新增「本次更新」，随时能回看本版改了什么

【修改】
· 悬浮窗及嵌入式：拆成并列三张卡（悬浮窗 / 嵌入式 / 样式与数值），与「输入方案」页版式统一
· 设置页统一观感：选中态改为无边框样式 + 统一 FilterChip；「备份」列表并入「恢复备份」项下
· 模型下载器：直连失败自动切换公益镜像；支持断点续传（中断保留 .part）；只解压真正需要的文件
  （*.int8.onnx 与 tokens.txt，跳过 fp32 与样例音频）；下载包用完即删
· 手写图标、语音手写图标重绘（描边风）

【修复】
· 微信表情要按多次退格才删得掉 —— 真因：微信表情在输入框里的底层文本是 [微笑] 这类短代码，
  以前一次只删一个码元，等于只删掉右括号，屏幕上看不出变化
· 语音的「联网 API」识别在 1.0.3 里其实一直不可用 —— 真因是 manifest 少声明 INTERNET 权限，已补
· 设置页个别条目「点了样式不跟随」—— 真因是同名函数重载吃掉了回调
· 二级设置页清理掉滚动嵌套导致的闪退隐患（列表内层重复套滚动容器）
""".trimIndent()

private val HANDWRITING_HELP_TEXT = """
手写输入使用说明

【一、模型从哪来（需自行下载 ✓）】
· 本项目采用**离线图片路线**：DeepHCCR（GoogLeNet，MIT 授权 ✓ 论文精度 95.3% ✓）
  · 仓库：github.com/chongyangtao/DeepHCCR
  · 权重：models/googlenet_hccr.caffemodel（约 39MB，仓库内直接下载 ✓）
· 需要转成 ONNX 才能在手机上跑 ✓（转换脚本由项目方提供 ✓）：
  · 产出两个文件：model.onnx（量化后约 9~10MB ✓）+ labels.txt（字符表 ✓）

【二、放到哪里】
· 目录：Documents/Oime/models/handwriting/
· 文件：model.onnx + labels.txt（缺一不可）
· 放好后回到本页点「刷新模型状态」→ 显示「模型已就绪」即可 ✓

【三、原理（为什么能 10MB 以内 ✓）】
· 手写时把笔画**渲染成 112×112 灰度图** → 交给离线模型识别 ✓
· 复用应用内已有的 ONNX Runtime ✓ ⇒ 运行时体积增量为 0 ✓

【四、当前进度】
· 入口 / 模型检测 / 目录约定：已完成 ✓
· 手写键盘页 + 推理接入：下一轮 ✓
""" + """"""

private val VOICE_HELP_TEXT = """
语音输入使用说明

【一、两种本地模型（离线，推荐）】
· SenseVoice —— 准确率优先
  · 目录：Documents/Oime/models/sense-voice/
  · 文件：model.int8.onnx + tokens.txt
  · 语言：中文 / 英文 / 日文 / 韩文 / 粤语
  · 交互：**松手后**给出整段文字（适合短句、要求准确）

· 流式 Zipformer —— 边说边出
  · 目录：Documents/Oime/models/zipformer/
  · 文件：encoder*.int8.onnx + decoder*.onnx + joiner*.int8.onnx + tokens.txt
  · 语言：中英混说
  · 交互：**边说边显示**（适合长句、实时性要求高）
  · ⚠️ 对硬件（CPU）要求较高，见【六】

【二、怎么用】
1. 先在「麦克风权限」里授权录音（不授权无法听写）
2. 键盘上**长按 ○ 键**开始听写，松开结束
3. 想换引擎：在上面的「识别引擎」里点选即可（切换会释放旧模型缓存）
4. 放好模型后，点「刷新模型状态」→ 看到「模型就绪」即可使用

【三、联网 API（可选，不想下载模型时用）】
· 点「联网 API 配置」填一个 OpenAI 兼容的 /audio/transcriptions 服务
  （baseUrl + apiKey + 模型名），识别走网络 ✓

【四、模型下载地址（PC 端解压后推入手机）】
· SenseVoice：
  github.com/k2-fsa/sherpa-onnx/releases → asr-models →
  sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8
· Zipformer：
  sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20

【五、小贴士】
· 模型文件较大，建议用数据线传；传完可点「刷新模型状态」复查
· 首次识别会加载模型，可能有一两秒延迟；之后走缓存会快很多

【六、硬件性能提示（较老机型请看这里）】
· 本地语音模型（**尤其是流式 Zipformer**）的加载与解码都依赖手机 CPU：
  · 较新的机型：模型加载后即可顺畅「边说边出」✓
  · **较老的机型**：可能出现「说话时不出字、点结束也没有文字」的现象 ✗
    —— 这一般是**硬件性能（CPU）不足**导致，**不是模型文件损坏** ✓
    （同一台机器上，模型能正常加载、只是解不出结果；换 SenseVoice 则通常可用 ✓）
· 给老机型的两个建议：
  ① **优先用 SenseVoice** —— 它一次出整段，算力需求更低，老机型实测可用 ✓
  ② 或者**换一个更小的模型**（例如体积更小的 zipformer 变体）✓
     · ⚠️ **注意：模型越小，识别精度通常会有所下降**（尤其嘈杂环境、长句、专有名词）
     · 取舍建议：**要准 → SenseVoice（大模型）**；**要快/要边说边出 → 小 zipformer** ✓
""".trimIndent()


/** 轮19.83：通用长文本弹窗（许可 / 隐私条约等）。 */
@Composable
private fun LongTextDialog(title: String, body: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("知道了") } },
    )
}

/** 轮19.83：开源许可清单。 */
private val LICENSE_TEXT = """
○输入法（Oime）是开源软件，使用了以下开源项目，在此致谢：

【librime】BSD 3-Clause
· RIME 输入法引擎，本项目的输入核心
· github.com/rime/librime

【sherpa-onnx】Apache License 2.0
· 语音识别（SenseVoice / 流式 Zipformer）与 TTS
· github.com/k2-fsa/sherpa-onnx

【AndroidX / Jetpack Compose】Apache License 2.0
· 界面与生命周期
· developer.android.com/jetpack

【LuaJ / AndroLua 兼容层】MIT
· 方案脚本（如被使用）

【opencc】Apache License 2.0
· 简繁转换（随 RIME 方案提供）

【RIME 方案与词库】（虎码 / 白霜等）版权归各自作者所有，
  随本应用分发时保留其原始许可与说明文件。

如需完整许可文本，见仓库内 LICENSE 及各子目录中的许可文件。
""".trimIndent()

/** 轮19.83：隐私条约。 */
private val PRIVACY_TEXT = """
○输入法 隐私条约（本地优先）

一、我们不采集什么
· 不采集你的输入内容（按键、候选、上屏文字）
· 不采集通讯录、短信、位置、设备标识
· **没有**任何统计/遥测 SDK，不连接自家服务器

二、数据在哪里
· 输入方案、词库、字体、音效、备份、日志：全部在
  Documents/Oime/ 下，**只存在你的设备上**
· 日志仅用于排查问题，默认只记错误与关键事件；
  详细日志需你手动开启，且随时可清空

三、什么时候会联网
· **只有**你主动做以下事情时才联网：
  1) 点开 GitHub 链接；
  2) 使用「联网 API」语音识别（请求发往你自己填写的服务地址）；
  3) 手动下载/更新方案、模型。
· 以上都可选择不用 ⇒ 全部功能可离线运行

四、权限说明
· 录音：仅在你长按 ○ 键听写时使用
· 使用情况/存储：仅用于读写 Documents/Oime/ 下的文件
· 网络：仅用于上面第三条列出的场景

五、第三方
· 语音识别所用的 sherpa-onnx 在**本地**运行；
  若你选择「联网 API」，数据将发送到**你自己配置**的服务，请自行确认其隐私政策。

六、联系
· 问题与建议：github.com/AZNixl/Oime（Issues）
""".trimIndent()

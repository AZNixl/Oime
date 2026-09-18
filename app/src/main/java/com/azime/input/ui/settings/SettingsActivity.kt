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
            val scheme = if (dark) androidx.compose.material3.darkColorScheme()
            else androidx.compose.material3.lightColorScheme()
            // 轮19.15：Card 默认底色（surfaceContainerLow/surfaceContainer）统一成浅灰 →
            // 所有二级页（键盘 / 主题 / O 圆环 …）里的卡片都跟着变浅灰
            // 轮19.26：M3 默认配色是**淡紫色系**，各卡片/容器按不同角色取色（surfaceContainerLow /
            // surfaceVariant / surfaceContainerHighest …），只覆盖两个角色会漏 —— 用户看到的紫底就是漏网的角色。
            // 这里把**整套中性色**统一成灰阶（浅色/深色各一套）。
            // 轮19.31：界面风格 —— miuix 用 MIUI 扁平灰阶（页面浅灰 + 卡片纯白），material 用中性灰
            val miuix = com.azime.input.core.theme.KeyboardTheme.isMiuix()
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
    var webApiDialogShow by remember { mutableStateOf(false) }
    val subTitles = mapOf(
        "schemas" to "输入方案",
        "keyboard" to "键盘",
        "theme" to "主题与配色",
        "float" to "悬浮窗",
        "oring" to "O 圆环",
        "about" to "关于",
    )
    // 反馈轮10：设置子级页支持系统返回键（原来滑动/返回直接回桌面）
    BackHandler(enabled = subPage != "main" || showManage) {
        if (showManage) showManage = false else subPage = "main"
    }
    // 反馈轮16：「部署」提到标题文本后面（原独立卡片移除）
    var deploying by remember { mutableStateOf(false) }
    // 轮19.1：方案页「刷新」——切方案组/方案管理后界面数据重新拉取（原需退出重进）
    var schemaRefreshRev by remember { mutableStateOf(0) }

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
                // 父级菜单③：语音输入（轮19：本地模型 + 联网 API 点选；系统接口已删除）
                item {
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column(Modifier.padding(vertical = 4.dp)) {
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
                            icon = OimeIcons.mic,
                            title = "麦克风权限",
                            subtitle = if (micGranted.value) "已授权（长按 ○ 键开始听写）" else "语音输入需要录音权限",
                            onClick = { if (!micGranted.value) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        )
                        // ── 识别引擎（轮19：三选一，speech_prefs.engine 与 Service 共用） ──
                        val voicePrefs = remember { context.getSharedPreferences("speech_prefs", android.content.Context.MODE_PRIVATE) }
                        var voiceEngine by remember { mutableStateOf(voicePrefs.getString("engine", "sense_voice") ?: "sense_voice") }
                        // 模型状态（进入页面时探测；导入模型后手动刷新）
                        var modelCheck by remember { mutableStateOf(0) }
                        val senseOk = remember(modelCheck) { com.azime.input.core.speech.SpeechEngineManager.hasSenseVoiceModel() }
                        val zipOk = remember(modelCheck) { com.azime.input.core.speech.SpeechEngineManager.hasZipformerModel() }
                        val apiCfg = remember(modelCheck) { com.azime.input.core.speech.SpeechEngineManager.webApiConfig(context) }
                        Text(
                            "识别引擎",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                        // (engineId, 标题, 副标题, 就绪)
                        val engines = listOf(
                            Triple(
                                com.azime.input.core.speech.SpeechEngineManager.ENGINE_SENSE_VOICE,
                                "SenseVoice（本地离线）",
                                if (senseOk) "模型就绪 · zh/en/ja/ko/yue · 准确率优先（松手出全文）"
                                else "未检测到模型：放入 Documents/Oime/models/sense-voice/",
                            ),
                            Triple(
                                com.azime.input.core.speech.SpeechEngineManager.ENGINE_ZIPFORMER,
                                "流式 Zipformer（本地离线）",
                                if (zipOk) "模型就绪 · zh-en · 边说边出（实时显示）"
                                else "未检测到模型：放入 Documents/Oime/models/zipformer/",
                            ),
                            Triple(
                                com.azime.input.core.speech.SpeechEngineManager.ENGINE_WEB_API,
                                "联网 API",
                                if (apiCfg != null) "已配置（${apiCfg.model}）" else "未配置（点此填写）",
                            ),
                        )
                        engines.forEach { (id, title, subtitle) ->
                            val selected = voiceEngine == id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (id == com.azime.input.core.speech.SpeechEngineManager.ENGINE_WEB_API && apiCfg == null) {
                                            // 未配置先弹配置
                                            webApiDialogShow = true
                                        } else {
                                            voiceEngine = id
                                            voicePrefs.edit().putString("engine", id).apply()
                                            // 切引擎释放旧模型缓存
                                            com.azime.input.core.speech.SpeechEngineManager.releaseEngines()
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // ── 联网 API 配置入口 ──
                        KsuItem(
                            icon = OimeIcons.cloud,
                            title = "联网 API 配置",
                            subtitle = if (apiCfg != null) "${apiCfg.baseUrl} · ${apiCfg.model}" else "OpenAI 兼容 /audio/transcriptions",
                            onClick = { webApiDialogShow = true },
                        )
                        // ── 模型目录说明 + 刷新 ──
                        KsuItem(
                            icon = OimeIcons.refresh,
                            title = "刷新模型状态",
                            subtitle = "模型放 Documents/Oime/models/（sense-voice / zipformer）",
                            onClick = { modelCheck++ },
                        )
                        Text(
                            "模型下载（PC 端解压后推入手机）：\n" +
                                "SenseVoice: github.com/k2-fsa/sherpa-onnx/releases → asr-models →\n" +
                                "  sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8（model.int8.onnx + tokens.txt）\n" +
                                "Zipformer: sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20\n" +
                                "  （encoder*.int8.onnx + decoder*.onnx + joiner*.int8.onnx + tokens.txt）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    } }
                }
            }
            // 轮19：联网 API 配置对话框（语音输入大项）
            if (webApiDialogShow) {
                WebApiConfigDialog(onDismiss = { webApiDialogShow = false })
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
                    Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { HintOffsetSettings() } }
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
                item { Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { FloatingWindowSettings() } } }
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
                item { Card(colors = grayCardColors(), shape = settingsCardShape()) { Column { ThemeColorSettings() } } }
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
                                icon = OimeIcons.info,
                                title = "版本",
                                subtitle = "${appVersionName(context)} · 包名 com.oime.input · 平台 RIME",
                                onClick = {},
                                showChevron = false,
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
                                icon = OimeIcons.refresh,
                                title = "恢复备份",
                                subtitle = "从 Oime_backup_*.json 恢复全部偏好（覆盖当前设置）",
                                onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
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
                        icon = OimeIcons.pip,
                        title = "悬浮窗",
                        subtitle = "编码预览悬浮窗 · 默认 / 自定义样式",
                        onClick = { subPage = "float" },
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
                        // 轮19.47：字号放大（12 → 18）
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (notEnabled) Color(0xFF3C4043) else cs.onPrimaryContainer,
                    )
                }
                // 轮19.15：第三行 = 当前方案
                if (ready && schema.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "方案 · $schema",
                        // 轮19.47：字号放大（12 → 14）
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = cs.onPrimaryContainer,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("h" to "横向", "v" to "竖向").forEach { (id, label) ->
                    val on = orient == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            )
                            .clickable { orient = id; km.setFloatOrientation(id); floatRev++ }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = MaterialTheme.typography.bodySmall) }
                }
            }
            var showFloatColorPick by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showFloatColorPick = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("悬浮窗背景色（默认跟随主题）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                            onClick = { uiStyle = st.id; km.setUiStyle(st.id); styleMenu = false; styleRev++ },
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
 * 键面提示位置微调（轮19.53）。
 *
 * 键面四向滑动提示 + 长按符号提示的位置，各给一对 X/Y 偏移（dp）。
 * 约定：上/下 的 Y = 从该侧边缘往里；左/右 的 X = 从同侧边缘往里；长按的 X = 从右边往里。
 */
@Composable
private fun HintOffsetSettings() {
    val km = com.azime.input.core.keyboard.KeyboardManager
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("提示位置微调", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "四向滑动提示与长按符号提示的位置，全部 ±80dp 可调。约定：X 正数向右，" +
                "Y 正数向下；上/下 的 Y 基准在该侧边缘，左/右 的 X 基准在同侧边缘，长按在同侧上角。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        var upX by remember { mutableStateOf(km.hintOffUpX().toFloat()) }
        var upY by remember { mutableStateOf(km.hintOffUpY().toFloat()) }
        var downX by remember { mutableStateOf(km.hintOffDownX().toFloat()) }
        var downY by remember { mutableStateOf(km.hintOffDownY().toFloat()) }
        var leftX by remember { mutableStateOf(km.hintOffLeftX().toFloat()) }
        var leftY by remember { mutableStateOf(km.hintOffLeftY().toFloat()) }
        var rightX by remember { mutableStateOf(km.hintOffRightX().toFloat()) }
        var rightY by remember { mutableStateOf(km.hintOffRightY().toFloat()) }
        var pressX by remember { mutableStateOf(km.hintOffPressX().toFloat()) }
        var pressY by remember { mutableStateOf(km.hintOffPressY().toFloat()) }

        XimeSlider("上滑提示 · X", "${upX.toInt()}dp", upX, -80f..80f) { upX = it; km.setHintOffUpX(it.toInt()) }
        XimeSlider("上滑提示 · Y", "${upY.toInt()}dp", upY, -80f..80f) { upY = it; km.setHintOffUpY(it.toInt()) }
        XimeSlider("下滑提示 · X", "${downX.toInt()}dp", downX, -80f..80f) { downX = it; km.setHintOffDownX(it.toInt()) }
        XimeSlider("下滑提示 · Y", "${downY.toInt()}dp", downY, -80f..80f) { downY = it; km.setHintOffDownY(it.toInt()) }
        XimeSlider("左滑提示 · X", "${leftX.toInt()}dp", leftX, -80f..80f) { leftX = it; km.setHintOffLeftX(it.toInt()) }
        XimeSlider("左滑提示 · Y", "${leftY.toInt()}dp", leftY, -80f..80f) { leftY = it; km.setHintOffLeftY(it.toInt()) }
        XimeSlider("右滑提示 · X", "${rightX.toInt()}dp", rightX, -80f..80f) { rightX = it; km.setHintOffRightX(it.toInt()) }
        XimeSlider("右滑提示 · Y", "${rightY.toInt()}dp", rightY, -80f..80f) { rightY = it; km.setHintOffRightY(it.toInt()) }
        XimeSlider("长按符号 · X", "${pressX.toInt()}dp", pressX, -80f..80f) { pressX = it; km.setHintOffPressX(it.toInt()) }
        XimeSlider("长按符号 · Y", "${pressY.toInt()}dp", pressY, -80f..80f) { pressY = it; km.setHintOffPressY(it.toInt()) }
        Text(
            "下次键盘弹出即生效。调好后把数值告诉我，我可以把它们设成默认值。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

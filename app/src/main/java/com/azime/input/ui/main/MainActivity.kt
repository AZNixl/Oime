package com.azime.input.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.azime.input.ui.keyboard.keyboardAccentActiveColor
import com.azime.input.ui.keyboard.keyboardAccentKeyColor
import com.azime.input.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

/**
 * 首次启动向导：4 页（HorizontalPager）
 *  1. 读取本地文件权限（方案 / 字体在外部 Documents/Oime）
 *  2. 启用输入法（系统设置里勾选）
 *  3. 选择输入法（切换为当前输入法）
 *  4. 进入设置（方案导入、键盘、外观）
 * 每页实时检测完成状态，可自由前后翻页或跳过。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 向导完成（跳过/完成/三步全部达成）后不再显示：直接进设置
        val wizardPrefs = getSharedPreferences("wizard_prefs", MODE_PRIVATE)
        val alreadyDone = runCatching {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            wizardPrefs.getBoolean("wizard_done", false) ||
                (Environment.isExternalStorageManager() &&
                    imm.enabledInputMethodList.any { it.packageName == packageName } &&
                    Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                        .orEmpty().startsWith("$packageName/"))
        }.getOrDefault(false)
        if (alreadyDone) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        setContentView(
            ComposeView(this).apply {
                setContent {
                    val dark = isSystemInDarkTheme()
                    val scheme = if (dark) darkColorScheme() else lightColorScheme()
                    MaterialTheme(
                        colorScheme = scheme.copy(
                            primary = keyboardAccentActiveColor(dark),
                            primaryContainer = keyboardAccentKeyColor(dark),
                            onPrimaryContainer = if (dark) Color(0xFFD7E3F4) else Color(0xFF202124),
                        ),
                    ) {
                        OnboardingScreen(
                            openImeSettings = {
                                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            pickIme = {
                                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .showInputMethodPicker()
                            },
                            openAppSettings = {
                                runCatching {
                                    startActivity(Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:$packageName"),
                                    ))
                                }.onFailure {
                                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            },
                            requestStoragePermission = { launcher ->
                                launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            },
                            // 跳过向导 / 完成：记录标志 + 进设置 + 关闭向导
                            finishWizard = {
                                wizardPrefs.edit().putBoolean("wizard_done", true).apply()
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                finish()
                            },
                            openSettings = {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            },
                        )
                    }
                }
            }
        )
    }
}

/** 存储权限是否已授予：API 30+ 看「所有文件访问」，低版本看 READ_EXTERNAL_STORAGE。 */
private fun storageGranted(context: android.content.Context): Boolean = if (
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
) {
    Environment.isExternalStorageManager()
} else {
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    openImeSettings: () -> Unit,
    pickIme: () -> Unit,
    openAppSettings: () -> Unit,
    requestStoragePermission: (androidx.activity.result.ActivityResultLauncher<String>) -> Unit,
    finishWizard: () -> Unit,
    openSettings: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    // 实时状态：回到前台 / 翻页时刷新
    var storageOk by remember { mutableStateOf(storageGranted(context)) }
    var enabledOk by remember { mutableStateOf(false) }
    var selectedOk by remember { mutableStateOf(false) }
    var settingsVisited by remember { mutableStateOf(false) }

    fun refreshStatus() {
        storageOk = storageGranted(context)
        // 注意：targetSdk 34 上读 Settings.Secure.ENABLED_INPUT_METHODS 会抛 SecurityException
        // （Android 14 限制 targetSdk ≤ 33 才能读），改用 InputMethodManager 公开 API。
        val imm = context.getSystemService(
            android.content.Context.INPUT_METHOD_SERVICE
        ) as android.view.inputmethod.InputMethodManager
        enabledOk = runCatching {
            imm.enabledInputMethodList.any { it.packageName == context.packageName }
        }.getOrDefault(false)
        // DEFAULT_INPUT_METHOD 同样防御性读取（受限时视为未完成，不崩溃）
        selectedOk = runCatching {
            val def = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
            ).orEmpty()
            def.startsWith("${context.packageName}/")
        }.getOrDefault(false)
    }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    // 回到前台刷新状态（用户从系统设置返回）
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pagerState.currentPage) { refreshStatus() }

    Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "○输入法 · Oime",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "首次使用向导（可随时跳过）",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp, bottom = 20.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    // 大图标
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(cs.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (page) {
                                0 -> "📂"
                                1 -> "⌨"
                                2 -> "✓"
                                else -> "⚙"
                            },
                            fontSize = 44.sp,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        when (page) {
                            0 -> "读取本地文件"
                            1 -> "启用输入法"
                            2 -> "选择输入法"
                            else -> "进入设置"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (page) {
                            0 -> "方案、字体等资源存放在 Documents/Oime，" +
                                "需要授权读取本地文件（Android 11+ 跳转「所有文件访问」开关）。"
                            1 -> "跳转到系统「输入法设置」，勾选 ○输入法，" +
                                "允许它在你的设备上使用。"
                            2 -> "把当前输入法切换为 ○输入法。" +
                                "在弹出的选择框中选中它即可。"
                            else -> "导入方案、编辑键盘布局、调整字体与打字振动，都从这里开始。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    // 状态徽标
                    val done = when (page) {
                        0 -> storageOk
                        1 -> enabledOk
                        2 -> selectedOk
                        else -> settingsVisited
                    }
                    Text(
                        if (done) "✓ 已完成" else "未完成",
                        fontSize = 13.sp,
                        color = if (done) Color(0xFF2E9E5B) else cs.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (done) cs.surfaceVariant else cs.surface,
                                MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    // 动作按钮
                    Button(
                        onClick = {
                            when (page) {
                                0 -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        openAppSettings()
                                    } else {
                                        requestStoragePermission(runtimePermLauncher)
                                    }
                                }
                                1 -> openImeSettings()
                                2 -> pickIme()
                                else -> { settingsVisited = true; finishWizard() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            when (page) {
                                0 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                    "授予所有文件访问" else "授予存储权限"
                                1 -> "去启用"
                                2 -> "选择输入法"
                                else -> "进入设置"
                            }
                        )
                    }
                    Spacer(Modifier.weight(1.2f))
                }
            }

            // 页码圆点
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .size(if (i == pagerState.currentPage) 10.dp else 7.dp)
                            .background(
                                if (i == pagerState.currentPage) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.35f),
                                CircleShape,
                            ),
                    )
                }
            }

            // 底部导航
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    if (pagerState.currentPage == 0) {
                        finishWizard()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                }) { Text(if (pagerState.currentPage == 0) "跳过向导" else "上一步") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage < 3) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                finishWizard()
                            }
                        }
                    },
                ) {
                    Text(if (pagerState.currentPage == 3) "完成" else "下一步")
                }
            }
        }
    }
}

package com.azime.input.ui.lua

import androidx.activity.enableEdgeToEdge
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azime.input.core.lua.LuaScriptManager

/**
 * Lua 脚本编辑器 v1：编辑 preset_keys.lua。
 *
 * - 打开时载入当前脚本内容；
 * - 「校验」仅编译不执行（LuaJ load），错误显示在编辑框下方；
 * - 「保存」先校验，语法错误拒绝写入；成功后写回并热重载（loadScript）。
 */
class LuaEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 轮19.10：状态栏沉浸（与设置页一致，edge-to-edge）
        enableEdgeToEdge()
        setContentView(
            ComposeView(this).apply {
                setContent {
                    com.azime.input.ui.theme.OimeTheme {
                        LuaEditorScreen(onBackClick = { finish() })
                    }
                }
            }
        )
    }
}

@Composable
private fun HelpText(title: String, body: String) {
    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(
        body,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuaEditorScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var source by remember { mutableStateOf(LuaScriptManager.scriptText()) }
    var error by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    fun save() {
        val err = LuaScriptManager.saveScript(source)
        if (err != null) {
            error = err
            Toast.makeText(context, "语法错误，未保存", Toast.LENGTH_SHORT).show()
        } else {
            error = null
            dirty = false
            Toast.makeText(context, "已保存并热重载", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预设置 (preset_keys.lua)") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showHelp = true }) { Text("说明") }
                    TextButton(onClick = { error = LuaScriptManager.validate(source) }) { Text("校验") }
                    TextButton(onClick = ::save, enabled = dirty) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it; dirty = true },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                isError = error != null,
                supportingText = {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        ?: Text("-- 在此编辑 preset_keys.lua；保存成功后立即热重载", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("预设置语法说明") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HelpText("用途", "preset_keys.lua 定义可被键盘按键动作引用的预设条目（兼容 trime2 格式）。键盘布局编辑器或 lua 键盘布局里的动作值先到这里查条目名。")
                    HelpText("定义条目", "-- 表格式：label 显示 / send 命令或文本 / commit 直接上屏\n[\"全选\"] = { label = \"全选\", send = \"select_all\" },\n[\"日期\"] = { label = \"日期\", commit = \"2026-01-01\" },\n\n-- 字符串式：等价于直接上屏\n[\"邮箱\"] = \"me@example.com\",")
                    HelpText("动作取值优先级", "1. preset_keys 条目名 → 取其 send/commit\n2. 内置命令 identifier → 直接执行\n3. 其他任意文本 → 直接上屏（支持 {Left}/{Right} 光标后缀，如 \"❰{Left}\"）")
                    HelpText("内置命令", "select_all / cut / copy / paste\ntoggle_ascii（中英切换）/ newline / backspace / delete\nspace / tab / esc / left / right / up / down\npage_up / page_down / home / end\ncaps_lock / shift / delete_all / undo\npage:symbols / page:numpad / page:emoji / page:main\nchoose_page / toggle_symbols")
                    HelpText("完整示例", "preset_keys = {\n    [\"今日日期\"] = { label = \"📅\", commit = \"2026-09-05\" },\n    [\"剪切\"] = { label = \"✂\", send = \"cut\" },\n    [\"光标跳行首\"] = { label = \"⇤\", send = \"home\" },\n}\n\nreturn preset_keys")
                    HelpText("提示", "「校验」只检查语法；「保存」成功后立即热重载，无需重启输入法。（布局不再从 lua/keyboards/ 读取，动作请用键盘编辑器里的内置功能键值。）")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("知道了") }
            },
        )
    }
}

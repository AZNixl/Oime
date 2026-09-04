package com.azime.input.ui.lua

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
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
        setContentView(
            ComposeView(this).apply {
                setContent {
                    MaterialTheme {
                        LuaEditorScreen(onBackClick = { finish() })
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuaEditorScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var source by remember { mutableStateOf(LuaScriptManager.scriptText()) }
    var error by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }

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
                title = { Text("Lua 脚本 (preset_keys.lua)") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
}

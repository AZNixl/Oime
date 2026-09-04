package com.azime.input.ui.editor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azime.input.core.keyboard.KeyboardManager
import com.azime.input.data.model.KeyboardLayout
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * 键盘布局编辑器 v1：
 *
 * - 布局列表（内置 + 自定义），当前主键盘高亮，可一键启用；
 * - 内置布局可「复制副本」生成自定义版；
 * - 自定义布局支持 JSON 源码编辑（保存前 Gson 反序列化校验）与删除；
 * - 可从空白模板新建。
 *
 * 可视化拖拽编辑器留待 v2；当前 JSON 结构与 KeyboardLayout/KeyboardRow/Key/KeyType 一一对应。
 */
class KeyboardEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    MaterialTheme {
                        KeyboardEditorScreen(onBackClick = { finish() })
                    }
                }
            }
        )
    }
}

private val gson = Gson()
private val prettyGson = GsonBuilder().setPrettyPrinting().create()

private const val BLANK_TEMPLATE =
"""{
  "name": "my_layout",
  "rows": [
    {
      "keys": [
        { "label": "Q", "code": "q", "width": 1.0, "type": "CHARACTER" },
        { "label": "W", "code": "w", "width": 1.0, "type": "CHARACTER" }
      ]
    }
  ]
}"""

private data class LayoutEntry(
    val name: String,
    val isBuiltin: Boolean,
    val isActive: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardEditorScreen(onBackClick: () -> Unit) {
    // version 仅用于触发列表重组：数据本体在 KeyboardManager 单例里
    var version by remember { mutableIntStateOf(0) }
    var editingJson by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf<String?>(null) }

    fun refresh() { version++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("键盘布局编辑器") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        editingJson = BLANK_TEMPLATE
                        editingName = null
                    }) { Text("新建") }
                },
            )
        },
    ) { padding ->
        if (editingJson != null) {
            JsonEditorPane(
                initialJson = editingJson!!,
                initialName = editingName,
                modifier = Modifier.padding(padding),
                onCancel = { editingJson = null },
                onSave = { layout ->
                    KeyboardManager.saveLayout(layout)
                    KeyboardManager.setActiveMain(layout.name)
                    editingJson = null
                    refresh()
                },
            )
        } else {
            LayoutListPane(
                version = version,
                modifier = Modifier.padding(padding),
                onActivate = { name ->
                    KeyboardManager.setActiveMain(name)
                    refresh()
                },
                onCopyBuiltin = { name ->
                    val newName = "${name}_custom"
                    val copy = KeyboardManager.copyOfBuiltin(name, newName)
                    if (copy != null) {
                        KeyboardManager.saveLayout(copy)
                        KeyboardManager.setActiveMain(newName)
                        editingName = newName
                        editingJson = prettyGson.toJson(copy)
                    }
                    refresh()
                },
                onEditJson = { name ->
                    val layout = KeyboardManager.loadLayout(name)
                    if (layout != null) {
                        editingName = name
                        editingJson = prettyGson.toJson(layout)
                    }
                },
                onDelete = { name ->
                    KeyboardManager.deleteLayout(name)
                    refresh()
                },
            )
        }
    }
}

// ── 布局列表 ─────────────────────────────────────────────────

@Composable
private fun LayoutListPane(
    version: Int,
    modifier: Modifier = Modifier,
    onActivate: (String) -> Unit,
    onCopyBuiltin: (String) -> Unit,
    onEditJson: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    // version 参与构建保证删除/启用后刷新
    val entries = remember(version) {
        buildList {
            for (name in listOf("qwerty", "symbols")) {
                add(LayoutEntry(name, isBuiltin = true, isActive = KeyboardManager.activeMainName() == name))
            }
            for (name in KeyboardManager.customLayoutNames()) {
                add(LayoutEntry(name, isBuiltin = false, isActive = KeyboardManager.activeMainName() == name))
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { (if (it.isBuiltin) "builtin:" else "custom:") + it.name }) { entry ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(if (entry.isBuiltin) "内置" else "自定义") },
                        )
                    }
                    if (entry.isActive) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "✓ 当前主键盘",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!entry.isActive) {
                            FilledTonalButton(onClick = { onActivate(entry.name) }) { Text("启用") }
                        }
                        if (entry.isBuiltin) {
                            OutlinedButton(onClick = { onCopyBuiltin(entry.name) }) { Text("复制副本") }
                        } else {
                            OutlinedButton(onClick = { onEditJson(entry.name) }) { Text("编辑 JSON") }
                            OutlinedButton(onClick = { onDelete(entry.name) }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
}

// ── JSON 编辑 ────────────────────────────────────────────────

@Composable
private fun JsonEditorPane(
    initialJson: String,
    initialName: String?,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: (KeyboardLayout) -> Unit,
) {
    var json by remember { mutableStateOf(initialJson) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (initialName != null) "编辑：$initialName" else "新建布局（JSON）",
            fontWeight = FontWeight.Medium,
        )
        OutlinedTextField(
            value = json,
            onValueChange = { json = it; error = null },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            isError = error != null,
            supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) { Text("取消") }
            Button(
                onClick = {
                    // 校验：JSON 可解析 + 名称非空且合法 + 行结构非空
                    val layout = runCatching { gson.fromJson(json, KeyboardLayout::class.java) }
                        .getOrElse { e ->
                            error = "JSON 解析失败：${e.message?.take(120)}"
                            return@Button
                        }
                    if (layout.name.isBlank() || !layout.name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) {
                        error = "name 仅允许字母、数字、下划线、连字符（1-64 位）"
                        return@Button
                    }
                    if (layout.rows.isEmpty() || layout.rows.any { it.keys.isEmpty() }) {
                        error = "rows 不能为空，且每行至少 1 个键"
                        return@Button
                    }
                    onSave(layout)
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存并启用") }
        }
    }
}

package com.azime.input.ui.editor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azime.input.core.keyboard.KeyboardManager
import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyboardLayout
import com.azime.input.data.model.KeyboardRow
import com.azime.input.data.model.KeyType

/**
 * 键盘布局编辑器 v2 —— 可视化操作：
 *
 * - 布局列表（内置 + 自定义）：启用 / 复制副本 / 编辑 / 删除；
 * - 网格编辑器：等比还原按键宽度，点击任意按键弹出属性对话框
 *   （标签 / code / 宽度 / 长按 / 四向滑动 / 右上角提示），支持增删键、增删行；
 * - 动作取值与 trime2 preset_keys 约定兼容（identifier / preset 引用 / 字面文本）。
 */
class KeyboardEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                KeyboardEditorScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardEditorScreen(onBack: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var editingLayout by remember(version) { mutableStateOf<KeyboardLayout?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    if (editingLayout == null) {
        LayoutListScreen(
            version = version,
            onBack = onBack,
            onEdit = { name -> editingLayout = KeyboardManager.loadLayout(name) },
            onNew = {
                editingLayout = KeyboardLayout(
                    name = "layout_${System.currentTimeMillis() / 1000 % 100000}",
                    rows = listOf(
                        KeyboardRow(listOf(Key("A", "a"), Key("B", "b"), Key("C", "c"))),
                    ),
                )
            },
            onDelete = { name -> pendingDelete = name },
        )
    } else {
        GridEditorScreen(
            initial = editingLayout!!,
            onDone = { saved ->
                if (saved != null) {
                    KeyboardManager.saveLayout(saved)
                    KeyboardManager.setActiveMain(saved.name)
                }
                editingLayout = null
                version++
            },
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除布局") },
            text = { Text("确定删除「$name」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    KeyboardManager.deleteLayout(name)
                    pendingDelete = null
                    version++
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

// ── 布局列表 ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutListScreen(
    version: Int,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val builtins = remember(version) { listOf("qwerty", "symbols", "numpad") }
    var refreshTick by remember { mutableStateOf(0) }
    val customs = remember(version, refreshTick) { KeyboardManager.customLayoutNames() }
    var active by remember(version, refreshTick) { mutableStateOf(KeyboardManager.activeMainName()) }
    var importMsg by remember { mutableStateOf<String?>(null) }

    fun activate(name: String) {
        KeyboardManager.setActiveMain(name)
        active = name
    }

    fun importLuaLayouts() {
        val results = KeyboardManager.importLuaLayouts()
        refreshTick++
        importMsg = if (results.isEmpty()) {
            "未发现布局文件：Documents/AZime/lua/keyboards/*.lua"
        } else {
            results.joinToString("；") { (n, err) ->
                if (err == null) "$n ✓" else "${n}：$err"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("键盘布局编辑器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = "新建布局")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("内置布局", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(builtins, key = { "builtin:$it" }) { name ->
                LayoutCard(
                    name = name,
                    subtitle = if (active == name) "使用中" else "内置 · 可复制副本编辑",
                    active = active == name,
                    custom = false,
                    onActivate = { activate(name) },
                    onEdit = { onEdit(name) },
                    onDelete = null,
                )
            }
            if (customs.isNotEmpty()) {
                item {
                    Text("自定义布局", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(customs, key = { "custom:$it" }) { name ->
                    LayoutCard(
                        name = name,
                        subtitle = if (active == name) "使用中" else "自定义",
                        active = active == name,
                        custom = true,
                        onActivate = { activate(name) },
                        onEdit = { onEdit(name) },
                        onDelete = { onDelete(name) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { importLuaLayouts() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("导入 lua 键盘布局", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Documents/AZime/lua/keyboards/*.lua（trime2 格式）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                importMsg?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun LayoutCard(
    name: String,
    subtitle: String,
    active: Boolean,
    custom: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onActivate)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (custom && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── 网格编辑器 ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridEditorScreen(initial: KeyboardLayout, onDone: (KeyboardLayout?) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var rows by remember { mutableStateOf(initial.rows.map { it.keys }) }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) } // row, col

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑：${initial.name}") },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onDone(initial.copy(name = name, rows = rows.map { KeyboardRow(it) }))
                    }) { Text("保存", fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("布局名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "点击按键编辑属性（长按 / 四向滑动 / 右上角提示）；动作值兼容 trime2 preset_keys。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            rows.forEachIndexed { r, keys ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    keys.forEachIndexed { col, key ->
                        Box(
                            modifier = Modifier
                                .weight(key.width)
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { editing = r to col },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                key.label,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (key.longClick != null || key.swipeUp != null) {
                                Text(
                                    "·",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp),
                                )
                            }
                        }
                    }
                    // 行尾：加键
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxSize()
                            .clickable {
                                rows = rows.toMutableList().also { list ->
                                    list[r] = keys + Key("新键", "x")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    rows = rows + listOf(listOf(Key("A", "a"), Key("B", "b"), Key("C", "c")))
                }) { Text("＋ 添加一行") }
                if (rows.size > 1) {
                    OutlinedButton(onClick = {
                        rows = rows.dropLast(1)
                    }) { Text("删除最后一行") }
                }
            }
        }
    }

    editing?.let { (r, col) ->
        val key = rows[r][col]
        KeyEditDialog(
            key = key,
            onDismiss = { editing = null },
            onDelete = {
                rows = rows.toMutableList().also { list ->
                    val remaining = list[r].toMutableList().also { it.removeAt(col) }
                    if (remaining.isEmpty()) list.removeAt(r) else list[r] = remaining
                }
                editing = null
            },
            onSave = { updated ->
                rows = rows.toMutableList().also { list ->
                    list[r] = list[r].toMutableList().also { it[col] = updated }
                }
                editing = null
            },
        )
    }
}

@Composable
private fun KeyEditDialog(
    key: Key,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Key) -> Unit,
) {
    var label by remember { mutableStateOf(key.label) }
    var code by remember { mutableStateOf(key.code) }
    var widthText by remember { mutableStateOf(key.width.toString()) }
    var longClick by remember { mutableStateOf(key.longClick ?: "") }
    var swipeUp by remember { mutableStateOf(key.swipeUp ?: "") }
    var swipeDown by remember { mutableStateOf(key.swipeDown ?: "") }
    var swipeLeft by remember { mutableStateOf(key.swipeLeft ?: "") }
    var swipeRight by remember { mutableStateOf(key.swipeRight ?: "") }
    var hint by remember { mutableStateOf(key.hint ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑按键") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = label, onValueChange = { label = it },
                        label = { Text("标签") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text("code") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = widthText, onValueChange = { widthText = it },
                    label = { Text("宽度（份数，如 1 / 1.5 / 4）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longClick, onValueChange = { longClick = it },
                    label = { Text("长按动作") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = swipeUp, onValueChange = { swipeUp = it },
                        label = { Text("上滑") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = swipeDown, onValueChange = { swipeDown = it },
                        label = { Text("下滑") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = swipeLeft, onValueChange = { swipeLeft = it },
                        label = { Text("左滑") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = swipeRight, onValueChange = { swipeRight = it },
                        label = { Text("右滑") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = hint, onValueChange = { hint = it },
                    label = { Text("右上角提示（长按符号）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "动作可用：select_all / cut / copy / paste / toggle_ascii / newline / caps_lock / delete_all / undo / page:symbols / page:numpad / page:emoji / preset_keys 条目名 / 任意文本",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val width = widthText.toFloatOrNull()?.coerceIn(0.5f, 10f) ?: key.width
                onSave(
                    key.copy(
                        label = label.ifBlank { key.label },
                        code = code.ifBlank { key.code },
                        width = width,
                        type = key.type,
                        longClick = longClick.ifBlank { null },
                        swipeUp = swipeUp.ifBlank { null },
                        swipeDown = swipeDown.ifBlank { null },
                        swipeLeft = swipeLeft.ifBlank { null },
                        swipeRight = swipeRight.ifBlank { null },
                        hint = hint.ifBlank { null },
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("删除按键", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

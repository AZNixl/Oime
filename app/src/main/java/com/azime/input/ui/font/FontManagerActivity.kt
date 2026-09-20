package com.azime.input.ui.font

import androidx.activity.enableEdgeToEdge
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azime.input.core.font.FontManager
import java.io.File

/**
 * 字体管理（xime.az 多选方式）：
 * 列出 Documents/Oime/fonts 下的 ttf/otf/ttc，点卡片多选，
 * 选择顺序即回退顺序（首选缺字形依次回退后续字体，最后回退系统默认）。
 */
class FontManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 轮19.10：状态栏沉浸（与设置页一致，edge-to-edge）
        enableEdgeToEdge()
        setContent {
            com.azime.input.ui.theme.OimeTheme {
                FontManagerScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var fonts by remember(version) { mutableStateOf(FontManager.fontFiles()) }
    var selected by remember(version) { mutableStateOf(FontManager.selectedFonts()) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    fun refresh() { version++ }

    fun toggle(name: String) {
        val next = if (name in selected) selected - name else selected + name
        selected = next
        FontManager.setSelectedFonts(next)
    }

    // SAF 导入：复制进应用私有目录，Android 13+ 一定可读
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val n = FontManager.importFromTree(context, uri)
            Toast.makeText(context, "已导入 $n 个字体", Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("字体管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "把 ttf / otf / ttc 字体文件放入 Documents/Oime/fonts 文件夹（支持子文件夹）即可在此看到。",
                        fontSize = 13.sp,
                    )
                    Text(
                        "点卡片可多选字体，选择顺序即回退顺序：首选缺字形时依次回退后续字体，最后用系统默认。选多个可互补字库。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("从文件夹导入（复制，推荐）")
                    }
                    if (selected.isNotEmpty()) {
                        Text(
                            "已选 ${selected.size} 个（回退顺序）：${selected.joinToString(" → ")}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = {
                            selected = emptyList()
                            FontManager.setSelectedFonts(emptyList())
                        }) { Text("清除全部选择") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (fonts.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("未发现字体，试试上面的「从文件夹导入」", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(fonts, key = { it.name }) { font ->
                        FontCard(
                            file = font,
                            order = selected.indexOf(font.name),
                            onToggle = { toggle(font.name) },
                            onDelete = { pendingDelete = font },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除字体") },
            text = { Text("确定删除「${file.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    FontManager.deleteFont(file.name)
                    pendingDelete = null
                    refresh()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FontCard(
    file: File,
    order: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    // 预览字体（文件损坏时静默回退默认字体，避免崩溃）
    val previewFamily = remember(file.absolutePath) {
        runCatching { FontFamily(Typeface.createFromFile(file)) }
            .getOrElse { FontFamily.Default }
    }
    val isSelected = order >= 0
    Card(
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.clickable { onToggle() },
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "○输入法 AaBbCc 123 输入",
                    fontFamily = previewFamily,
                    fontSize = 18.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    file.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isSelected) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "回退顺序第 ${order + 1} 位",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // 选中勾标
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "已选",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

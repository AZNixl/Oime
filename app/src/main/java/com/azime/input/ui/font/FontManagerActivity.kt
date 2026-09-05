package com.azime.input.ui.font

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azime.input.core.font.FontManager
import java.io.File

/**
 * 字体管理（外置目录版）：
 * 直接列出 Documents/AZime/fonts 下的 ttf/otf/ttc，
 * 每个字体实时预览；键帽字体 / 候选字体两个角色独立选用。
 */
class FontManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FontManagerScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontManagerScreen(onBack: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var fonts by remember(version) { mutableStateOf(FontManager.fontFiles()) }
    var keyFont by remember(version) { mutableStateOf(FontManager.keyFontName()) }
    var candFont by remember(version) { mutableStateOf(FontManager.candidateFontName()) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    fun refresh() { version++ }

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
                Text(
                    "把 ttf / otf / ttc 字体文件放入 Documents/AZime/fonts 文件夹即可在此看到（无需导入）。",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (fonts.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("fonts 文件夹为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(fonts, key = { it.name }) { font ->
                        FontCard(
                            file = font,
                            isKey = keyFont == font.name,
                            isCand = candFont == font.name,
                            onSetKey = { FontManager.setKeyFont(font.name); refresh() },
                            onSetCand = { FontManager.setCandidateFont(font.name); refresh() },
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
    isKey: Boolean,
    isCand: Boolean,
    onSetKey: () -> Unit,
    onSetCand: () -> Unit,
    onDelete: () -> Unit,
) {
    // 预览字体（文件损坏时静默回退默认字体，避免崩溃）
    val previewFamily = remember(file.absolutePath) {
        runCatching { FontFamily(Typeface.createFromFile(file)) }
            .getOrElse { FontFamily.Default }
    }
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
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
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onSetKey,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isKey) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isKey) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text(if (isKey) "键帽 ✓" else "用作键帽") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onSetCand,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isCand) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isCand) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text(if (isCand) "候选 ✓" else "用作候选") }
                Spacer(Modifier.weight(1f))
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
}

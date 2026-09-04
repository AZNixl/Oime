package com.azime.input.ui.font

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.azime.input.core.font.FontManager

/**
 * 字体管理 v1：
 * - 列出已导入字体（ttf/otf/ttc），每项实时预览；
 * - SAF 导入（存内部 filesDir/fonts，Android 11+ 下外部 File 读不可靠）；
 * - 启用 / 删除；「默认」= 系统字体。
 * 字体在键盘视图下次重建时生效。
 */
class FontManagerActivity : AppCompatActivity() {

    private val fontPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    FontManager.importFrom(uri)
                        .onSuccess { name ->
                            Toast.makeText(this, "已导入：$name", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { e ->
                            Toast.makeText(this, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    MaterialTheme {
                        FontManagerScreen(
                            onBackClick = { finish() },
                            onPickFont = {
                                fontPicker.launch(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType("*/*")
                                )
                            },
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontManagerScreen(
    onBackClick: () -> Unit,
    onPickFont: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    var activeName by remember { mutableStateOf(FontManager.activeName()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("字体管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onPickFont) { Text("导入") }
                },
            )
        },
    ) { padding ->
        val fonts = remember(version) { FontManager.fontFiles() }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "默认（系统字体）",
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (activeName.isBlank()) {
                                Text("✓ 使用中", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "曦码 AZime 输入法 AaBbCc 123",
                            fontSize = 15.sp,
                        )
                        if (activeName.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = {
                                FontManager.setActive("")
                                activeName = ""
                                version++
                            }) { Text("恢复默认") }
                        }
                    }
                }
            }
            itemsIndexed(fonts, key = { _, f -> f.name }) { _, file ->
                val typeface = remember(file.absolutePath) { runCatching { Typeface.createFromFile(file) }.getOrNull() }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (activeName == file.name) {
                                Text("✓ 使用中", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (typeface != null) {
                            Text(
                                text = "曦码 AZime 输入法 AaBbCc 123",
                                fontSize = 15.sp,
                                fontFamily = FontFamily(typeface),
                            )
                        } else {
                            Text(
                                text = "（无法加载预览）",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeName != file.name) {
                                FilledTonalButton(onClick = {
                                    FontManager.setActive(file.name)
                                    activeName = file.name
                                    version++
                                }) { Text("使用") }
                            }
                            OutlinedButton(onClick = {
                                FontManager.deleteFont(file.name)
                                activeName = FontManager.activeName()
                                version++
                            }) { Text("删除") }
                        }
                    }
                }
            }
            if (fonts.isEmpty()) {
                item {
                    Text(
                        text = "还没有导入字体。\n点右上角「导入」选择手机里的 ttf/otf/ttc 文件。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

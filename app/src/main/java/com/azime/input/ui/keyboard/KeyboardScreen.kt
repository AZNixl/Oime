package com.azime.input.ui.keyboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import com.azime.input.core.font.FontManager
import com.azime.input.core.keyboard.KeyboardManager
import com.azime.input.core.lua.LuaScriptManager
import com.azime.input.core.rime.Candidate
import com.azime.input.data.keyboard.EmojiData
import com.azime.input.data.keyboard.KeyActions
import com.azime.input.data.keyboard.LongPressSymbols
import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyType

import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** 键盘 → Service 的动作。 */
sealed interface KeyAction {
    data class CharKey(val c: Char) : KeyAction
    data class DirectCommit(val text: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object ToggleSymbols : KeyAction
    data object ToggleAscii : KeyAction
    data class Candidate(val index: Int) : KeyAction
    data object PageDown : KeyAction
    data class SelectSchema(val schemaId: String) : KeyAction

    // ── 扩展动作（preset_keys identifier / 手势） ──
    /** 解析后的命令（select_all/cut/copy/paste/…）或字面提交。 */
    data class Resolved(val value: String) : KeyAction
    data object DeleteAll : KeyAction
    data object Undo : KeyAction
    /** 退格左滑（trime2 退格脚本同款锚点模型）：
     *  Start 记锚点（composing 中不进入）；To 按位移换算选区活动端；松手 DeleteSelection。 */
    data object BackspaceSelectStart : KeyAction
    data class BackspaceSelectTo(val charsFromAnchor: Int) : KeyAction
    data object DeleteSelection : KeyAction
    data object CapsLock : KeyAction
    data class OpenPage(val page: String) : KeyAction
    /** 红摇杆：按步移动光标（cursor=1 字/步 / pointer=远距跳转）。 */
    data class Joystick(val dx: Int) : KeyAction
    data class SetJoystickMode(val mode: String) : KeyAction
    data object OpenSettings : KeyAction
    data object ToggleClipboardPanel : KeyAction
    data object ToggleMenuPanel : KeyAction
    /** 从剪贴板面板/条上屏：提交后清除条目并收起面板。 */
    data class CommitClipboard(val text: String) : KeyAction
    data class SetToolbarItems(val ids: List<String>) : KeyAction
    /** 键盘 UI 内部：切页（main/symbols/numpad/emoji），不经 Service。 */
    data class SwitchPage(val page: String) : KeyAction
}

/** 键盘 UI 状态，由 AZimeService 持有并驱动。 */
data class KeyboardUiState(
    val candidates: List<Candidate> = emptyList(),
    val preedit: String = "",
    val asciiMode: Boolean = false,
    val shiftOn: Boolean = false,
    val capsOn: Boolean = false,
    val page: String = "main", // main | symbols | numpad | emoji
    val hasPrevPage: Boolean = false,
    val hasNextPage: Boolean = false,
    val schemaName: String = "",
    val schemas: List<String> = emptyList(),
    val ready: Boolean = false,
    val statusMessage: String = "",
    // 工具栏
    val clipText: String = "",
    val clipAtMs: Long = 0L,
    val showClipboardPanel: Boolean = false,
    val showMenuPanel: Boolean = false,
    val joystickMode: String = "cursor", // cursor | pointer
    /** 工具栏配置版本号：自定义保存后触发重组 */
    val toolbarRev: Int = 0,
)

// ── 配色：浅色/深色双主题（跟随系统），参考小企鹅 fcitx5-android ──
data class KeyboardColors(
    val bg: Color,
    val barBg: Color,
    val keyBg: Color,
    val funcKeyBg: Color,
    val accentKeyBg: Color,
    val accentKeyText: Color,
    val accentActive: Color,
    val accentActiveText: Color,
    val text: Color,
    val subText: Color,
    val joystick: Color,
)

private val LightColors = KeyboardColors(
    bg = Color(0xFFE9EBEE), barBg = Color.White,
    keyBg = Color.White, funcKeyBg = Color(0xFFD3D7DC),
    accentKeyBg = Color(0xFFC7DDF6), accentKeyText = Color(0xFF202124),
    accentActive = Color(0xFF1A73E8), accentActiveText = Color.White,
    text = Color(0xFF202124), subText = Color(0xFF80868B),
    joystick = Color(0xFFD32F2F),
)

private val DarkColors = KeyboardColors(
    bg = Color(0xFF1B1D1F), barBg = Color(0xFF26282A),
    keyBg = Color(0xFF2A2D2F), funcKeyBg = Color(0xFF3C4043),
    accentKeyBg = Color(0xFF3B5C8A), accentKeyText = Color(0xFFD7E3F4),
    accentActive = Color(0xFF8AB4F8), accentActiveText = Color(0xFF202124),
    text = Color(0xFFE8EAED), subText = Color(0xFF9AA0A6),
    joystick = Color(0xFFE57373),
)

@Composable
private fun keyboardColors(): KeyboardColors =
    if (isSystemInDarkTheme()) DarkColors else LightColors

private val KeySpacing = 4.dp

/**
 * ○输入法 键盘主界面：工具栏 + 候选栏（增高行）+ 按键区。
 */
@Composable
fun AzimeKeyboardScreen(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = keyboardColors()
    // 多字体：键帽用 keyFont，候选栏用 candidateFont
    val keyFontFamily = remember { FontManager.keyTypeface()?.let { FontFamily(it) } }
    val candFontFamily = remember { FontManager.candidateTypeface()?.let { FontFamily(it) } }

    // 尺寸可调（设置页滑杆）
    val keyH = KeyboardManager.keyHeightDp().dp
    val barH = KeyboardManager.barHeightDp().dp

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = keyFontFamily ?: FontFamily.Default),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(c.bg),
        ) {
            if (state.showMenuPanel) {
                MenuPanel(state = state, onAction = onAction)
            }
            if (state.showClipboardPanel) {
                ClipboardPanel(state = state, onAction = onAction)
            }
            ToolbarRow(state = state, onAction = onAction, barHeight = barH)
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = candFontFamily ?: keyFontFamily ?: FontFamily.Default),
            ) {
                if (state.page == "emoji") {
                    EmojiPane(state = state, onAction = onAction, keyHeight = keyH)
                } else {
                    val layout = KeyboardManager.layoutFor(state.page) ?: KeyboardManager.mainLayout()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KeySpacing, vertical = KeySpacing),
                        verticalArrangement = Arrangement.spacedBy(KeySpacing),
                    ) {
                        for (row in layout.rows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(keyH),
                                horizontalArrangement = Arrangement.spacedBy(KeySpacing),
                            ) {
                                for (key in row.keys) {
                                    KeyboardKey(key = key, state = state, onAction = onAction)
                                }
                                // 键宽与第一行（10 键）一致：不足 10 份的行尾部留白
                                val total = row.keys.sumOf { it.width.toDouble() }.toFloat()
                                if (total < 10f - 0.01f) {
                                    Spacer(Modifier.weight(10f - total))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 剪贴板面板：分词 / 提取英文词 / 提取网址 ─────────────────

@Composable
private fun ClipboardPanel(state: KeyboardUiState, onAction: (KeyAction) -> Unit) {
    val c = keyboardColors()
    if (state.clipText.isBlank()) return

    val tokens = remember(state.clipText) {
        // 分词：按空白与中英边界切
        Regex("""[A-Za-z]+|[0-9]+|[\u4e00-\u9fa5]""").findAll(state.clipText).map { it.value }.toList()
    }
    val englishWords = remember(state.clipText) {
        Regex("""[A-Za-z]{2,}""").findAll(state.clipText).map { it.value }.distinct().toList()
    }
    val urls = remember(state.clipText) {
        Regex("""(https?://\S+|www\.\S+|[A-Za-z0-9-]+\.[A-Za-z]{2,}(?:/\S*)?)""")
            .findAll(state.clipText).map { it.value }.distinct().toList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.barBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "剪贴板",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                "收起 ▲",
                fontSize = 12.sp,
                color = c.subText,
                modifier = Modifier.clickable { onAction(KeyAction.ToggleClipboardPanel) },
            )
        }
        Text(
            text = state.clipText,
            fontSize = 13.sp,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(c.bg, RoundedCornerShape(8.dp))
                .clickable { onAction(KeyAction.CommitClipboard(state.clipText)) }
                .fillMaxWidth()
                .padding(8.dp),
        )
        ChipRow("分词", tokens, onAction)
        ChipRow("英文", englishWords, onAction)
        ChipRow("网址", urls, onAction)
    }
}

@Composable
private fun ChipRow(title: String, items: List<String>, onAction: (KeyAction) -> Unit) {
    val c = keyboardColors()
    if (items.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, color = c.subText)
        Spacer(Modifier.width(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.take(12).forEach { token ->
                Text(
                    text = token,
                    fontSize = 13.sp,
                    color = c.text,
                    modifier = Modifier
                        .background(c.funcKeyBg, RoundedCornerShape(6.dp))
                        .clickable { onAction(KeyAction.CommitClipboard(token)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ── 工具栏：○ 菜单键（长按自定义） + 自定义工具区 + 剪贴板条 + 红摇杆 ──

@Composable
private fun ToolbarRow(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    barHeight: androidx.compose.ui.unit.Dp,
) {
    val c = keyboardColors()
    var showCustomize by remember { mutableStateOf(false) }
    var showSchemaMenu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val stepPx = with(density) { 18.dp.toPx() }

    // 剪贴板条仅在新复制后 10 秒内显示，避免常驻干扰
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.clipAtMs) {
        nowMs = System.currentTimeMillis()
        if (state.clipText.isNotBlank()) {
            kotlinx.coroutines.delay(10_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val clipFresh = state.clipText.isNotBlank() && (nowMs - state.clipAtMs) < 10_000

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight / 2 + 12.dp)
            .background(c.barBg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ○ 菜单键（最左第一位；点击开面板，长按自定义工具栏）
        var oPressing by remember { mutableStateOf(false) }
        var oLongFired by remember { mutableStateOf(false) }
        LaunchedEffect(oPressing) {
            if (oPressing) {
                kotlinx.coroutines.delay(400)
                if (oPressing && !oLongFired) {
                    oLongFired = true
                    showCustomize = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(c.bg, CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        oLongFired = false
                        oPressing = true
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                        }
                        oPressing = false
                    }
                }
                .clickable { onAction(KeyAction.ToggleMenuPanel) },
            contentAlignment = Alignment.Center,
        ) {
            Text("○", fontSize = 20.sp, color = c.text, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(6.dp))

        // 自定义工具区（长按 ○ 勾选）
        KeyboardManager.toolbarItems().forEach { id ->
            when (id) {
                "schema" -> Box(
                    modifier = Modifier
                        .clickable { showSchemaMenu = true }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) { Text("方案", fontSize = 14.sp, color = c.subText) }
                "numpad" -> Box(
                    modifier = Modifier
                        .clickable { onAction(KeyAction.SwitchPage("numpad")) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) { Text("123", fontSize = 14.sp, color = c.subText) }
                "emoji" -> Box(
                    modifier = Modifier
                        .clickable { onAction(KeyAction.SwitchPage("emoji")) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) { Text("☺", fontSize = 14.sp, color = c.subText) }
                "symbols" -> Box(
                    modifier = Modifier
                        .clickable { onAction(KeyAction.SwitchPage("symbols")) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) { Text("符", fontSize = 14.sp, color = c.subText) }
                "settings" -> Box(
                    modifier = Modifier
                        .clickable { onAction(KeyAction.OpenSettings) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) { Text("⚙", fontSize = 14.sp, color = c.subText) }
                else -> Unit
            }
        }

        // 剪贴板条（新复制后 10 秒内显示；点击打开剪贴板面板）
        if (clipFresh) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "clipboard: " + state.clipText.replace("\n", " "),
                fontSize = 13.sp,
                color = c.subText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAction(KeyAction.ToggleClipboardPanel) },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        // 方案快捷菜单
        if (showSchemaMenu) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) { (-40).dp.roundToPx() }),
                onDismissRequest = { showSchemaMenu = false },
            ) {
                Column(
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp),
                ) {
                    if (state.schemas.isEmpty()) {
                        Text("引擎部署中…", fontSize = 14.sp, color = c.subText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                    state.schemas.forEach { schemaId ->
                        Text(
                            text = schemaDisplay(schemaId).removePrefix("○输入法 · "),
                            fontSize = 15.sp,
                            color = if (schemaId == state.schemaName) c.accentActive else c.text,
                            fontWeight = if (schemaId == state.schemaName) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable {
                                    showSchemaMenu = false
                                    if (schemaId != state.schemaName) onAction(KeyAction.SelectSchema(schemaId))
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }

        // 红摇杆（固定最右）：cursor=1 字/步；pointer=快捷指针 10 字/步远距跳转
        var joyPressing by remember { mutableStateOf(false) }
        var joyLongFired by remember { mutableStateOf(false) }
        var showJoystickBubble by remember { mutableStateOf(false) }
        LaunchedEffect(joyPressing) {
            if (joyPressing) {
                kotlinx.coroutines.delay(400)
                if (joyPressing && !joyLongFired) {
                    joyLongFired = true
                    showJoystickBubble = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(c.joystick, CircleShape)
                .pointerInput(state.joystickMode) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        joyLongFired = false
                        joyPressing = true
                        var anchor: androidx.compose.ui.geometry.Offset? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val a = anchor
                            if (a != null) {
                                val dx = change.position.x - a.x
                                if (abs(dx) > stepPx) {
                                    onAction(KeyAction.Joystick((dx / stepPx).roundToInt()))
                                    joyLongFired = true // 拖动即生效，抑制气泡
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    anchor = change.position
                                }
                            } else {
                                anchor = change.position
                            }
                        }
                        joyPressing = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("•", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        if (showJoystickBubble) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { (-34).dp.roundToPx() }),
                onDismissRequest = { showJoystickBubble = false },
            ) {
                Column(
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp),
                ) {
                    Text("光标移动",
                        fontSize = 14.sp, color = c.text,
                        modifier = Modifier.clickable {
                            onAction(KeyAction.SetJoystickMode("cursor")); showJoystickBubble = false
                        }.padding(horizontal = 18.dp, vertical = 9.dp))
                    Text("快捷指针（远距跳转）",
                        fontSize = 14.sp, color = c.text,
                        modifier = Modifier.clickable {
                            onAction(KeyAction.SetJoystickMode("pointer")); showJoystickBubble = false
                        }.padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
        }
    }

    if (showCustomize) {
        ToolbarCustomizeDialog(
            current = KeyboardManager.toolbarItems(),
            onSave = { ids ->
                showCustomize = false
                onAction(KeyAction.SetToolbarItems(ids))
            },
            onDismiss = { showCustomize = false },
        )
    }
}

/** ○ 菜单面板（面板化，非浮窗）：嵌在工具栏上方展开。 */
@Composable
private fun MenuPanel(state: KeyboardUiState, onAction: (KeyAction) -> Unit) {
    val c = keyboardColors()
    fun close() = onAction(KeyAction.ToggleMenuPanel)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.barBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuTile("剪贴板", c, { close(); onAction(KeyAction.ToggleClipboardPanel) }, Modifier.weight(1f))
            MenuTile("26键", c, { close(); onAction(KeyAction.SwitchPage("main")) }, Modifier.weight(1f))
            MenuTile("数字", c, { close(); onAction(KeyAction.SwitchPage("numpad")) }, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuTile("emoji", c, { close(); onAction(KeyAction.SwitchPage("emoji")) }, Modifier.weight(1f))
            MenuTile("符号", c, { close(); onAction(KeyAction.SwitchPage("symbols")) }, Modifier.weight(1f))
            MenuTile("设置", c, { close(); onAction(KeyAction.OpenSettings) }, Modifier.weight(1f))
        }
        if (state.schemas.isNotEmpty()) {
            Text("切换方案", fontSize = 12.sp, color = c.subText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.schemas.forEach { schemaId ->
                    Text(
                        text = schemaDisplay(schemaId).removePrefix("○输入法 · "),
                        fontSize = 13.sp,
                        color = if (schemaId == state.schemaName) c.accentActive else c.text,
                        fontWeight = if (schemaId == state.schemaName) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .background(c.funcKeyBg, RoundedCornerShape(8.dp))
                            .clickable { close(); onAction(KeyAction.SelectSchema(schemaId)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuTile(
    label: String,
    c: KeyboardColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(c.funcKeyBg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, color = c.text)
    }
}

/** 工具栏自定义对话框（长按 ○ 呼出）。 */
@Composable
private fun ToolbarCustomizeDialog(
    current: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = KeyboardManager.availableToolbarTools
    val selected = remember { mutableStateListOf<String>().apply { addAll(current) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义工具栏") },
        text = {
            Column {
                Text(
                    "○ 菜单键与红摇杆固定，不可移除；以下工具按勾选顺序显示在工具栏中间。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (id in selected) selected.remove(id) else selected.add(id)
                            },
                    ) {
                        Checkbox(checked = id in selected, onCheckedChange = {
                            if (it) selected.add(id) else selected.remove(id)
                        })
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toList()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ── emoji 键盘 ───────────────────────────────────────────────

@Composable
private fun EmojiPane(state: KeyboardUiState, onAction: (KeyAction) -> Unit, keyHeight: androidx.compose.ui.unit.Dp) {
    val c = keyboardColors()
    var category by remember { mutableStateOf(0) }
    val emojis = EmojiData.categories.getOrNull(category)?.second ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KeySpacing),
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        // 分类标签行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EmojiData.categories.forEachIndexed { i, (label, _) ->
                Text(
                    text = label,
                    fontSize = 16.sp,
                    color = if (i == category) c.accentActive else c.subText,
                    modifier = Modifier
                        .clickable { category = i }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "ABC",
                fontSize = 13.sp,
                color = c.text,
                modifier = Modifier
                    .clickable { onAction(KeyAction.SwitchPage("main")) }
                    .padding(horizontal = 10.dp),
            )
        }
        // emoji 网格（8 列）
        emojis.chunked(8).forEach { chunk ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight),
                horizontalArrangement = Arrangement.spacedBy(KeySpacing),
            ) {
                chunk.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(c.keyBg, RoundedCornerShape(8.dp))
                            .clickable { onAction(KeyAction.DirectCommit(emoji)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
                if (chunk.size < 8) {
                    Spacer(Modifier.weight((8 - chunk.size).toFloat()))
                }
            }
        }
        // 底行：返回 + 空格
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight),
            horizontalArrangement = Arrangement.spacedBy(KeySpacing),
        ) {
            KeyboardKey(
                key = Key("ABC", code = "emoji_back", width = 1.5f, type = KeyType.FUNCTION),
                state = state, onAction = onAction,
            )
            KeyboardKey(
                key = Key("空格", code = "space", width = 7f, type = KeyType.SPACE),
                state = state, onAction = onAction,
            )
            KeyboardKey(
                key = Key("⌫", code = "backspace", width = 1.5f, type = KeyType.DELETE),
                state = state, onAction = onAction,
            )
        }
    }
}

// ── 增高行：候选栏 ───────────────────────────────────────────

@Composable
private fun CandidateBar(state: KeyboardUiState, onAction: (KeyAction) -> Unit) {
    val c = keyboardColors()
    var showSchemaMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.barBg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 中/英切换
        Box(
            modifier = Modifier
                .background(
                    color = if (state.asciiMode) c.funcKeyBg else c.accentKeyBg,
                    shape = RoundedCornerShape(6.dp),
                )
                .clickable { onAction(KeyAction.ToggleAscii) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (state.asciiMode) "EN" else "中",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (state.asciiMode) c.text else c.accentKeyText,
            )
        }

        Spacer(Modifier.width(8.dp))

        if (state.candidates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.candidates.forEachIndexed { index, candidate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onAction(KeyAction.Candidate(index)) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        if (index < 9) {
                            Text(
                                text = "${index + 1} ",
                                fontSize = 12.sp,
                                color = c.subText,
                            )
                        }
                        Text(
                            text = candidate.text,
                            fontSize = 17.sp,
                            maxLines = 1,
                            color = c.text,
                        )
                        if (candidate.comment.isNotBlank()) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = candidate.comment,
                                fontSize = 11.sp,
                                maxLines = 1,
                                color = c.subText,
                            )
                        }
                    }
                }
                if (state.hasNextPage) {
                    Text(
                        text = "▶",
                        fontSize = 13.sp,
                        color = c.subText,
                        modifier = Modifier
                            .clickable { onAction(KeyAction.PageDown) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            Column(Modifier.weight(1f)) {
                if (state.preedit.isNotEmpty()) {
                    Text(
                        text = state.preedit,
                        fontSize = 16.sp,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = when {
                            state.statusMessage.isNotEmpty() -> state.statusMessage
                            !state.ready -> "正在初始化输入引擎…"
                            else -> schemaDisplay(state.schemaName)
                        },
                        fontSize = 13.sp,
                        color = c.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (state.ready && state.schemas.size > 1) {
                            Modifier.clickable { showSchemaMenu = true }
                        } else Modifier,
                    )
                }
            }
        }

        if (showSchemaMenu && state.schemas.isNotEmpty()) {
            Popup(
                alignment = Alignment.BottomEnd,
                onDismissRequest = { showSchemaMenu = false },
            ) {
                Column(
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp),
                ) {
                    state.schemas.forEach { schemaId ->
                        Text(
                            text = schemaDisplay(schemaId).removePrefix("AZime · "),
                            fontSize = 15.sp,
                            color = if (schemaId == state.schemaName) c.accentKeyText else c.text,
                            fontWeight = if (schemaId == state.schemaName) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable {
                                    showSchemaMenu = false
                                    if (schemaId != state.schemaName) {
                                        onAction(KeyAction.SelectSchema(schemaId))
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun schemaDisplay(schemaId: String): String = when {
    schemaId.isBlank() -> "○输入法"
    else -> "○输入法 · $schemaId"
}

// ── 按键 ─────────────────────────────────────────────────────

/** 手势方向。 */
private enum class Dir { UP, DOWN, LEFT, RIGHT }

@Composable
private fun RowScope.KeyboardKey(key: Key, state: KeyboardUiState, onAction: (KeyAction) -> Unit) {
    val c = keyboardColors()
    val isShiftActive = key.code == "shift" && (state.shiftOn || state.capsOn)
    val bg = when {
        isShiftActive -> c.accentActive
        key.type == KeyType.ENTER -> c.accentKeyBg
        key.type == KeyType.CHARACTER || key.type == KeyType.SPACE -> c.keyBg
        else -> c.funcKeyBg
    }
    val fg = when {
        isShiftActive -> c.accentActiveText
        key.type == KeyType.ENTER -> c.accentKeyText
        else -> c.text
    }
    val label = when {
        key.code == "space" && state.page == "main" && state.schemaName.isNotBlank() -> schemaDisplay(state.schemaName)
        key.type != KeyType.CHARACTER -> key.label
        // 键帽显示：中文模式大写、英文模式小写（输入逻辑不变：中文仍送小写编码）
        state.asciiMode -> key.label.lowercase()
        else -> key.label
    }

    // 长按符号：内置映射（fcitx5 风格）或 preset_keys 条目
    val longPressSymbols = remember(key.code, state.page) {
        if (state.page != "symbols" && key.type == KeyType.CHARACTER) {
            LongPressSymbols[key.code.firstOrNull()] ?: emptyList()
        } else emptyList()
    }
    val hasCustomLong = !key.longClick.isNullOrBlank()
    val isPageKey = key.type == KeyType.FUNCTION && key.code == "symbols"
    val autoRepeat = key.type == KeyType.DELETE

    var pressing by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    var showBubble by remember { mutableStateOf(false) }
    var showPageBubble by remember { mutableStateOf(false) }
    var swipePreview by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val bubbleOffset = with(density) { IntOffset(0, -58.dp.roundToPx()) }
    val swipeThreshold = with(density) { 30.dp.toPx() }

    val hasGestures = longPressSymbols.isNotEmpty() || autoRepeat || hasCustomLong || isPageKey ||
        key.swipeUp != null || key.swipeDown != null || key.swipeLeft != null || key.swipeRight != null

    // 长按定时器
    LaunchedEffect(pressing) {
        if (pressing) {
            delay(400)
            if (pressing && !longFired) {
                when {
                    longPressSymbols.isNotEmpty() -> {
                        longFired = true
                        showBubble = true
                    }
                    isPageKey -> {
                        longFired = true
                        showPageBubble = true
                    }
                    hasCustomLong -> {
                        longFired = true
                        onAction(KeyAction.Resolved(key.longClick!!))
                    }
                    autoRepeat -> {
                        longFired = true
                        delay(150)
                        while (pressing) {
                            onKeyAction(key, onAction)
                            delay(45)
                        }
                    }
                }
            }
        }
    }

    var baseModifier = Modifier
        .weight(key.width)
        .fillMaxSize()
        .background(bg, RoundedCornerShape(8.dp))
    if (hasGestures) {
        // 手势闭包内读取最新 state（preedit 等会随打字频繁变化）
        val currentState by rememberUpdatedState(state)
        baseModifier = baseModifier.pointerInput(key.code, key.longClick, state.page) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                longFired = false
                pressing = true
                val startX = down.position.x
                val startY = down.position.y
                var activeDir: Dir? = null
                // 退格左滑（trime2 退格脚本锚点模型）：
                // engaged 后 target = floor(dx / SWIPE_STEP)，右滑回退可缩到锚点
                var selectEngaged = false
                var lastSelectStep = 0
                val swipeStepPx = 24f // 与 trime2 脚本 SWIPE_STEP=24 一致（原始像素）
                val engageThresholdPx = 10f

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - startX
                    val dy = change.position.y - startY

                    if (activeDir == null) {
                        val isDelete = key.type == KeyType.DELETE
                        // 退格左滑：小阈值 + 横向占优即进入选择模式（组合中不进）
                        if (isDelete && dx < -engageThresholdPx && abs(dx) > abs(dy)) {
                            if (currentState.preedit.isEmpty()) {
                                activeDir = Dir.LEFT
                                selectEngaged = true
                                longFired = true
                                swipePreview = "选择"
                                onAction(KeyAction.BackspaceSelectStart)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        } else {
                            val dist = abs(dx) + abs(dy)
                            if (dist > swipeThreshold) {
                                val dir = when {
                                    abs(dx) > abs(dy) -> if (dx > 0) Dir.RIGHT else Dir.LEFT
                                    else -> if (dy > 0) Dir.DOWN else Dir.UP
                                }
                                val action = when (dir) {
                                    Dir.UP -> key.swipeUp ?: if (isDelete) KeyActions.BS_UP else null
                                    Dir.DOWN -> key.swipeDown ?: if (isDelete) KeyActions.BS_DOWN else null
                                    Dir.LEFT -> key.swipeLeft
                                    Dir.RIGHT -> key.swipeRight
                                }
                                if (action != null) {
                                    activeDir = dir
                                    longFired = true
                                    swipePreview = actionPreview(action)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                    } else if (activeDir == Dir.LEFT && selectEngaged) {
                        // 位移换算选区活动端：负值向左扩选，右滑回退（最多缩回锚点）
                        val moved = (dx / swipeStepPx).toInt()
                        if (moved != lastSelectStep) {
                            onAction(KeyAction.BackspaceSelectTo(moved))
                            lastSelectStep = moved
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                pressing = false
                swipePreview = null

                when {
                    selectEngaged -> {
                        // 松手：删除选区（选区为空时 service 端安全跳过）
                        onAction(KeyAction.DeleteSelection)
                    }
                    activeDir != null -> {
                        val action = when (activeDir) {
                            Dir.UP -> key.swipeUp ?: if (key.type == KeyType.DELETE) KeyActions.BS_UP else null
                            Dir.DOWN -> key.swipeDown ?: if (key.type == KeyType.DELETE) KeyActions.BS_DOWN else null
                            Dir.LEFT -> key.swipeLeft
                            Dir.RIGHT -> key.swipeRight
                        }
                        if (action != null) onAction(KeyAction.Resolved(action))
                    }
                    !longFired -> onKeyAction(key, onAction)
                }
            }
        }
    } else {
        baseModifier = baseModifier.clickable { onKeyAction(key, onAction) }
    }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = swipePreview ?: label,
            fontSize = if (key.type == KeyType.CHARACTER) 20.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
        // 键面右上角长按符号提示
        if (swipePreview == null && key.hint != null && key.type == KeyType.CHARACTER) {
            Text(
                text = key.hint,
                fontSize = 9.sp,
                color = c.subText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 5.dp),
            )
        }
        if (showBubble && longPressSymbols.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = bubbleOffset,
                onDismissRequest = { showBubble = false },
            ) {
                Row(
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    longPressSymbols.forEach { symbol ->
                        Text(
                            text = symbol,
                            fontSize = 22.sp,
                            color = c.text,
                            modifier = Modifier
                                .clickable {
                                    onAction(KeyAction.DirectCommit(symbol))
                                    showBubble = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (showPageBubble) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = bubbleOffset,
                onDismissRequest = { showPageBubble = false },
            ) {
                Column(
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp),
                ) {
                    listOf(
                        "26键符号键盘" to "symbols",
                        "九宫格数字键盘" to "numpad",
                        "emoji 键盘" to "emoji",
                    ).forEach { (name, page) ->
                        Text(
                            text = name,
                            fontSize = 14.sp,
                            color = if (page == KeyboardManager.preferredPage()) c.accentActive else c.text,
                            modifier = Modifier
                                .clickable {
                                    showPageBubble = false
                                    KeyboardManager.setPreferredPage(page)
                                    onAction(KeyAction.SwitchPage(page))
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 滑动方向上的键面预览文本。 */
private fun actionPreview(action: String): String = when (action) {
    KeyActions.BS_UP, "delete_all" -> "全删"
    KeyActions.BS_DOWN, "undo" -> "撤回"
    KeyActions.BS_LEFT, "select_back" -> "选择"
    "newline" -> "⏎"
    "toggle_ascii" -> "中/EN"
    "caps_lock" -> "⇪"
    else -> {
        val resolved = LuaScriptManager.resolveAction(action)
        when (resolved) {
            is com.azime.input.core.lua.ResolvedAction.Commit -> resolved.text
            else -> action
        }
    }
}

private fun onKeyAction(key: Key, onAction: (KeyAction) -> Unit) {
    when (key.type) {
        KeyType.CHARACTER -> onAction(KeyAction.CharKey(key.code.first()))
        KeyType.SPACE -> onAction(KeyAction.Space)
        KeyType.ENTER -> onAction(KeyAction.Enter)
        KeyType.DELETE -> onAction(KeyAction.Backspace)
        KeyType.MODIFIER -> onAction(KeyAction.Shift)
        KeyType.FUNCTION -> when (key.code) {
            "symbols" -> onAction(KeyAction.ToggleSymbols)
            "emoji_back" -> onAction(KeyAction.SwitchPage("main"))
            // lua 布局的自定义 FUNCTION 键：命令 / preset 引用 / 文本上屏（trime2 语义）
            else -> {
                val resolved = LuaScriptManager.resolveAction(key.code)
                if (resolved != null) onAction(KeyAction.Resolved(resolved))
                else onAction(KeyAction.ToggleAscii)
            }
        }
    }
}

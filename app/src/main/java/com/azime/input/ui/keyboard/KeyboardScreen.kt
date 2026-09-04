package com.azime.input.ui.keyboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.azime.input.core.keyboard.KeyboardManager
import com.azime.input.core.rime.Candidate
import com.azime.input.data.keyboard.KeyboardPages
import com.azime.input.data.keyboard.LongPressSymbols
import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyType
import com.azime.input.data.model.KeyboardLayout

import kotlinx.coroutines.delay
import kotlin.math.abs

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
}

/** 键盘 UI 状态，由 AZimeService 持有并驱动。 */
data class KeyboardUiState(
    val candidates: List<Candidate> = emptyList(),
    val preedit: String = "",
    val asciiMode: Boolean = false,
    val shiftOn: Boolean = false,
    val symbolPage: Boolean = false,
    val hasPrevPage: Boolean = false,
    val hasNextPage: Boolean = false,
    val schemaName: String = "",
    val schemas: List<String> = emptyList(),
    val ready: Boolean = false,
    val statusMessage: String = "",
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
)

private val LightColors = KeyboardColors(
    bg = Color(0xFFE9EBEE), barBg = Color.White,
    keyBg = Color.White, funcKeyBg = Color(0xFFD3D7DC),
    accentKeyBg = Color(0xFFC7DDF6), accentKeyText = Color(0xFF202124),
    accentActive = Color(0xFF1A73E8), accentActiveText = Color.White,
    text = Color(0xFF202124), subText = Color(0xFF80868B),
)

private val DarkColors = KeyboardColors(
    bg = Color(0xFF1B1D1F), barBg = Color(0xFF26282A),
    keyBg = Color(0xFF2A2D2F), funcKeyBg = Color(0xFF3C4043),
    accentKeyBg = Color(0xFF3B5C8A), accentKeyText = Color(0xFFD7E3F4),
    accentActive = Color(0xFF8AB4F8), accentActiveText = Color(0xFF202124),
    text = Color(0xFFE8EAED), subText = Color(0xFF9AA0A6),
)

@Composable
private fun keyboardColors(): KeyboardColors =
    if (isSystemInDarkTheme()) DarkColors else LightColors

private val KeyHeight = 46.dp
private val KeySpacing = 4.dp

/**
 * AZime 键盘主界面：增高行（候选栏）+ 4 行按键。
 */
@Composable
fun AzimeKeyboardScreen(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 布局来自 KeyboardManager（内置 + 自定义覆盖）；切换发生在 Service 初始化阶段，
    // 此处读内存缓存即可，无需响应式刷新
    val layout = if (state.symbolPage) KeyboardManager.symbolLayout() else KeyboardManager.mainLayout()
    val c = keyboardColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bg),
    ) {
        CandidateBar(state = state, onAction = onAction)
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
                        .height(KeyHeight),
                    horizontalArrangement = Arrangement.spacedBy(KeySpacing),
                ) {
                    for (key in row.keys) {
                        KeyboardKey(key = key, state = state, onAction = onAction)
                    }
                }
            }
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
                        // 编号前缀：符号页/候选存在时按数字键可直接选中（librime selector 原生支持）
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
                        // 空态点方案名 → 弹方案切换菜单
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
    schemaId.isBlank() -> "AZime"
    else -> "AZime · $schemaId"
}

// ── 按键 ─────────────────────────────────────────────────────

@Composable
private fun RowScope.KeyboardKey(key: Key, state: KeyboardUiState, onAction: (KeyAction) -> Unit) {
    val c = keyboardColors()
    val isShiftActive = key.code == "shift" && state.shiftOn
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
        key.type == KeyType.CHARACTER && (state.shiftOn || state.asciiMode) -> key.label
        key.type == KeyType.CHARACTER -> key.label.lowercase()
        key.code == "space" && state.schemaName.isNotBlank() -> schemaDisplay(state.schemaName)
        else -> key.label
    }

    // 长按行为：字符键(仅主键盘页)弹符号气泡；退格键连删
    val longPressSymbols = remember(key.code) {
        if (!state.symbolPage && key.type == KeyType.CHARACTER) {
            LongPressSymbols[key.code.firstOrNull()] ?: emptyList()
        } else emptyList()
    }
    val autoRepeat = key.type == KeyType.DELETE
    val supportsLongPress = longPressSymbols.isNotEmpty() || autoRepeat

    var pressing by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    var showBubble by remember { mutableStateOf(false) }
    var swipePreview by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val bubbleOffset = with(density) { IntOffset(0, -58.dp.roundToPx()) }
    val swipeThreshold = with(density) { 30.dp.toPx() }

    LaunchedEffect(pressing) {
        if (pressing) {
            delay(400)
            if (pressing && !longFired) {
                when {
                    longPressSymbols.isNotEmpty() -> {
                        longFired = true
                        showBubble = true
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
    if (supportsLongPress) {
        baseModifier = baseModifier.pointerInput(key.code, state.symbolPage) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                longFired = false
                pressing = true
                val startX = down.position.x
                val startY = down.position.y
                var swipeTarget: String? = null

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    if (swipeTarget == null && longPressSymbols.isNotEmpty()) {
                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        if (abs(dy) > swipeThreshold && abs(dy) > abs(dx) * 1.2f) {
                            // 上滑取主符号（数字），下滑取次符号；滑出即预览并抑制长按/点击
                            swipeTarget = if (dy < 0) {
                                longPressSymbols.first()
                            } else {
                                longPressSymbols.getOrElse(1) { longPressSymbols.first() }
                            }
                            longFired = true
                            swipePreview = swipeTarget
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                pressing = false
                swipePreview = null
                when {
                    swipeTarget != null -> onAction(KeyAction.DirectCommit(swipeTarget))
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
            else -> onAction(KeyAction.ToggleAscii)
        }
    }
}

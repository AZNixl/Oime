package com.azime.input.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azime.input.core.rime.Candidate
import com.azime.input.data.keyboard.KeyboardPages
import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyType
import com.azime.input.data.model.KeyboardLayout

/** 键盘 → Service 的动作。 */
sealed interface KeyAction {
    data class CharKey(val c: Char) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object ToggleSymbols : KeyAction
    data object ToggleAscii : KeyAction
    data class Candidate(val index: Int) : KeyAction
    data object PageDown : KeyAction
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
    val ready: Boolean = false,
    val statusMessage: String = "",
)

// ── 配色（浅色，参考小企鹅 fcitx5-android）────────────────────
private val KeyboardBg = Color(0xFFE9EBEE)
private val KeyBg = Color.White
private val FuncKeyBg = Color(0xFFD3D7DC)
private val AccentKeyBg = Color(0xFFC7DDF6)
private val AccentActive = Color(0xFF1A73E8)
private val KeyText = Color(0xFF202124)
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
    val layout = if (state.symbolPage) KeyboardPages.symbols else KeyboardPages.qwerty
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KeyboardBg),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 中/英切换
        Box(
            modifier = Modifier
                .background(
                    color = if (state.asciiMode) FuncKeyBg else AccentKeyBg,
                    shape = RoundedCornerShape(6.dp),
                )
                .clickable { onAction(KeyAction.ToggleAscii) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (state.asciiMode) "EN" else "中",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = KeyText,
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
                    val label = buildString {
                        append(candidate.text)
                        if (candidate.comment.isNotBlank()) append(" ").append(candidate.comment)
                    }
                    Text(
                        text = label,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = KeyText,
                        modifier = Modifier
                            .clickable { onAction(KeyAction.Candidate(index)) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                if (state.hasNextPage) {
                    Text(
                        text = "▶",
                        fontSize = 13.sp,
                        color = Color(0xFF80868B),
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
                        color = KeyText,
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
                        color = Color(0xFF80868B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    val isShiftActive = key.code == "shift" && state.shiftOn
    val bg = when {
        isShiftActive -> AccentActive
        key.type == KeyType.ENTER -> AccentKeyBg
        key.type == KeyType.CHARACTER || key.type == KeyType.SPACE -> KeyBg
        else -> FuncKeyBg
    }
    val fg = if (isShiftActive) Color.White else KeyText
    val label = when {
        key.type == KeyType.CHARACTER && (state.shiftOn || state.asciiMode) -> key.label
        key.type == KeyType.CHARACTER -> key.label.lowercase()
        key.code == "space" && state.schemaName.isNotBlank() -> schemaDisplay(state.schemaName)
        else -> key.label
    }

    Box(
        modifier = Modifier
            .weight(key.width)
            .fillMaxSize()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onKeyAction(key, onAction) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (key.type == KeyType.CHARACTER) 20.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
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

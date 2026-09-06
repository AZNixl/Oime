package com.azime.input.ui.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import com.azime.input.core.font.FontManager
import com.azime.input.core.haptic.HapticsManager
import com.azime.input.core.keyboard.KeyboardManager
import com.azime.input.core.lua.LuaScriptManager
import com.azime.input.core.rime.Candidate
import com.azime.input.data.keyboard.EmojiData
import com.azime.input.data.keyboard.BracketPairs
import com.azime.input.data.keyboard.KeyActions
import com.azime.input.data.keyboard.KeyboardPages
import com.azime.input.data.keyboard.LongPressSymbols
import com.azime.input.data.keyboard.SymbolData
import com.azime.input.data.keyboard.longPressHint
import com.azime.input.data.model.Key
import com.azime.input.data.model.KeyType

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /** 重新部署方案（方案设置页 / ○ 菜单）。 */
    data object Deploy : KeyAction
    /** 收起键盘（工具栏末尾关闭键）。 */
    data object HideKeyboard : KeyAction
    data object ToggleClipboardPanel : KeyAction
    data object ToggleMenuPanel : KeyAction
    /** 从剪贴板面板/条上屏：提交后清除条目并收起面板。 */
    data class CommitClipboard(val text: String) : KeyAction
    data class SetToolbarItems(val ids: List<String>) : KeyAction
    /** 键盘 UI 内部：切页（main/symbols/numpad/emoji），不经 Service。 */
    data class SwitchPage(val page: String) : KeyAction
    /** 更多候选面板开关。 */
    data object ToggleCandidatePanel : KeyAction
    /** 候选上一页（PageUp keysym 0xFF54）。 */
    data object PageUp : KeyAction

    // ── 剪贴板面板（jqb.lua 风格：历史 / 收藏 + ︙菜单） ──
    data class SetClipTab(val tab: String) : KeyAction
    /** 面板内点选上屏：不清空面板与历史。 */
    data class CommitClipText(val text: String) : KeyAction
    /** 剪贴板条目收藏（加入常用短语）。 */
    data class ClipFav(val text: String) : KeyAction
    /** 删除条目（list = clipboard | phrase）。 */
    data class ClipDelete(val list: String, val index: Int) : KeyAction
    /** 条目置顶。 */
    data class ClipTop(val list: String, val index: Int) : KeyAction
    /** 清空列表。 */
    data class ClipClear(val list: String) : KeyAction
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
    val showCandidatePanel: Boolean = false,
    /** 剪贴板历史（最新在前，Service 持久化 clipboard.json）。 */
    val clipHistory: List<String> = emptyList(),
    /** 常用短语/收藏（Service 持久化 phrase.json）。 */
    val phraseItems: List<String> = emptyList(),
    /** 剪贴板面板选项卡：clipboard | phrase。 */
    val clipTab: String = "clipboard",
    val joystickMode: String = "cursor", // cursor | pointer
    /** 工具栏配置版本号：自定义保存后触发重组 */
    val toolbarRev: Int = 0,
)

// ── 配色：浅色/深色双主题（跟随系统或强制，参考小企鹅 fcitx5-android） ──
// 强调色由 KeyboardTheme 提供（设置 → 主题与配色 可调）。
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

/** 按深浅色 + 当前主题强调色构建配色。 */
fun buildKeyboardColors(dark: Boolean): KeyboardColors {
    val accent = Color(
        if (dark) com.azime.input.core.theme.KeyboardTheme.accentDark()
        else com.azime.input.core.theme.KeyboardTheme.accentLight()
    )
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF202124) else Color.White
    return if (dark) {
        KeyboardColors(
            bg = Color(0xFF1B1D1F), barBg = Color(0xFF26282A),
            keyBg = Color(0xFF2A2D2F), funcKeyBg = Color(0xFF3C4043),
            accentKeyBg = accent.copy(alpha = 0.35f).compositeOver(Color(0xFF2A2D2F)),
            accentKeyText = onAccent,
            accentActive = accent, accentActiveText = onAccent,
            text = Color(0xFFE8EAED), subText = Color(0xFF9AA0A6),
            joystick = Color(0xFFE57373),
        )
    } else {
        KeyboardColors(
            bg = Color(0xFFE9EBEE), barBg = Color.White,
            keyBg = Color.White, funcKeyBg = Color(0xFFD3D7DC),
            accentKeyBg = accent.copy(alpha = 0.28f).compositeOver(Color.White),
            accentKeyText = Color(0xFF202124),
            accentActive = accent, accentActiveText = onAccent,
            text = Color(0xFF202124), subText = Color(0xFF80868B),
            joystick = Color(0xFFD32F2F),
        )
    }
}

@Composable
private fun keyboardColors(): KeyboardColors =
    buildKeyboardColors(
        com.azime.input.core.theme.KeyboardTheme.isDark(isSystemInDarkTheme()),
    )

/** 键盘主题色（供设置页跟随）：回车键背景 accentKeyBg 与高亮 accentActive。 */
fun keyboardAccentKeyColor(dark: Boolean): Color =
    buildKeyboardColors(dark).accentKeyBg

fun keyboardAccentActiveColor(dark: Boolean): Color =
    buildKeyboardColors(dark).accentActive

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
    // 多字体（xime 方式）：多选字体构建回退链，键帽/候选/面板统一使用
    val kbFontFamily = remember { FontManager.keyboardFontFamily() }

    // 尺寸可调（设置页滑杆）；工具栏固定紧凑高度，「增高行」= 键盘底部额外空行
    val keyH = KeyboardManager.keyHeightDp().dp
    val barH = KeyboardManager.barHeightDp().dp
    var showToolbarCustomize by remember { mutableStateOf(false) }
    // 主键盘区标准总高（4 行 + 间距）；emoji/候选/菜单面板统一与此等高
    val stdH = keyH * 4 + KeySpacing * 5
    val areaH = if (KeyboardManager.barEnabled()) stdH + KeySpacing + barH else stdH

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = kbFontFamily ?: FontFamily.Default),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                // 沉浸式圆角：顶部两角圆角化，配合透明 IME 窗口贴合系统底部弹层样式
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(c.bg),
        ) {
            ToolbarRow(
                state = state,
                onAction = onAction,
                barHeight = 38.dp,
                onOpenCustomize = { showToolbarCustomize = true },
            )
            // 面板优先：定制工具栏 / 更多候选 / ○ 菜单 / 剪贴板 覆盖主键盘区（等高），否则显示键盘
            if (showToolbarCustomize) {
                ToolbarCustomizePanel(
                    current = KeyboardManager.toolbarItems(),
                    onSave = {
                        showToolbarCustomize = false
                        onAction(KeyAction.SetToolbarItems(it))
                    },
                    onDismiss = { showToolbarCustomize = false },
                    totalHeight = areaH,
                )
            } else if (state.showCandidatePanel) {
                CandidatePanel(state = state, onAction = onAction, totalHeight = areaH)
            } else if (state.showMenuPanel) {
                MenuPanel(
                    state = state,
                    onAction = onAction,
                    onOpenCustomize = { showToolbarCustomize = true },
                    totalHeight = areaH,
                )
            } else if (state.showClipboardPanel) {
                ClipboardPanel(state = state, onAction = onAction, totalHeight = areaH)
            } else if (state.page == "emoji" || state.page == "symgrid") {
                CategoryGridPane(
                    data = if (state.page == "emoji") EmojiGrid else SymbolGrid,
                    state = state,
                    onAction = onAction,
                    keyHeight = keyH,
                    totalHeight = areaH,
                )
            } else if (state.page == "numpad") {
                // 九宫格：5 列专用布局（左列滑动预览符号键 + 返回，中间三列数字，右列功能键）
                NumpadPane(state = state, onAction = onAction, keyHeight = keyH)
            } else {
                val layout = KeyboardManager.layoutFor(state.page) ?: KeyboardManager.mainLayout()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KeySpacing, vertical = KeySpacing),
                    verticalArrangement = Arrangement.spacedBy(KeySpacing),
                ) {
                    for (row in layout.rows) {
                        // 行高 = 标准键高 × 行内最大 height 系数（编辑器可调）
                        val rowH = keyH * (row.keys.maxOfOrNull { it.height.coerceIn(0.5f, 2f) } ?: 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowH),
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
                    // 增高行：键盘最后一行下方多一行（无按键），高度由设置滑杆控制
                    if (KeyboardManager.barEnabled()) {
                        Row(modifier = Modifier.fillMaxWidth().height(barH)) {}
                    }
                }
            }
        }
    }
}

// ── 剪贴板面板（jqb.lua 风格：剪贴板/收藏 双选项卡 + 卡片列表 + ︙菜单） ──

@Composable
private fun ClipboardPanel(state: KeyboardUiState, onAction: (KeyAction) -> Unit, totalHeight: androidx.compose.ui.unit.Dp) {
    val c = keyboardColors()
    val isPhrase = state.clipTab == "phrase"
    val items = if (isPhrase) state.phraseItems else state.clipHistory

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(c.barBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // 顶行：返回键固定左上角 + 双选项卡
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 返回键（固定左上角）
            Text(
                text = "←",
                fontSize = 17.sp,
                color = c.text,
                modifier = Modifier
                    .background(c.funcKeyBg, RoundedCornerShape(8.dp))
                    .clickable { onAction(KeyAction.ToggleClipboardPanel) }
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(8.dp))
            ClipTabLabel("剪贴板", selected = !isPhrase, c = c) {
                onAction(KeyAction.SetClipTab("clipboard"))
            }
            Spacer(Modifier.width(6.dp))
            ClipTabLabel("收藏", selected = isPhrase, c = c) {
                onAction(KeyAction.SetClipTab("phrase"))
            }
        }
        Spacer(Modifier.height(4.dp))
        if (items.isEmpty()) {
            Text(
                text = if (isPhrase) "收藏为空：在剪贴板条目的 ︙ 菜单里点「收藏」"
                else "剪贴板为空：复制文字后会自动记录在这里",
                fontSize = 13.sp,
                color = c.subText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEachIndexed { index, text ->
                    ClipCard(index = index, text = text, tab = state.clipTab, onAction = onAction)
                }
            }
        }
    }
}

/** 面板选项卡（jqb 滑块式：选中侧填充强调色）。 */
@Composable
private fun ClipTabLabel(text: String, selected: Boolean, c: KeyboardColors, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (selected) c.accentActiveText else c.subText,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(if (selected) c.accentActive else c.funcKeyBg, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    )
}

/** 条目卡片：序号 + 文本（最多 3 行）+ 标签行（英文/电话/网址等，横向滑动点选）+ ︙ 菜单。 */
@Composable
private fun ClipCard(index: Int, text: String, tab: String, onAction: (KeyAction) -> Unit) {
    val c = keyboardColors()
    var menuOpen by remember { mutableStateOf(false) }
    // 标签（jqb.lua 风格）：英文单词 / 电话号码 / 网址，常驻词条下方，左右滑动快速点选
    val tags = remember(text) {
        val urls = Regex("""(https?://\S+|www\.\S+)""").findAll(text).map { it.value }
        val phones = Regex("""\+?\d[\d-]{6,}\d""").findAll(text).map { it.value }
        val words = Regex("""[A-Za-z]{2,}""").findAll(text).map { it.value }
        (urls + phones + words).distinct().take(12).toList()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg, RoundedCornerShape(10.dp))
            .clickable { onAction(KeyAction.CommitClipText(text)) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("${index + 1}.", fontSize = 11.sp, color = c.subText, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 14.sp, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.forEach { tag ->
                        Text(
                            text = tag,
                            fontSize = 12.sp,
                            color = c.text,
                            maxLines = 1,
                            modifier = Modifier
                                .background(c.funcKeyBg, RoundedCornerShape(6.dp))
                                .clickable { onAction(KeyAction.CommitClipText(tag)) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
        Box {
            Text(
                "︙",
                fontSize = 16.sp,
                color = c.subText,
                modifier = Modifier
                    .clickable { menuOpen = true }
                    .padding(4.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (tab == "clipboard") {
                    DropdownMenuItem(
                        text = { Text("收藏") },
                        onClick = { menuOpen = false; onAction(KeyAction.ClipFav(text)) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("置顶") },
                    onClick = { menuOpen = false; onAction(KeyAction.ClipTop(tab, index)) },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { menuOpen = false; onAction(KeyAction.ClipDelete(tab, index)) },
                )
            }
        }
    }
}

// ── 工具栏：○ 菜单键（合并红摇杆，居中） + 自定义工具（先左后右） + 剪贴板条覆盖 ──

@Composable
private fun ToolbarRow(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    barHeight: androidx.compose.ui.unit.Dp,
    onOpenCustomize: () -> Unit,
) {
    val c = keyboardColors()
    var showSchemaMenu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val stepPx = with(density) { 18.dp.toPx() }

    // 剪贴板条：复制后常驻，点击直接上屏；打字/新复制时消亡（参考 复制自动添加到候选.lua）
    val clipFresh = state.clipText.isNotBlank()

    // 打字中（有输入码或候选）：输入码 + 候选上下排布覆盖整个工具栏（参考 xime）
    val composing = state.preedit.isNotEmpty() || state.candidates.isNotEmpty()

    when {
        // ── 组合行：上行输入码 + 下行候选横滚（xime 布局），两行完整显示不裁剪 ──
        composing -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = barHeight)
                .background(c.barBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (state.preedit.isNotEmpty()) {
                    Text(
                        text = state.preedit,
                        fontSize = 12.sp,
                        color = c.subText,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.candidates.forEachIndexed { index, candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onAction(KeyAction.Candidate(index)) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            if (index < 9) {
                                Text("${index + 1} ", fontSize = 11.sp, color = c.subText)
                            }
                            Text(candidate.text, fontSize = 18.sp, maxLines = 1, color = c.text)
                            if (candidate.comment.isNotBlank()) {
                                Spacer(Modifier.width(3.dp))
                                Text(candidate.comment, fontSize = 10.sp, maxLines = 1, color = c.subText)
                            }
                        }
                    }
                }
            }
            if (state.hasPrevPage) {
                Text(
                    "◀",
                    fontSize = 13.sp,
                    color = c.subText,
                    modifier = Modifier
                        .clickable { onAction(KeyAction.PageUp) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            if (state.hasNextPage) {
                Text(
                    "▶",
                    fontSize = 13.sp,
                    color = c.subText,
                    modifier = Modifier
                        .clickable { onAction(KeyAction.PageDown) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            if (state.candidates.isNotEmpty()) {
                // 更多候选面板入口（打字时打开大面板选字）
                Text(
                    "▾",
                    fontSize = 16.sp,
                    color = c.subText,
                    modifier = Modifier
                        .clickable { onAction(KeyAction.ToggleCandidatePanel) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
        // ── 剪贴板条：复制内容覆盖整条工具栏，点击直接上屏 ──
        clipFresh -> Text(
            text = state.clipText.replace("\n", " "),
            fontSize = 14.sp,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(c.barBg)
                .background(c.bg, RoundedCornerShape(6.dp))
                .clickable { onAction(KeyAction.CommitClipboard(state.clipText)) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        // ── 常规工具栏：左工具 · ○（菜单+光标摇杆合一，居中） · 右工具 ──
        else -> {
            val items = KeyboardManager.toolbarItems()
            val leftItems = items.take((items.size + 1) / 2)
            val rightItems = items.drop((items.size + 1) / 2)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .background(c.barBg)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧工具（先左）
                leftItems.forEach { id -> toolbarToolItem(id, state, onAction, c) { showSchemaMenu = true } }

                Spacer(Modifier.weight(1f))

                // ── ○ 菜单键（居中，以 ○ 外观为准）：点击开菜单 / 长按定制工具栏 / 横向拖动移光标（原红摇杆） ──
                var oPressing by remember { mutableStateOf(false) }
                var oLongFired by remember { mutableStateOf(false) }
                LaunchedEffect(oPressing) {
                    if (oPressing) {
                        kotlinx.coroutines.delay(400)
                        if (oPressing && !oLongFired) {
                            oLongFired = true
                            onOpenCustomize()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(c.bg, CircleShape)
                        .pointerInput(state.joystickMode) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                oLongFired = false
                                oPressing = true
                                var anchor: androidx.compose.ui.geometry.Offset? = null
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    if (!ch.pressed) break
                                    val a = anchor
                                    if (a != null) {
                                        val dx = ch.position.x - a.x
                                        if (abs(dx) > stepPx) {
                                            onAction(KeyAction.Joystick((dx / stepPx).roundToInt()))
                                            oLongFired = true // 拖动即移光标，抑制长按定制
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            anchor = ch.position
                                        }
                                    } else {
                                        anchor = ch.position
                                    }
                                }
                                oPressing = false
                            }
                        }
                        .clickable { onAction(KeyAction.ToggleMenuPanel) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("○", fontSize = 20.sp, color = c.text, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.weight(1f))

                // 右侧工具（后右）
                rightItems.forEach { id -> toolbarToolItem(id, state, onAction, c) { showSchemaMenu = true } }

                Spacer(Modifier.width(2.dp))

                // 关闭键盘（固定工具栏最后一位）
                Text(
                    "⌄",
                    fontSize = 18.sp,
                    color = c.subText,
                    modifier = Modifier
                        .clickable { onAction(KeyAction.HideKeyboard) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
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
}

/** 工具栏单个工具项（剪贴板/方案/数字/emoji/符号/设置）。 */
@Composable
private fun RowScope.toolbarToolItem(id: String, state: KeyboardUiState, onAction: (KeyAction) -> Unit, c: KeyboardColors, onSchema: () -> Unit) {
    when (id) {
        "clipboard" -> Box(
            modifier = Modifier
                .clickable { onAction(KeyAction.ToggleClipboardPanel) }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) { Text("📋", fontSize = 14.sp, color = c.subText) }
        "schema" -> Box(
            modifier = Modifier
                .clickable { onSchema() }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) { Text("方案", fontSize = 14.sp, color = c.subText) }
        "numpad" -> Box(
            modifier = Modifier
                .clickable { onAction(KeyAction.SwitchPage(if (state.page == "numpad") "main" else "numpad")) }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) { Text(if (state.page == "numpad") "26" else "123", fontSize = 14.sp, color = c.subText) }
        "emoji" -> Box(
            modifier = Modifier
                .clickable { onAction(KeyAction.SwitchPage("emoji")) }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) { Text("☺", fontSize = 14.sp, color = c.subText) }
        "symbols" -> Box(
            modifier = Modifier
                .clickable { onAction(KeyAction.SwitchPage("symgrid")) }
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
// ── ○ 菜单面板（参考 xime MenuBar：顶部关闭/设置 + 图标网格分页 + 方案 chips） ──

@Composable
private fun MenuPanel(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    onOpenCustomize: () -> Unit,
    totalHeight: androidx.compose.ui.unit.Dp,
) {
    val c = keyboardColors()
    val density = LocalDensity.current
    var showSchemaPopup by remember { mutableStateOf(false) }
    fun close() = onAction(KeyAction.ToggleMenuPanel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(c.barBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶行：↑ 关闭（左） + ⚙ 设置（右），仿 xime
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(c.funcKeyBg, CircleShape)
                    .clickable { close() },
                contentAlignment = Alignment.Center,
            ) { Text("↑", fontSize = 16.sp, color = c.text, fontWeight = FontWeight.Bold) }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(c.funcKeyBg, CircleShape)
                    .clickable { close(); onAction(KeyAction.OpenSettings) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = c.text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 图标网格：4 列（icon + label，仿 xime MenuItemButton）
        val menuItems: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = listOf(
            Triple(Icons.AutoMirrored.Filled.Assignment, "剪贴板") { close(); onAction(KeyAction.ToggleClipboardPanel) },
            Triple(Icons.Default.Keyboard, "26键") { close(); onAction(KeyAction.SwitchPage("main")) },
            Triple(Icons.Default.Dialpad, "数字") { close(); onAction(KeyAction.SwitchPage("numpad")) },
            Triple(Icons.Default.EmojiEmotions, "表情") { close(); onAction(KeyAction.SwitchPage("emoji")) },
            Triple(Icons.Default.Category, "符号") { close(); onAction(KeyAction.SwitchPage("symgrid")) },
            Triple(Icons.Default.List, "输入方案") { showSchemaPopup = true },
            Triple(Icons.Default.Sync, "部署") { close(); onAction(KeyAction.Deploy) },
            Triple(Icons.Default.Tune, "定制工具栏") { close(); onOpenCustomize() },
            Triple(
                Icons.Default.MyLocation,
                if (state.joystickMode == "pointer") "指针模式✓" else "光标模式",
            ) { onAction(KeyAction.SetJoystickMode(if (state.joystickMode == "pointer") "cursor" else "pointer")) },
        )
        menuItems.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { (icon, label, action) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(c.keyBg, RoundedCornerShape(12.dp))
                            .clickable { action() }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon, contentDescription = label, tint = c.text.copy(alpha = 0.75f), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(label, fontSize = 10.sp, color = c.text, maxLines = 1)
                    }
                }
                // 补位空格保持 4 列
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 输入方案浮窗（「输入方案」磁贴呼出）
        if (showSchemaPopup) {
            Popup(
                alignment = Alignment.Center,
                onDismissRequest = { showSchemaPopup = false },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 300.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .background(c.barBg, RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        "选择输入方案",
                        fontSize = 12.sp,
                        color = c.subText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    if (state.schemas.isEmpty()) {
                        Text(
                            "引擎部署中…",
                            fontSize = 14.sp,
                            color = c.subText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    state.schemas.forEach { schemaId ->
                        Text(
                            text = schemaDisplay(schemaId).removePrefix("○输入法 · "),
                            fontSize = 15.sp,
                            color = if (schemaId == state.schemaName) c.accentActive else c.text,
                            fontWeight = if (schemaId == state.schemaName) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable {
                                    showSchemaPopup = false
                                    close()
                                    if (schemaId != state.schemaName) onAction(KeyAction.SelectSchema(schemaId))
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 工具栏自定义面板（○ 菜单「定制工具栏」/ 长按 ○ 呼出）。
 * 与主键盘同高（覆盖键盘区，不超出键盘外）。
 * 注意：不用 AlertDialog——Compose 对话框窗口 z-order 低于 IME 窗口会被键盘挡住，
 * 键盘内的弹层一律使用内联面板或 Popup。
 */
@Composable
private fun ToolbarCustomizePanel(
    current: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    totalHeight: androidx.compose.ui.unit.Dp,
) {
    val c = keyboardColors()
    val selected = remember { mutableStateListOf<String>().apply { addAll(current) } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(c.barBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("定制工具栏", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
        Spacer(Modifier.height(2.dp))
        Text(
            "○ 菜单键固定居中（拖动移光标）；勾选的工具先左后右排列在 ○ 两侧。",
            fontSize = 12.sp,
            color = c.subText,
        )
        Spacer(Modifier.height(6.dp))
        KeyboardManager.availableToolbarTools.forEach { (id, name) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (id in selected) selected.remove(id) else selected.add(id)
                    },
            ) {
                Checkbox(checked = id in selected, onCheckedChange = { on ->
                    if (on) selected.add(id) else selected.remove(id)
                })
                Text(name, fontSize = 14.sp, color = c.text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = { onSave(selected.toList()) }, modifier = Modifier.weight(1f)) {
                Text("保存")
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
        }
    }
}

// ── 更多候选面板：网格展示当前页全部候选，◀▶ 翻页，点选上屏 ──

@Composable
private fun CandidatePanel(state: KeyboardUiState, onAction: (KeyAction) -> Unit, totalHeight: androidx.compose.ui.unit.Dp) {
    val c = keyboardColors()
    val keyH = KeyboardManager.keyHeightDp().dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(c.bg)
            .padding(horizontal = KeySpacing, vertical = KeySpacing),
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        // 顶行：标题 + 翻页 + 收起
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("更多候选", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text)
            Spacer(Modifier.weight(1f))
            if (state.hasPrevPage) {
                Text(
                    "◀",
                    fontSize = 16.sp,
                    color = c.text,
                    modifier = Modifier
                        .clickable { onAction(KeyAction.PageUp) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (state.hasNextPage) {
                Text(
                    "▶",
                    fontSize = 16.sp,
                    color = c.text,
                    modifier = Modifier
                        .clickable { onAction(KeyAction.PageDown) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Text(
                "收起 ▲",
                fontSize = 12.sp,
                color = c.subText,
                modifier = Modifier
                    .clickable { onAction(KeyAction.ToggleCandidatePanel) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        // 候选网格：5 列（选中后候选变化，候选为空时 Service 自动收起面板）
        if (state.candidates.isEmpty()) {
            Text(
                "暂无候选：请先输入编码",
                fontSize = 13.sp,
                color = c.subText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        } else {
            var base = 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KeySpacing),
            ) {
                state.candidates.chunked(5).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(keyH),
                        horizontalArrangement = Arrangement.spacedBy(KeySpacing),
                    ) {
                        rowItems.forEachIndexed { i, candidate ->
                            val idx = base + i
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(c.keyBg, RoundedCornerShape(8.dp))
                                    .clickable { onAction(KeyAction.Candidate(idx)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (idx < 9) {
                                        Text("${idx + 1} ", fontSize = 11.sp, color = c.subText)
                                    }
                                    Text(
                                        candidate.text,
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        color = c.text,
                                    )
                                }
                            }
                        }
                        repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                    base += rowItems.size
                }
            }
        }
    }
}

// ── emoji 键盘 ───────────────────────────────────────────────

// ── 分类网格键盘（emoji / 符号共用）：标签行 + 左右滑动翻页 ──

/** 分类网格键盘数据源：标题 + (分类标签, 内容列表)。 */
private data class CategoryGridData(
    val title: String,
    val categories: List<Pair<String, List<String>>>,
)

private val EmojiGrid = CategoryGridData("emoji", EmojiData.categories)
private val SymbolGrid = CategoryGridData("symgrid", SymbolData.categories)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryGridPane(
    data: CategoryGridData,
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    keyHeight: androidx.compose.ui.unit.Dp,
    totalHeight: androidx.compose.ui.unit.Dp,
) {
    val c = keyboardColors()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { data.categories.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .padding(KeySpacing),
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        // 顶行：返回键固定左上角 + 分类标签（点标签翻页）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 返回键（固定左上角）
            Text(
                text = "←",
                fontSize = 17.sp,
                color = c.text,
                modifier = Modifier
                    .background(c.funcKeyBg, RoundedCornerShape(8.dp))
                    .clickable { onAction(KeyAction.SwitchPage("main")) }
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(4.dp))
            // 分类标签（横向滑动）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                data.categories.forEachIndexed { i, (label, _) ->
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        color = if (i == pagerState.currentPage) c.accentActive else c.subText,
                        modifier = Modifier
                            .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }
        // 网格内容：左右滑动切分类（占满剩余高度，单页可竖向滚动）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val items = data.categories.getOrNull(page)?.second ?: emptyList()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KeySpacing),
            ) {
                items.chunked(8).forEach { chunk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(keyHeight),
                        horizontalArrangement = Arrangement.spacedBy(KeySpacing),
                    ) {
                        chunk.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(c.keyBg, RoundedCornerShape(8.dp))
                                    .clickable { onAction(KeyAction.DirectCommit(item)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item, fontSize = 21.sp, maxLines = 1)
                            }
                        }
                        if (chunk.size < 8) {
                            Spacer(Modifier.weight((8 - chunk.size).toFloat()))
                        }
                    }
                }
            }
        }
    }
}


// ── 九宫格：5 列专用布局 ─────────────────────────────────────
// 左列 = 3 行高「滑动预览符号」键（上下滑动选择，点击上屏）+ 返回键
// 中间三列 = 1-0 十个数字 + "." "," 两个符号；右列 = ⌫ / 符号面板 / 空格 / ⏎

@Composable
private fun NumpadPane(state: KeyboardUiState, onAction: (KeyAction) -> Unit, keyHeight: androidx.compose.ui.unit.Dp) {
    val topRows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KeySpacing),
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        // 前 3 行：符号滑键（跨 3 行）+ 数字三列 + 功能三键
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight * 3 + KeySpacing * 2),
            horizontalArrangement = Arrangement.spacedBy(KeySpacing),
        ) {
            NumpadSliderKey(onAction = onAction, modifier = Modifier.weight(1f).fillMaxSize())
            topRows.forEach { col ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(KeySpacing),
                ) {
                    col.forEach { digit ->
                        Row(Modifier.weight(1f)) {
                            KeyboardKey(
                                key = Key(digit, code = digit, width = 1f, type = KeyType.CHARACTER),
                                state = state, onAction = onAction,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KeySpacing),
            ) {
                listOf(
                    Key("⌫", code = "backspace", width = 1f, type = KeyType.DELETE),
                    Key("符", code = "symgrid", width = 1f, type = KeyType.FUNCTION),
                    Key("空格", code = "space", width = 1f, type = KeyType.SPACE),
                ).forEach { k ->
                    Row(Modifier.weight(1f)) { KeyboardKey(key = k, state = state, onAction = onAction) }
                }
            }
        }
        // 第 4 行：返回 + 0 . , + ⏎
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight),
            horizontalArrangement = Arrangement.spacedBy(KeySpacing),
        ) {
            KeyboardKey(
                key = Key("返回", code = "main", width = 1f, type = KeyType.FUNCTION),
                state = state, onAction = onAction,
            )
            listOf("0", ".", ",").forEach { ch ->
                KeyboardKey(
                    key = Key(ch, code = ch, width = 1f, type = KeyType.CHARACTER),
                    state = state, onAction = onAction,
                )
            }
            KeyboardKey(
                key = Key("⏎", code = "enter", width = 1f, type = KeyType.ENTER),
                state = state, onAction = onAction,
            )
        }
    }
}

/**
 * 九宫格左列滑动预览符号键：上下滑动在符号带上选择（仅预览，松手不上屏），
 * 点击键面上屏当前选中符号（默认中间位），上屏后回到中间。
 */
@Composable
private fun NumpadSliderKey(onAction: (KeyAction) -> Unit, modifier: Modifier) {
    val c = keyboardColors()
    val symbols = KeyboardManager.sliderSymbols()
    var selIdx by remember { mutableStateOf(symbols.size / 2) }
    var selecting by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .background(c.funcKeyBg, RoundedCornerShape(8.dp))
            .pointerInput(symbols) {
                val stepPx = with(density) { 34.dp.toPx() }
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    selecting = true
                    var anchorY: Float? = null
                    var startIdx = selIdx
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        if (anchorY == null) anchorY = ch.position.y
                        val steps = ((anchorY - ch.position.y) / stepPx).toInt()
                        val target = (startIdx + steps).coerceIn(0, symbols.size - 1)
                        if (target != selIdx) {
                            selIdx = target
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    // 滑动选择-松手上屏（轮7回退轮6的「点击上屏」交互）；未滑动 = 直接上屏当前符号
                    selecting = false
                    onAction(KeyAction.DirectCommit(symbols[selIdx]))
                    selIdx = symbols.size / 2
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = symbols.getOrNull(selIdx - 1) ?: "",
                fontSize = 13.sp,
                color = c.subText,
                maxLines = 1,
            )
            Text(
                text = symbols.getOrNull(selIdx) ?: "",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (selecting) c.accentActive else c.text,
                maxLines = 1,
            )
            Text(
                text = symbols.getOrNull(selIdx + 1) ?: "",
                fontSize = 13.sp,
                color = c.subText,
                maxLines = 1,
            )
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
        // 空格键显示：自定义文本 > 当前方案名（短名） > 默认「空格」
        key.code == "space" && state.page == "main" -> {
            val custom = KeyboardManager.spaceLabel()
            when {
                custom.isNotBlank() -> custom
                state.schemaName.isNotBlank() -> state.schemaName.substringAfterLast('.')
                else -> key.label
            }
        }
        key.type != KeyType.CHARACTER -> key.label
        // 键帽显示：中文模式大写、英文模式小写（输入逻辑不变：中文仍送小写编码）
        state.asciiMode -> key.label.lowercase()
        else -> key.label
    }

    // 占位空键：不渲染背景、不响应手势（用于行内对齐偏移）
    if (key.code == "spacer") {
        Box(Modifier.weight(key.width))
        return
    }

    // ── 组合中的预设选候选键（trime2 26键.lua composing=select_2/3/4 思路） ──
    // 打字时：123 键 = 第三候选、句号键 = 第二候选、空格键 = 第一候选，
    // 键面直接显示对应候选文本，点击即上屏该候选（无对应候选时退回原动作）。
    val composingNow = state.preedit.isNotEmpty() || state.candidates.isNotEmpty()
    if (composingNow && state.page == "main") {
        val selectIndex = when {
            key.type == KeyType.FUNCTION && key.code == "symbols" -> 2
            key.type == KeyType.CHARACTER && key.code == "." -> 1
            key.type == KeyType.SPACE && key.code == "space" -> 0
            else -> -1
        }
        if (selectIndex >= 0) {
            val candText = state.candidates.getOrNull(selectIndex)?.text.orEmpty()
            // 无对应候选时退回原动作（空格仍上屏空格，句号/123 走原逻辑）
            if (candText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(key.width)
                        .fillMaxSize()
                        .background(c.accentKeyBg, RoundedCornerShape(8.dp))
                        .clickable {
                            HapticsManager.press()
                            onAction(KeyAction.Candidate(selectIndex))
                            HapticsManager.release()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = candText,
                        fontSize = if (candText.length <= 4) 14.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.accentKeyText,
                        maxLines = 1,
                    )
                }
                return
            }
        }
    }

    // 长按符号：内置映射（用户规范）或 preset_keys 条目；K 键 = 常用括号气泡（26键.lua）
    val longPressSymbols = remember(key.code, state.page) {
        if (state.page != "symbols" && key.type == KeyType.CHARACTER) {
            if (key.code == "k") BracketPairs
            else LongPressSymbols[key.code.firstOrNull()] ?: emptyList()
        } else emptyList()
    }
    /** 气泡/松手提交：内置命令走命令分发，{Left} 后缀走文本+光标移动，其他字面上屏。 */
    fun commitLongSymbol(s: String) {
        when {
            s == "select_all" || s == "cut" || s == "copy" || s == "paste" ->
                onAction(KeyAction.Resolved(s))
            s.endsWith("{Left}") -> onAction(KeyAction.Resolved(s))
            else -> onAction(KeyAction.DirectCommit(s))
        }
    }
    val hasCustomLong = !key.longClick.isNullOrBlank()
    val isPageKey = key.type == KeyType.FUNCTION && key.code == "symbols"
    val autoRepeat = key.type == KeyType.DELETE

    var pressing by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    var showBubble by remember { mutableStateOf(false) }
    var showPageBubble by remember { mutableStateOf(false) }
    var swipePreview by remember { mutableStateOf<String?>(null) }
    // 长按气泡滑动选择的符号下标（多符号时滑动切换，松手上屏）
    var longSelIdx by remember { mutableStateOf(0) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val bubbleOffset = with(density) { IntOffset(0, -58.dp.roundToPx()) }
    val swipeThreshold = with(density) { 30.dp.toPx() }
    // 长按气泡滑动选择步长（每个符号占 40dp）
    val longStepPx = with(density) { 40.dp.toPx() }

    val hasGestures = longPressSymbols.isNotEmpty() || autoRepeat || hasCustomLong || isPageKey ||
        key.swipeUp != null || key.swipeDown != null || key.swipeLeft != null || key.swipeRight != null

    // 长按定时器（触发时间 180ms）
    LaunchedEffect(pressing) {
        if (pressing) {
            delay(180)
            if (pressing && !longFired) {
                when {
                    longPressSymbols.isNotEmpty() -> {
                        longFired = true
                        longSelIdx = 0
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
                HapticsManager.press()
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
                        if (longFired && longPressSymbols.isNotEmpty()) {
                            // 长按气泡已弹出：横向滑动选择符号（多符号时），松手上屏（不触发四向手势）
                            if (longPressSymbols.size > 1) {
                                val idx = (dx / longStepPx).roundToInt().coerceIn(0, longPressSymbols.size - 1)
                                if (idx != longSelIdx) {
                                    longSelIdx = idx
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        } else {
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
                                        // 滑动预览受设置内四向开关控制（动作照常执行）
                                        val hintOn = when (dir) {
                                            Dir.UP -> KeyboardManager.hintUp()
                                            Dir.DOWN -> KeyboardManager.hintDown()
                                            Dir.LEFT -> KeyboardManager.hintLeft()
                                            Dir.RIGHT -> KeyboardManager.hintRight()
                                        }
                                        if (hintOn) swipePreview = actionPreview(action)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
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
                HapticsManager.release()
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
                    // 长按气泡：松手直接上屏当前选中符号（单符号 = 首个；多符号 = 滑动选中项）
                    longFired && showBubble && longPressSymbols.isNotEmpty() -> {
                        val symbol = longPressSymbols[
                            if (longPressSymbols.size == 1) 0 else longSelIdx.coerceIn(0, longPressSymbols.size - 1)
                        ]
                        commitLongSymbol(symbol)
                        longSelIdx = 0
                        showBubble = false
                    }
                    !longFired -> onKeyAction(key, onAction)
                }
            }
        }
    } else {
        baseModifier = baseModifier.clickable {
            HapticsManager.press()
            onKeyAction(key, onAction)
            HapticsManager.release()
        }
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
        // 键面右上角长按符号提示（受设置「长按符号提示」开关控制）
        val hintText = key.hint ?: longPressHint(key.code)
        if (swipePreview == null && KeyboardManager.hintLong() && hintText != null && key.type == KeyType.CHARACTER) {
            Text(
                text = hintText,
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
                    longPressSymbols.forEachIndexed { si, symbol ->
                        // 滑动选中的符号高亮（多符号时）；点击气泡亦可直接上屏
                        val isSel = longPressSymbols.size > 1 && si == longSelIdx
                        Text(
                            text = symbol.removeSuffix("{Left}"),
                            fontSize = 22.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) c.accentActive else c.text,
                            modifier = Modifier
                                .background(
                                    if (isSel) c.accentKeyBg else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    commitLongSymbol(symbol)
                                    longSelIdx = 0
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
            "main" -> onAction(KeyAction.SwitchPage("main"))
            "numpad" -> onAction(KeyAction.SwitchPage("numpad"))
            "symgrid" -> onAction(KeyAction.SwitchPage("symgrid"))
            "emoji" -> onAction(KeyAction.SwitchPage("emoji"))
            // lua 布局的自定义 FUNCTION 键：命令 / preset 引用 / 文本上屏（trime2 语义，Service 端解析）
            else -> {
                if (LuaScriptManager.resolveAction(key.code) != null) {
                    onAction(KeyAction.Resolved(key.code))
                } else {
                    onAction(KeyAction.ToggleAscii)
                }
            }
        }
    }
}

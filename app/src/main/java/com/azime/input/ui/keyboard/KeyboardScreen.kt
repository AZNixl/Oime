package com.azime.input.ui.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlaylistAddCheck
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.azime.input.core.rime.RimeManager
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
import kotlin.math.sqrt

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
    /** ○ 菜单「方案组」（轮18 trime2 架构）：切换方案组（记录组 id + 重启进程）。 */
    data class SelectSchemaGroup(val groupId: String) : KeyAction
    /** ○ 键长按语音输入（轮15）：空闲→开始识别；识别中→停止并上屏结果。 */
    data object ToggleVoiceInput : KeyAction
    /** ○ 菜单「方案开关」：切换当前方案 schema.yaml 的 switches 开关。 */
    data class ToggleSwitch(val name: String) : KeyAction

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
    /** 打开键盘布局编辑器（反馈轮16：O 菜单直达入口）。 */
    data object OpenKeyboardEditor : KeyAction
    /** 重新部署方案（方案设置页 / ○ 菜单）。 */
    data object Deploy : KeyAction
    /** ○ 菜单亮暗切换（反馈轮12）：翻转 KeyboardTheme 深浅模式并立即换色。 */
    data object ToggleThemeMode : KeyAction
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
    /** 轮19.1：分词——把条目按标点/空白切分成词条，逐条加入剪贴板历史，便于逐词上屏。 */
    data class ClipSplit(val text: String) : KeyAction
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
    /** 语音输入状态（轮15）：idle | listening（listening 时声纹动画覆盖工具栏，点击结束）。 */
    val voiceState: String = "idle",
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
    /** 主题亮暗版本号：O 菜单切换后触发整键盘重组换色（反馈轮12）。 */
    val themeRev: Int = 0,
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
    voiceRms: androidx.compose.runtime.State<Float>,
    modifier: Modifier = Modifier,
) {
    val c = keyboardColors()
    // 多字体（xime 方式）：多选字体构建回退链，键帽/候选/面板统一使用
    val kbFontFamily = remember { FontManager.keyboardFontFamily() }

    // 尺寸可调（设置页滑杆）；工具栏高度（增高 1/5=46dp），「增高行」= 键盘底部额外空行
    val keyH = KeyboardManager.keyHeightDp().dp
    val barH = KeyboardManager.barHeightDp().dp
    // 反馈轮9：按键圆角/行距/列距可调（默认 8dp / 4dp / 4dp）
    val keyCorner = KeyboardManager.keyCornerDp().dp
    val rowGap = KeyboardManager.rowGapDp().dp
    val colGap = KeyboardManager.colGapDp().dp
    // 主键盘区标准总高（4 行 + 间距）；emoji/候选/菜单面板统一与此等高
    val stdH = keyH * 4 + rowGap * 5
    val areaH = if (KeyboardManager.barEnabled()) stdH + rowGap + barH else stdH

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = kbFontFamily ?: FontFamily.Default),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                // 反馈轮9：工具栏与键盘同色且不再做顶部圆角（整体平直，贴系统底栏）
                .background(c.bg),
        ) {
            ToolbarRow(
                state = state,
                onAction = onAction,
                // 反馈轮11：工具栏高度按 ○ 菜单键（36dp）+ 上下 4dp 留白 = 44dp，整体居中
                barHeight = 44.dp,
                voiceRms = voiceRms,
            )
            // 面板优先：更多候选 / ○ 菜单 / 剪贴板 覆盖主键盘区（等高），否则显示键盘
            if (state.showCandidatePanel) {
                CandidatePanel(state = state, onAction = onAction, totalHeight = areaH)
            } else if (state.showMenuPanel) {
                MenuPanel(
                    state = state,
                    onAction = onAction,
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
                        .padding(horizontal = colGap, vertical = rowGap),
                    verticalArrangement = Arrangement.spacedBy(rowGap),
                ) {
                    for (row in layout.rows) {
                        // 行高 = 标准键高 × 行内最大 height 系数（编辑器可调）
                        val rowH = keyH * (row.keys.maxOfOrNull { it.height.coerceIn(0.5f, 2f) } ?: 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowH),
                            horizontalArrangement = Arrangement.spacedBy(colGap),
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

            // ── 悬浮窗（编码预览，参考 trime 悬浮窗 / 悬浮窗显示优化.lua）──
            // 输入时在键盘上方悬浮显示输入码；默认模式固定样式，自定义模式位置/字号/透明度可调
            if (KeyboardManager.floatEnabled() && state.preedit.isNotEmpty()) {
                val density = LocalDensity.current
                val custom = KeyboardManager.floatMode() == "custom"
                val fx = KeyboardManager.floatXDp().dp
                val fy = KeyboardManager.floatYDp().dp
                val bgAlpha = if (custom) KeyboardManager.floatBgAlpha() / 100f else 0.92f
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(
                        with(density) { fx.roundToPx() },
                        with(density) { -fy.roundToPx() },
                    ),
                ) {
                    Text(
                        text = state.preedit,
                        fontSize = KeyboardManager.floatTextSp().sp,
                        color = c.text,
                        maxLines = 1,
                        modifier = Modifier
                            .background(c.barBg.copy(alpha = bgAlpha), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(c.barBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
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
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEachIndexed { index, text ->
                        ClipCard(index = index, text = text, tab = state.clipTab, onAction = onAction)
                    }
                    // 底部留白避开悬浮栏
                    Spacer(Modifier.height(56.dp))
                }
            }
        }
        // 底部悬浮栏（反馈轮9，参考 PiliPlus）：返回键 + 选项卡；轮10 半透明 + 同心圆角（外R22 内R18）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .background(c.funcKeyBg.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "←",
                fontSize = 17.sp,
                color = c.text,
                modifier = Modifier
                    .clickable { onAction(KeyAction.ToggleClipboardPanel) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            ClipTabLabel("剪贴板", selected = !isPhrase, c = c) {
                onAction(KeyAction.SetClipTab("clipboard"))
            }
            ClipTabLabel("收藏", selected = isPhrase, c = c) {
                onAction(KeyAction.SetClipTab("phrase"))
            }
        }
    }
}

/** 面板选项卡（jqb 滑块式：选中侧填充强调色；轮10 内圆角 R18 与外层 R22 同心）。 */
@Composable
private fun ClipTabLabel(text: String, selected: Boolean, c: KeyboardColors, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (selected) c.accentActiveText else c.subText,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(if (selected) c.accentActive else c.funcKeyBg, RoundedCornerShape(18.dp))
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
                // 轮19.1：分词（拆成词条入历史，便于逐词取用）
                DropdownMenuItem(
                    text = { Text("分词") },
                    onClick = { menuOpen = false; onAction(KeyAction.ClipSplit(text)) },
                )
                DropdownMenuItem(
                    text = { Text("置顶") },
                    onClick = { menuOpen = false; onAction(KeyAction.ClipTop(tab, index)) },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { menuOpen = false; onAction(KeyAction.ClipDelete(tab, index)) },
                )
                // 轮19.1：全清（清空当前列表：剪贴板历史 / 收藏短语）
                DropdownMenuItem(
                    text = { Text(if (tab == "clipboard") "全清（历史）" else "全清（收藏）") },
                    onClick = { menuOpen = false; onAction(KeyAction.ClipClear(tab)) },
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
    voiceRms: androidx.compose.runtime.State<Float>,
) {
    val c = keyboardColors()
    var showSchemaMenu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // ○ 拖动步长：18dp/步（反馈轮10：指针模式已去除）
    val stepPx = with(density) { 18.dp.toPx() }

    // ── 语音输入（轮15）：识别中整条工具栏替换为声纹动画，点击结束 ──
    if (state.voiceState == "listening") {
        VoiceWavePanel(
            rms = voiceRms,
            onStop = { onAction(KeyAction.ToggleVoiceInput) },
            barHeight = barHeight,
        )
        return
    }

    // 剪贴板条：复制后常驻，点击直接上屏；打字/新复制时消亡（参考 复制自动添加到候选.lua）
    val clipFresh = state.clipText.isNotBlank()

    // 打字中（有输入码或候选）：输入码 + 候选上下排布覆盖整个工具栏（反馈轮9）
    val composing = state.preedit.isNotEmpty() || state.candidates.isNotEmpty()

    when {
        // ── 组合行：上下排布（上=输入码小字，下=候选横滚），高度恒定不加高 ──
        composing -> Column(
            modifier = Modifier
                .fillMaxWidth()
                // 反馈轮10：与常规工具栏同为 barHeight，打字时不再加高（对齐 xime.az）
                .height(barHeight)
                .background(c.bg)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            if (state.preedit.isNotEmpty()) {
                Text(
                    text = state.preedit,
                    // 轮18.2：输入码小字随候选字号缩放
                    fontSize = (KeyboardManager.fontSizeBar() * 0.7f).sp,
                    lineHeight = (KeyboardManager.fontSizeBar() * 0.82f).sp,
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
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    ) {
                        if (index < 9) {
                            Text("${index + 1} ", fontSize = (KeyboardManager.fontSizeBar() * 0.62f).sp, color = c.subText)
                        }
                        // 轮18.2：候选字号接入设置（fontSizeBar，默认 16）
                        Text(candidate.text, fontSize = KeyboardManager.fontSizeBar().sp, maxLines = 1, color = c.text)
                        if (candidate.comment.isNotBlank()) {
                            Spacer(Modifier.width(3.dp))
                            Text(candidate.comment, fontSize = (KeyboardManager.fontSizeBar() * 0.62f).sp, maxLines = 1, color = c.subText)
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
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (state.hasNextPage) {
                    Text(
                        "▶",
                        fontSize = 13.sp,
                        color = c.subText,
                        modifier = Modifier
                            .clickable { onAction(KeyAction.PageDown) }
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (state.candidates.isNotEmpty()) {
                    // 更多候选面板入口（打字时打开大面板选字）
                    Text(
                        "▾",
                        fontSize = 15.sp,
                        color = c.subText,
                        modifier = Modifier
                            .clickable { onAction(KeyAction.ToggleCandidatePanel) }
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
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
                .background(c.bg)
                .clickable { onAction(KeyAction.CommitClipboard(state.clipText)) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        // ── 常规工具栏：工具在 ○ 两侧剩余空间内居中排列（反馈轮10，⌄ 关闭键已删除） ──
        else -> {
            val items = KeyboardManager.toolbarItems()
            val leftItems = items.take((items.size + 1) / 2)
            val rightItems = items.drop((items.size + 1) / 2)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .background(c.bg)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧工具（剩余空间内居中）
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leftItems.forEach { id -> toolbarToolItem(id, state, onAction, c) { showSchemaMenu = true } }
                }

                // ── ○ 菜单键（居中，圆环造型）：点击开菜单 / 长按定制工具栏 /
                //    横向拖动移光标（圆环内点跟随手指、限幅环内）/ 下滑收起键盘 ──
                var oPressing by remember { mutableStateOf(false) }
                var oLongFired by remember { mutableStateOf(false) }
                // 圆环内点偏移（拖动时跟随手指，限幅在圆环半径内；反馈轮10）
                var ringKnob by remember { mutableStateOf(Offset.Zero) }
                LaunchedEffect(oPressing) {
                    if (oPressing) {
                        kotlinx.coroutines.delay(400)
                        if (oPressing && !oLongFired) {
                            oLongFired = true
                            // 轮15：长按 ○ = 语音输入（定制工具栏入口保留在 ○ 菜单）
                            onAction(KeyAction.ToggleVoiceInput)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(29.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                oLongFired = false
                                oPressing = true
                                ringKnob = Offset.Zero
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val capR = minOf(size.width, size.height) / 2f - 5f
                                val downPx = with(this@pointerInput) { 40.dp.toPx() }
                                var anchor: Offset? = null
                                var swipedDown = false
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    if (!ch.pressed) {
                                        // 反馈轮16：长按（语音）/拖动（移光标）后的抬起事件消费掉，
                                        // 不再交给后面的 clickable —— 避免松手时又弹出 ○ 菜单
                                        if (oLongFired) ch.consume()
                                        break
                                    }
                                    if (anchor == null) anchor = ch.position
                                    val a = anchor!!
                                    val dx = ch.position.x - a.x
                                    val dy = ch.position.y - a.y
                                    if (dy > downPx && abs(dy) > abs(dx)) {
                                        // 下滑：收起键盘（反馈轮10：取代工具栏 ⌄ 关闭键）
                                        swipedDown = true
                                        oLongFired = true
                                        break
                                    }
                                    if (abs(dx) > stepPx) {
                                        onAction(KeyAction.Joystick((dx / stepPx).roundToInt()))
                                        oLongFired = true // 拖动即移光标，抑制长按定制
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        anchor = ch.position
                                    }
                                    // 圆环内点跟随手指，限幅：移动距离不超过圆环中心点（环半径内）
                                    if (oLongFired) {
                                        val off = Offset(ch.position.x - cx, ch.position.y - cy)
                                        val r = sqrt(off.x * off.x + off.y * off.y)
                                        ringKnob = if (r > capR && r > 0f) off * (capR / r) else off
                                    }
                                }
                                if (swipedDown) onAction(KeyAction.HideKeyboard)
                                oPressing = false
                                ringKnob = Offset.Zero
                            }
                        }
                        .clickable { onAction(KeyAction.ToggleMenuPanel) },
                    contentAlignment = Alignment.Center,
                ) {
                    // 圆环（轮15 重构）：静态白色实线圆环 + 呼吸动画（alpha 0.55~1.0 缓变，不刺眼）；
                    // 尺寸缩小 1/5（36→29dp）、描边加粗 1/4（2.5→3.1dp）；
                    // 按下变强调色、拖动内点跟随手指（行为保留）
                    val breath = rememberInfiniteTransition(label = "oBreath").animateFloat(
                        0.55f, 1f,
                        infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "oBreathAlpha",
                    )
                    Canvas(Modifier.size(29.dp)) {
                        val strokeW = 3.1.dp.toPx()
                        val ringR = size.minDimension / 2f - strokeW - 1f
                        drawCircle(
                            color = if (oPressing) c.accentActive
                            else Color.White.copy(alpha = breath.value),
                            radius = ringR,
                            style = Stroke(strokeW),
                        )
                        val knob = ringKnob
                        if (knob != Offset.Zero) {
                            drawCircle(c.accentActive, radius = 3.dp.toPx(), center = center + knob)
                        }
                    }
                }

                // 右侧工具（剩余空间内居中；收起键盘改为 ○ 键下滑手势）
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rightItems.forEach { id -> toolbarToolItem(id, state, onAction, c) { showSchemaMenu = true } }
                }
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
                        text = RimeManager.schemaDisplayName(schemaId),
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

/**
 * 语音输入声纹面板（轮15）：覆盖工具栏区域。
 * RMS 驱动波形振幅（SpeechRecognizer onRmsChanged），叠加时间相位正弦波让波形自然流动；
 * 点击结束（ToggleVoiceInput 由 Service 停止识别并上屏结果）。
 */
@Composable
private fun VoiceWavePanel(
    rms: androidx.compose.runtime.State<Float>,
    onStop: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp,
) {
    val c = keyboardColors()
    // 时间相位：让波形在 RMS 平稳时也有自然起伏
    val phase = rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(1200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "wavePhase",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight + 4.dp)
            .background(c.bg)
            .clickable { onStop() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val level = (rms.value / 10f).coerceIn(0f, 1f) // RMS dB（-2~10 常见）归一化
            val bars = 28
            val gap = 3.dp.toPx()
            val barW = (size.width - gap * (bars + 1)) / bars
            val midY = size.height * 0.55f
            val maxAmp = size.height * 0.32f
            val accent = keyboardAccentActiveColor(c.barBg.luminance() < 0.5f)
            for (i in 0 until bars) {
                // 中间高、两端低的钟形包络 + 相位波 + RMS 驱动
                val t = i / (bars - 1f)
                val env = kotlin.math.sin(t * Math.PI).toFloat()
                val w = kotlin.math.sin(phase.value + i * 0.6f)
                val amp = maxAmp * env * (0.18f + 0.22f * (w + 1f) / 2f + 0.6f * level * env)
                val h = (2.dp.toPx() + amp).coerceAtMost(size.height * 0.9f)
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f + 0.45f * env * (0.4f + 0.6f * level)),
                    topLeft = Offset(gap + i * (barW + gap), midY - h / 2f),
                    size = androidx.compose.ui.geometry.Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
                )
            }
        }
        Text(
            "正在听写…点击结束",
            fontSize = 11.sp,
            color = c.subText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
        )
    }
}

/** 工具栏单个工具项（剪贴板/方案/数字/emoji/符号/设置；反馈轮12：细线自绘图标）。 */
@Composable
private fun RowScope.toolbarToolItem(id: String, state: KeyboardUiState, onAction: (KeyAction) -> Unit, c: KeyboardColors, onSchema: () -> Unit) {
    // 工具 id 来自 availableToolbarTools 白名单，直接取图标
    val icon = toolbarToolIcon(id)
    Box(
        modifier = Modifier
            .clickable {
                when (id) {
                    "clipboard" -> onAction(KeyAction.ToggleClipboardPanel)
                    "schema" -> onSchema()
                    "numpad" -> onAction(KeyAction.SwitchPage(if (state.page == "numpad") "main" else "numpad"))
                    "emoji" -> onAction(KeyAction.SwitchPage("emoji"))
                    "symbols" -> onAction(KeyAction.SwitchPage("symgrid"))
                    "settings" -> onAction(KeyAction.OpenSettings)
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = id,
            tint = c.text,
            // 反馈轮11：图标调大（18→24dp），与 ○ 菜单键（36dp）比例协调
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 工具栏工具 id → 细线图标（反馈轮12 方案A：1.8px 圆头描边自绘，随 tint 变色）。 */
private fun toolbarToolIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
    "clipboard" -> ToolbarOutlineIcons.clipboard
    "schema" -> ToolbarOutlineIcons.schemas
    "numpad" -> ToolbarOutlineIcons.digits
    "emoji" -> ToolbarOutlineIcons.emoji
    "symbols" -> ToolbarOutlineIcons.symbols
    "settings" -> ToolbarOutlineIcons.settings
    else -> ToolbarOutlineIcons.schemas
}

// ── 工具栏细线图标（反馈轮12 方案A 定稿）：24 网格手绘，stroke 1.8 圆头 ──

private object ToolbarOutlineIcons {

    private val stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)

    private fun build(name: String, vararg parts: Pair<String, Boolean>): androidx.compose.ui.graphics.vector.ImageVector =
        androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            parts.forEach { (d, isFill) ->
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes(),
                    fill = if (isFill) stroke else null,
                    stroke = if (isFill) null else stroke,
                    strokeLineWidth = 1.8f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                )
            }
        }.build()

    /** 剪贴板：圆角板 + 顶部夹子 + 两行内容线。 */
    val clipboard by lazy {
        build(
            "tb_clipboard",
            "M7.5 4H16.5A2.5 2.5 0 0 1 19 6.5V18.5A2.5 2.5 0 0 1 16.5 21H7.5A2.5 2.5 0 0 1 5 18.5V6.5A2.5 2.5 0 0 1 7.5 4Z" to false,
            "M10.5 2.2H13.5A1.5 1.5 0 0 1 15 3.7V4.3A1.5 1.5 0 0 1 13.5 5.8H10.5A1.5 1.5 0 0 1 9 4.3V3.7A1.5 1.5 0 0 1 10.5 2.2Z" to false,
            "M8.5 11.5H15.5M8.5 15.5H13" to false,
        )
    }

    /** 输入方案：三组「点 + 横线」列表。 */
    val schemas by lazy {
        build(
            "tb_schemas",
            "M5.5 4.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3zM5.5 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3zM5.5 16.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z" to true,
            "M10 6H18.5M10 12H18.5M10 18H15.5" to false,
        )
    }

    /** 数字：3x3 空心点阵 + 底中横线（拨号盘意象）。 */
    val digits by lazy {
        build(
            "tb_digits",
            "M5 3.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4zM12 3.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4zM19 3.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4zM5 10.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4zM12 10.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4zM19 10.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4zM5 17.3a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4z" to false,
            "M9.7 19H14.3" to false,
        )
    }

    /** 表情：圆脸 + 双眼 + 微笑弧。 */
    val emoji by lazy {
        build(
            "tb_emoji",
            "M12 3.4a8.6 8.6 0 1 0 0 17.2a8.6 8.6 0 1 0 0-17.2z" to false,
            "M9 8.75a1.25 1.25 0 1 0 0 2.5a1.25 1.25 0 1 0 0-2.5zM15 8.75a1.25 1.25 0 1 0 0 2.5a1.25 1.25 0 1 0 0-2.5z" to true,
            "M8.4 14.2C10.6 16.5 13.4 16.5 15.6 14.2" to false,
        )
    }

    /** 符号：圆 / 方 / 三角 / 菱形四形状。 */
    val symbols by lazy {
        build(
            "tb_symbols",
            "M7.5 4.5a3 3 0 1 0 0 6a3 3 0 1 0 0-6z" to false,
            "M14.8 4.5H18.4A1.2 1.2 0 0 1 19.6 5.7V9.3A1.2 1.2 0 0 1 18.4 10.5H14.8A1.2 1.2 0 0 1 13.6 9.3V5.7A1.2 1.2 0 0 1 14.8 4.5Z" to false,
            "M7.5 13.6L10.6 19H4.4Z" to false,
            "M16.6 13.2L19.8 17.1L16.6 21L13.4 17.1Z" to false,
        )
    }

    /** 设置：双圆齿轮 + 八向齿线。 */
    val settings by lazy {
        build(
            "tb_settings",
            "M12 5.4a6.6 6.6 0 1 0 0 13.2a6.6 6.6 0 1 0 0-13.2z" to false,
            "M12 9.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z" to false,
            "M12 2.6V5.2M12 18.8V21.4M2.6 12H5.2M18.8 12H21.4M5.55 5.55L7.4 7.4M16.6 16.6L18.45 18.45M18.45 5.55L16.6 7.4M7.4 16.6L5.55 18.45" to false,
        )
    }
}
// ── ○ 菜单面板（参考 xime MenuBar：顶部关闭/设置 + 图标网格分页 + 方案 chips） ──

@Composable
private fun MenuPanel(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    totalHeight: androidx.compose.ui.unit.Dp,
) {
    val c = keyboardColors()
    // 子级页：null=主菜单 | switches=方案开关 | groups=方案组 | schema=输入方案 | toolbar=定制工具栏
    // 子级面板与一级菜单同区域同风格（同背景、同顶部行），在本面板内切换，不跳转独立界面。
    var subPage by remember { mutableStateOf<String?>(null) }
    fun close() = onAction(KeyAction.ToggleMenuPanel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(c.barBg),
    ) {
        when (subPage) {
            "toolbar" -> ToolbarCustomizePanel(
                current = KeyboardManager.toolbarItems(),
                onSave = {
                    onAction(KeyAction.SetToolbarItems(it))
                    subPage = null
                },
                onDismiss = { subPage = null },
                totalHeight = totalHeight,
            )
            "groups" -> {
                // 反馈轮13：方案组大项 —— 组 → 方案 两级（参考 trime2）。
                // 一次只加载一个组；切换=重装组文件+全量部署（SelectSchemaGroup）。
                // state.schemas 作为缓存 key：切组后 refreshState 更新 → 此处重新枚举高亮。
                val groups = remember(state.schemas) {
                    RimeManager.schemaGroups(com.azime.input.AZimeApplication.instance)
                }
                val currentGid = remember(state.schemas) {
                    RimeManager.currentGroupId(com.azime.input.AZimeApplication.instance)
                }
                MenuSubPanel(
                    c = c, title = "方案组", totalHeight = totalHeight,
                    onBack = { subPage = null }, onClose = { close() },
                ) {
                    if (state.statusMessage.isNotEmpty()) {
                        Text(
                            state.statusMessage,
                            fontSize = 13.sp, color = c.subText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                    if (groups.size <= 1) {
                        Text(
                            "暂无其他方案组：在设置中导入方案包（ZIP / 文件夹）即可创建新组",
                            fontSize = 13.sp, color = c.subText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                    groups.chunked(4).forEach { rowGroups ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowGroups.forEach { g ->
                                val selected = g.id == currentGid
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (selected) c.accentKeyBg else c.keyBg, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!selected) onAction(KeyAction.SelectSchemaGroup(g.id))
                                        }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        g.name,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (selected) c.accentActive else c.text,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        "${g.schemaIds.size} 个方案",
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        color = c.subText,
                                    )
                                }
                            }
                            repeat(4 - rowGroups.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            "manage" -> {
                // 轮18（trime2 架构）：方案管理 = 当前组方案列表（只读），点击直接切换方案。
                // 组目录自带 default.custom.yaml 由 librime 自动 patch，App 不碰 yaml、无启用集概念。
                val app = com.azime.input.AZimeApplication.instance
                val gid = remember(state.schemas) { RimeManager.currentGroupId(app) }
                val allSchemas = remember(state.schemas) {
                    RimeManager.schemaGroups(app).firstOrNull { it.id == gid }?.schemaIds ?: emptyList()
                }
                MenuSubPanel(
                    c = c, title = "方案管理", totalHeight = totalHeight,
                    onBack = { subPage = null }, onClose = { close() },
                ) {
                    Text(
                        "组「$gid」共 ${allSchemas.size} 个方案，点击切换当前方案",
                        fontSize = 12.sp, color = c.subText,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    allSchemas.chunked(2).forEach { rowIds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowIds.forEach { id ->
                                val on = id == state.schemaName
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (on) c.accentKeyBg else c.keyBg, RoundedCornerShape(12.dp))
                                        .clickable {
                                            onAction(KeyAction.SelectSchema(id))
                                            subPage = null
                                        }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        RimeManager.schemaDisplayName(id),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (on) c.accentActive else c.text,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        if (on) "当前" else "点击切换",
                                        fontSize = 9.sp,
                                        color = c.subText,
                                    )
                                }
                            }
                            repeat(2 - rowIds.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            "schema" -> MenuSubPanel(
                c = c, title = "输入方案", totalHeight = totalHeight,
                onBack = { subPage = null }, onClose = { close() },
            ) {
                if (state.schemas.isEmpty()) {
                    Text(
                        "引擎部署中…",
                        fontSize = 14.sp,
                        color = c.subText,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
                    )
                }
                // 反馈轮10：与一级菜单同款横向卡片网格
                state.schemas.chunked(4).forEach { rowSchemas ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowSchemas.forEach { schemaId ->
                            val selected = schemaId == state.schemaName
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) c.accentKeyBg else c.keyBg, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (schemaId != state.schemaName) onAction(KeyAction.SelectSchema(schemaId))
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    // 反馈轮11：读方案 yaml 的 name 字段（方案名），不再只显示文件名
                                    text = RimeManager.schemaDisplayName(schemaId),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) c.accentActive else c.text,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        repeat(4 - rowSchemas.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            "switches" -> {
                // 大项「方案开关」：只保留当前方案的功能开关
                // （反馈轮10：去掉方案选择列表与当前方案显示，切换方案走「输入方案」）
                val switches = remember(state.schemaName) { RimeManager.schemaSwitches(state.schemaName) }
                MenuSubPanel(
                    c = c, title = "方案开关", totalHeight = totalHeight,
                    onBack = { subPage = null }, onClose = { close() },
                ) {
                    if (switches.isEmpty()) {
                        Text(
                            "当前方案没有可切换的开关",
                            fontSize = 13.sp, color = c.subText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                    // 反馈轮11：横向 2 列卡片网格（状态名 + 开关名；开 → accent 底色，点按切换）
                    switches.chunked(2).forEach { rowSwitches ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowSwitches.forEach { sw ->
                                var checked by remember(state.schemaName, sw.name) {
                                    mutableStateOf(RimeManager.getOption(sw.name))
                                }
                                val stateText = when {
                                    sw.states.size >= 2 -> if (checked) sw.states[1] else sw.states[0]
                                    sw.states.size == 1 -> if (checked) sw.states[0] else "关"
                                    else -> if (checked) "开" else "关"
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (checked) c.accentKeyBg else c.keyBg, RoundedCornerShape(12.dp))
                                        .clickable {
                                            checked = !checked
                                            onAction(KeyAction.ToggleSwitch(sw.name))
                                        }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        stateText,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        color = if (checked) c.accentActive else c.text,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        sw.name,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = c.subText,
                                    )
                                }
                            }
                            repeat(2 - rowSwitches.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            else -> {
                // ── 主菜单 ──（反馈轮14：悬浮栏移到底部居中，对齐剪贴板面板）
                Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 图标网格：4 列（icon + label，仿 xime MenuItemButton）
                        // 反馈轮12：第 6 项亮暗切换——显示切换目标（暗色态显示「亮色」）
                        val sysDark = isSystemInDarkTheme()
                        val themeDark = remember(state.themeRev, sysDark) {
                            com.azime.input.core.theme.KeyboardTheme.isDark(sysDark)
                        }
                        val menuItems: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = listOf(
                            Triple(Icons.AutoMirrored.Filled.Assignment, "剪贴板") { close(); onAction(KeyAction.ToggleClipboardPanel) },
                            Triple(Icons.Default.Tune, "方案开关") { subPage = "switches" },
                            // 反馈轮13：方案组大项（组 → 方案 两级，一次只加载一个组）
                            Triple(Icons.Default.Apps, "方案组") { subPage = "groups" },
                            // 反馈轮15：方案管理 —— 组内启用集选择（部署范围 + 「输入方案」列表）
                            Triple(Icons.Default.PlaylistAddCheck, "方案管理") { subPage = "manage" },
                            Triple(Icons.Default.List, "输入方案") { subPage = "schema" },
                            Triple(Icons.Default.Sync, "部署") { close(); onAction(KeyAction.Deploy) },
                            // 反馈轮16：键盘编辑 —— 布局编辑器直达入口
                            Triple(Icons.Default.Edit, "键盘编辑") { close(); onAction(KeyAction.OpenKeyboardEditor) },
                            Triple(Icons.Default.Category, "定制工具栏") { subPage = "toolbar" },
                            Triple(
                                if (themeDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                if (themeDark) "亮色" else "暗色",
                            ) { onAction(KeyAction.ToggleThemeMode) },
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
                        // 底部留白避开悬浮栏
                        Spacer(Modifier.height(56.dp))
                    }
                    // 底部悬浮栏：↑ 关闭 + 标题 + ⚙ 设置（反馈轮14，alpha 0.8 同剪贴板）
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(c.funcKeyBg.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "↑",
                            fontSize = 16.sp,
                            color = c.text,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { close() }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                        Text("○ 菜单", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = c.text,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { close(); onAction(KeyAction.OpenSettings) }
                                .padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * ○ 菜单子级面板骨架：与一级菜单同区域同风格
 * （← 返回 + 标题 + ↑ 关闭 + 滚动内容，不跳转独立界面）。
 */
@Composable
private fun MenuSubPanel(
    c: KeyboardColors,
    title: String,
    totalHeight: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onClose: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // 反馈轮14：悬浮栏对齐剪贴板面板——移到底部居中（BottomCenter + alpha 0.8 + R22）
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            content = {
                content()
                // 底部留白避开悬浮栏
                Spacer(Modifier.height(56.dp))
            },
        )
        // 底部悬浮栏：← 返回 + 标题 + ↑ 关闭
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .background(c.funcKeyBg.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "←",
                fontSize = 16.sp,
                color = c.text,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
            Text(
                "↑",
                fontSize = 16.sp,
                color = c.text,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
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
    // 反馈轮9：与一级菜单同 chrome（← 返回 + 标题 + ↑ 关闭）+ 卡片排布
    MenuSubPanel(
        c = c, title = "定制工具栏", totalHeight = totalHeight,
        onBack = onDismiss, onClose = onDismiss,
    ) {
        Text(
            "○ 菜单键固定居中（拖动移光标）；点选的工具先左后右排列在 ○ 两侧。",
            fontSize = 12.sp,
            color = c.subText,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        // 反馈轮10：横向卡片网格（选中 = 强调色），与一级菜单同风格
        KeyboardManager.availableToolbarTools.chunked(4).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTools.forEach { (id, name) ->
                    val on = id in selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (on) c.accentKeyBg else c.keyBg, RoundedCornerShape(12.dp))
                            .clickable {
                                if (on) selected.remove(id) else selected.add(id)
                            }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            toolbarToolIcon(id),
                            contentDescription = name,
                            tint = c.text.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(name, fontSize = 10.sp, color = c.text, maxLines = 1)
                    }
                }
                repeat(4 - rowTools.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
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
                                        Text("${idx + 1} ", fontSize = (KeyboardManager.fontSizeBar() * 0.65f).sp, color = c.subText)
                                    }
                                    Text(
                                        candidate.text,
                                        // 轮18.2：更多候选字号 = 工具栏候选字号 + 2（面板空间更大）
                                        fontSize = (KeyboardManager.fontSizeBar() + 2).sp,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        // 网格内容：左右滑动切分类（占满全高，单页可竖向滚动）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize(),
        ) { page ->
            val items = data.categories.getOrNull(page)?.second ?: emptyList()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = KeySpacing)
                    .padding(top = KeySpacing),
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
                // 底部留白避开悬浮栏
                Spacer(Modifier.height(56.dp))
            }
        }
        // 底部悬浮栏（反馈轮9，参考 PiliPlus）：返回键 + 分类标签（轮10 半透明 + 同心圆角）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .background(c.funcKeyBg.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "←",
                fontSize = 17.sp,
                color = c.text,
                modifier = Modifier
                    .clickable { onAction(KeyAction.SwitchPage("main")) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            // 分类标签（横向滑动）
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
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
    }
}


// ── 九宫格：5 列专用布局 ─────────────────────────────────────
// 左列 = 3 行高「滑动预览符号」键（上下滑动选择，点击上屏）+ 返回键
// 中间三列 = 1-0 十个数字 + "." "," 两个符号；右列 = ⌫ / 符号面板 / 空格 / ⏎

@Composable
private fun NumpadPane(state: KeyboardUiState, onAction: (KeyAction) -> Unit, keyHeight: androidx.compose.ui.unit.Dp) {
    // 反馈轮9：数字横向 123/456/789 排布（列存 = 1,4,7 / 2,5,8 / 3,6,9）
    val topRows = listOf(listOf("1", "4", "7"), listOf("2", "5", "8"), listOf("3", "6", "9"))
    val keyCornerDp = KeyboardManager.keyCornerDp().dp
    val rowGap = KeyboardManager.rowGapDp().dp
    val colGap = KeyboardManager.colGapDp().dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(colGap),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        // 前 3 行：符号滑键（跨 3 行）+ 数字三列 + 功能三键
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight * 3 + rowGap * 2),
            horizontalArrangement = Arrangement.spacedBy(colGap),
        ) {
            NumpadSliderKey(onAction = onAction, modifier = Modifier.weight(1f).fillMaxSize())
            topRows.forEach { col ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(rowGap),
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
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                listOf(
                    Key("⌫", code = "backspace", width = 1f, type = KeyType.DELETE),
                    Key("符号", code = "symgrid", width = 1f, type = KeyType.FUNCTION),
                    Key("空格", code = "space", width = 1f, type = KeyType.SPACE),
                ).forEach { k ->
                    Row(Modifier.weight(1f)) { KeyboardKey(key = k, state = state, onAction = onAction) }
                }
            }
        }
        // 第 4 行：返回 + = 0 . + ⏎（反馈轮9：0 左 = 号、右 . 号）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight),
            horizontalArrangement = Arrangement.spacedBy(colGap),
        ) {
            KeyboardKey(
                key = Key("返回", code = "main", width = 1f, type = KeyType.FUNCTION),
                state = state, onAction = onAction,
            )
            listOf("=", "0", ".").forEach { ch ->
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
        // 增高行（反馈轮10：九宫格也支持，与主键盘切换时高度一致）
        if (KeyboardManager.barEnabled()) {
            Spacer(Modifier.height(rowGap))
            Row(modifier = Modifier.fillMaxWidth().height(KeyboardManager.barHeightDp().dp)) {}
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
            .background(c.funcKeyBg, RoundedCornerShape(KeyboardManager.keyCornerDp().dp))
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
        // 空格键显示：自定义文本 > 当前方案名（短名） > 默认「空格」（轮11：isNotEmpty，纯空格标签也生效）
        key.code == "space" && state.page == "main" -> {
            val custom = KeyboardManager.spaceLabel()
            when {
                custom.isNotEmpty() -> custom
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
    val keyCornerDp = KeyboardManager.keyCornerDp().dp
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
                // 保持原键背景/文字色，仅替换键面文本（反馈轮8：候选键不变色）
                val mapBg = if (key.type == KeyType.FUNCTION) c.funcKeyBg else c.keyBg
                Box(
                    modifier = Modifier
                        .weight(key.width)
                        .fillMaxSize()
                        .background(mapBg, RoundedCornerShape(keyCornerDp))
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
                        color = c.text,
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
    // 触发前移动超过 5dp 取消长按（对齐 xime.az KeyButton，防止打字抖动误触发）
    var longCancelled by remember { mutableStateOf(false) }
    var showBubble by remember { mutableStateOf(false) }
    var showPageBubble by remember { mutableStateOf(false) }
    var swipePreview by remember { mutableStateOf<String?>(null) }
    // 长按气泡滑动选择的符号下标（多符号时滑动切换，松手上屏）
    var longSelIdx by remember { mutableStateOf(0) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // 反馈轮11：气泡位置可在设置微调（水平偏移 + 键高之上的垂直余量）
    val bubbleOffset = with(density) {
        IntOffset(
            KeyboardManager.bubbleXDp().dp.roundToPx(),
            -(KeyboardManager.keyHeightDp().dp + KeyboardManager.bubbleYExtraDp().dp).roundToPx(),
        )
    }
    val swipePreviewAbove = KeyboardManager.swipePreviewAbove()
    // 滑动手势触发距离（设置内可调，反馈轮10）
    val swipeThreshold = with(density) { KeyboardManager.swipeThresholdDp().dp.toPx() }
    // 轮19.1：退格（行为键）上下滑用更短门槛——30dp 默认值对退格太高，
    // 实测「退格下滑撤回」要拖很久才触发，用户感知为失效；18dp 仍属刻意移动。
    val deleteSwipeThreshold = minOf(swipeThreshold, with(density) { 18.dp.toPx() })
    // 长按气泡滑动选择步长（每个符号占 40dp）
    val longStepPx = with(density) { 40.dp.toPx() }

    val hasGestures = longPressSymbols.isNotEmpty() || autoRepeat || hasCustomLong || isPageKey ||
        key.swipeUp != null || key.swipeDown != null || key.swipeLeft != null || key.swipeRight != null

    // 长按定时器（触发时间设置内可调，默认 180ms；触发前移动超 5dp 取消）
    LaunchedEffect(pressing) {
        if (pressing) {
            delay(KeyboardManager.longPressMs().toLong())
            if (pressing && !longFired && !longCancelled) {
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
                        delay(KeyboardManager.repeatStartMs().toLong())
                        while (pressing) {
                            onKeyAction(key, onAction)
                            delay(KeyboardManager.repeatIntervalMs().toLong())
                        }
                    }
                }
            }
        }
    }

    // 反馈轮14：按下动画反馈——背景高亮渐变 + 轻微缩放（spring 回弹）。
    // 有手势键用 pressing（awaitEachGesture down/up），无手势键走 InteractionSource。
    val clickSource = remember { MutableInteractionSource() }
    val clickPressed by clickSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressing || clickPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "keyPress",
    )
    // 暗色键盘按下变亮、亮色键盘按下变暗（以工具栏底色亮度判定明暗）
    val pressTint = if (c.barBg.luminance() < 0.5f) Color.White else Color.Black
    val bgAnimated = lerp(
        bg,
        pressTint.copy(alpha = 0.12f).compositeOver(bg),
        pressProgress,
    )

    var baseModifier = Modifier
        .weight(key.width)
        .fillMaxSize()
        .graphicsLayer {
            val s = 1f - 0.05f * pressProgress
            scaleX = s
            scaleY = s
        }
        .background(bgAnimated, RoundedCornerShape(keyCornerDp))
    if (hasGestures) {
        // 手势闭包内读取最新 state（preedit 等会随打字频繁变化）
        val currentState by rememberUpdatedState(state)
        baseModifier = baseModifier.pointerInput(key.code, key.longClick, state.page) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                longFired = false
                longCancelled = false
                pressing = true
                HapticsManager.press()
                val startX = down.position.x
                val startY = down.position.y
                // 长按取消阈值（对齐 xime.az：5dp 内移动不取消，超出即视为滑动意图）
                val longCancelPx = with(density) { 5.dp.toPx() }
                var activeDir: Dir? = null
                // 退格左滑（trime2 退格脚本锚点模型）：
                // engaged 后 target = floor(dx / SWIPE_STEP)，右滑回退可缩到锚点
                var selectEngaged = false
                var lastSelectStep = 0
                // 反馈轮9：滑动删除降敏 1/5（30px/字，原 24px）
                val swipeStepPx = 30f
                val engageThresholdPx = 12f

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - startX
                    val dy = change.position.y - startY

                    if (activeDir == null) {
                        // 长按未触发前的移动检测：超过 5dp 取消长按定时器（不影响后续四向手势判定）
                        if (!longFired && !longCancelled &&
                            (abs(dx) > longCancelPx || abs(dy) > longCancelPx)
                        ) {
                            longCancelled = true
                        }
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
                                // 退格用更短门槛（见 deleteSwipeThreshold 注释）
                                val threshold = if (isDelete) deleteSwipeThreshold else swipeThreshold
                                if (dist > threshold) {
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
        baseModifier = baseModifier.clickable(
            interactionSource = clickSource,
            indication = null,
        ) {
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
            // 反馈轮11：四向预览位置可选——键面中央（默认替换键名）或键上方气泡
            text = if (swipePreviewAbove) label else (swipePreview ?: label),
            // 反馈轮9：键面字号可调（键盘/工具栏分开设置）
            fontSize = if (key.type == KeyType.CHARACTER) KeyboardManager.fontSizeKey().sp
            else (KeyboardManager.fontSizeKey() * 0.7f).sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
        // 键上方气泡预览（设置「四向预览位置」选键面上方时生效）
        val pv = swipePreview
        if (swipePreviewAbove && pv != null) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = bubbleOffset,
            ) {
                Text(
                    text = pv,
                    fontSize = 13.sp,
                    color = c.text,
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
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
        // 轮19.1 修复：四向滑动提示——原实现只控制「滑动过程中的临时预览」，
        // 开关打开后键面不显示任何提示（用户反馈「打开开关不显示」）。
        // 现按设置把该方向的滑动符号常驻渲染在键面对应位置（无滑动动作的键不显示）。
        if (key.type == KeyType.CHARACTER || key.type == KeyType.DELETE) {
            val dirHintFont = 9.sp
            key.swipeUp?.let {
                if (KeyboardManager.hintUp()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 3.dp),
                )
            }
            key.swipeDown?.let {
                if (KeyboardManager.hintDown()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 3.dp),
                )
            }
            key.swipeLeft?.let {
                if (KeyboardManager.hintLeft()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 5.dp),
                )
            }
            key.swipeRight?.let {
                if (KeyboardManager.hintRight()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp),
                )
            }
        }
        if (showBubble && longPressSymbols.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = bubbleOffset,
                onDismissRequest = { showBubble = false },
            ) {
                // 反馈轮9：横向放置——一行 5 个，多出的排第二行（不再纵向分页）
                Column(
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    longPressSymbols.chunked(5).forEach { rowSymbols ->
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            rowSymbols.forEach { symbol ->
                                val si = longPressSymbols.indexOf(symbol)
                                val isSel = longPressSymbols.size > 1 && si == longSelIdx
                                Text(
                                    text = symbol.removeSuffix("{Left}"),
                                    fontSize = 16.sp,
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
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
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

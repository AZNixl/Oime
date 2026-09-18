package com.azime.input.ui.keyboard

import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.GridOn
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
import com.azime.input.core.action.ActionResolver
import com.azime.input.core.action.ResolvedAction
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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

    // ── 扩展动作（内置功能键值 / 手势） ──
    /** 解析后的命令（select_all/cut/copy/paste/…）或字面提交。 */
    data class Resolved(val value: String) : KeyAction
    data object DeleteAll : KeyAction
    data object Undo : KeyAction
    /** 轮19.17：重做（与撤回对称的 redo 栈）。 */
    data object Redo : KeyAction
    /** 轮19.19：单手模式开关（关 ↔ 开；开时用上次那侧，默认左手）。 */
    data object ToggleHandMode : KeyAction
    /** 轮19.20：单手模式左右切换（空白处箭头）。 */
    data object SwitchHandSide : KeyAction
    /** 轮19.19：悬浮模式开关（键盘整体可拖动）。 */
    data object ToggleFloatKeyboard : KeyAction
    /** 轮19.34：唤起系统输入法选择器（切换输入法）。 */
    data object SwitchIme : KeyAction
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
    /** 轮19.11：工具栏「方案」按钮 → 剪贴板同款悬浮栏，方案横向排布、点击切换。 */
    data object ToggleSchemaPanel : KeyAction
    /** 从剪贴板面板/条上屏：提交后清除条目并收起面板。 */
    data class CommitClipboard(val text: String) : KeyAction
    /** 轮19.18：复制条**左右划动**消亡（不必先上屏；用于清掉强制复制的无效内容）。 */
    data object DismissClipStrip : KeyAction
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
    /** 轮19.11：方案快捷面板。 */
    val showSchemaPanel: Boolean = false,
    /** 轮19.24：复制条**完整文本**（clipText 只存前 80 字用于显示，上屏必须用完整文本）。 */
    val clipFull: String = "",
    /** 轮19.19：布局版本号（单手/悬浮等只改 prefs 的动作靠它触发重组）。 */
    val layoutRev: Int = 0,
    /** 轮19.11：光标屏幕坐标（悬浮窗跟随光标用；-1 表示未知）。 */
    val cursorLeft: Int = -1,
    val cursorBottom: Int = -1,
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
    // ── 轮19.35：界面风格视觉 token（边框/阴影/字重/等宽）──
    /** 键面描边（null = 无描边）。 */
    val keyBorder: Color? = null,
    /** 键面阴影 dp（0 = 无）。 */
    val keyShadowDp: Int = 0,
    /** 键面字重是否加粗（One UI）。 */
    val keyBold: Boolean = false,
    /** 键面是否用等宽字体（Nothing OS）。 */
    val monoFont: Boolean = false,
)

/** 按深浅色 + 当前主题强调色构建配色。 */
fun buildKeyboardColors(dark: Boolean): KeyboardColors {
    val accent = Color(
        if (dark) com.azime.input.core.theme.KeyboardTheme.accentDark()
        else com.azime.input.core.theme.KeyboardTheme.accentLight()
    )
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF202124) else Color.White
    // 轮19.30：该模式下开了自定义 → 字母键 / 功能键用自定义色（强调色走 accent，本来就是自定义项）
    val theme = com.azime.input.core.theme.KeyboardTheme
    val customOn = if (dark) theme.customDarkOn() else theme.customLightOn()
    val customKeyBg = if (customOn) Color(theme.keyBgColor(dark)) else null
    val customFuncBg = if (customOn) Color(theme.funcBgColor(dark)) else null
    // 轮19.35：风格 token 表（材质/Miuix/One UI/iOS/Nothing/Material You）
    val tokens = com.azime.input.core.theme.UiStyles.ofCurrent()
    val dynamic = if (tokens.dynamicColor) runCatching {
        com.azime.input.core.theme.DynamicPalette.get(
            com.azime.input.AZimeApplication.instance, dark,
        )
    }.getOrNull() else null
    val palette = dynamic ?: if (dark) tokens.dark else tokens.light
    val pureAccent = theme.accentPureEffective(dark)
    val baseKey = customKeyBg ?: Color(palette.keyBg)
    val border = if (tokens.keyBorderAlpha > 0f) Color(palette.text).copy(alpha = tokens.keyBorderAlpha) else null
    return if (dark) {
        KeyboardColors(
            bg = Color(palette.bg),
            barBg = Color(palette.barBg),
            keyBg = baseKey,
            funcKeyBg = customFuncBg ?: Color(palette.funcKeyBg),
            // 原色模式直接用强调色；柔和模式仍与键底做半透明混合（默认观感）
            accentKeyBg = if (pureAccent) accent else accent.copy(alpha = 0.35f).compositeOver(baseKey),
            accentKeyText = onAccent,
            accentActive = accent, accentActiveText = onAccent,
            text = Color(palette.text),
            subText = Color(palette.subText),
            joystick = Color(0xFFE57373),
            keyBorder = border, keyShadowDp = tokens.keyShadowDp,
            keyBold = tokens.boldKeys, monoFont = tokens.monospace,
        )
    } else {
        KeyboardColors(
            bg = Color(palette.bg),
            barBg = Color(palette.barBg),
            keyBg = baseKey,
            funcKeyBg = customFuncBg ?: Color(palette.funcKeyBg),
            accentKeyBg = if (pureAccent) accent else accent.copy(alpha = 0.28f).compositeOver(baseKey),
            accentKeyText = onAccent,
            accentActive = accent, accentActiveText = onAccent,
            text = Color(palette.text),
            subText = Color(palette.subText),
            joystick = Color(0xFFD32F2F),
            keyBorder = border, keyShadowDp = tokens.keyShadowDp,
            keyBold = tokens.boldKeys, monoFont = tokens.monospace,
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
    // 轮19.13：横屏用分体布局 + 更矮的键高（竖屏逻辑完全不变）
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val keyH = (if (isLandscape) KeyboardManager.landscapeKeyHeightDp()
    else KeyboardManager.keyHeightDp()).dp
    val barH = KeyboardManager.barHeightDp().dp
    // 反馈轮9：按键圆角/行距/列距可调（默认 8dp / 4dp / 4dp）
    // 轮19.35：几何默认沿用用户设置；仅当「几何跟随风格」开启时用风格建议圆角
    val styleTokens = com.azime.input.core.theme.UiStyles.ofCurrent()
    val keyCorner = (if (com.azime.input.core.theme.KeyboardTheme.geometryFollowsStyle()) {
        styleTokens.keyCornerDp
    } else {
        KeyboardManager.keyCornerDp()
    }).dp
    val rowGap = KeyboardManager.rowGapDp().dp
    val colGap = KeyboardManager.colGapDp().dp
    // 主键盘区标准总高（4 行 + 3 道行距 + 2dp 底留白，顶留白为 0）；emoji/候选/菜单面板统一与此等高。
    // 轮19.6：原为 keyH*4 + rowGap*5（对应旧的上下各 rowGap 留白），底部留白改 2dp 后
    // 面板比键盘高了 rowGap-2dp → emoji/符号页与主键盘不等高。
    val stdH = keyH * 4 + rowGap * 3 + 2.dp
    val areaH = if (KeyboardManager.barEnabled()) stdH + rowGap + barH else stdH

    // 轮19.19：单手模式（缩到一侧 78%）/ 悬浮模式（86% + 可拖动整体移动）
    val hand = KeyboardManager.handMode()
    val floating = KeyboardManager.floatKeyboard()
    val widthFrac = when {
        floating -> 0.86f
        hand != KeyboardManager.HAND_OFF -> 0.78f
        else -> 1f
    }
    val boxAlign = when (hand) {
        KeyboardManager.HAND_LEFT -> Alignment.BottomStart
        KeyboardManager.HAND_RIGHT -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }
    // 悬浮偏移（拖动时用本地状态，松手才落盘，避免 prefs 抖动）
    val density0 = LocalDensity.current
    var fxDp by remember(floating) { mutableStateOf(KeyboardManager.floatKbdX().toFloat()) }
    var fyDp by remember(floating) { mutableStateOf(KeyboardManager.floatKbdY().toFloat()) }

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = kbFontFamily ?: FontFamily.Default),
    ) {
        // 轮19.21：用 BoxWithConstraints 拿到窗口可用高度 → 拖动时把键盘**钳制在窗口内**，
        // 保证任何情况下整个键盘都可见（19.20 只做了窗口全屏，若窗口未生效就会把上半截拖出窗口）
        // 轮19.23：悬浮时把容器**撑到屏幕高度**——IME 窗口是按内容高度测量的（WRAP_CONTENT），
        // 只 setLayout(MATCH_PARENT) 会被系统覆盖回来 → 之前窗口只有键盘高，拖动被"框"在下半屏。
        // 让内容自己变高，窗口才真的高，才能自由拖动。
        val screenHdp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (floating) Modifier.height((screenHdp - 48).coerceAtLeast(320).dp)
                    else Modifier.background(c.bg),
                ),
            contentAlignment = boxAlign,
        ) {
            val winHdp = maxHeight.value
        // 轮19.20：单手模式——空白一侧显示切换箭头（点击切左右手）
        if (hand != KeyboardManager.HAND_OFF) {
            val arrowAlign = if (hand == KeyboardManager.HAND_LEFT) Alignment.CenterEnd else Alignment.CenterStart
            Box(
                modifier = Modifier
                    .align(arrowAlign)
                    .padding(horizontal = 10.dp)
                    .size(34.dp)
                    .background(c.funcKeyBg, RoundedCornerShape(17.dp))
                    .clickable { onAction(KeyAction.SwitchHandSide) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (hand == KeyboardManager.HAND_LEFT) "▶" else "◀",
                    fontSize = 14.sp,
                    color = c.text,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFrac)
                .then(
                    if (floating) {
                        Modifier
                            .offset { IntOffset(with(density0) { fxDp.dp.roundToPx() }, with(density0) { fyDp.dp.roundToPx() }) }
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.barBg)
                            // 上报键盘矩形：服务端 onComputeInsets 用它把可触摸区域限定成键盘，
                            // 其余区域触摸穿透给下面的 App（否则键盘会"吃掉"整屏触摸）
                            .onGloballyPositioned { coords ->
                                val top = coords.positionInWindow().y.toInt()
                                KeyboardManager.setFloatKbdRect(top, top + coords.size.height)
                            }
                    } else {
                        Modifier
                            .background(c.bg)
                            .onGloballyPositioned {
                                KeyboardManager.setFloatKbdRect(-1, -1)
                            }
                    },
                ),
        ) {
            // 悬浮模式：顶部拖动条（拖这里整体移动键盘；松手落盘）
            if (floating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(c.barBg)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    KeyboardManager.setFloatKbdPos(fxDp.toInt(), fyDp.toInt())
                                },
                            ) { change, drag ->
                                change.consume()
                                fxDp = (fxDp + with(density0) { drag.x.toDp().value }).coerceIn(-300f, 300f)
                                // 钳制：键盘顶不能越过窗口顶（否则上半截看不见）
                                fyDp = (fyDp + with(density0) { drag.y.toDp().value })
                                    .coerceIn(-(winHdp - 260f).coerceAtLeast(0f), 60f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .background(c.subText.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                    )
                }
            }
            ToolbarRow(
                state = state,
                onAction = onAction,
                // 反馈轮11：○ 菜单键 36dp + 上下留白；轮19.6：44→40dp；
                // 轮19.8：改为设置内可微调（键盘 → 工具栏高度，32~72dp）
                barHeight = KeyboardManager.toolbarHeightDp().dp,
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
                // 轮19.17：横屏不再用分体布局，只用同一套布局 + 横屏键高（-25%）
                // 轮19.54：读取布局版本号 ⇒ 编辑器「保存」后键盘**自动热重载**（原来保存了不生效）
                val layoutRev = KeyboardManager.layoutRev()
                val layout = remember(layoutRev, state.page) {
                    KeyboardManager.layoutFor(state.page) ?: KeyboardManager.mainLayout()
                }
                // 轮19.52：键盘左右边距（曲面屏可用，默认 0）
                val sideMargin = KeyboardManager.keyboardSideMarginDp().dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 轮19.4：底部留白 rowGap → 2dp（用户反馈「与最下沿还有距离」）
                        // 轮19.6：顶部留白 rowGap → 0（工具栏图标在「灰色带 + 首行」整体中才居中，且空白更窄）
                        .padding(
                            start = colGap + sideMargin,
                            end = colGap + sideMargin,
                            top = 0.dp,
                            bottom = 2.dp,
                        ),
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
                val bgAlpha = if (custom) KeyboardManager.floatBgAlpha() / 100f else 0.92f
                // 轮19.11：字号与工具栏/候选同步（原来固定 floatTextSp，放大键盘字号时不同步）
                val fSp = KeyboardManager.fontSizeBar().toFloat()
                val pSp = fSp * 0.7f
                // 跟随光标：用 IME 窗口在屏幕上的 top 把光标屏幕坐标换算成弹层偏移
                val rootView = androidx.compose.ui.platform.LocalView.current
                val imeTop = remember(rootView) {
                    val loc = IntArray(2)
                    rootView.getLocationOnScreen(loc)
                    loc[1]
                }
                var floatH by remember { mutableStateOf(0) }
                val hasCursor = state.cursorBottom > 0
                // 轮19.34：候选数量可调（1~9）
                val showCandidates = state.candidates.take(KeyboardManager.floatCandCount())
                // 轮19.34：背景色可自定义（0 = 跟随主题）；候选可竖向排列；首选可加强调底色
                val customFloatBg = KeyboardManager.floatBgColor()
                val floatBg = if (customFloatBg != 0) Color(customFloatBg).copy(alpha = bgAlpha)
                else c.barBg.copy(alpha = bgAlpha)
                val vertical = KeyboardManager.floatOrientation() == "v"
                val firstAccent = KeyboardManager.floatFirstAccent()
                Popup(
                    alignment = Alignment.TopStart,
                    offset = if (hasCursor) {
                        // 位置 = 光标左端，浮在光标上方 6dp（高度按实测内容高度回退修正）
                        IntOffset(
                            state.cursorLeft.coerceAtLeast(0),
                            state.cursorBottom - imeTop - floatH - with(density) { 6.dp.roundToPx() },
                        )
                    } else {
                        IntOffset(
                            with(density) { KeyboardManager.floatXDp().dp.roundToPx() },
                            with(density) { -KeyboardManager.floatYDp().dp.roundToPx() },
                        )
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .onGloballyPositioned { floatH = it.size.height }
                            .background(floatBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        // 轮19.55：**显示完整输入码**（原来只显示前三码，余下的丢给工具栏 ⇒ 用户反馈"mn 跑到工具栏去了"）
                        Text(
                            text = state.preedit,
                            fontSize = pSp.sp,
                            lineHeight = (pSp * 1.16f).sp,
                            color = c.subText,
                            maxLines = 1,
                        )
                        if (showCandidates.isNotEmpty()) {
                            // 轮19.34：横向/竖向可调；**首选加强调色底**（原色模式下文字用 on-accent）
                            @Composable
                            fun CandItem(i: Int, text: String) {
                                val emphasize = i == 0 && firstAccent
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = if (emphasize) {
                                        Modifier
                                            .background(c.accentKeyBg, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    } else {
                                        Modifier
                                    },
                                ) {
                                    if (i < 9) {
                                        Text(
                                            "${i + 1} ",
                                            fontSize = (fSp * 0.62f).sp,
                                            color = if (emphasize) c.accentKeyText.copy(alpha = 0.85f) else c.subText,
                                        )
                                    }
                                    Text(
                                        text,
                                        fontSize = fSp.sp,
                                        color = if (emphasize) c.accentKeyText else c.text,
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (vertical) {
                                // 轮19.55：竖向可反向（反向 = 第 1 个候选在最下）
                                val vReverse = KeyboardManager.floatVerticalReverse()
                                Column {
                                    // 正向：1 在最上；反向：1 在最下（越靠后越靠上），序号始终按真实候选号
                                    val order = if (vReverse) showCandidates.indices.reversed().toList()
                                    else showCandidates.indices.toList()
                                    order.forEachIndexed { pos, i ->
                                        if (pos > 0) Spacer(Modifier.height(3.dp))
                                        CandItem(i, showCandidates[i].text)
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    showCandidates.forEachIndexed { i, cand ->
                                        if (i > 0) Spacer(Modifier.width(10.dp))
                                        CandItem(i, cand.text)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 轮19.11b：方案切换改为「**主键盘下方中间的横向悬浮栏**」
            // （原来弹出的是左上角 popup 菜单 + 整屏面板，用户要求改到键盘下方中间）
            if (state.showSchemaPanel) {
                val d2 = LocalDensity.current
                Popup(
                    alignment = Alignment.BottomCenter,
                    offset = IntOffset(0, with(d2) { (-14).dp.roundToPx() }),
                    onDismissRequest = { onAction(KeyAction.ToggleSchemaPanel) },
                ) {
                    Row(
                        modifier = Modifier
                            // 轮19.16：改灰色半透明（与剪贴板/菜单悬浮栏同 chrome：funcKeyBg @80%）
                            .background(c.funcKeyBg.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.schemas.isEmpty()) {
                            Text("引擎部署中…", fontSize = 13.sp, color = c.subText,
                                modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        state.schemas.forEach { id ->
                            val selected = id == state.schemaName
                            val name = remember(id) {
                                com.azime.input.core.rime.RimeManager.schemaDisplayName(id)
                            }
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (selected) c.accentKeyBg else c.keyBg,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable {
                                        if (!selected) onAction(KeyAction.SelectSchema(id))
                                        onAction(KeyAction.ToggleSchemaPanel)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    name, fontSize = 14.sp, maxLines = 1,
                                    color = if (selected) c.accentKeyText else c.text,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (selected) {
                                    Spacer(Modifier.width(5.dp))
                                    Icon(
                                        com.azime.input.ui.icons.OimeIcons.check,
                                        contentDescription = "当前",
                                        tint = c.accentActive,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
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
    // 轮19.24：︙ 菜单改为**横向悬浮栏**，出现时**覆盖面板的功能键区**（原来是 DropdownMenu 弹窗）
    var menuTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

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
                        ClipCard(
                            index = index, text = text, tab = state.clipTab, onAction = onAction,
                            onMenu = { t, i -> menuTarget = t to i },
                        )
                    }
                    // 底部留白避开悬浮栏
                    Spacer(Modifier.height(56.dp))
                }
            }
        }
        // 轮19.24：︙ 横向悬浮栏——覆盖在面板功能键区上方（点条目 ︙ 呼出）
        menuTarget?.let { (targetText, targetIndex) ->
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .background(c.funcKeyBg.copy(alpha = 0.95f), RoundedCornerShape(22.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val actions: List<Pair<String, () -> Unit>> = buildList {
                    if (state.clipTab == "clipboard") {
                        add("收藏" to { onAction(KeyAction.ClipFav(targetText)) })
                    }
                    add("分词" to { onAction(KeyAction.ClipSplit(targetText)) })
                    add("置顶" to { onAction(KeyAction.ClipTop(state.clipTab, targetIndex)) })
                    add("删除" to { onAction(KeyAction.ClipDelete(state.clipTab, targetIndex)) })
                    add(if (state.clipTab == "clipboard") "全清历史" to { onAction(KeyAction.ClipClear(state.clipTab)) }
                    else "全清收藏" to { onAction(KeyAction.ClipClear(state.clipTab)) })
                }
                actions.forEach { (label, act) ->
                    Box(
                        modifier = Modifier
                            .background(c.keyBg, RoundedCornerShape(14.dp))
                            .clickable {
                                act()
                                menuTarget = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(label, fontSize = 13.sp, color = c.text, maxLines = 1) }
                }
                Box(
                    modifier = Modifier
                        .background(c.keyBg, RoundedCornerShape(14.dp))
                        .clickable { menuTarget = null }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("✕", fontSize = 13.sp, color = c.subText) }
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
private fun ClipCard(
    index: Int,
    text: String,
    tab: String,
    onAction: (KeyAction) -> Unit,
    onMenu: (String, Int) -> Unit,
) {
    val c = keyboardColors()
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
        // 轮19.24：︙ 交给父级弹出**横向悬浮栏**（覆盖面板功能键区）
        Box(
            modifier = Modifier
                .clickable { onMenu(text, index) }
                .padding(4.dp),
        ) {
            Text("︙", fontSize = 16.sp, color = c.subText)
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
    // 轮19.6：上滑应用弧需要 context 启动应用 / 读应用图标
    val context = androidx.compose.ui.platform.LocalContext.current
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
            verticalArrangement = Arrangement.Center,
        ) {
            // 轮19.4：工具栏高度固定不动，字号按可用高度收敛——原来字号调到 24~28sp 时
            // 「输入码行 + 候选行」总高超过 barHeight，文字伸出工具栏被窗口裁掉。
            // 结构：输入码行(0.7×字号×1.2) + 候选行(字号×1.2) ≤ barHeight - 6dp
            //   ⇒ 字号上限 = (barHeight - 6dp) / 2.04
            val candCapSp = ((barHeight.value - 6f) / 2.04f).coerceIn(9f, 34f)
            val candSp = minOf(KeyboardManager.fontSizeBar().toFloat(), candCapSp)
            val preeditSp = candSp * 0.7f
            // 轮19.11：悬浮窗生效时，前三码交给悬浮窗显示，工具栏只显示余下的
            val floatOn = KeyboardManager.floatEnabled() && state.preedit.isNotEmpty()
            // 轮19.55：悬浮窗已显示完整输入码 ⇒ 工具栏**不再重复显示**（原来显示第 4 码起的部分）
            val preeditForBar = if (floatOn) "" else state.preedit
            if (preeditForBar.isNotEmpty()) {
                Text(
                    text = preeditForBar,
                    // 轮18.2：输入码小字随候选字号缩放；轮19.4：随工具栏可用高度收敛
                    fontSize = preeditSp.sp,
                    lineHeight = (preeditSp * 1.16f).sp,
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
                            Text("${index + 1} ", fontSize = (candSp * 0.62f).sp, color = c.subText)
                        }
                        // 轮18.2：候选字号接入设置（fontSizeBar）；轮19.4：受工具栏高度上限收敛
                        Text(candidate.text, fontSize = candSp.sp, maxLines = 1, color = c.text)
                        if (candidate.comment.isNotBlank()) {
                            Spacer(Modifier.width(3.dp))
                            Text(candidate.comment, fontSize = (candSp * 0.62f).sp, maxLines = 1, color = c.subText)
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
        // 轮19.28（按用户要求定稿）：复制条 = **内容靠左** + 右侧「✕ 消亡」按钮；
        // 整条**长按也可消亡**；两种消亡都**震动提示**；**废弃左右划动**（原阈值设置随之失效）。
        clipFresh -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(c.bg)
                // 轮19.29：长按消亡实测无效 → **废弃**（只保留右侧 ✕ 按钮）
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 内容：靠左，单行省略；点击内容 = 上屏（保持原有习惯）
            Text(
                text = state.clipText.replace("\n", " "),
                fontSize = KeyboardManager.fontSizeBar().sp,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onAction(KeyAction.CommitClipboard(state.clipFull.ifEmpty { state.clipText }))
                    }
                    .padding(vertical = 6.dp),
            )
            // 右侧消亡按钮（点击即消亡 + 震动）
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(c.funcKeyBg, RoundedCornerShape(17.dp))
                    .clickable {
                        com.azime.input.core.haptic.HapticsManager.haptic(
                            com.azime.input.core.haptic.HapticsManager.Type.DISMISS,
                        )
                        onAction(KeyAction.DismissClipStrip)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 15.sp, color = c.subText)
            }
        }
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

                // ── ○ 菜单键（居中，圆环造型）：点击开菜单 / 长按语音 /
                //    横向拖动移光标（呼啦圈）/ 下滑收起键盘 / **上滑呼出应用弧**（轮19.6） ──
                var oPressing by remember { mutableStateOf(false) }
                var oLongFired by remember { mutableStateOf(false) }
                // 圆环内点偏移（拖动时跟随手指，限幅在圆环半径内；反馈轮10）
                var ringKnob by remember { mutableStateOf(Offset.Zero) }
                // 轮19.6：O 圆环形状（ring / square / eye）+ 眼睛随机动画
                val ringShape = KeyboardManager.ringShape()
                var eyeBlink by remember { mutableStateOf(false) }
                var eyeDx by remember { mutableStateOf(0f) }  // -1 左看 / 0 中 / 1 右看
                var eyeDy by remember { mutableStateOf(0f) }  // -1 上看 / 0 中 / 1 下看
                var eyeSquint by remember { mutableStateOf(false) } // 眯眼（眼睛变小）
                // 轮19.29：新增表情所需状态
                var eyePupil by remember { mutableStateOf(1f) }   // 瞳孔缩放（惊讶放大 / 生气缩小）
                var eyeArc by remember { mutableStateOf(0f) }     // >0 = 笑眼弧线（0=圆点）
                // 上滑应用弧：显示中 / 高亮槽位（-1 = 未选）
                var showAppArc by remember { mutableStateOf(false) }
                var arcSel by remember { mutableStateOf(-1) }
                val ringApps = KeyboardManager.ringApps()
                LaunchedEffect(ringShape) {
                    if (ringShape != KeyboardManager.RING_SHAPE_EYE) return@LaunchedEffect
                    // 轮19.11：动作扩充 + 时间区间拉长（用户要求「拉长触发时间」）
                    //   动作：眨眼(圆点↔长条) / 左右看 / 上下看 / 眯眼 / 连眨两下
                    //   间隔：动作之间 2000~9000ms（原来 500~3100ms）
                    //   单次时长：看 700~2400ms，眨眼 120~260ms，眯眼 900~2600ms
                    val rnd = java.util.Random()
                    while (true) {
                        // 轮19.29：动作扩充到 10 套（原 5 套 + 惊讶 / 困倦 / 笑眼 / 转圈看 / 生气）
                        when (rnd.nextInt(10)) {
                            0 -> { // 眨眼
                                eyeBlink = true
                                kotlinx.coroutines.delay(120L + rnd.nextInt(140))
                                eyeBlink = false
                            }
                            1 -> { // 连眨两下
                                repeat(2) {
                                    eyeBlink = true
                                    kotlinx.coroutines.delay(110L + rnd.nextInt(90))
                                    eyeBlink = false
                                    kotlinx.coroutines.delay(150L + rnd.nextInt(120))
                                }
                            }
                            2 -> { // 左右看
                                val dir = if (rnd.nextBoolean()) -1f else 1f
                                eyeDx = dir
                                kotlinx.coroutines.delay(700L + rnd.nextInt(1700))
                                eyeDx = 0f
                            }
                            3 -> { // 上下看
                                val dir = if (rnd.nextBoolean()) -1f else 1f
                                eyeDy = dir
                                kotlinx.coroutines.delay(700L + rnd.nextInt(1700))
                                eyeDy = 0f
                            }
                            4 -> { // 眯眼
                                eyeSquint = true
                                kotlinx.coroutines.delay(900L + rnd.nextInt(1700))
                                eyeSquint = false
                            }
                            5 -> { // 惊讶：瞳孔放大 + 上抬（睁大眼睛）
                                eyePupil = 1.55f
                                eyeDy = -0.6f
                                kotlinx.coroutines.delay(500L + rnd.nextInt(500))
                                eyePupil = 1f
                                eyeDy = 0f
                            }
                            6 -> { // 困倦：半闭下沉 → 停一会儿 → 惊醒（睁大 + 上抬）
                                eyeSquint = true
                                eyeDy = 0.8f
                                kotlinx.coroutines.delay(1200L + rnd.nextInt(900))
                                eyeSquint = false
                                eyePupil = 1.4f
                                eyeDy = -0.5f
                                kotlinx.coroutines.delay(260L)
                                eyePupil = 1f
                                eyeDy = 0f
                            }
                            7 -> { // 笑眼：眼睛弯成上弧 + 微微上抬
                                eyeArc = 1f
                                eyeDy = -0.35f
                                kotlinx.coroutines.delay(700L + rnd.nextInt(800))
                                eyeArc = 0f
                                eyeDy = 0f
                            }
                            8 -> { // 转圈看：上 → 右 → 下 → 左
                                val seq = listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)
                                for ((dx, dy) in seq) {
                                    eyeDx = dx
                                    eyeDy = dy
                                    kotlinx.coroutines.delay(230L)
                                }
                                eyeDx = 0f
                                eyeDy = 0f
                            }
                            else -> { // 生气：瞳孔缩小 + 下压 + 快速抖两下
                                eyePupil = 0.7f
                                eyeSquint = true
                                eyeDy = 0.7f
                                repeat(2) {
                                    eyeDx = 0.18f
                                    kotlinx.coroutines.delay(70L)
                                    eyeDx = -0.18f
                                    kotlinx.coroutines.delay(70L)
                                }
                                kotlinx.coroutines.delay(500L + rnd.nextInt(600))
                                eyeDx = 0f
                                eyeDy = 0f
                                eyePupil = 1f
                                eyeSquint = false
                            }
                        }
                        kotlinx.coroutines.delay(2000L + rnd.nextInt(7000))
                    }
                }
                LaunchedEffect(oPressing) {
                    if (oPressing) {
                        kotlinx.coroutines.delay(400)
                        if (oPressing && !oLongFired) {
                            oLongFired = true
                            // 轮15：长按 ○ = 语音输入（定制工具栏入口保留在 ○ 菜单）
                            onAction(KeyAction.ToggleVoiceInput)
                            com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.LONG_PRESS)
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
                                showAppArc = false
                                arcSel = -1
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val capR = minOf(size.width, size.height) / 2f - 5f
                                val downPx = with(this@pointerInput) { 40.dp.toPx() }
                                // 上滑阈值（比下滑略小，悬浮栏呼出要跟手）
                                val upPx = with(this@pointerInput) { 34.dp.toPx() }
                                // 横向悬浮栏 5 槽：图标 48dp + 间距 6dp = 54dp/槽（面板 280dp 居中后，
                                // 槽 0 的中心在 ○ 键中心左侧 108dp）
                                val arcStepPx = with(this@pointerInput) { 54.dp.toPx() }
                                val arcFirstPx = with(this@pointerInput) { (-108).dp.toPx() }
                                var anchor: Offset? = null
                                var swipedDown = false
                                var swipedUp = false
                                // 轮19.11：下滑关闭悬浮栏后，本次手势不再参与其它判定
                                var gestureDone = false
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
                                    // 上滑：呼出应用弧（松手才启动）
                                    if (!swipedUp && dy < -upPx && abs(dy) > abs(dx)) {
                                        swipedUp = true
                                        oLongFired = true
                                        showAppArc = true
                                        arcSel = -1
                                        com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.LONG_PRESS)
                                    }
                                    if (gestureDone) {
                                        // 已处理完（如下滑关栏），只等抬起
                                    } else if (swipedUp) {
                                        // 轮19.11：① 下滑即关闭悬浮栏（原来只能打开）；
                                        //          ② 只有横向移动超过 24dp 才视为「选中某槽」，
                                        //             否则松手仅关闭、不启动应用（避免误启动中间槽）
                                        if (dy > downPx && abs(dy) > abs(dx)) {
                                            swipedUp = false
                                            gestureDone = true   // 不要再被后面的「下滑收键盘」分支吃掉
                                            arcSel = -1
                                            showAppArc = false
                                            com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.LONG_PRESS)
                                        } else if (abs(dx) > with(this@pointerInput) { 24.dp.toPx() }) {
                                            val idx = ((dx - arcFirstPx) / arcStepPx)
                                                .roundToInt().coerceIn(0, 4)
                                            if (idx != arcSel) {
                                                arcSel = idx
                                                com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.STEP)
                                            }
                                        }
                                        ringKnob = Offset.Zero
                                    } else if (dy > downPx && abs(dy) > abs(dx)) {
                                        // 轮19.3：只标记「下滑意图」，**不 break**——原实现一越过阈值
                                        // 就退出循环并立即 HideKeyboard，手指还没松键盘就没了。
                                        // 现在循环继续跟踪直到抬起，松手才收起。
                                        if (!swipedDown) {
                                            swipedDown = true
                                            oLongFired = true
                                            com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.LONG_PRESS)
                                        }
                                    } else {
                                        if (abs(dx) > stepPx) {
                                            onAction(KeyAction.Joystick((dx / stepPx).roundToInt()))
                                            oLongFired = true // 拖动即移光标，抑制长按定制
                                            com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.STEP)
                                            anchor = ch.position
                                        }
                                        // 圆环内点跟随手指，限幅：移动距离不超过圆环中心点（环半径内）
                                        if (oLongFired) {
                                            val off = Offset(ch.position.x - cx, ch.position.y - cy)
                                            val r = sqrt(off.x * off.x + off.y * off.y)
                                            ringKnob = if (r > capR && r > 0f) off * (capR / r) else off
                                        }
                                    }
                                }
                                when {
                                    // 上滑松手：启动高亮槽位的应用
                                    swipedUp -> {
                                        val pkg = ringApps.getOrNull(arcSel).orEmpty()
                                        if (pkg.isNotBlank()) {
                                            com.azime.input.core.apps.AppLauncher.launch(
                                                context.applicationContext, pkg,
                                            )
                                        }
                                    }
                                    swipedDown -> onAction(KeyAction.HideKeyboard)
                                }
                                showAppArc = false
                                arcSel = -1
                                oPressing = false
                                ringKnob = Offset.Zero
                            }
                        }
                        .clickable { onAction(KeyAction.ToggleMenuPanel) },
                    contentAlignment = Alignment.Center,
                ) {
                    // 圆环（轮15 重构）：静态白色实线圆环 + 呼吸动画（alpha 0.55~1.0 缓变，不刺眼）；
                    // 轮19.4：移动光标时圆环自身平移（呼啦圈模型，锚点不动）；
                    // 轮19.6：三种形状——圆环 / 圆角方形环 / 圆环+双眼（随机眨眼、左右看）；
                    // 轮19.9（省电）：呼吸动画**降帧**——原来用 rememberInfiniteTransition，
                    // 键盘可见期间每个 vsync（60~120fps）都要让 IME 窗口失效重绘一次。
                    // 实测该 App 9.7h 内 main(1659s)+RenderThread(496s) 占进程 CPU 的 92%，
                    // 而这是唯一的常驻动画。改为 120ms 步进（≈8fps）正弦取值，视觉几乎无差。
                    // 轮19.11b：呼吸**放慢 + 改正弦**——19.9 为了省电把 InfiniteTransition 换成
                    // 120ms 步进的三角波，周期没变(1.6s)但线性往返没有缓入缓出，观感变成"急促开关"。
                    // 现在周期 1.6s→**3.6s**，用正弦曲线（cos 映射），120ms 步进（≈8fps）省电不变。
                    var breathAlpha by remember { mutableStateOf(1f) }
                    LaunchedEffect(Unit) {
                        val periodMs = 3600f
                        val stepMs = 120L
                        var t = 0f
                        while (true) {
                            val phase = (t % periodMs) / periodMs
                            // 0 → 1 → 0 的正弦往返
                            val s01 = (1f - kotlin.math.cos(phase * 2f * Math.PI.toFloat())) / 2f
                            breathAlpha = 0.55f + 0.45f * s01
                            kotlinx.coroutines.delay(stepMs)
                            t += stepMs.toFloat()
                        }
                    }
                    Canvas(
                        Modifier
                            .size(29.dp)
                            .graphicsLayer {
                                translationX = ringKnob.x
                                translationY = ringKnob.y
                            },
                    ) {
                        val strokeW = 3.1.dp.toPx()
                        val ringR = size.minDimension / 2f - strokeW - 1f
                        // 轮19.42：原为**写死的 Color.White** —— 浅色主题（Nothing OS / Material You）下
                        // 白环压白底完全看不见。改为跟随主题文字色（浅色主题=深色环，深色主题=浅色环）。
                        val col = if (oPressing) c.accentActive else c.text.copy(alpha = breathAlpha)
                        when (ringShape) {
                            KeyboardManager.RING_SHAPE_SQUARE -> {
                                // 圆角正方形环（边长 = 直径，圆角 ≈ 30%）
                                val side = ringR * 2f
                                val half = side / 2f
                                drawRoundRect(
                                    color = col,
                                    topLeft = Offset(center.x - half, center.y - half),
                                    size = androidx.compose.ui.geometry.Size(side, side),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.30f, side * 0.30f),
                                    style = Stroke(strokeW),
                                )
                            }
                            KeyboardManager.RING_SHAPE_EYE -> {
                                drawCircle(color = col, radius = ringR, style = Stroke(strokeW))
                                // 双眼：圆点（睁眼）↔ 长条（闭眼）；eyeDx 左右看
                                val eyeR = (if (eyeSquint) 1.5.dp.toPx() else 2.2.dp.toPx()) * eyePupil
                                val sep = 3.6.dp.toPx()
                                val dyEye = -0.4.dp.toPx() + eyeDy * 1.3.dp.toPx()
                                val dxEye = eyeDx * 1.4.dp.toPx()
                                for (sx in listOf(-sep, sep)) {
                                    val cxE = center.x + sx + dxEye
                                    val cyE = center.y + dyEye
                                    if (eyeArc > 0f) {
                                        // 轮19.29 笑眼：向上弯的弧（∩），用弧线描边
                                        drawArc(
                                            color = col,
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(cxE - eyeR * 1.6f, cyE - eyeR * 0.9f),
                                            size = androidx.compose.ui.geometry.Size(eyeR * 3.2f, eyeR * 1.8f),
                                            style = Stroke(width = 1.6.dp.toPx()),
                                        )
                                    } else if (eyeBlink) {
                                        drawRoundRect(
                                            color = col,
                                            topLeft = Offset(cxE - eyeR, cyE - eyeR * 0.28f),
                                            size = androidx.compose.ui.geometry.Size(eyeR * 2f, eyeR * 0.56f),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(eyeR * 0.28f, eyeR * 0.28f),
                                        )
                                    } else {
                                        drawCircle(color = col, radius = eyeR, center = Offset(cxE, cyE))
                                    }
                                }
                            }
                            else -> drawCircle(color = col, radius = ringR, style = Stroke(strokeW))
                        }
                    }
                    // 上滑快捷应用：**横向悬浮栏**（轮19.8：原来是半圆弧分布，改为横向居中排布）
                    // 5 个图标一行居中，滑动选择-松手打开
                    if (showAppArc) {
                        Popup(
                            alignment = Alignment.TopCenter,
                            // 轮19.8b：Compose 的 AlignmentOffsetPositionProvider 语义是
                            //   popupPos = 父左上 + 父对齐点 − 弹层对齐点 + offset
                            // ⇒ TopCenter 下「弹层中心 = 父中心 + offset.x」，所以居中要 offset.x = 0
                            // （上一版写 -面板宽/2，把整条往左推了半个屏，用户看到的就是没居中）
                            offset = IntOffset(
                                0,
                                // 面板高 72dp + 10dp 间距，贴在 ○ 键上方
                                with(density) { (-82).dp.roundToPx() },
                            ),
                            onDismissRequest = { showAppArc = false },
                        ) {
                            Row(
                                // 面板收窄到 280dp（原 296dp 在窄屏上几乎占满整宽）
                                modifier = Modifier
                                    .size(280.dp, 72.dp)
                                    .background(c.barBg.copy(alpha = 0.94f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (i in 0 until KeyboardManager.RING_APP_SLOTS) {
                                    val pkg = ringApps.getOrNull(i).orEmpty()
                                    val sel = i == arcSel
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                if (sel) c.accentKeyBg else c.keyBg,
                                                RoundedCornerShape(14.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val icon = if (pkg.isNotBlank()) {
                                            com.azime.input.core.apps.AppLauncher.icon(context, pkg)
                                        } else null
                                        if (icon != null) {
                                            androidx.compose.foundation.Image(
                                                bitmap = icon,
                                                contentDescription = pkg,
                                                modifier = Modifier.size(if (sel) 34.dp else 30.dp),
                                            )
                                        } else {
                                            Text(
                                                text = if (pkg.isBlank()) "＋"
                                                else com.azime.input.core.apps.AppLauncher.label(context, pkg).take(2),
                                                fontSize = 12.sp,
                                                color = c.subText,
                                            )
                                        }
                                    }
                                }
                            }
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
    // 时间相位：让波纹在 RMS 平稳时也持续扩散
    val phase = rememberInfiniteTransition(label = "ripple").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "ripplePhase",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight + 4.dp)
            .background(c.bg)
            .clickable { onStop() },
        contentAlignment = Alignment.Center,
    ) {
        // 轮19.3：语音动画改为**中央波纹**——原实现是铺满整条工具栏的 28 根音量条
        // （用户反馈「太长、占满工具栏」）。现在只占中央 120dp，同心圆扩散 + 中心点随音量。
        // 轮19.11b：语音动画改为**长条波纹**（一排竖条 + 钟形包络 + 相位流动），
        // 长度 = 工具栏宽度的一半（居中）。
        Canvas(
            Modifier
                .fillMaxWidth(0.5f)
                .height(barHeight - 6.dp),
        ) {
            val level = (rms.value / 10f).coerceIn(0f, 1f)
            val accent = keyboardAccentActiveColor(c.barBg.luminance() < 0.5f)
            val bars = 16
            val gap = 3.dp.toPx()
            val barW = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1f)
            val midY = size.height / 2f
            val maxAmp = size.height * 0.42f
            for (i in 0 until bars) {
                val t = i / (bars - 1f)
                // 钟形包络：中间高、两端低
                val env = kotlin.math.sin(t * Math.PI).toFloat()
                val w = kotlin.math.sin(phase.value * 2f * Math.PI.toFloat() + i * 0.7f)
                val amp = maxAmp * env * (0.22f + 0.28f * (w + 1f) / 2f + 0.6f * level)
                val h = (2.dp.toPx() + amp).coerceAtMost(size.height * 0.94f)
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f + 0.45f * env * (0.4f + 0.6f * level)),
                    topLeft = Offset(i * (barW + gap), midY - h / 2f),
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
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
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
                    // 轮19.11：方案按钮改为「剪贴板同款悬浮栏 + 横向方案」的面板
                    "schema" -> onAction(KeyAction.ToggleSchemaPanel)
                    "numpad" -> onAction(KeyAction.SwitchPage(if (state.page == "numpad") "main" else "numpad"))
                    "emoji" -> onAction(KeyAction.SwitchPage("emoji"))
                    "symbols" -> onAction(KeyAction.SwitchPage("symgrid"))
                    "settings" -> onAction(KeyAction.OpenSettings)
                    "voice" -> onAction(KeyAction.ToggleVoiceInput)
                    "candidates" -> onAction(KeyAction.ToggleCandidatePanel)
                    "keyboard" -> onAction(KeyAction.OpenKeyboardEditor)
                    "deploy" -> onAction(KeyAction.Deploy)
                    "theme" -> onAction(KeyAction.ToggleThemeMode)
                    "ascii" -> onAction(KeyAction.ToggleAscii)
                    "undo" -> onAction(KeyAction.Undo)
                    "redo" -> onAction(KeyAction.Redo)
                    "onehand" -> onAction(KeyAction.ToggleHandMode)
                    "floatkbd" -> onAction(KeyAction.ToggleFloatKeyboard)
                    "hide" -> onAction(KeyAction.HideKeyboard)
                    "float", "ring" -> onAction(KeyAction.OpenSettings)
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
private fun toolbarToolIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector =
    // 轮19.4：工具栏图标统一走 OimeIcons（方案 J · 长投影立体）
    com.azime.input.ui.icons.OimeIcons.byName(id)
        ?: com.azime.input.ui.icons.OimeIcons.schemas

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
                                            com.azime.input.core.haptic.HapticsManager.press()
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
                                        color = if (selected) c.accentKeyText else c.text,
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
                                            com.azime.input.core.haptic.HapticsManager.press()
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
                                        color = if (on) c.accentKeyText else c.text,
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
                                        com.azime.input.core.haptic.HapticsManager.press()
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
                                    color = if (selected) c.accentKeyText else c.text,
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
                                            com.azime.input.core.haptic.HapticsManager.press()
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
                                        color = if (checked) c.accentKeyText else c.text,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        sw.name,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        // 轮19.34：选中态背景是强调色，副标题必须用 on-accent（原来灰字压蓝底看不见）
                                        color = if (checked) c.accentKeyText.copy(alpha = 0.85f) else c.subText,
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
                        // 轮19.11：○ 菜单图标改用 OimeIcons（此前仍是 Material 旧图标，与工具栏不一致）
                        val oi = com.azime.input.ui.icons.OimeIcons
                        val menuItems: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = listOf(
                            Triple(oi.clipboard, "剪贴板") { close(); onAction(KeyAction.ToggleClipboardPanel) },
                            // 轮19.14：方案组 / 方案管理 / 输入方案 从 ○ 菜单移除，统一到设置里管理
                            Triple(oi.tune, "方案开关") { subPage = "switches" },
                            Triple(oi.refresh, "部署") { close(); onAction(KeyAction.Deploy) },
                            // 反馈轮16：键盘编辑 —— 布局编辑器直达入口
                            Triple(oi.keyboard, "键盘编辑") { close(); onAction(KeyAction.OpenKeyboardEditor) },
                            Triple(oi.tune, "定制工具栏") { subPage = "toolbar" },
                            Triple(
                                if (themeDark) oi.sun else oi.moon,
                                if (themeDark) "亮色" else "暗色",
                            ) { onAction(KeyAction.ToggleThemeMode) },
                            // 轮19.34：切换输入法（唤起系统选择器）
                            Triple(oi.keyboard, "切换输入法") { close(); onAction(KeyAction.SwitchIme) },
                            // 轮19.64：**设置**直达（用户要求 ○ 菜单内一点就到设置页）
                            Triple(oi.settings, "设置") { close(); onAction(KeyAction.OpenSettings) },
                        )
                        // 轮19.11b：19.11 加进 ○ 菜单的那些功能已移除——它们只出现在「定制工具栏」的可选列表里
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
                                            .clickable {
                                                com.azime.input.core.haptic.HapticsManager.press()
                                                action()
                                            }
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
                        // 轮19.16：统一为左侧「← 返回」（原右侧「↑ 关闭」已删）
                        Text(
                            "←",
                            fontSize = 16.sp,
                            color = c.text,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    com.azime.input.core.haptic.HapticsManager.press()
                                    close()
                                }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                        Text("○ 菜单", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
                        Icon(
                            com.azime.input.ui.icons.OimeIcons.settings,
                            contentDescription = "设置",
                            tint = c.text,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    com.azime.input.core.haptic.HapticsManager.press()
                                    close(); onAction(KeyAction.OpenSettings)
                                }
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
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    /** 轮19.10：标题右侧的动作槽（如「定制工具栏」的保存按钮）。 */
    headerTrailing: (@Composable () -> Unit)? = null,
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
                    .clickable {
                        com.azime.input.core.haptic.HapticsManager.press()
                        onBack()
                    }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
            // 轮19.10：标题后紧跟的动作按钮（有则显示）
            headerTrailing?.invoke()
            // 轮19.16：右侧「↑ 关闭」已删除——统一只用左侧「← 返回」（onClose 不再需要单独入口）
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
        // 轮19.10：保存按钮移到悬浮栏标题右侧（原来在面板底部）
        headerTrailing = {
            Text(
                "保存",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.accentActive,
                modifier = Modifier
                    .clickable {
                        com.azime.input.core.haptic.HapticsManager.press()
                        onSave(selected.toList())
                    }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        },
    ) {
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
                                com.azime.input.core.haptic.HapticsManager.press()
                                // 轮19.11：两侧空间有限，最多 6 个
                                if (on) selected.remove(id)
                                else if (selected.size < KeyboardManager.MAX_TOOLBAR_TOOLS) selected.add(id)
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
    }
}

// ── 更多候选面板：网格展示当前页全部候选，◀▶ 翻页，点选上屏 ──

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                // 轮19.48：**按内容自适应宽度 + 自动换行**（FlowRow）。
                // 原来用「等宽 5 列网格」——注释（拆字/编码）很长时会被单元格裁掉，
                // 看起来像"固定长度"。改为内容决定宽度后，注释能完整显示，背景自然贴合文字。
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeySpacing),
                    verticalArrangement = Arrangement.spacedBy(KeySpacing),
                ) {
                    run {
                        state.candidates.forEachIndexed { idx, candidate ->
                            Box(
                                modifier = Modifier
                                    .background(c.keyBg, RoundedCornerShape(8.dp))
                                    .clickable { onAction(KeyAction.Candidate(idx)) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 轮19.43（按用户要求）：注释（拆字/拼音）**放到候选字上方**，
                                // 并给它一个**随文字长度伸缩**的胶囊背景（原来在右侧、被裁切）
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (candidate.comment.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .background(c.funcKeyBg, RoundedCornerShape(5.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                candidate.comment,
                                                fontSize = (KeyboardManager.fontSizeBar() * 0.58f).sp,
                                                maxLines = 1,
                                                color = c.subText,
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (idx < 9) {
                                            Text(
                                                "${idx + 1} ",
                                                fontSize = (KeyboardManager.fontSizeBar() * 0.65f).sp,
                                                color = c.subText,
                                            )
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
                        }
                    }
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
            // 轮19.14：每页排成 **4 行**（每行 = ceil(n/4)，上限 8 列；条目多于一页时仍按 8 列滚动）
            val perRow = if (items.isEmpty()) 8
            else if (items.size <= 24) maxOf(1, kotlin.math.ceil(items.size / 4.0).toInt())
            else 8
            val chunks = items.chunked(perRow)
            // 轮19.6：行高自适应填充——分类条目少（如「全部符号」只有 3 行）时下方大片空白，
            // 现按可用高度均分行高（下限 = 标准键高，上限 2.6×，底部预留悬浮栏空间）。
            val barReserve = 56.dp
            val avail = totalHeight - KeySpacing * 2 - barReserve
            val rowH = if (chunks.isEmpty()) keyHeight else {
                val byFill = (avail - KeySpacing * (chunks.size - 1)) / chunks.size
                maxOf(keyHeight, byFill.coerceAtMost(keyHeight * 2.6f))
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = KeySpacing)
                    .padding(top = 0.dp),
                verticalArrangement = Arrangement.spacedBy(KeySpacing),
            ) {
                chunks.forEach { chunk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowH),
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
                                // 轮19.14：显式用键盘主题文字色——原来不指定颜色，
                                // 而 IME 内 MaterialTheme 恒为 lightColorScheme ⇒ 暗色下字是黑的，
                                // 深色键面上完全看不清（截图即此问题）
                                Text(
                                    item,
                                    fontSize = 21.sp,
                                    maxLines = 1,
                                    color = c.text,
                                )
                            }
                        }
                        if (chunk.size < perRow) {
                            Spacer(Modifier.weight((perRow - chunk.size).toFloat()))
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
    // 轮19.14：第一列（滑键）/第五列（功能键）收窄，中间三列数字加宽（总权重仍为 5）
    val sideW = 0.78f
    val midW = (5f - sideW * 2f) / 3f
    val keyCornerDp = KeyboardManager.keyCornerDp().dp
    val rowGap = KeyboardManager.rowGapDp().dp
    val colGap = KeyboardManager.colGapDp().dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 轮19.2 高度对齐主键盘：垂直内边距必须用 rowGap（原来用 colGap）——
            // 主键盘 = 4×键高 + 5×行距；九宫格原来 = 4×键高 + 3×行距 + 2×列距，
            // 行列距不同（用户可调）时切页会看到高度跳变。
            // 轮19.4：底部同样收敛到 2dp，与主键盘一致。
            .padding(start = colGap, end = colGap, top = 0.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        // 前 3 行：符号滑键（跨 3 行）+ 数字三列 + 功能三键
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight * 3 + rowGap * 2),
            horizontalArrangement = Arrangement.spacedBy(colGap),
        ) {
            NumpadSliderKey(onAction = onAction, modifier = Modifier.weight(sideW).fillMaxSize())
            topRows.forEach { col ->
                Column(
                    modifier = Modifier.weight(midW),
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
                modifier = Modifier.weight(sideW),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                listOf(
                    Key("⌫", code = "backspace", width = 1f, type = KeyType.DELETE, icon = "backspace"),
                    Key("符号", code = "symgrid", width = 1f, type = KeyType.FUNCTION, icon = "symbols"),
                    Key("空格", code = "space", width = 1f, type = KeyType.SPACE, icon = "space"),
                ).forEach { k ->
                    Row(Modifier.weight(1f)) { KeyboardKey(key = k, state = state, onAction = onAction) }
                }
            }
        }
        // 第 4 行：返回 + 00 0 . + ⏎（反馈轮9：0 左 = 号、右 . 号；轮19.3：= → 00，
        // 因为 = 已并入左列滑键符号带）。注意：九宫格实际渲染在本函数内，
        // 改 KeyboardPages.numpad 只影响编辑器预览，不会生效。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight),
            horizontalArrangement = Arrangement.spacedBy(colGap),
        ) {
            // 与上方各列对齐：返回 / ⏎ 同宽（sideW），四个字符键同宽（midW）
            KeyboardKey(
                key = Key("返回", code = "main", width = sideW, type = KeyType.FUNCTION, icon = "back"),
                state = state, onAction = onAction,
            )
            listOf("00", "0", ".").forEach { ch ->
                KeyboardKey(
                    key = Key(ch, code = ch, width = midW, type = KeyType.CHARACTER),
                    state = state, onAction = onAction,
                )
            }
            KeyboardKey(
                key = Key("⏎", code = "enter", width = sideW, type = KeyType.ENTER, icon = "enter"),
                state = state, onAction = onAction,
            )
        }
        // 增高行（反馈轮10：九宫格也支持，与主键盘切换时高度一致）
        // 轮19.3：**不要再加显式 Spacer**——外层 Column 已有 Arrangement.spacedBy(rowGap)，
        // 显式 Spacer 会多出一个 rowGap，使九宫格比主键盘高 2×rowGap（切页高度跳变根因）。
        if (KeyboardManager.barEnabled()) {
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
                            com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.STEP)
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
        // 空格键显示（轮19.5 修正优先级）：
        //   1) 自定义文本有可见字符  → 显示自定义文本
        //   2) 自定义是纯空格（isNotEmpty 但全空白）→ 返回空串 = 只显示图标
        //   3) 未自定义            → 显示**方案名称**（schema.yaml 的 name，不是文件名）
        key.code == "space" && state.page == "main" -> {
            val custom = KeyboardManager.spaceLabel()
            when {
                // 轮19.30：**原样返回**（不再 trim）——前后空格是给用户微调文本位置用的，
                // 之前 trim 掉后"文本只能居中"（用户反馈）
                custom.isNotBlank() -> custom
                custom.isNotEmpty() -> ""   // 只打了空格 → 走图标
                state.schemaName.isNotBlank() ->
                    // 轮19.7：remember 住（RimeManager 内部也有缓存）——原来每次重组都查一次名称
                    remember(state.schemaName) {
                        com.azime.input.core.rime.RimeManager.schemaDisplayName(state.schemaName)
                    }
                else -> ""                  // 无方案 → 走图标
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
        // 轮19.62（用户指路 + 仓库记录定位）：**这里才是"组合中选候选键"的唯一实现点**——
        // 原来把 符号键/句号键/空格键 写死成候选 3/2/1（DEVLOG 轮8），所以设置里怎么改都"没生效" ✗
        // 现在改成**读「候选快捷键」设置**：第二候选键 ← candidateKey2()、第三候选键 ← candidateKey3()；
        // 「无」= 不映射（该键恢复原行为）。空格键仍固定为第一候选。
        val k2 = KeyboardManager.candidateKey2()
        val k3 = KeyboardManager.candidateKey3()
        val selectIndex = when {
            key.type == KeyType.SPACE && key.code == "space" -> 0
            k2.isNotEmpty() && key.code == k2 -> 1
            k3.isNotEmpty() && key.code == k3 -> 2
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
                        // 轮19.36：振动/音效改到**按下瞬间**（原来在 clickable 里＝抬手才振）
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                awaitFirstDown(requireUnconsumed = false)
                                HapticsManager.press()
                                com.azime.input.core.sound.SoundManager.playPress()
                            }
                        }
                        .clickable {
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

    // 长按符号（轮19.20）：
    // ① **自定义优先且完全覆盖**——`popup`（编辑器「长按气泡」）或 `longClick`（编辑器「长按」）
    //    任一非空，就**不再**合并内置表。19.19 的实现只在 popup 非空时覆盖，
    //    导致 K 键（内置括号表非空）的自定义永远进不来 —— 即用户说的"不能完全覆盖原生配置"。
    // ② 英文模式优先取 ASCII 符号（H：中文「——」/ 英文「_」）
    val longPressSymbols = remember(key.code, state.page, key.popup, key.longClick, state.asciiMode) {
        val custom = when {
            key.popup.isNotEmpty() -> key.popup
            !key.longClick.isNullOrBlank() -> listOf(key.longClick!!)
            else -> emptyList()
        }
        when {
            state.page == "symbols" || key.type != KeyType.CHARACTER -> emptyList()
            custom.isNotEmpty() -> custom
            // 轮19.24：随中英自动切换（K 键括号表也有 ASCII 变体）
            else -> com.azime.input.data.keyboard.longPressSymbolsFor(key.code, state.asciiMode)
        }
    }
    // 轮19.23（H 键英文不出 `_` 的真因）：手势块是 pointerInput(code, longClick, page)——
    // **asciiMode 不在 key 里**，切换中英后手势闭包不重建，提交时读到的仍是旧 state
    // （日志实证：show 时 list=[_] 正确，commit 时 ascii=false 且提交了——）。
    // 用 rememberUpdatedState 让手势里永远读最新值（Compose 处理过期闭包的标准做法）。
    val curState by rememberUpdatedState(state)
    val curSymbols by rememberUpdatedState(longPressSymbols)
    /** 气泡/松手提交：内置命令走命令分发，{Left} 后缀走文本+光标移动，其他字面上屏。 */
    fun commitLongSymbol(s: String) {
        // 轮19.21：埋点放到**提交点**（原来放在 remember{} 里，长按不会重算 → 日志永远不出现）
        com.azime.input.core.diag.Diag.log("Sym", "commit sym=$s ascii=${curState.asciiMode}")
        when {
            s == "select_all" || s == "cut" || s == "copy" || s == "paste" ->
                onAction(KeyAction.Resolved(s))
            s.endsWith("{Left}") -> onAction(KeyAction.Resolved(s))
            else -> onAction(KeyAction.DirectCommit(s))
        }
    }
    val hasCustomLong = !key.longClick.isNullOrBlank()
    val isPageKey = key.type == KeyType.FUNCTION && key.code == "symbols"
    // 轮19.43：退格键 + **空格键** 都走长按连发（空格连发 = 像电脑空格键那样连续输入）
    val autoRepeat = key.type == KeyType.DELETE || key.type == KeyType.SPACE

    var pressing by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    // 触发前移动超过 5dp 取消长按（对齐 xime.az KeyButton，防止打字抖动误触发）
    var longCancelled by remember { mutableStateOf(false) }
    // 轮19.30：长按动作可能改变引擎状态（如空格长按切中英）→ 只需要复位**按下态**。
    // ⚠️ 绝不能复位 `longFired`：抬手判定是 `!longFired -> onKeyAction(...)`，
    // 一旦被清掉，长按切完中英抬手就会**多输入一个空格**（19.24 引入的回归）。
    LaunchedEffect(state.asciiMode) {
        pressing = false
    }
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
    // 长按气泡滑动选择步长（轮19.2：40dp→22dp。原 40dp 时 K 键 6 对括号要滑 200dp
    // 才能到最后一项，用户反馈「难以滑到第四第五对」）
    val longStepPx = with(density) { 22.dp.toPx() }
    // 气泡多行时上滑换行步长（一行 5 项，上滑一行 = 前缀 +5）
    val longRowStepPx = with(density) { 30.dp.toPx() }

    val hasGestures = longPressSymbols.isNotEmpty() || autoRepeat || hasCustomLong || isPageKey ||
        key.swipeUp != null || key.swipeDown != null || key.swipeLeft != null || key.swipeRight != null

    // 长按定时器（触发时间设置内可调，默认 180ms；触发前移动超 5dp 取消）
    LaunchedEffect(pressing) {
        if (pressing) {
            delay(KeyboardManager.longPressMs().toLong())
            if (pressing && !longFired && !longCancelled) {
                when {
                    curSymbols.isNotEmpty() -> {
                        // 轮19.21：长按即打点——记录本次气泡的真实列表与英文状态
                        com.azime.input.core.diag.Diag.log("Sym", "show code=${key.code} ascii=${curState.asciiMode} list=$curSymbols")
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
    // 轮19.36：**振动/音效统一在"按下瞬间"触发**——
    // 手势键走 awaitEachGesture 的 down 分支（按下即振）；没有手势的键（九宫格数字、符号页等）
    // 原来挂在 clickable 上（**抬手才振**），体感与主键盘不一致。这里对齐到按下即振。
    if (!hasGestures) {
        LaunchedEffect(clickPressed) {
            if (clickPressed) {
                HapticsManager.press()
                com.azime.input.core.sound.SoundManager.playPress()
            }
        }
    }
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
        // 轮19.35：风格 token —— 描边 / 阴影（One UI 的柔和阴影、Nothing OS 的细描边）
        .then(
            if (c.keyShadowDp > 0) {
                Modifier.shadow(c.keyShadowDp.dp, RoundedCornerShape(keyCornerDp))
            } else {
                Modifier
            },
        )
        .then(
            if (c.keyBorder != null) {
                Modifier.border(1.dp, c.keyBorder, RoundedCornerShape(keyCornerDp))
            } else {
                Modifier
            },
        )
        .background(bgAnimated, RoundedCornerShape(keyCornerDp))
    if (hasGestures) {
        // 手势闭包内读取最新 state（preedit 等会随打字频繁变化）
        val currentState by rememberUpdatedState(state)
        // 轮19.55：key 用**整个数据类**做 key（原来只有 code/longClick/page）——
        // 否则编辑了四向滑动动作后，手势块不会重启 ⇒ 用的还是旧动作（用户反馈"改了不即时生效"）
        baseModifier = baseModifier.pointerInput(key, state.page) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                longFired = false
                longCancelled = false
                pressing = true
                HapticsManager.press(); com.azime.input.core.sound.SoundManager.playPress()
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
                        if (longFired && curSymbols.isNotEmpty()) {
                            // 长按气泡已弹出：滑动选择符号（多符号时），松手上屏（不触发四向手势）
                            if (curSymbols.size > 1) {
                                // 轮19.2：横向按 22dp/项 选列，上滑换行（气泡每行 5 项）
                                val colSteps = (dx / longStepPx).roundToInt()
                                val rowSteps = (-dy / longRowStepPx).toInt().coerceAtLeast(0)
                                val idx = (rowSteps * 5 + colSteps)
                                    .coerceIn(0, curSymbols.size - 1)
                                if (idx != longSelIdx) {
                                    longSelIdx = idx
                                    com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.STEP)
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
                                    com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.LONG_PRESS)
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
                                        com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.LONG_PRESS)
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
                            com.azime.input.core.haptic.HapticsManager.haptic(com.azime.input.core.haptic.HapticsManager.Type.STEP)
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
                    longFired && showBubble && curSymbols.isNotEmpty() -> {
                        val symbol = curSymbols[
                            if (curSymbols.size == 1) 0 else longSelIdx.coerceIn(0, curSymbols.size - 1)
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
            HapticsManager.press(); com.azime.input.core.sound.SoundManager.playPress()
            onKeyAction(key, onAction)
            HapticsManager.release()
        }
    }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
    ) {
        // 轮19.4：功能键优先渲染图标（OimeIcons）；无图标 / 滑动预览中回落文字。
        // 轮19.5：空格键例外——有文本（自定义文本或方案名称）时优先文本，文本为空才显示图标。
        // 轮19.25：第四行首键（符号/数字入口）的图标跟随设置偏好——26 键符号 ↔ 九宫格
        // 轮19.65：123/符号键不再用图标 —— 按 preferredPage 直接显示文字 "123" / "？#！"
        val isSymbolsKey = key.code == "symbols"
        val iconName = if (isSymbolsKey) null else key.icon
        val labelText = if (isSymbolsKey) {
            if (KeyboardManager.preferredPage() == "numpad") "123" else "？#！"
        } else label
        val keyIcon = if (iconName != null && swipePreview == null) {
            com.azime.input.ui.icons.OimeIcons.byName(iconName)
        } else null
        // 轮19.6：仅主键盘空格优先文本（九宫格/符号页空格永远显示图标）
        val preferText = key.code == "space" && state.page == "main" && label.isNotBlank()
        if (keyIcon != null && !preferText) {
            Icon(
                keyIcon,
                contentDescription = key.label,
                tint = fg,
                modifier = Modifier.size(
                    (KeyboardManager.fontSizeKey() * 1.25f).dp.coerceIn(14.dp, 34.dp),
                ),
            )
        } else {
            Text(
                // 反馈轮11：四向预览位置可选——键面中央（默认替换键名）或键上方气泡
                text = if (swipePreviewAbove) labelText else (swipePreview ?: labelText),
                // 反馈轮9：键面字号可调（键盘/工具栏分开设置）
                // 轮19.53：**撤回** 19.52 的"带提示时主字缩小 15%"——用户要求不要动文本大小，只调提示位置
                fontSize = if (key.type == KeyType.CHARACTER) KeyboardManager.fontSizeKey().sp
                else (KeyboardManager.fontSizeKey() * 0.7f).sp,
                // 轮19.35：字重与字体跟随风格（One UI 半粗 / Nothing OS 等宽）
                fontWeight = if (c.keyBold) FontWeight.SemiBold else FontWeight.Medium,
                fontFamily = if (c.monoFont && key.type == KeyType.CHARACTER) FontFamily.Monospace else null,
                color = fg,
                maxLines = 1,
                // 轮19.30：空格键自定义文本可按设置左右微调（默认 0 = 居中）
                modifier = if (key.code == "space" && state.page == "main") {
                    Modifier.offset(x = KeyboardManager.spaceLabelOffsetDp().dp)
                } else {
                    Modifier
                },
            )
        }
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
        // 轮19.24：键面符号随中英切换（英文模式显示该键的 ASCII 长按符号）
        // 轮19.55：优先用**键自己的**长按动作/符号（编辑过的要覆盖内置默认），
        // 都没编辑过才回落内置表。短 token（≤4 字符）才当符号显示，避免把 cut 这种动作名当提示。
        fun firstSymbolOf(v: String?): String? = v?.trim()?.split(" ")
            ?.firstOrNull { it.isNotBlank() }?.removeSuffix("{Left}")?.takeIf { it.length <= 4 }
        val hintText = key.hint
            ?: key.popup.firstOrNull { it.isNotBlank() }?.removeSuffix("{Left}")?.takeIf { it.length <= 4 }
            ?: firstSymbolOf(key.longClick)
            ?: key.longClick?.takeIf { it.isNotBlank() }?.let { actionDisplayName(it) }
                ?.takeIf { it != key.longClick }
            ?: com.azime.input.data.keyboard.longPressHint(key.code, state.asciiMode)
        if (swipePreview == null && KeyboardManager.hintLong() && hintText != null && key.type == KeyType.CHARACTER) {
            Text(
                text = hintText,
                fontSize = 8.sp,
                color = c.subText,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = -KeyboardManager.hintOffPressX().dp,
                        y = KeyboardManager.hintOffPressY().dp,
                    ),
            )
        }
        // 轮19.1 修复：四向滑动提示——原实现只控制「滑动过程中的临时预览」，
        // 开关打开后键面不显示任何提示（用户反馈「打开开关不显示」）。
        // 现按设置把该方向的滑动符号常驻渲染在键面对应位置（无滑动动作的键不显示）。
        if (key.type == KeyType.CHARACTER || key.type == KeyType.DELETE) {
            val dirHintFont = 8.sp
            // 轮19.11b：四向提示位置 = 字面方向（上→正上、下→正下、左→正左、右→正右），
            // 长按仍固定右上角（见上方 hintText 的 TopEnd）。
            // 轮19.56：偏移语义 = **从该侧边缘往里的距离**（正值一律朝键面内侧）：
            //   上/下 = 距上/下边缘往里；左/右 = 距左/右边缘往里（Y 正数向下）；
            //   长按 = 距右边往里 / 距顶边往里。默认值：上6 下6 左3 右3 长按(3,3)
            val upX = KeyboardManager.hintOffUpX().dp
            val upY = KeyboardManager.hintOffUpY().dp
            val downX = KeyboardManager.hintOffDownX().dp
            val downY = KeyboardManager.hintOffDownY().dp
            val leftX = KeyboardManager.hintOffLeftX().dp
            val leftY = KeyboardManager.hintOffLeftY().dp
            val rightX = KeyboardManager.hintOffRightX().dp
            val rightY = KeyboardManager.hintOffRightY().dp
            key.swipeUp?.let {
                if (KeyboardManager.hintUp()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopCenter).offset(x = upX, y = upY),
                )
            }
            key.swipeDown?.let {
                if (KeyboardManager.hintDown()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(x = downX, y = -downY),
                )
            }
            key.swipeLeft?.let {
                if (KeyboardManager.hintLeft()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText, maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = leftX, y = leftY),
                )
            }
            key.swipeRight?.let {
                if (KeyboardManager.hintRight()) Text(
                    actionPreview(it), fontSize = dirHintFont, color = c.subText, maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = -rightX, y = rightY),
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
                                    // 轮19.56：内置动作显示中文名（Time → 时间）
                                    text = actionDisplayName(symbol.removeSuffix("{Left}")),
                                    fontSize = 16.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    // 轮19.52：选中项底色是 accentKeyBg ⇒ 文字必须用 on 色，
                                    // 原来 accentActive（同色）压上去 ⇒ 整块看不出字（用户截图）
                                    color = if (isSel) c.accentKeyText else c.text,
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
                // 轮19.2：只保留「26键符号键盘 / 九宫格数字键盘」两项，横向排布、图标代替文字
                Row(
                    modifier = Modifier
                        .background(c.barBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        "symbols" to com.azime.input.ui.icons.OimeIcons.symbols,  // 26 键符号键盘
                        "numpad" to com.azime.input.ui.icons.OimeIcons.numpad,    // 九宫格数字键盘
                    ).forEach { (page, icon) ->
                        val on = page == KeyboardManager.preferredPage()
                        Icon(
                            icon,
                            contentDescription = page,
                            tint = if (on) c.accentActive else c.text,
                            modifier = Modifier
                                .clickable {
                                    showPageBubble = false
                                    KeyboardManager.setPreferredPage(page)
                                    onAction(KeyAction.SwitchPage(page))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 滑动方向上的键面预览文本。 */
/** 轮19.56：内置动作的**中文显示名**（键面提示与长按气泡都用它，避免"Time 就显示 Time"）。 */
fun actionDisplayName(action: String): String = when (action.trim().lowercase()) {
    "date" -> "日期"
    "time" -> "时间"
    "chinesedate" -> "农历"
    "repeatcommit" -> "重复"
    "deploy" -> "部署"
    "switch_ime" -> "切换输入法"
    "clipboard" -> "剪贴板"
    "menu" -> "○菜单"
    "select_all" -> "全选"
    "cut" -> "剪切"
    "copy" -> "复制"
    "paste" -> "粘贴"
    "undo" -> "撤回"
    "delete_all" -> "全删"
    "newline" -> "换行"
    "escape", "esc" -> "清空"
    else -> action
}

private fun actionPreview(action: String): String = when (action) {
    KeyActions.BS_UP, "delete_all" -> "全删"
    KeyActions.BS_DOWN, "undo" -> "撤回"
    KeyActions.BS_LEFT, "select_back" -> "选择"
    "newline" -> "⏎"
    "toggle_ascii" -> "中/EN"
    "caps_lock" -> "⇪"
    else -> {
        // 内置动作（date/time/…）显示中文名；字面文本原样显示
        val named = actionDisplayName(action)
        if (named != action) {
            named
        } else {
            val resolved = ActionResolver.resolveAction(action)
            when (resolved) {
                is com.azime.input.core.action.ResolvedAction.Commit -> resolved.text
                else -> action
            }
        }
    }
}

private fun onKeyAction(key: Key, onAction: (KeyAction) -> Unit) {
    when (key.type) {
        // 轮19.10：多字符 code（如九宫格的 "00"）必须整串上屏——原来取 .first() 只出一个 0
        KeyType.CHARACTER ->
            if (key.code.length > 1) onAction(KeyAction.DirectCommit(key.code))
            else onAction(KeyAction.CharKey(key.code.first()))
        KeyType.SPACE -> onAction(KeyAction.Space)
        KeyType.ENTER -> onAction(KeyAction.Enter)
        KeyType.DELETE -> onAction(KeyAction.Backspace)
        KeyType.MODIFIER -> onAction(KeyAction.Shift)
        KeyType.FUNCTION -> when (key.code) {
            // 轮19.25：切页沿用 ToggleSymbols（其内部已按 preferredPage 偏好：26 键符号 / 九宫格）；
            // 键面图标在 keyIcon 处同步成对应图标
            "symbols" -> onAction(KeyAction.ToggleSymbols)
            "emoji_back" -> onAction(KeyAction.SwitchPage("main"))
            "main" -> onAction(KeyAction.SwitchPage("main"))
            "numpad" -> onAction(KeyAction.SwitchPage("numpad"))
            "symgrid" -> onAction(KeyAction.SwitchPage("symgrid"))
            "emoji" -> onAction(KeyAction.SwitchPage("emoji"))
            // lua 布局的自定义 FUNCTION 键：命令 / preset 引用 / 文本上屏（trime2 语义，Service 端解析）
            else -> {
                if (ActionResolver.resolveAction(key.code) != null) {
                    onAction(KeyAction.Resolved(key.code))
                } else {
                    onAction(KeyAction.ToggleAscii)
                }
            }
        }
    }
}

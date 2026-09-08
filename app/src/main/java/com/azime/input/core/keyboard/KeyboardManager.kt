package com.azime.input.core.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.azime.input.data.keyboard.KeyboardPages
import com.azime.input.data.model.KeyboardLayout
import com.google.gson.Gson
import java.io.File

/**
 * 键盘布局管理器。
 *
 * 职责：
 * - 持有内置布局（[KeyboardPages.qwerty] / [KeyboardPages.symbols]）；
 * - 用户自定义布局以 JSON 形式持久化在 `filesDir/keyboards/<name>.json`，
 *   启动时全量加载进内存（键盘进程生命周期内不再读盘）；
 * - 维护「当前主键盘」指针（SharedPreferences），可指向内置或自定义布局。
 *
 * 线程模型：所有读写走 [lock]；`mainLayout()/symbolLayout()` 只读内存缓存，
 * 供 Compose 直接调用（布局变更发生在 Service 初始化阶段，无需响应式刷新）。
 */
object KeyboardManager {

    private const val DIR_NAME = "keyboards"
    private const val PREFS_NAME = "keyboard_prefs"
    private const val PREF_ACTIVE_MAIN = "active_main_layout"
    private const val PREF_PREFERRED_PAGE = "preferred_page"
    private const val PREF_KEY_HEIGHT_DP = "key_height_dp"
    private const val PREF_BAR_HEIGHT_DP = "bar_height_dp"
    private const val PREF_BAR_ENABLED = "bar_enabled"
    private const val PREF_TOOLBAR_ITEMS = "toolbar_items"
    private const val DEFAULT_MAIN = "qwerty"
    private const val DEFAULT_PAGE = "symbols"
    private const val DEFAULT_KEY_HEIGHT_DP = 46
    private const val DEFAULT_BAR_HEIGHT_DP = 48

    /** 布局名仅允许字母/数字/下划线/连字符，直接用作文件名。 */
    private val namePattern = Regex("[A-Za-z0-9_-]{1,64}")

    private val gson = Gson()
    private val lock = Any()

    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences

    /** name -> 用户自定义布局。 */
    private val customs = LinkedHashMap<String, KeyboardLayout>()

    @Volatile
    private var activeMain: String = DEFAULT_MAIN

    /** 专用页保留名：symbols/numpad/emoji 有专用渲染，不允许激活为主键盘布局（防编辑器副本劫持主键盘）。 */
    val ReservedPageNames: Set<String> = setOf("symbols", "numpad", "emoji")

    @Volatile
    private var preferredPage: String = DEFAULT_PAGE

    // ── 生命周期 ─────────────────────────────────────────────

    fun initialize(context: Context) {
        synchronized(lock) {
            dir = File(context.filesDir, DIR_NAME)
            dir.mkdirs()
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            activeMain = (prefs.getString(PREF_ACTIVE_MAIN, DEFAULT_MAIN) ?: DEFAULT_MAIN)
                .takeUnless { it in ReservedPageNames } ?: DEFAULT_MAIN
            preferredPage = prefs.getString(PREF_PREFERRED_PAGE, DEFAULT_PAGE) ?: DEFAULT_PAGE
            reloadCustomsLocked()
        }
    }

    private fun reloadCustomsLocked() {
        customs.clear()
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: return
        for (file in files) {
            val layout = runCatching {
                gson.fromJson(file.readText(), KeyboardLayout::class.java)
            }.getOrNull()
            // 损坏/非法的 JSON 直接删除，回退内置布局
            if (layout == null || layout.name.isBlank() || layout.rows.isEmpty()) {
                file.delete()
                continue
            }
            // 与内置同名的旧版本自定义布局视为过期：删除并回退新版内置（防旧 JSON 遮蔽新布局结构）
            val builtin = builtinByName(layout.name)
            if (builtin != null && builtin.rev > layout.rev) {
                file.delete()
                if (activeMain == layout.name) setActiveMainLocked(builtin.name)
                continue
            }
            customs[layout.name] = layout
        }
    }

    // ── 布局获取（Compose 渲染路径） ──────────────────────────

    /** 当前主键盘布局：自定义覆盖优先，否则按名字回退内置，最终兜底 qwerty。 */
    fun mainLayout(): KeyboardLayout = synchronized(lock) {
        customs[activeMain]
            ?: builtinByName(activeMain)
            ?: builtinByName(DEFAULT_MAIN)
            ?: KeyboardPages.qwerty
    }

    /** 符号页布局（内置；后续可扩展为自定义符号页）。 */
    fun symbolLayout(): KeyboardLayout = KeyboardPages.symbols

    /** 九宫格数字布局。 */
    fun numpadLayout(): KeyboardLayout = KeyboardPages.numpad

    /** 按页面名取布局；emoji 页由 UI 层专门渲染，返回 null。 */
    fun layoutFor(page: String): KeyboardLayout? = when (page) {
        "main" -> mainLayout()
        "symbols" -> symbolLayout()
        "numpad" -> numpadLayout()
        else -> null
    }

    /** 符号键切换的默认键盘页（symbols/numpad/emoji）。 */
    fun preferredPage(): String = preferredPage

    fun setPreferredPage(page: String) {
        synchronized(lock) {
            preferredPage = page
            prefs.edit().putString(PREF_PREFERRED_PAGE, page).apply()
        }
    }

    // ── 键盘尺寸（设置页滑杆可调） ──────────────────────────

    fun keyHeightDp(): Int = prefs.getInt(PREF_KEY_HEIGHT_DP, DEFAULT_KEY_HEIGHT_DP)

    fun setKeyHeightDp(dp: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_KEY_HEIGHT_DP, dp.coerceIn(36, 64)).apply() }
    }

    /** 增高行（键盘底部空行）高度，范围 1-72dp。 */
    fun barHeightDp(): Int = prefs.getInt(PREF_BAR_HEIGHT_DP, DEFAULT_BAR_HEIGHT_DP)

    fun setBarHeightDp(dp: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_BAR_HEIGHT_DP, dp.coerceIn(1, 72)).apply() }
    }

    /** 增高行开关：关闭时工具栏/候选栏回落为紧凑高度（38dp）。 */
    fun barEnabled(): Boolean = prefs.getBoolean(PREF_BAR_ENABLED, false)

    fun setBarEnabled(v: Boolean) {
        synchronized(lock) { prefs.edit().putBoolean(PREF_BAR_ENABLED, v).apply() }
    }

    /** 空格键自定义显示文本：空 = 显示当前方案名（默认）。 */
    private const val PREF_SPACE_LABEL = "space_label"

    fun spaceLabel(): String = prefs.getString(PREF_SPACE_LABEL, "") ?: ""

    /** 原样保存（反馈轮11：不再 trim——空格也是有效标签字符）。 */
    fun setSpaceLabel(v: String) {
        synchronized(lock) { prefs.edit().putString(PREF_SPACE_LABEL, v).apply() }
    }

    // ── 字号 / 外观（反馈轮9：键盘与工具栏字号分开；圆角/行距/列距可调） ──

    private const val PREF_FONT_SIZE_KEY = "font_size_key"
    private const val PREF_FONT_SIZE_BAR = "font_size_bar"
    private const val PREF_KEY_CORNER = "key_corner_dp"
    private const val PREF_ROW_GAP = "row_gap_dp"
    private const val PREF_COL_GAP = "col_gap_dp"

    /** 键盘键面字号（sp），默认 20。 */
    fun fontSizeKey(): Int = prefs.getInt(PREF_FONT_SIZE_KEY, 20)

    fun setFontSizeKey(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_FONT_SIZE_KEY, v.coerceIn(12, 30)).apply() }
    }

    /** 工具栏/候选字号（sp），默认 18。 */
    fun fontSizeBar(): Int = prefs.getInt(PREF_FONT_SIZE_BAR, 18)

    fun setFontSizeBar(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_FONT_SIZE_BAR, v.coerceIn(12, 28)).apply() }
    }

    /** 按键圆角（dp），默认 8。 */
    fun keyCornerDp(): Int = prefs.getInt(PREF_KEY_CORNER, 8)

    fun setKeyCornerDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_KEY_CORNER, v.coerceIn(0, 20)).apply() }
    }

    /** 键盘行距（dp），默认 4。 */
    fun rowGapDp(): Int = prefs.getInt(PREF_ROW_GAP, 4)

    fun setRowGapDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_ROW_GAP, v.coerceIn(1, 10)).apply() }
    }

    /** 键盘列距（dp），默认 4。 */
    fun colGapDp(): Int = prefs.getInt(PREF_COL_GAP, 4)

    fun setColGapDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_COL_GAP, v.coerceIn(1, 10)).apply() }
    }

    // ── 悬浮窗（编码预览悬浮窗，参考 trime CompositionView） ──

    private const val PREF_FLOAT_ENABLE = "float_enable"
    private const val PREF_FLOAT_MODE = "float_mode" // default | custom
    private const val PREF_FLOAT_X = "float_x_dp"
    private const val PREF_FLOAT_Y = "float_y_dp"
    private const val PREF_FLOAT_TEXT = "float_text_sp"
    private const val PREF_FLOAT_ALPHA = "float_bg_alpha"

    /** 悬浮窗开关：输入时在键盘上方悬浮显示输入码（默认关）。 */
    fun floatEnabled(): Boolean = prefs.getBoolean(PREF_FLOAT_ENABLE, false)

    fun setFloatEnabled(v: Boolean) {
        synchronized(lock) { prefs.edit().putBoolean(PREF_FLOAT_ENABLE, v).apply() }
    }

    /** 悬浮窗模式：default=内置样式（固定位置/字号）；custom=用户自定义位置字号透明度。 */
    fun floatMode(): String = prefs.getString(PREF_FLOAT_MODE, "default") ?: "default"

    fun setFloatMode(v: String) {
        synchronized(lock) { prefs.edit().putString(PREF_FLOAT_MODE, v).apply() }
    }

    /** 悬浮窗水平位置（dp，距键盘左缘），默认 16。 */
    fun floatXDp(): Int = prefs.getInt(PREF_FLOAT_X, 16)

    fun setFloatXDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_FLOAT_X, v.coerceIn(0, 400)).apply() }
    }

    /** 悬浮窗垂直位置（dp，距键盘顶部向上），默认 100。 */
    fun floatYDp(): Int = prefs.getInt(PREF_FLOAT_Y, 100)

    fun setFloatYDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_FLOAT_Y, v.coerceIn(20, 400)).apply() }
    }

    /** 悬浮窗字号（sp），默认 22。 */
    fun floatTextSp(): Int = prefs.getInt(PREF_FLOAT_TEXT, 22)

    fun setFloatTextSp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_FLOAT_TEXT, v.coerceIn(14, 40)).apply() }
    }

    /** 悬浮窗背景不透明度（0-100），默认 92。 */
    fun floatBgAlpha(): Int = prefs.getInt(PREF_FLOAT_ALPHA, 92)

    fun setFloatBgAlpha(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_FLOAT_ALPHA, v.coerceIn(20, 100)).apply() }
    }

    /**
     * 九宫格滑键符号带（空格分隔自定义；空 = 内置默认符号）。
     * 示例："！ @ 。 、 ？" —— 以空格切分，每段一个符号。
     */
    private const val PREF_SLIDER_SYMBOLS = "numpad_slider_symbols"

    fun sliderSymbols(): List<String> {
        val raw = prefs.getString(PREF_SLIDER_SYMBOLS, "")?.trim() ?: ""
        if (raw.isEmpty()) return com.azime.input.data.keyboard.KeyboardPages.NumpadSliderSymbols
        val list = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        return list.ifEmpty { com.azime.input.data.keyboard.KeyboardPages.NumpadSliderSymbols }
    }

    fun setSliderSymbols(raw: String) {
        synchronized(lock) { prefs.edit().putString(PREF_SLIDER_SYMBOLS, raw.trim()).apply() }
    }

    fun sliderSymbolsRaw(): String = prefs.getString(PREF_SLIDER_SYMBOLS, "") ?: ""

    /**
     * 符号提示显示开关：长按符号角标 + 四向滑动预览，各自独立。
     * 关闭时动作仍执行，只是不在键面上显示提示文字。
     */
    private const val PREF_HINT_LONG = "hint_long"
    private const val PREF_HINT_UP = "hint_up"
    private const val PREF_HINT_DOWN = "hint_down"
    private const val PREF_HINT_LEFT = "hint_left"
    private const val PREF_HINT_RIGHT = "hint_right"

    private fun hintPref(key: String, default: Boolean = true): Boolean =
        prefs.getBoolean(key, default)

    private fun setHintPref(key: String, v: Boolean) {
        synchronized(lock) { prefs.edit().putBoolean(key, v).apply() }
    }

    fun hintLong(): Boolean = hintPref(PREF_HINT_LONG)
    fun hintUp(): Boolean = hintPref(PREF_HINT_UP)
    fun hintDown(): Boolean = hintPref(PREF_HINT_DOWN)
    fun hintLeft(): Boolean = hintPref(PREF_HINT_LEFT)
    fun hintRight(): Boolean = hintPref(PREF_HINT_RIGHT)

    fun setHintLong(v: Boolean) = setHintPref(PREF_HINT_LONG, v)
    fun setHintUp(v: Boolean) = setHintPref(PREF_HINT_UP, v)
    fun setHintDown(v: Boolean) = setHintPref(PREF_HINT_DOWN, v)
    fun setHintLeft(v: Boolean) = setHintPref(PREF_HINT_LEFT, v)
    fun setHintRight(v: Boolean) = setHintPref(PREF_HINT_RIGHT, v)

    // ── 按键响应时间（反馈轮10：用户自行微调） ──────────────

    private const val PREF_LONG_PRESS_MS = "long_press_ms"
    private const val PREF_REPEAT_START_MS = "repeat_start_ms"
    private const val PREF_REPEAT_MS = "repeat_interval_ms"
    private const val PREF_SWIPE_THRESHOLD_DP = "swipe_threshold_dp"

    /** 长按触发时间 ms（默认 180，100-1000）。 */
    fun longPressMs(): Int = prefs.getInt(PREF_LONG_PRESS_MS, 180)

    fun setLongPressMs(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_LONG_PRESS_MS, v.coerceIn(100, 1000)).apply() }
    }

    /** 连发起动延时 ms（默认 150，50-500）。 */
    fun repeatStartMs(): Int = prefs.getInt(PREF_REPEAT_START_MS, 150)

    fun setRepeatStartMs(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_REPEAT_START_MS, v.coerceIn(50, 500)).apply() }
    }

    /** 连发间隔 ms（默认 45，20-200）。 */
    fun repeatIntervalMs(): Int = prefs.getInt(PREF_REPEAT_MS, 45)

    fun setRepeatIntervalMs(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_REPEAT_MS, v.coerceIn(20, 200)).apply() }
    }

    /** 滑动手势触发距离 dp（默认 30，10-80）。 */
    fun swipeThresholdDp(): Int = prefs.getInt(PREF_SWIPE_THRESHOLD_DP, 30)

    fun setSwipeThresholdDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_SWIPE_THRESHOLD_DP, v.coerceIn(10, 80)).apply() }
    }

    // ── 手势提示位置（反馈轮11：四向预览与长按符号位置可调） ──

    private const val PREF_BUBBLE_X_DP = "bubble_x_dp"
    private const val PREF_BUBBLE_Y_EXTRA_DP = "bubble_y_extra_dp"
    private const val PREF_SWIPE_PREVIEW_ABOVE = "swipe_preview_above"

    /** 长按符号气泡水平偏移 dp（默认 3，0-24）。 */
    fun bubbleXDp(): Int = prefs.getInt(PREF_BUBBLE_X_DP, 3)

    fun setBubbleXDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_BUBBLE_X_DP, v.coerceIn(0, 24)).apply() }
    }

    /** 长按符号气泡垂直余量 dp（气泡上移 = 键高 + 该值，默认 15，5-40）。 */
    fun bubbleYExtraDp(): Int = prefs.getInt(PREF_BUBBLE_Y_EXTRA_DP, 15)

    fun setBubbleYExtraDp(v: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_BUBBLE_Y_EXTRA_DP, v.coerceIn(5, 40)).apply() }
    }

    /** 四向滑动预览显示位置：false = 键面中央（默认），true = 键面上方气泡。 */
    fun swipePreviewAbove(): Boolean = prefs.getBoolean(PREF_SWIPE_PREVIEW_ABOVE, false)

    fun setSwipePreviewAbove(v: Boolean) {
        synchronized(lock) { prefs.edit().putBoolean(PREF_SWIPE_PREVIEW_ABOVE, v).apply() }
    }

    /** 尺寸指纹：变化时 Service 重建键盘视图（onStartInputView 检查）。 */
    fun sizeSignature(): String =
        "${keyHeightDp()}x${barHeightDp()}x${barEnabled()}x${hintLong()}" +
            "x${hintUp()}x${hintDown()}x${hintLeft()}x${hintRight()}x${spaceLabel()}x${sliderSymbolsRaw()}" +
            "x${fontSizeKey()}x${fontSizeBar()}x${keyCornerDp()}x${rowGapDp()}x${colGapDp()}" +
            "x${floatEnabled()}x${floatMode()}x${floatXDp()}x${floatYDp()}x${floatTextSp()}x${floatBgAlpha()}" +
            "x${bubbleXDp()}x${bubbleYExtraDp()}" +
            "x${com.azime.input.core.theme.KeyboardTheme.mode()}" +
            "x${com.azime.input.core.font.FontManager.rev()}"

    // ── 工具栏自定义（○ 菜单键之外的可显示工具） ────────────

    /** 全部可选工具 id → 显示名（顺序即勾选顺序）。 */
    val availableToolbarTools: List<Pair<String, String>> = listOf(
        "clipboard" to "剪贴板",
        "schema" to "方案",
        "numpad" to "数字",
        "emoji" to "emoji",
        "symbols" to "符号",
        "settings" to "设置",
    )

    private val defaultToolbarItems = listOf("clipboard", "schema", "numpad", "emoji", "symbols")

    fun toolbarItems(): List<String> {
        val raw = prefs.getString(PREF_TOOLBAR_ITEMS, null) ?: return defaultToolbarItems
        val valid = availableToolbarTools.map { it.first }.toSet()
        val list = raw.split(',').filter { it in valid }
        return list.ifEmpty { defaultToolbarItems }
    }

    fun setToolbarItems(ids: List<String>) {
        synchronized(lock) {
            prefs.edit().putString(PREF_TOOLBAR_ITEMS, ids.joinToString(",")).apply()
        }
    }

    private fun builtinByName(name: String): KeyboardLayout? = when (name) {
        KeyboardPages.qwerty.name -> KeyboardPages.qwerty
        KeyboardPages.symbols.name -> KeyboardPages.symbols
        KeyboardPages.numpad.name -> KeyboardPages.numpad
        else -> null
    }

    // ── 自定义布局 CRUD（供设置页 / 键盘编辑器调用） ──────────

    /** 已保存的自定义布局名（按保存顺序）。 */
    fun customLayoutNames(): List<String> = synchronized(lock) { customs.keys.toList() }

    fun loadLayout(name: String): KeyboardLayout? = synchronized(lock) {
        customs[name] ?: builtinByName(name)
    }

    /** 保存/覆盖一个自定义布局，并写入 JSON。 */
    fun saveLayout(layout: KeyboardLayout) {
        require(layout.name.isNotBlank()) { "布局名不能为空" }
        require(namePattern.matches(layout.name)) { "布局名仅允许字母、数字、下划线、连字符" }
        synchronized(lock) {
            customs[layout.name] = layout
            layoutFile(layout.name).writeText(gson.toJson(layout))
        }
    }

    /** 删除自定义布局；若它正被使用，主键盘指针回退到内置 qwerty。 */
    fun deleteLayout(name: String) {
        synchronized(lock) {
            customs.remove(name)
            layoutFile(name).delete()
            if (activeMain == name) setActiveMainLocked(DEFAULT_MAIN)
        }
    }

    /** 切换主键盘布局（接受内置名或自定义名）。 */
    fun setActiveMain(name: String) {
        synchronized(lock) { setActiveMainLocked(name) }
    }

    fun activeMainName(): String = activeMain

    /** 导出内置布局为可编辑副本（键盘编辑器「从内置复制」入口用）。 */
    fun copyOfBuiltin(name: String, newName: String): KeyboardLayout? {
        val src = builtinByName(name) ?: return null
        require(namePattern.matches(newName)) { "布局名仅允许字母、数字、下划线、连字符" }
        return src.copy(name = newName)
    }

    private fun setActiveMainLocked(name: String) {
        if (name in ReservedPageNames) return
        activeMain = name
        prefs.edit().putString(PREF_ACTIVE_MAIN, name).apply()
    }

    private fun layoutFile(name: String) = File(dir, "$name.json")
}

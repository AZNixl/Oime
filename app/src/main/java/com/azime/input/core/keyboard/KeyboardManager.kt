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

    fun setSpaceLabel(v: String) {
        synchronized(lock) { prefs.edit().putString(PREF_SPACE_LABEL, v.trim()).apply() }
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

    /** 尺寸指纹：变化时 Service 重建键盘视图（onStartInputView 检查）。 */
    fun sizeSignature(): String =
        "${keyHeightDp()}x${barHeightDp()}x${barEnabled()}x${hintLong()}" +
            "x${hintUp()}x${hintDown()}x${hintLeft()}x${hintRight()}x${spaceLabel()}x${sliderSymbolsRaw()}"

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

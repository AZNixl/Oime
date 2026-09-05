package com.azime.input.core.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.azime.input.core.lua.LuaScriptManager
import com.azime.input.core.storage.StorageManager
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

    @Volatile
    private var preferredPage: String = DEFAULT_PAGE

    // ── 生命周期 ─────────────────────────────────────────────

    fun initialize(context: Context) {
        synchronized(lock) {
            dir = File(context.filesDir, DIR_NAME)
            dir.mkdirs()
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            activeMain = prefs.getString(PREF_ACTIVE_MAIN, DEFAULT_MAIN) ?: DEFAULT_MAIN
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

    /** 增高行（工具栏+候选栏）高度。 */
    fun barHeightDp(): Int = prefs.getInt(PREF_BAR_HEIGHT_DP, DEFAULT_BAR_HEIGHT_DP)

    fun setBarHeightDp(dp: Int) {
        synchronized(lock) { prefs.edit().putInt(PREF_BAR_HEIGHT_DP, dp.coerceIn(38, 72)).apply() }
    }

    /** 尺寸指纹：变化时 Service 重建键盘视图（onStartInputView 检查）。 */
    fun sizeSignature(): String = "${keyHeightDp()}x${barHeightDp()}"

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
        activeMain = name
        prefs.edit().putString(PREF_ACTIVE_MAIN, name).apply()
    }

    // ── trime2 lua 键盘布局导入 ──────────────────────────────

    /**
     * 扫描外置键盘 lua 目录（Documents/AZime/lua/keyboards/*.lua），
     * 解析并导入为自定义布局。返回 (布局名或文件名, 错误信息或 null)。
     */
    fun importLuaLayouts(): List<Pair<String, String?>> = synchronized(lock) {
        val results = mutableListOf<Pair<String, String?>>()
        for (file in StorageManager.getKeyboardLuaFiles()) {
            val layout = LuaScriptManager.parseKeyboardLayout(file)
            if (layout == null) {
                results += file.name to "解析失败"
            } else {
                customs[layout.name] = layout
                layoutFile(layout.name).writeText(gson.toJson(layout))
                results += layout.name to null
            }
        }
        results
    }

    private fun layoutFile(name: String) = File(dir, "$name.json")
}

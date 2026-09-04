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
    private const val DEFAULT_MAIN = "qwerty"

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

    // ── 生命周期 ─────────────────────────────────────────────

    fun initialize(context: Context) {
        synchronized(lock) {
            dir = File(context.filesDir, DIR_NAME)
            dir.mkdirs()
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            activeMain = prefs.getString(PREF_ACTIVE_MAIN, DEFAULT_MAIN) ?: DEFAULT_MAIN
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

    private fun layoutFile(name: String) = File(dir, "$name.json")
}

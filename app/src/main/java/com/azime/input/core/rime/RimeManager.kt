package com.azime.input.core.rime

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeProcessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TreeMap

/** 方案 schema.yaml 的 switches 开关项（name + states 两态文案）。 */
data class SchemaSwitch(val name: String, val states: List<String>)

/**
 * RIME 引擎封装：负责资产部署（assets/rime -> sharedDataDir）与面向键盘的简化 API。
 *
 * 引擎本体复用上游 Xime 的 [RimeEngine]（com.kingzcheung.xime.rime 包名不可改，
 * JNI 符号按该包名生成，librime_jni.so 中的 52 个符号与之逐一对应）。
 */
object RimeManager {

    private const val TAG = "RimeManager"
    private const val ASSETS_ROOT = "rime"
    private const val DEPLOY_MARKER = ".azime_deployed"

    /** X11 keysym：RIME processKey 使用 X11 键值。 */
    const val KEY_BACKSPACE = 0xFF08
    const val KEY_RETURN = 0xFF0D
    const val KEY_SPACE = 0x20
    const val KEY_SHIFT = 0xFFE1
    const val MASK_SHIFT = 1

    /**
     * 同步 assets/rime 到 sharedDataDir 并初始化引擎。
     * 幂等：内容未变化时跳过拷贝；引擎已初始化时跳过初始化。
     * 耗时操作（词典部署由 librime 在后台维护线程执行），必须在 IO 线程调用。
     */
    suspend fun ensureReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        val sharedDir = sharedDirOf(context)
        // 轮18（trime2 架构）：userDataDir = 当前组目录，方案/lua/models 原样加载，零拷贝
        val userDir = userDirForGroup(context, currentGroupId(context))
        sharedDir.mkdirs()
        userDir.mkdirs()

        // 同步内置公共资产（default.yaml/opencc/内置方案）到 shared 目录
        val assetsChanged = try {
            syncAssets(context, sharedDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync rime assets", e)
            return@withContext false
        }

        if (!RimeEngine.isInitialized()) {
            RimeEngine.getInstance().initialize(
                userDataDir = userDir.absolutePath,
                sharedDataDir = sharedDir.absolutePath,
            )
        }
        // 关键：librime 不会在 initialize 后自动部署。
        // 组目录的方案在此处被编译到 <组目录>/build/（与 trime2 一致）。
        if (RimeEngine.getInstance().getAvailableSchemas().isEmpty() || assetsChanged) {
            val kicked = RimeEngine.getInstance().startMaintenance(true)
            Log.i(TAG, "kick full maintenance: kicked=$kicked")
            // 轮17.1 修复：触发维护后立即等待完成（避免 build/ 目录被清空后没有重新生成）
            if (kicked) {
                ensureSessionAfterMaintenance()
            }
        }
        RimeEngine.isInitialized()
    }

    /** 引擎是否可用（已初始化且有可用方案）。 */
    /**
     * 轮19.9：输入法服务销毁时释放引擎（对齐 trime2 `Rime.finalize()` /
     * Xime `onDestroy` 里的 `rimeEngine.destroy()`）。librime 的 JNI 没有 finalize，
     * 不主动 destroy 时词典/模型会一直挂在 native 内存里。
     */
    fun releaseAll() {
        runCatching { RimeEngine.getInstance().destroy() }
        sessionReady = false
        clearDisplayNameCache()
    }

    fun isReady(): Boolean =
        RimeEngine.isInitialized() && RimeEngine.getInstance().getAvailableSchemas().isNotEmpty()

    /** 部署完成、可建会话。首次运行会触发词典编译，可能耗时数十秒。 */
    fun ensureSession(): Boolean = RimeEngine.getInstance().ensureSession()

    @Volatile private var sessionReady = false

    /** 会话是否已建立；未建立时（如首次部署耗时超过等待窗口）可按键重试。 */
    fun isSessionReady(): Boolean = sessionReady

    /** 建立输入会话（幂等，维护完成后快速返回）。 */
    fun ensureSessionNow(): Boolean {
        sessionReady = RimeEngine.getInstance().ensureSession()
        return sessionReady
    }

    /**
     * 强制等待维护完成并重建会话（轮17.1 修复）。
     * 问题：RimeEngine.ensureSession() 第221行快速短路——旧会话活着就直接返回，
     * 不等待维护完成 → build/ 目录被清空后没有重新生成 → 打不出字。
     * 修复：先等待维护真正结束，再调用 ensureSession。
     */
    suspend fun ensureSessionAfterMaintenance(): Boolean = withContext(Dispatchers.IO) {
        // 等待维护完成（最多 180 秒）
        var waited = 0L
        while (RimeEngine.getInstance().isMaintaining() && waited < 180_000L) {
            delay(100)
            waited += 100
        }
        if (RimeEngine.getInstance().isMaintaining()) {
            Log.e(TAG, "ensureSessionAfterMaintenance: maintenance timeout after ${waited}ms")
            return@withContext false
        }
        Log.i(TAG, "ensureSessionAfterMaintenance: maintenance completed in ${waited}ms")
        // 维护完成后重建会话
        sessionReady = RimeEngine.getInstance().ensureSession()
        sessionReady
    }

    /** 处理一次 X11 键值按键，返回完整状态（候选/上屏文本/preedit）。 */
    fun processKey(keycode: Int, mask: Int = 0): RimeProcessResult =
        RimeEngine.getInstance().processKeyAndGetResult(keycode, mask)

    /** 选择候选词。 */
    fun selectCandidate(index: Int): Boolean =
        RimeEngine.getInstance().selectCandidate(index)

    /** 取当前候选（未按键时刷新 UI 用）。 */
    fun getProcessResult(): RimeProcessResult =
        RimeEngine.getInstance().getProcessResult(false)

    /** 切换中英文模式，返回切换后的状态。 */
    fun toggleAsciiMode(): Boolean {
        val engine = RimeEngine.getInstance()
        engine.toggleAsciiMode()
        return engine.isAsciiMode()
    }

    fun isAsciiMode(): Boolean = RimeEngine.getInstance().isAsciiMode()

    /** 模拟物理 shift 键交给 RIME（ascii_composer 处理中英切换/大小写）。 */
    fun processShift(): RimeProcessResult = processKey(KEY_SHIFT)

    fun clearComposition() = RimeEngine.getInstance().clearComposition()

    fun currentSchema(): String = RimeEngine.getInstance().getCurrentSchema()

    fun availableSchemas(): List<String> = RimeEngine.getInstance().getAvailableSchemas().toList()

    fun switchSchema(schemaId: String): Boolean = RimeEngine.getInstance().switchSchema(schemaId)

    fun isMaintaining(): Boolean = RimeEngine.getInstance().isMaintaining()

    // ── 方案 switches 开关（○ 菜单「方案开关」） ──────────────

    fun getOption(name: String): Boolean = RimeEngine.getInstance().getOption(name)

    fun setOption(name: String, value: Boolean) = RimeEngine.getInstance().setOption(name, value)

    /**
     * 查找方案的 .schema.yaml 源文件：
     * 轮18.2：先查当前组目录（导入组方案在 Documents/Oime/schema/<组>/），
     * 再回落 shared（内置公共方案）。目录内允许子目录（walkTopDown）。
     */
    private fun schemaYamlFile(schemaId: String): File? {
        if (schemaId.isBlank()) return null
        val name = "$schemaId.schema.yaml"
        val app = com.azime.input.AZimeApplication.instance
        // 1. 当前组目录（含子目录，一次浅层遍历）
        runCatching {
            val groupDir = userDirForGroup(app, currentGroupId(app))
            groupDir.walkTopDown()
                .maxDepth(3)
                .filter { it.isFile && it.name == name }
                .firstOrNull()?.let { return it }
        }
        // 2. shared 公共目录（顶层）
        val shared = File(app.filesDir, "rime/shared/$name")
        if (shared.isFile) return shared
        return null
    }

    /** 解析方案 .schema.yaml 的 switches 段（name + states，跳过 options 型无名条目）。 */
    fun schemaSwitches(schemaId: String): List<SchemaSwitch> {
        if (schemaId.isBlank()) return emptyList()
        val f = schemaYamlFile(schemaId) ?: return emptyList()
        val result = mutableListOf<SchemaSwitch>()
        var curName: String? = null
        var curStates = mutableListOf<String>()
        var inStatesBlock = false
        var inSwitches = false
        fun flush() {
            val n = curName
            if (n != null) result.add(SchemaSwitch(n, curStates.toList()))
            curName = null
            curStates = mutableListOf()
            inStatesBlock = false
        }
        runCatching {
            f.useLines { raw ->
                for (rawLine in raw) {
                    // 剥离行内注释（" #" 之后）：如 "switches: # 0 默认关" / "states: [ 中文, 西文 ]  #中英文"
                    val noComment = run {
                        val i = rawLine.indexOf(" #")
                        if (i >= 0) rawLine.substring(0, i) else rawLine
                    }
                    val line = noComment.trimEnd()
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) continue
                    if (!inSwitches) {
                        // 允许 "switches:" 后带行内注释（虎单整即此格式）
                        if (t.startsWith("switches:")) inSwitches = true
                        continue
                    }
                    // switches 段结束：遇到顶格非注释键
                    if (!line.startsWith(" ") && !t.startsWith("-")) { flush(); inSwitches = false; continue }
                    val entry = t.removePrefix("- ")
                    when {
                        entry.startsWith("name:") -> {
                            flush()
                            curName = entry.removePrefix("name:").trim().trim('\'', '"')
                        }
                        entry.startsWith("states:") -> {
                            val inline = entry.removePrefix("states:").trim()
                            if (inline.startsWith("[")) {
                                curStates = inline.trim('[', ']')
                                    .split(',')
                                    .map { it.trim().trim('\'', '"') }
                                    .filter { it.isNotEmpty() }
                                    .toMutableList()
                            } else {
                                inStatesBlock = true
                            }
                        }
                        // options 型（无 name，不可切换）：丢弃
                        entry.startsWith("options:") || entry.startsWith("abort:") -> flush()
                        inStatesBlock && t.startsWith("- ") && curName != null ->
                            curStates.add(entry.trim().trim('\'', '"'))
                    }
                }
            }
        }
        flush()
        return result
    }

    /**
     * 方案显示名：优先读 schema.yaml 的 name 字段（轮18.2：组目录优先，回落 shared），退回 id。
     * 轮19.7：**加缓存**——空格键标签在每次重组时都会调用它，原来每次都读磁盘文件
     * （useLines），键盘可见期间等于每帧一次 IO，是明确的耗电点。切组/部署后调 clearDisplayNameCache()。
     */
    private val displayNameCache = HashMap<String, String>()

    fun clearDisplayNameCache() = synchronized(displayNameCache) { displayNameCache.clear() }

    fun schemaDisplayName(schemaId: String): String {
        if (schemaId.isBlank()) return "○输入法"
        synchronized(displayNameCache) { displayNameCache[schemaId]?.let { return it } }
        var name = schemaId
        val f = schemaYamlFile(schemaId)
        if (f != null) runCatching {
            f.useLines { lines ->
                for (line in lines) {
                    val m = Regex("""^\s*name:\s*(.+)$""").find(line)
                    if (m != null) {
                        name = m.groupValues[1].trim().trim('\'', '"')
                        return@useLines
                    }
                }
            }
        }
        synchronized(displayNameCache) { displayNameCache[schemaId] = name }
        return name
    }

    // ── 方案组管理（参考 trime2：方案组 → 方案 两级，单次只加载一个组） ──

    /** 内置方案组 id（assets 内置的拼音方案）。 */
    // ── 方案组管理（轮18：trime2 架构 —— 组目录即 librime user 数据目录，零拷贝） ──
    //
    // 参考 trime2 fork（Documents/rime/schemas/<组>/）的真实行为：
    // - 每个组目录 = 一个完整方案包，librime 直接以它为 user_data_dir
    // - 方案/dict/lua/models/opencc 从组目录原样加载，build 产物生成在组目录 build/ 下
    // - 组自带 default.custom.yaml 由 librime 部署时自动 patch（标准行为，本 App 不碰 yaml）
    // - sharedDataDir 固定指向内置公共目录（default.yaml/opencc/内置方案），组目录缺资源时回落
    // - 切组 = 记录组 id → 重启进程（librime JNI 无 finalize，user 目录无法在线更换）

    const val BUILTIN_GROUP_ID = "__builtin__"

    /** 内置组（assets 预置方案）的 user 数据目录。 */
    private fun builtinUserDir(context: Context): File = File(context.filesDir, "rime/user")

    /** 公共 shared 目录（default.yaml / opencc / 内置方案，assets 同步），组目录缺资源时回落。 */
    private fun sharedDirOf(context: Context): File = File(context.filesDir, "rime/shared")

    /** 指定组的 user 数据目录：内置组 → files/rime/user；导入组 → Documents/Oime/schema/<组>/。 */
    fun userDirForGroup(context: Context, groupId: String): File =
        if (groupId == BUILTIN_GROUP_ID) builtinUserDir(context)
        else File(com.azime.input.core.storage.StorageManager.schemaDir, groupId)

    private const val GROUP_PREFS = "schema_group_prefs"
    private const val KEY_CURRENT_GROUP = "current_group"
    private const val KEY_GROUP_SCHEMA = "group_schema_"

    data class SchemaGroup(
        val id: String,
        val name: String,
        val schemaIds: List<String>,
    )

    private fun groupPrefs(context: Context) =
        context.getSharedPreferences(GROUP_PREFS, Context.MODE_PRIVATE)

    fun currentGroupId(context: Context): String =
        groupPrefs(context).getString(KEY_CURRENT_GROUP, BUILTIN_GROUP_ID) ?: BUILTIN_GROUP_ID

    fun setCurrentGroup(context: Context, groupId: String) {
        groupPrefs(context).edit().putString(KEY_CURRENT_GROUP, groupId).apply()
    }

    /** 记录组内最后使用的方案（切组/重启后回落用）。 */
    fun recordGroupSchema(context: Context, schemaId: String) {
        val gid = currentGroupId(context)
        groupPrefs(context).edit().putString(KEY_GROUP_SCHEMA + gid, schemaId).apply()
    }

    /**
     * 枚举方案组：内置 + Documents/Oime/schema/ 下的目录（trime2 模式：目录原样即环境）。
     * schema_id = <x>.schema.yaml 的文件名（librime 以文件名解析方案 id）。
     */
    fun schemaGroups(context: Context): List<SchemaGroup> {
        val groups = mutableListOf<SchemaGroup>()
        val builtinSchemas = runCatching { RimeEngine.getInstance().getAvailableSchemas().toList() }
            .getOrDefault(emptyList())
        groups.add(SchemaGroup(BUILTIN_GROUP_ID, "内置", builtinSchemas))
        val root = com.azime.input.core.storage.StorageManager.schemaDir
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            val ids = dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".schema.yaml") }
                .map { it.name.removeSuffix(".schema.yaml") }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            groups.add(SchemaGroup(dir.name, dir.name, ids))
        }
        return groups
    }

    /**
     * 切换方案组（trime2 模式）：在线切换，不杀进程。
     * 轮18.2：旧实现 apply() 异步写 pref + 600ms 后 Runtime.exit(0)——落盘竞态导致
     * 重启后读回旧组（切组失败），且硬杀进程被用户感知为「闪退」。
     * 新实现：commit() 同步落盘 → destroy 引擎 → 按新组目录重新 initialize →
     * 触发部署 → 建会话。全程在 IO 线程，完成后回调主线程刷新 UI。
     */
    fun switchSchemaGroupOnline(
        context: Context,
        groupId: String,
        onSwitched: (Boolean) -> Unit = {},
    ) {
        if (groupId == currentGroupId(context)) {
            onSwitched(true)
            return
        }
        // commit() 同步写盘，杜绝落盘竞态
        groupPrefs(context).edit().putString(KEY_CURRENT_GROUP, groupId).commit()
        clearDisplayNameCache() // 轮19.7：换组后方案名完全不同，缓存必须失效
        Log.i(TAG, "switchSchemaGroupOnline: [$groupId] switching in place")
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching {
                // 销毁旧引擎（nativeDestroy 释放 librime 全部会话与词典资源）
                RimeEngine.getInstance().destroy()
                sessionReady = false
                // 按新组目录重新初始化 + 部署 + 建会话
                val ready = ensureReady(context)
                ready && ensureSessionAfterMaintenance()
            }.getOrDefault(false)
            Log.i(TAG, "switchSchemaGroupOnline: [$groupId] result=$ok")
            android.os.Handler(android.os.Looper.getMainLooper()).post { onSwitched(ok) }
        }
    }

    /** 组内首选方案：组内最后使用的；无记录时返回 null（librime 回落 schema_list[0]）。 */
    fun groupPreferredSchemaId(context: Context, groupId: String): String? =
        groupPrefs(context).getString(KEY_GROUP_SCHEMA + groupId, null)

    /**
     * 读取组目录 default.custom.yaml 中 patch/schema_list 已启用的方案 id 列表。
     * 解析「- schema: xxx」与「- {schema: xxx}」两种写法；无文件/无 schema_list 时返回空。
     */
    fun groupEnabledSchemas(context: Context, groupId: String): List<String> {
        val f = File(userDirForGroup(context, groupId), "default.custom.yaml")
        if (!f.isFile) return emptyList()
        val result = mutableListOf<String>()
        var inList = false
        runCatching {
            f.useLines { lines ->
                for (raw in lines) {
                    val t = raw.trim()
                    if (t.startsWith("schema_list:")) { inList = true; continue }
                    if (inList) {
                        if (t.startsWith("-")) {
                            val entry = t.removePrefix("-").trim()
                            val id = when {
                                entry.startsWith("schema:") ->
                                    entry.removePrefix("schema:").trim().trim('\'', '"')
                                entry.startsWith("{schema:") ->
                                    entry.removePrefix("{schema:").trim('}', ' ', '\'', '"')
                                else -> null
                            }
                            if (!id.isNullOrBlank()) result.add(id)
                        } else if (t.isNotEmpty() && !t.startsWith("#")) {
                            inList = false // 离开 schema_list 块
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * 勾选/取消勾选方案（轮18.2）：重写组目录 default.custom.yaml 的
     * patch/schema_list 段（保留文件其余 patch 内容），然后在线重部署。
     * enabledIds 顺序即部署顺序；首项为部署后回落的首选方案。
     */
    fun setGroupEnabledSchemas(
        context: Context,
        groupId: String,
        enabledIds: List<String>,
        onDone: (Boolean) -> Unit = {},
    ) {
        val dir = userDirForGroup(context, groupId)
        val f = File(dir, "default.custom.yaml")
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching {
                if (f.isFile) {
                    val sb = StringBuilder()
                    var inList = false
                    var listWritten = false
                    f.useLines { lines ->
                        for (raw in lines) {
                            val t = raw.trim()
                            if (t.startsWith("schema_list:")) {
                                // 用启用集重写 schema_list 块（块式写法，兼容 librime）
                                if (!listWritten && enabledIds.isNotEmpty()) {
                                    sb.append("  schema_list:\n")
                                    enabledIds.forEach { sb.append("    - schema: $it\n") }
                                    listWritten = true
                                }
                                inList = true
                                continue
                            }
                            if (inList) {
                                if (t.startsWith("-")) continue // 旧列表项丢弃
                                if (t.isEmpty() || t.startsWith("#")) {
                                    // 保留 schema_list 尾部的注释/空行（近似处理）
                                    continue
                                }
                                inList = false
                                // 落到下方通用分支写出该行
                            }
                            if (inList) continue
                            sb.append(raw).append('\n')
                        }
                    }
                    if (!listWritten && enabledIds.isNotEmpty()) {
                        // 原 patch 无 schema_list：附加到 patch 段末尾（简化：追加到文件尾）
                        sb.append("  schema_list:\n")
                        enabledIds.forEach { sb.append("    - schema: $it\n") }
                    }
                    f.writeText(sb.toString())
                } else {
                    // 无 custom.yaml：新建最小结构
                    f.writeText(buildString {
                        append("patch:\n")
                        append("  schema_list:\n")
                        enabledIds.forEach { append("    - schema: $it\n") }
                    })
                }
                Log.i(TAG, "setGroupEnabledSchemas: [$groupId] -> $enabledIds")
                true
            }.getOrDefault(false)
            if (ok) {
                // 在线重部署：destroy → 重新 init → 维护 → 会话
                RimeEngine.getInstance().destroy()
                sessionReady = false
                val ready = runCatching { ensureReady(context) }.getOrDefault(false)
                val sessionOk = ready && ensureSessionAfterMaintenance()
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(sessionOk) }
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(false) }
            }
        }
    }

    /** 立即重新部署当前组（导入/部署键由设置页调用）：触发全量维护并重建会话。 */
    suspend fun deployImportedSchemas(context: Context) = withContext(Dispatchers.IO) {
        clearDisplayNameCache() // 轮19.7：部署后 schema.yaml 可能被重写，方案名缓存失效
        val kicked = runCatching { RimeEngine.getInstance().startMaintenance(true) }
            .onFailure { Log.e(TAG, "deployImportedSchemas failed", it) }
            .getOrDefault(false)
        if (kicked) ensureSessionAfterMaintenance()
        kicked
    }

    // ── 资产同步 ──────────────────────────────────────────────

    /**
     * 将 assets/rime 拷贝到 sharedDir。
     * 判定策略：marker 文件记录上次同步的「文件名:长度」清单 hash，
     * 清单一致且目标文件都存在则跳过；否则全量覆盖并重写 marker。
     * 返回资产清单是否发生变化（变化时需触发重新部署并清理旧文件）。
     */
    private fun syncAssets(context: Context, sharedDir: File): Boolean {
        val wanted = TreeMap<String, Long>() // 相对路径 -> 长度
        collectAssets(context, ASSETS_ROOT, "", wanted)
        if (wanted.isEmpty()) return false

        val manifest = wanted.entries.joinToString("\n") { "${it.key}:${it.value}" }
        val marker = File(sharedDir, DEPLOY_MARKER)
        val oldManifest = if (marker.exists()) marker.readText() else ""
        val unchanged = oldManifest == manifest &&
            wanted.keys.all { File(sharedDir, it).exists() }
        if (unchanged) return false

        Log.i(TAG, "Syncing ${wanted.size} rime assets to ${sharedDir.absolutePath}")
        for ((rel, _) in wanted) {
            val target = File(sharedDir, rel)
            target.parentFile?.mkdirs()
            context.assets.open("$ASSETS_ROOT/$rel").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        marker.writeText(manifest)

        // 清理内置裁剪后遗留的旧资产（依据上一轮清单；导入方案不受影响）
        val oldFiles = oldManifest.split('\n')
            .map { it.substringBeforeLast(':') }
            .filter { it.isNotBlank() }
        oldFiles.filter { it !in wanted.keys }.forEach {
            val f = File(sharedDir, it)
            if (f.exists()) {
                Log.i(TAG, "Removing stale asset: $it")
                f.delete()
            }
        }
        return true
    }

    /** 递归枚举 assets/rime 下所有文件（assets 的 list() 不递归）。 */
    private fun collectAssets(context: Context, base: String, rel: String, out: TreeMap<String, Long>) {
        val dir = if (rel.isEmpty()) base else "$base/$rel"
        for (name in context.assets.list(dir) ?: return) {
            val childRel = if (rel.isEmpty()) name else "$rel/$name"
            val childPath = "$base/$childRel"
            val children = context.assets.list(childPath)
            if (children.isNullOrEmpty()) {
                // available() 不保证等于文件全大小，但同一资产两次读取结果一致，
                // 足够用作「内容是否变化」的快速判定
                val size = context.assets.open(childPath).use { it.available().toLong() }
                out[childRel] = size
            } else {
                collectAssets(context, base, childRel, out)
            }
        }
    }
}

/** 键盘 UI 使用的候选条目（弱化上游类型，避免 UI 直接依赖 JNI 包）。 */
data class Candidate(val text: String, val comment: String)

fun RimeCandidate.toCandidate(): Candidate = Candidate(text, comment)

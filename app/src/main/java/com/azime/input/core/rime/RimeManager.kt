package com.azime.input.core.rime

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeProcessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        val sharedDir = File(context.filesDir, "rime/shared")
        val userDir = File(context.filesDir, "rime/user")
        sharedDir.mkdirs()
        userDir.mkdirs()
        var assetsChanged = false

        try {
            val changed = syncAssets(context, sharedDir)
            // 轮13：启动时只加载「当前方案组」（参考 trime2 方案组-方案两级模型）
            val groupChanged = syncGroup(context, sharedDir, currentGroupId(context))
            assetsChanged = changed || groupChanged
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
        // 关键：librime 不会在 initialize 后自动部署（Xime 原版由服务层显式调度维护）。
        // 不触发的话 availableSchemas 永远为空，会话只挂内置 .default 兜底方案。
        // startMaintenance 是异步的：发起后由 ensureSession 的等待循环收尾。
        if (RimeEngine.getInstance().getAvailableSchemas().isEmpty() || assetsChanged) {
            val kicked = RimeEngine.getInstance().startMaintenance(true)
            Log.i(TAG, "kick full maintenance: kicked=$kicked")
        }
        RimeEngine.isInitialized()
    }

    /** 引擎是否可用（已初始化且有可用方案）。 */
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

    /** 解析方案 .schema.yaml 的 switches 段（name + states，跳过 options 型无名条目）。 */
    fun schemaSwitches(schemaId: String): List<SchemaSwitch> {
        if (schemaId.isBlank()) return emptyList()
        val f = File(
            com.azime.input.AZimeApplication.instance.filesDir,
            "rime/shared/$schemaId.schema.yaml",
        )
        if (!f.exists()) return emptyList()
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

    /** 方案显示名：优先读 shared 目录下 schema.yaml 的 name 字段，退回 id。 */
    fun schemaDisplayName(schemaId: String): String {
        if (schemaId.isBlank()) return "○输入法"
        val f = File(
            com.azime.input.AZimeApplication.instance.filesDir,
            "rime/shared/$schemaId.schema.yaml",
        )
        if (f.exists()) runCatching {
            f.useLines { lines ->
                for (line in lines) {
                    val m = Regex("""^\s*name:\s*(.+)$""").find(line)
                    if (m != null) return m.groupValues[1].trim()
                }
            }
        }
        return schemaId
    }

    // ── 方案组管理（参考 trime2：方案组 → 方案 两级，单次只加载一个组） ──

    /** 内置方案组 id（assets 内置的拼音方案）。 */
    const val BUILTIN_GROUP_ID = "__builtin__"
    private const val BUILTIN_SCHEMA_ID = "pinyin_simp"
    private const val GROUP_PREFS = "schema_group_prefs"
    private const val KEY_CURRENT_GROUP = "current_group"
    private const val KEY_GROUP_SCHEMA = "group_schema_"
    private const val KEY_GROUP_ENABLED = "group_enabled_"

    /** 方案组：一次只能加载一个组；组内可包含多个方案。 */
    data class SchemaGroup(
        val id: String,
        val name: String,
        val builtin: Boolean,
        val schemaIds: List<String>,
    )

    private fun groupPrefs(context: Context) =
        context.getSharedPreferences(GROUP_PREFS, Context.MODE_PRIVATE)

    /** 当前已加载的方案组 id（默认内置组）。 */
    fun currentGroupId(context: Context): String =
        groupPrefs(context).getString(KEY_CURRENT_GROUP, BUILTIN_GROUP_ID) ?: BUILTIN_GROUP_ID

    fun setCurrentGroup(context: Context, groupId: String) {
        groupPrefs(context).edit().putString(KEY_CURRENT_GROUP, groupId).apply()
    }

    /** 记录组内上次使用的方案：重写 schema_list 时置首，librime 部署后回落 schema_list[0] 即回到它。 */
    fun recordGroupSchema(context: Context, schemaId: String) {
        val gid = currentGroupId(context)
        groupPrefs(context).edit().putString(KEY_GROUP_SCHEMA + gid, schemaId).apply()
    }

    private fun groupPreferredSchema(context: Context, groupId: String): String? =
        groupPrefs(context).getString(KEY_GROUP_SCHEMA + groupId, null)

    // ── 方案选择层（轮15，参考 trime2/同文：组 → 启用集 → 运行时切换） ──
    // 组内可能含几十个 schema（聚合包全量识别），用户实际只用其中几个；
    // 启用集决定 schema_list（部署范围）与「输入方案」列表。未设置 = 全部启用（兼容迁移）。

    /** 当前组启用的方案 id 集合；null = 从未选择过（视为全部启用）。 */
    fun groupEnabledIds(context: Context, groupId: String): Set<String>? {
        val raw = groupPrefs(context).getString(KEY_GROUP_ENABLED + groupId, null) ?: return null
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** 保存启用集（空列表 = 清除选择，恢复全部启用）。调用方负责重新部署。 */
    fun setGroupEnabled(context: Context, groupId: String, ids: List<String>) {
        val key = KEY_GROUP_ENABLED + groupId
        val editor = groupPrefs(context).edit()
        if (ids.isEmpty()) editor.remove(key) else editor.putString(key, ids.joinToString(","))
        editor.apply()
    }

    /** 从 *.schema.yaml 逐行解析 schema_id（读到即返回，避免整读大词典文件）。 */
    private fun schemaIdOf(f: File): String? {
        if (!f.name.endsWith(".schema.yaml")) return null
        return runCatching {
            f.useLines { lines ->
                for (line in lines) {
                    val m = Regex("""^\s*schema_id:\s*(\S+)""").find(line)
                    if (m != null) return@useLines m.groupValues[1]
                }
                null
            }
        }.getOrNull()
    }

    /** 枚举全部方案组：内置组 + 方案目录下每个子目录（目录名即组名，组内扫 *.schema.yaml）。 */
    fun schemaGroups(context: Context): List<SchemaGroup> {
        val groups = mutableListOf(
            SchemaGroup(BUILTIN_GROUP_ID, "内置方案", true, listOf(BUILTIN_SCHEMA_ID)),
        )
        val root = try {
            com.azime.input.core.storage.StorageManager.schemaDir
        } catch (e: Exception) {
            null
        }
        if (root != null && root.isDirectory) {
            root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?.forEach { dir ->
                    val ids = dir.walkTopDown()
                        .filter { it.isFile && !it.name.startsWith(".") }
                        .mapNotNull { schemaIdOf(it) }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .toList()
                    if (ids.isNotEmpty()) groups.add(SchemaGroup(dir.name, dir.name, false, ids))
                }
        }
        return groups
    }

    /**
     * 把指定方案组的文件同步进 sharedDir 并重写 default.custom.yaml（启动/切组共用）。
     * 1) 删除上一组遗留文件（.imported 清单），与内置资产同名的从 assets 恢复；
     * 2) 拷入新组全部文件（组文件覆盖同名内置 —— 组是覆盖层）；
     * 3) schema_list 只写组内方案，组内上次使用的方案置首。
     * 修复「方案自己变动」：此前 default.custom.yaml 全量平铺所有导入方案，
     * 每次部署后 librime 回落 schema_list[0]，把用户切过的方案跳回拼音。
     * 返回是否发生了变化（需要重新部署）。
     */
    private fun syncGroup(context: Context, sharedDir: File, groupId: String): Boolean {
        val groupRoot: File? = if (groupId == BUILTIN_GROUP_ID) null
        else File(com.azime.input.core.storage.StorageManager.schemaDir, groupId)
            .takeIf { it.isDirectory }

        // 轮14 修复：librime 只在 shared 根目录解析 <id>.schema.yaml / *.dict.yaml 等配置与词典，
        // 聚合组（组内嵌套方案包子目录）此前保留结构拷贝导致嵌套包的方案全部无法部署（「方案未识别」）。
        // 新规则：yaml/txt 一律拍平到 shared 根（同名后者覆盖）；lua/opencc/models/build 等资源保留子目录结构；
        // 组内自带 default.custom.yaml 跳过（schema_list 由本函数生成，见 defaultText）。
        val newFiles: List<Pair<String, File>> = groupRoot
            ?.walkTopDown()
            ?.filter { it.isFile && !it.name.startsWith(".") && it.name != "default.custom.yaml" }
            ?.map { src ->
                val rel = src.relativeTo(groupRoot).invariantSeparatorsPath
                val flat = src.extension == "yaml" || src.extension == "txt"
                (if (flat) src.name else rel) to src
            }
            ?.toList()
            ?: emptyList()

        val manifest = TreeMap<String, Long>()
        newFiles.forEach { (target, f) -> manifest[target] = f.length() }
        val manifestText = manifest.entries.joinToString("\n") { "${it.key}:${it.value}" }
        val marker = File(sharedDir, ".imported")
        val oldManifest = if (marker.exists()) marker.readText() else ""

        val ids = newFiles.mapNotNull { (target, f) ->
            if (target.endsWith(".schema.yaml")) schemaIdOf(f) else null
        }.filter { it.isNotBlank() }.distinct()
        // 轮15：只部署「启用集」内的方案（未选择过 = 全部启用）；schema_list 同源，
        // O 菜单「输入方案」列表（availableSchemas）即启用集。
        // 轮16修复：确保当前选中方案一定在 schema_list 内，即使不在启用集（避免无方案可用）
        val enabledSet = groupEnabledIds(context, groupId)
        val preferred = groupPreferredSchema(context, groupId)
        val enabledIds = when {
            enabledSet == null -> ids  // 从未配置启用集 = 全部启用
            enabledSet.isEmpty() -> ids  // 空启用集异常 = 回退全部启用
            preferred != null && preferred in ids && preferred !in enabledSet -> 
                // 当前方案不在启用集但在组内 → 强制加入（避免切组后无方案）
                (enabledSet + preferred).filter { it in ids }
            else -> ids.filter { it in enabledSet }
        }
        val preferredInList = preferred?.takeIf { it in enabledIds }
            ?: enabledIds.firstOrNull() ?: BUILTIN_SCHEMA_ID
        val ordered = (listOf(preferredInList) + enabledIds.filter { it != preferredInList })

        val defaultCustom = File(sharedDir, "default.custom.yaml")
        // 变更检测：manifest 变化 或 文件缺失 或 default.custom.yaml 不存在时重新同步
        val unchanged = marker.exists() && oldManifest == manifestText &&
            manifest.keys.all { File(sharedDir, it).exists() } &&
            defaultCustom.exists()
        if (unchanged) return false

        // 1) 删除上一组遗留文件；与内置资产同名的从 assets 恢复
        oldManifest.split('\n')
            .map { it.substringBeforeLast(':') }
            .filter { it.isNotBlank() && it !in manifest.keys }
            .forEach { rel ->
                val f = File(sharedDir, rel)
                if (f.exists()) f.delete()
                runCatching {
                    context.assets.open("$ASSETS_ROOT/$rel").use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                } // 不在内置资产中会抛 FileNotFoundException，忽略即可
            }
        // 2) 拷入新组文件（覆盖同名内置：组定制优先）
        newFiles.forEach { (rel, src) ->
            val dst = File(sharedDir, rel)
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
        marker.writeText(manifestText)
        // 3) 安全更新 default.custom.yaml：只修改 patch.schema_list，保留用户的其他 patch
        //    （trime 正确做法：通过 JNI selectRimeSchemas() 设置，不碰 yaml；
        //     xime RimeEngine 缺该 API，暂时通过 YamlPatcher 实现，避免覆盖用户配置）
        val schemaListChanged = YamlPatcher.patchSchemaList(defaultCustom, ordered)
        Log.i(TAG, "Synced schema group [$groupId]: ${newFiles.size} files, schema_list=$ordered, yaml_patched=$schemaListChanged")
        return true
    }

    /** 切换方案组：记录当前组 → 同步组文件 → 全量维护（回落组内 preferred）→ 重建会话。 */
    suspend fun switchSchemaGroup(context: Context, groupId: String): Boolean =
        withContext(Dispatchers.IO) {
            setCurrentGroup(context, groupId)
            val sharedDir = File(context.filesDir, "rime/shared")
            sharedDir.mkdirs()
            val changed = runCatching { syncGroup(context, sharedDir, groupId) }
                .onFailure { Log.e(TAG, "switchSchemaGroup failed", it) }
                .getOrDefault(false)
            // 切组必须重新部署：部署完成后 librime 回落 schema_list[0]（组内上次使用的方案）
            val maintenanceStarted = runCatching { RimeEngine.getInstance().startMaintenance(true) }.getOrDefault(false)
            if (maintenanceStarted) {
                // 反馈轮16 修复「O 菜单切组失败」：startMaintenance 是异步的，而旧会话在维护
                // 结束前仍存活 → ensureSession() 被「有会话且有方案」短路直接返回，会话仍挂在
                // 上一组的方案上。改为：先等维护真正结束，再把会话显式切到新组首选方案。
                var waited = 0L
                while (RimeEngine.getInstance().isMaintaining() && waited < 180_000L) {
                    delay(100)
                    waited += 100
                }
                val timedOut = RimeEngine.getInstance().isMaintaining()
                if (timedOut) {
                    Log.e(TAG, "switchSchemaGroup: maintenance timeout after ${waited}ms")
                } else {
                    Log.i(TAG, "switchSchemaGroup: maintenance completed in ${waited}ms")
                }
                ensureSessionNow()
                val preferred = groupPreferredSchemaId(context, groupId)
                runCatching { switchSchema(preferred) }
                    .onFailure { Log.e(TAG, "switchSchema($preferred) after group switch failed", it) }
                return@withContext !timedOut
            } else {
                Log.e(TAG, "switchSchemaGroup: startMaintenance returned false")
                return@withContext false
            }
        }

    /**
     * 组内首选方案 id：上次使用的（且在启用集内）→ 启用集第一个 → 内置兜底。
     * 与 syncGroup 生成 schema_list 时的置首规则一致（维护后新会话回落的首选）。
     * 轮16修复：当前方案不在启用集时也返回（避免无方案可切）。
     */
    fun groupPreferredSchemaId(context: Context, groupId: String): String {
        if (groupId == BUILTIN_GROUP_ID) return BUILTIN_SCHEMA_ID
        val ids = schemaGroups(context).firstOrNull { it.id == groupId }?.schemaIds ?: emptyList()
        val enabledSet = groupEnabledIds(context, groupId)
        val preferred = groupPreferredSchema(context, groupId)
        val enabledIds = when {
            enabledSet == null -> ids
            enabledSet.isEmpty() -> ids
            preferred != null && preferred in ids && preferred !in enabledSet ->
                (enabledSet + preferred).filter { it in ids }
            else -> ids.filter { it in enabledSet }
        }
        return preferred?.takeIf { it in enabledIds }
            ?: enabledIds.firstOrNull()
            ?: BUILTIN_SCHEMA_ID
    }

    /** 立即重新部署当前方案组（导入/重命名/部署键由设置页调用）。 */
    suspend fun deployImportedSchemas(context: Context) = withContext(Dispatchers.IO) {
        val sharedDir = File(context.filesDir, "rime/shared")
        sharedDir.mkdirs()
        val changed = runCatching { syncGroup(context, sharedDir, currentGroupId(context)) }
            .onFailure { Log.e(TAG, "deployImportedSchemas failed", it) }
            .getOrDefault(false)
        // 部署键必须真正触发引擎重新部署（导入无变化时也要全量维护，
        // 让 Lua 环境 / opencc / 模型等在本次部署中重新加载）并重建会话
        if (runCatching { RimeEngine.getInstance().startMaintenance(true) }.getOrDefault(false)) {
            ensureSessionNow()
        }
        changed
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

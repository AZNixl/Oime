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

        // 轮17 重构（参考 trime）：只同步 assets，不再需要方案组逻辑
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

    // ── trime 风格方案管理 API（轮17 重构） ──────────────
    //
    // 参考 trime/trime2 的实现：
    // - librime 只管理扁平方案列表，不需要方案组概念（xime 自己发明的）
    // - 不生成 default.custom.yaml，用 JNI selectRimeSchemas() 设置启用方案
    // - 部署流程：exitRime() + startRime(fullCheck=true)
    //
    // 现实限制：xime RimeEngine 缺 getSelectedRimeSchemaList / selectRimeSchemas JNI
    // 方法，且 librime_jni.so 预编译无源码无法补充。故用 YamlPatcher 模拟：
    // - getSelectedSchemas：解析 default.custom.yaml 的 patch.schema_list
    // - setSelectedSchemas：用 YamlPatcher 只修改 schema_list，保留用户其他 patch
    //
    // 这是 trime 架构的 Kotlin 层过渡实现，未来补充 JNI 后可无缝切换。

    /**
     * 获取已启用的方案列表（从 default.custom.yaml 的 patch.schema_list 解析）。
     * 对应 trime 的 getSelectedRimeSchemaList() / enabledSchemata()。
     */
    fun getSelectedSchemas(context: Context): List<String> {
        val defaultCustom = File(context.filesDir, "rime/shared/default.custom.yaml")
        if (!defaultCustom.exists()) return emptyList()
        val result = mutableListOf<String>()
        var inPatch = false
        var inSchemaList = false
        defaultCustom.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (line.startsWith("patch:")) {
                    inPatch = true
                    continue
                }
                if (inPatch && trimmed.startsWith("schema_list:")) {
                    inSchemaList = true
                    continue
                }
                if (inSchemaList && trimmed.startsWith("- schema:")) {
                    val id = trimmed.substringAfter("- schema:").trim()
                    if (id.isNotBlank()) result.add(id)
                    continue
                }
                // 遇到缩进回退或非 schema 项，退出 schema_list 解析
                if (inSchemaList && trimmed.isNotEmpty() && !trimmed.startsWith("-") && !trimmed.startsWith("schema:")) {
                    break
                }
            }
        }
        return result
    }

    /**
     * 设置启用的方案列表（用 YamlPatcher 修改 default.custom.yaml 的 schema_list）。
     * 对应 trime 的 setEnabledSchemata(schemaIds: Array<String>)。
     *
     * @param ids 方案 ID 列表（按优先级排序，首个为默认方案）
     * @return 是否发生了修改
     */
    fun setSelectedSchemas(context: Context, ids: List<String>): Boolean {
        val defaultCustom = File(context.filesDir, "rime/shared/default.custom.yaml")
        defaultCustom.parentFile?.mkdirs()
        return YamlPatcher.patchSchemaList(defaultCustom, ids)
    }

    /**
     * 部署方案（同步 assets → 触发 librime 维护 → 等待完成）。
     * 对应 trime 的 deploy(skipImport: Boolean)。
     *
     * trime 的正确做法是 exitRime() + startRime(fullCheck=true)，但 xime RimeEngine
     * 未暴露 exitRime，故用 ensureReady 的现有逻辑（syncAssets + startMaintenance）。
     */
    suspend fun deploy(context: Context): Boolean = withContext(Dispatchers.IO) {
        // 复用 ensureReady 的同步和维护逻辑
        ensureReady(context)
    }


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

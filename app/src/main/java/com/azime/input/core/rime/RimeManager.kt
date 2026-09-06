package com.azime.input.core.rime

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeProcessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TreeMap

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
            deployPendingImport(sharedDir)
            assetsChanged = changed
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

    /** 立即部署导入目录中的方案（导入完成后由设置页调用）。 */
    suspend fun deployImportedSchemas(context: Context) = withContext(Dispatchers.IO) {
        val sharedDir = File(context.filesDir, "rime/shared")
        sharedDir.mkdirs()
        val changed = runCatching { deployPendingImport(sharedDir) }
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

    /**
     * 把导入目录中新增/变更的文件同步进 sharedDir 并触发重新部署。
     * 判定依据：sharedDir/.imported marker 记录上次已部署的「相对路径:长度」清单。
     * 轮7：保留子目录结构（lua/ opencc/ models/ …）并拷贝全部扩展名
     *（此前只平面拷贝 yaml/txt，导致方案包里的 Lua 脚本与词典资源全部缺失、切换后无候选）。
     * 返回是否发生了同步（需要重新部署）。
     */
    private fun deployPendingImport(sharedDir: File): Boolean {
        val importRoot = try {
            com.azime.input.core.storage.StorageManager.schemaDir
        } catch (e: Exception) {
            return false // Application 未初始化（少见），跳过
        }
        if (!importRoot.isDirectory) return false
        val files = importRoot.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .toList()
        if (files.isEmpty()) return false

        val manifest = TreeMap<String, Long>()
        files.forEach { manifest[it.relativeTo(importRoot).invariantSeparatorsPath] = it.length() }
        val manifestText = manifest.entries.joinToString("\n") { "${it.key}:${it.value}" }
        val marker = File(sharedDir, ".imported")
        if (marker.exists() && marker.readText() == manifestText &&
            manifest.keys.all { File(sharedDir, it).exists() }
        ) return false

        files.forEach { src ->
            val rel = src.relativeTo(importRoot).invariantSeparatorsPath
            val dst = File(sharedDir, rel)
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
        marker.writeText(manifestText)
        Log.i(TAG, "Deployed ${files.size} imported schema files (structure preserved)")

        // 把导入方案的 schema_id 追加进 schema_list（librime 只部署清单里的方案）
        val importedIds = files.filter { it.extension == "yaml" }.mapNotNull { f ->
            val text = try { f.readText() } catch (e: Exception) { return@mapNotNull null }
            Regex("""schema_id:\s*(\S+)""").find(text)?.groupValues?.get(1)
        }.filter { it.isNotBlank() }.distinct()
        if (importedIds.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("# 由 AZime 自动生成：内置拼音 + 已导入方案\n")
            sb.append("patch:\n  schema_list:\n    - schema: pinyin_simp\n")
            importedIds.forEach { sb.append("    - schema: $it\n") }
            File(sharedDir, "default.custom.yaml").writeText(sb.toString())
        }
        runCatching { RimeEngine.getInstance().startMaintenance(true) }
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

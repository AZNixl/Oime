package com.azime.input.core.handwriting

import com.azime.input.core.diag.Diag
// ⚠️ 扩展函数必须 import ✓（不能全限定名调用 ✗ —— 本项目铁律）
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 轮19.126：**模型下载器**（直连优先 → 失败自动切代理 ✓）
 *
 * 代理清单参照 trime2 的「下载中心」脚本（`rime/tools/下载/main.lua`）✓
 * —— 那些都是**公益免费**的 GitHub 加速镜像 ✓（非付费 ✓ 但可能失效/限速 ✗）
 * 用法：`download(url, targetFile, onProgress)` —— 内部依次尝试 直连 + 各代理 ✓
 */
object ModelDownloader {

    // ── 轮19.137：**全局进度状态** ✓（返回上一级不中断、回来还能看到 ✓）──
    /** key = 模型 id（或 "hw"）✓ */
    class DlState {
        // ⚠️ 非 Compose 文件里不能用 by 委托 ✗（需 getValue/setValue import ✓）⇒ 显式 .value ✓
        val stage = androidx.compose.runtime.mutableStateOf("")
        val pct = androidx.compose.runtime.mutableStateOf(-1)
        val msg = androidx.compose.runtime.mutableStateOf("")
        val running = androidx.compose.runtime.mutableStateOf(false)
    }
    val states = androidx.compose.runtime.mutableStateMapOf<String, DlState>()
    fun stateOf(id: String): DlState = states.getOrPut(id) { DlState() }
    /** 全局作用域 ✓：不随页面销毁而取消 ✓ */
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    fun launchDownload(id: String, block: suspend () -> Unit) {
        val st = stateOf(id)
        if (st.running.value) return
        st.running.value = true; st.stage.value = "准备中…"; st.pct.value = -1; st.msg.value = ""
        scope.launch {
            runCatching { block() }.onFailure {
                st.msg.value = "❌ 失败：" + (it.message ?: it.javaClass.simpleName)
            }
            st.running.value = false; st.stage.value = ""; st.pct.value = -1
        }
    }

    /** 公益 GitHub 加速镜像（前缀 + `encodeURIComponent(原始URL)` ✓ 与 Lua 脚本的 `?q=` 约定一致 ✓） */
    private val PROXIES = listOf(
        "",                                              // 直连优先 ✓
        "https://ghfast.top/?q=",
        "https://ghproxy.net/?q=",
        "https://mirror.ghproxy.com/?q=",
        "https://github.moeyy.xyz/?q=",
        "https://git.xfj0.cn/?q=",
        "https://gh.ddlc.top/?q=",
        "https://gh-proxy.com/?q=",
        "https://proxy.zycc.xyz/?q=",
        "https://gh.llkk.cc/?q=",
        "https://hub.gitmirror.com/?q=",
        "https://gh.idayer.com/?q=",
    )

    /** 轮19.127：**可下载的语音模型**（都在 k2-fsa/sherpa-onnx 的 asr-models release 上 ✓ GitHub 直连 ✓） */
    data class VoiceModel(
        val id: String,
        val title: String,
        val fileName: String,       // 下载后的临时文件名 ✓
        val targetSubDir: String,   // 解压后落到 models/<targetSubDir>/ ✓
        val url: String,
        val note: String,
    )

    val VOICE_MODELS = listOf(
        VoiceModel(
            id = "sense_voice",
            title = "SenseVoice（准确率优先 · 松手出整段）",
            fileName = "sense-voice.tar.bz2",
            targetSubDir = "sense-voice",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2",
            note = "约 240MB · zh/en/ja/ko/yue",
        ),
        VoiceModel(
            id = "zipformer",
            title = "流式 Zipformer（边说边出 · 中英混说）",
            fileName = "zipformer.tar.bz2",
            targetSubDir = "zipformer",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2",
            note = "约 200MB · 对 CPU 要求较高（老机型会慢 ✓）",
        ),
    )

    /**
     * 下载 + 解压 + 归位 一体化 ✓（语音模型专用 ✓）
     * 全程：直连优先 → 自动切代理 ✓；解压用 tar.bz2 ✓；顶层目录自动剥掉 ✓
     */
    fun installVoiceModel(
        model: VoiceModel,
        modelsRoot: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onStage: (String) -> Unit = {},
        shouldStop: () -> Boolean = { false },
    ): Result {
        // 临时下载目录：`Documents/Oime/downloads/` ✓（用户可自行检查遗留 ✓）
        val dlDir = File(modelsRoot, "downloads").apply { mkdirs() }
        val tmp = File(dlDir, model.fileName)
        onStage("下载中…")
        val r = download(model.url, tmp, onProgress, shouldStop)
        if (!r.ok) return r
        onStage("解压中…")
        val target = File(modelsRoot, model.targetSubDir)
        target.mkdirs()
        val ok = if (model.fileName.endsWith(".tar.bz2")) extractTarBz2(tmp, target) else unzip(tmp, target)
        tmp.delete()      // 解压完删掉下载包 ✓（用户要求 ✓）
        Diag.log("Dl", "已删除临时包 $tmp（存在=${tmp.exists()} ✓）")
        return if (ok) Result(true, r.via, "已安装到 models/${model.targetSubDir}/ ✓")
        else Result(false, r.via, "解压失败（通道 ✓ 但解压出错 ✗）")
    }

    fun buildUrl(raw: String, prefix: String): String =
        if (prefix.isEmpty()) raw else prefix + java.net.URLEncoder.encode(raw, "UTF-8")

    /** 结果：成功与否 + 实际使用的通道 ✓（便于 UI 提示"可能需要代理" ✓）*/
    data class Result(val ok: Boolean, val via: String, val message: String)

    /**
     * 下载 rawUrl 到 targetFile ✓（自动尝试直连与各代理 ✓ 带进度回调 ✓）
     * @param onProgress (已下载字节, 总字节) —— 总字节未知时传 -1 ✓
     */
    fun download(
        rawUrl: String,
        targetFile: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        shouldStop: () -> Boolean = { false },
    ): Result {
        targetFile.parentFile?.mkdirs()
        var lastErr = ""
        for ((i, prefix) in PROXIES.withIndex()) {
            if (shouldStop()) return Result(false, "", "已取消")
            val via = if (prefix.isEmpty()) "直连" else prefix.substringAfter("//").substringBefore("/")
            val url = buildUrl(rawUrl, prefix)
            val tmp = File(targetFile.absolutePath + ".part")
            try {
                Diag.log("Dl", "尝试[$i] $via → $url")
                val ok = fetch(url, tmp, onProgress, shouldStop)
                if (ok && tmp.length() > 0 && targetFile.parentFile?.let { tmp.renameTo(targetFile) } != false) {
                    Diag.log("Dl", "$via 下载成功（${targetFile.length() / 1024} KB ✓）")
                    return Result(true, via, "下载成功（$via ✓）")
                }
                lastErr = "内容为空"
            } catch (t: Throwable) {
                lastErr = t.message ?: t.javaClass.simpleName
                Diag.warn("Dl", "$via 失败：$lastErr")
                // 轮19.137：**保留 .part** ✓（下次可续传 ✓ 以前删掉 ⇒ 永远从头 ✗）
                if (tmp.length() > 0) {
                    Diag.log("Dl", "已保留断点文件 ${tmp.name}（${tmp.length()/1024} KB）✓")
                    return Result(false, via, "已中断（已保留 ${tmp.length()/1024} KB，重试可续传 ✓）")
                }
            }
        }
        return Result(false, "", "全部通道失败（${lastErr}）—— 若在受限网络，可能需要自备代理")
    }

    private fun fetch(url: String, out: File, onProgress: (Long, Long) -> Unit, shouldStop: () -> Boolean): Boolean {
        // 轮19.137：**断点续传** ✓（已下部分保留 ⇒ 用 Range 从断点接 ✓）
        val already = if (out.exists()) out.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Oime model downloader)")
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            // 206 = 服务器接受续传 ✓；200 = 不支持 ⇒ 从头（截断本地 ✓）
            val starting = if (code == 206) already else 0L
            if (starting == 0L && out.exists()) out.delete()
            val totalPart = conn.contentLength.toLong()
            val total = if (starting > 0 && totalPart > 0) starting + totalPart else totalPart
            conn.inputStream.use { input ->
                FileOutputStream(out, starting > 0).use { fos ->   // append = 续传 ✓
                    val buf = ByteArray(64 * 1024)
                    var done = starting
                    var lastReport = 0L
                    while (true) {
                        if (shouldStop()) throw RuntimeException("已取消")
                        val n = input.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 256 * 1024) {
                            lastReport = done
                            onProgress(done, total)
                        }
                    }
                    fos.flush()
                    onProgress(done, total)
                    // ★ 轮19.137：**完整性校验** ✓（以前断流被当成成功 ✗ ⇒ 残缺模型 ⇒ 加载崩溃 ✗✗）
                    if (total > 0 && done < total) {
                        throw RuntimeException("下载不完整（$done/$total）—— 已保留，可重试续传 ✓")
                    }
                }
            }
            return true
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 解压 **.tar.bz2** 到 dir ✓（轮19.127：语音模型是 tar.bz2 ✗ Android 原生解不了 ✓
     * 用 commons-compress ✓）
     * @param flatten 顶层只有一个目录时，把它**剥掉** ✓（App 期望文件直接落在目标目录 ✓）
     */
    /**
     * 只解「我们需要的部分」✓（用户要求 ✓）：模型与词表 ✓，跳过样例音频/文档等 ✗
     * @param keep 过滤谓词（默认：.onnx / .txt ✓）
     */
    // 轮19.137：**只留真正需要的** ✓（用户反馈"全解压了"✗）
    //  · 只要 **int8** 量化模型 ✓（fp32 版动辄 300~900MB ✗ 用不上 ✗）
    //  · decoder 无 int8 版 ⇒ 单独放行 ✓
    //  · tokens.txt 必须留 ✓
    var keepFilter: (String) -> Boolean = { n ->
        val f = n.substringAfterLast('/').lowercase()
        when {
            f == "tokens.txt" -> true
            f.endsWith(".int8.onnx") -> true
            // 轮19.138：**删掉 decoder 例外** ✗ —— 实测 zipformer 的 decoder **有 int8 版** ✓
            // （原来那条放行了 13.9MB 的 fp32 decoder ✗ ⇒ 多解压 ✓ 用户反馈 ✓）
            else -> false                                            // 其余（fp32/样例音频/文档）✗
        }
    }

    fun extractTarBz2(archive: File, dir: File, flatten: Boolean = true): Boolean = runCatching {
        dir.mkdirs()
        org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
            archive.inputStream().buffered()
        ).use { bz ->
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bz).use { tar ->
                var topDir: String? = null
                var single = true
                // 先扫一遍判断是否只有一个顶层目录 ✓（tar 不能重读 ⇒ 记下条目再解 ✗）
                while (true) {
                    val e = tar.nextEntry ?: break
                    val name = e.name
                    val first = name.substringBefore('/')
                    if (topDir == null) topDir = first
                    if (!name.startsWith("$first/") || name.substringAfter('/').contains('/')) { /* 仍有可能是单目录 ✓ */ }
                    if (first.contains('.') && !e.isDirectory) { single = false; break }
                    val rest = name.removePrefix("$first/")
                    if (rest.isEmpty() && !e.isDirectory) { single = false; break }
                    if (flatten && single) {
                        if (rest.isEmpty()) continue
                        if (!keepFilter(rest)) continue      // 只要需要的文件 ✓
                        val f = File(dir, rest)
                        if (!f.canonicalPath.startsWith(dir.canonicalPath)) continue
                        if (e.isDirectory) f.mkdirs() else {
                            f.parentFile?.mkdirs()
                            FileOutputStream(f).use { tar.copyTo(it) }
                        }
                    } else {
                        // 轮19.137：**这里原来全解压 ✗** ⇒ 也套过滤器 ✓
                        if (!e.isDirectory && !keepFilter(name)) continue
                        val f = File(dir, name)
                        if (!f.canonicalPath.startsWith(dir.canonicalPath)) continue
                        if (e.isDirectory) f.mkdirs() else {
                            f.parentFile?.mkdirs()
                            FileOutputStream(f).use { tar.copyTo(it) }
                        }
                    }
                }
            }
        }
        true
    }.getOrElse {
        Diag.warn("Dl", "tar.bz2 解压失败：${it.message}")
        false
    }

    /** 解压 zip 到 dir ✓ */
    fun unzip(zip: File, dir: File): Boolean = runCatching {
        dir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val f = File(dir, e.name)
                // 防目录穿越 ✓
                if (!f.canonicalPath.startsWith(dir.canonicalPath)) { zis.closeEntry(); continue }
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
        true
    }.getOrElse { Diag.warn("Dl", "解压失败：${it.message}"); false }
}

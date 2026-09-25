package com.azime.input.core.diag

import java.io.File

/**
 * 轮19.83：**日志系统**（原诊断埋点升级）。
 *
 * 为什么需要：本机（ColorOS / Android 16）的 logcat **只放行系统日志**，第三方 App 的 `Log.d`
 * 一条都读不到（实测：App 运行正常但 logcat 里零条本应用日志）⇒ 日志必须自己落盘 ✓
 *
 * ## 输出策略（参考 trime / xime 一类 IME 的通行做法）
 * - **默认轻量**：只记「错误 / 警告 / 关键事件（生命周期、部署、切引擎）」✓ —— 耗电与 IO 都可忽略
 * - **详细模式按需开**：设置 → 关于 → 日志 → 「详细日志」开启后，才记按键/候选/光标/复制条等高频埋点 ✓
 *   （高频埋点是**最耗 IO 的一类**，绝不能默认常开 ✗）
 *
 * ## 文件与轮转
 * - 目录：`Documents/Oime/logs/`（外部可读，PC 端 adb 直接取 ✓）
 * - 按天分文件：`oime-YYYY-MM-DD.log` ✓；崩溃另存 `crash-YYYY-MM-DD.log` ✓
 * - 自动清理：只保留最近 [KEEP_DAYS] 天；单文件超过 [MAX_BYTES] 时截断重写，避免无限增长 ✓
 */
object Diag {

    private const val PREFIX = "oime"
    private const val CRASH_PREFIX = "crash"
    private const val KEEP_DAYS = 7
    private const val MAX_BYTES = 2L * 1024 * 1024

    @Volatile private var dir: File? = null
    @Volatile private var verbose: Boolean = false
    @Volatile private var installed = false

    /** 由 App / 服务启动时初始化；[logDir] 通常是 StorageManager.logsDir。 */
    fun init(logDir: File, verboseLog: Boolean) {
        dir = logDir.apply { runCatching { mkdirs() } }
        verbose = verboseLog
        purgeOld()
    }

    /** 详细日志开关（设置页可改，立即生效）。 */
    fun setVerbose(on: Boolean) { verbose = on }

    fun isVerbose(): Boolean = verbose

    fun dir(): File? = dir

    // ── 级别 ──────────────────────────────────────────────────

    /** 错误：一定记录 ✓ */
    fun err(tag: String, msg: String, tr: Throwable? = null) {
        write("E", tag, msg + (tr?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
        if (tr != null) write("E", tag, android.util.Log.getStackTraceString(tr).trim())
    }

    /** 警告：一定记录 ✓ */
    fun warn(tag: String, msg: String) = write("W", tag, msg)

    /** 关键事件（生命周期/部署/切引擎…）：一定记录 ✓ */
    fun info(tag: String, msg: String) = write("I", tag, msg)

    /** 详细埋点（按键/候选/光标…）：**仅在详细模式开启时**记录 ✓ */
    fun log(tag: String, msg: String) {
        if (!verbose) return
        write("D", tag, msg)
    }

    /** 崩溃：单独文件，便于直接发出来 ✓（**先打设备信息块**，参考 trime 的 crash 日志做法 ✓） */
    fun crash(tr: Throwable) {
        val f = fileFor(CRASH_PREFIX)
        runCatching {
            f.appendText("\n=== ${stamp("yyyy-MM-dd HH:mm:ss.SSS")} ===\n")
            f.appendText(deviceInfo())
            f.appendText("\n--- stacktrace ---\n")
            f.appendText(android.util.Log.getStackTraceString(tr))
            f.appendText("\n")
        }
    }

    /** 设备与版本信息块（崩溃报告里最需要的一段 ✓） */
    private fun deviceInfo(): String = runCatching {
        val ctx = com.azime.input.AZimeApplication.instance
        val pm = ctx.packageManager
        val verName = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val verCode = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, 0).versionCode
        }.getOrNull() ?: -1
        buildString {
            append("app=").append(ctx.packageName).append('\n')
            append("versionName=").append(verName).append('\n')
            append("versionCode=").append(verCode).append('\n')
            append("android=").append(android.os.Build.VERSION.RELEASE)
                .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
            append("brand=").append(android.os.Build.BRAND).append('\n')
            append("model=").append(android.os.Build.MODEL).append('\n')
            append("device=").append(android.os.Build.DEVICE).append('\n')
            append("abis=").append(android.os.Build.SUPPORTED_ABIS.joinToString(",")).append('\n')
            append("time=").append(stamp("yyyy-MM-dd HH:mm:ss")).append('\n')
        }
    }.getOrDefault("(device info unavailable)\n")

    /** 安装全局崩溃捕获（写入 crash-*.log 后交回系统默认处理 ✓）。 */
    fun installCrashHandler() {
        if (installed) return
        installed = true
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { crash(e) }
            runCatching { prev?.uncaughtException(t, e) }
        }
    }

    /** 清空全部日志 ✓ */
    fun clearAll() {
        runCatching { dir?.listFiles()?.forEach { it.delete() } }
    }

    /** 全部日志文件（新的在前）。 */
    fun files(): List<File> =
        (dir?.listFiles() ?: emptyArray())
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }

    // ── 内部 ──────────────────────────────────────────────────

    private fun write(level: String, tag: String, msg: String) {
        runCatching { android.util.Log.println(levelToLogPriority(level), tag, msg) }
        runCatching {
            val f = fileFor("$PREFIX-")
            if (f.exists() && f.length() > MAX_BYTES) f.delete()
            f.appendText("${stamp("HH:mm:ss.SSS")} $level [$tag] $msg\n")
        }
    }

    private fun levelToLogPriority(level: String): Int = when (level) {
        "E" -> android.util.Log.ERROR
        "W" -> android.util.Log.WARN
        "I" -> android.util.Log.INFO
        else -> android.util.Log.DEBUG
    }

    /** 按天分文件：oime-YYYY-MM-DD.log / crash-YYYY-MM-DD.log（传入的前缀以 '-' 结尾表示按天） */
    private fun fileFor(prefix: String): File {
        val d = dir ?: return File("/dev/null")
        runCatching { d.mkdirs() }
        val name = if (prefix.endsWith("-")) {
            prefix.trimEnd('-') + "-" + stamp("yyyy-MM-dd") + ".log"
        } else {
            prefix + "-" + stamp("yyyy-MM-dd") + ".log"
        }
        return File(d, name)
    }

    private fun stamp(pattern: String): String =
        java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(java.util.Date())

    /** 只保留最近 KEEP_DAYS 天 ✓ */
    private fun purgeOld() {
        runCatching {
            val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600_000L
            dir?.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
        }
    }
}

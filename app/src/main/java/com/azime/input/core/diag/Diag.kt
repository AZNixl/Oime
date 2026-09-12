package com.azime.input.core.diag

import java.io.File

/**
 * 轮19.22：诊断埋点。
 *
 * 背景：本机（ColorOS / Android 16）的 logcat **只放行系统日志**，第三方 App 的 `Log.d`
 * 一条都读不到（实测：App 运行正常但 logcat 里零条本应用日志）。
 * 因此把关键诊断同时追加写入 `/sdcard/Download/oime_diag.log`，PC 端用 adb 直接读。
 * 文件超过 128KB 时自动清空重来，避免无限增长。
 */
object Diag {
    private const val PATH = "/sdcard/Download/oime_diag.log"
    private const val MAX = 128 * 1024

    fun log(tag: String, msg: String) {
        runCatching { android.util.Log.d(tag, msg) }
        runCatching {
            val f = File(PATH)
            if (f.exists() && f.length() > MAX) f.delete()
            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date())
            f.appendText("$ts [$tag] $msg\n")
        }
    }
}

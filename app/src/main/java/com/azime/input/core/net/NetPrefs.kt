package com.azime.input.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 轮19.145：**联网下载的用户意愿** + **当前网络状态** ✓
 *
 * ## 背景（用户反馈："启动界面没有联网权限的索取界面，直接就可以联网下模型了"）
 * 先把平台事实说清楚（这是关键 ✓ 也是"看不到索取界面"的真因 ✓）：
 *
 * · Android 的 `INTERNET` 属于 **normal 权限** ⇒ **装机时系统自动授予** ✓
 *   系统**不提供**运行时弹窗 ✗ ⇒ 任何 App 都做不出"联网权限索取界面" ✗（不是我们漏写 ✗）
 * · 真正该给用户的是**知情与选择**：模型动辄 200~240MB ✗ ⇒
 *   ① 向导里明说"本应用需要联网下载模型"，并给一个**显式开关** ✓
 *   ② 默认**仅 Wi-Fi 下载** ✓（走流量下 240MB 代价太高 ✗）
 *
 * 两个开关都存在 `wizard_prefs`（与首次向导同一个 SharedPreferences ✓）。
 */
object NetPrefs {

    private const val FILE = "wizard_prefs"
    private const val K_ALLOW = "net_allow_download"
    private const val K_WIFI_ONLY = "net_wifi_only"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 允许联网下载模型 ✓（默认允许 ✓ —— 保持既有行为不被破坏 ✓） */
    fun allowDownload(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ALLOW, true)

    fun setAllowDownload(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_ALLOW, v).apply()
    }

    /** 仅 Wi-Fi 下载 ✓（**默认开** ✓ —— 模型很大 ✗ 不建议走流量 ✗） */
    fun wifiOnly(ctx: Context): Boolean = prefs(ctx).getBoolean(K_WIFI_ONLY, true)

    fun setWifiOnly(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_WIFI_ONLY, v).apply()
    }
}

/** 当前网络类型（只区分"能不能放心下大文件"✓，不细分 5G/4G ✗） */
enum class NetState {
    /** Wi-Fi / 以太网 / VPN —— 视为"不计流量"，可下载 ✓ */
    WIFI,

    /** 蜂窝网络 —— 大文件要拦一下 ✓ */
    MOBILE,

    /** 没有任何可用网络 ✓ */
    NONE,

    /** 查不到（缺权限 / 系统异常 ✓）—— 按"允许"处理 ✓ 不误伤功能 ✓ */
    UNKNOWN,
}

fun netStateLabel(s: NetState): String = when (s) {
    NetState.WIFI -> "Wi-Fi"
    NetState.MOBILE -> "移动网络"
    NetState.NONE -> "未联网"
    NetState.UNKNOWN -> "未知"
}

/**
 * 读当前网络类型 ✓
 * 需要 `ACCESS_NETWORK_STATE`（同为 normal 权限 ⇒ 装机即授予 ✓ 已在 manifest 声明 ✓）
 */
fun netState(ctx: Context): NetState = runCatching {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@runCatching NetState.UNKNOWN
    val net = cm.activeNetwork ?: return@runCatching NetState.NONE
    val cap = cm.getNetworkCapabilities(net) ?: return@runCatching NetState.NONE
    when {
        cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetState.WIFI
        // 以太网 / VPN 都不计手机流量 ⇒ 归到"可下载" ✓
        cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetState.WIFI
        cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetState.WIFI
        cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetState.MOBILE
        // 其它传输方式：只要声明了 INTERNET 能力就当可下载 ✓（不误伤 ✓）
        cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetState.WIFI
        else -> NetState.NONE
    }
}.getOrDefault(NetState.UNKNOWN)

/** 现在能不能直接开始下载（不需要再问）✓ */
fun canDownloadNow(ctx: Context): Boolean =
    NetPrefs.allowDownload(ctx) &&
        !(NetPrefs.wifiOnly(ctx) && netState(ctx) == NetState.MOBILE)

/** 被拦住的**原因**文案 ✓（空串 = 没被拦 ✓） */
fun downloadBlockReason(ctx: Context): String = when {
    !NetPrefs.allowDownload(ctx) ->
        "已关闭「允许联网下载」（可在 设置 → 语音手写管理 或 首次向导 里打开）"
    NetPrefs.wifiOnly(ctx) && netState(ctx) == NetState.MOBILE ->
        "当前是移动网络，且开着「仅 Wi-Fi 下载」—— 模型约 200~240MB，建议连 Wi-Fi"
    else -> ""
}

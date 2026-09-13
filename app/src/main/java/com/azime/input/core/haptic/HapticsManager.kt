package com.azime.input.core.haptic

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import com.azime.input.AZimeApplication

/**
 * 打字振动管理器（仿 xime）。
 *
 * 配置项（SharedPreferences 持久化，设置页「打字振动」卡片可调）：
 * - [enabled] 总开关
 * - 模式：`system` 系统默认振动 / `custom` 自定义时长振动
 * - [customMs] 自定义模式振动时长（毫秒）
 * - [pressEnabled] 按下按键时震动
 * - [releaseEnabled] 抬起按键时震动
 */
object HapticsManager {

    private const val PREFS_NAME = "haptic_prefs"
    private const val KEY_ENABLED = "haptic_enabled"
    private const val KEY_MODE = "haptic_mode"          // system | custom
    private const val KEY_CUSTOM_MS = "haptic_custom_ms"
    private const val KEY_PRESS = "haptic_press"
    private const val KEY_RELEASE = "haptic_release"

    private const val DEFAULT_MS = 15

    private val prefs
        get() = AZimeApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var vibrator: Vibrator? = null

    // ── 配置读写 ──
    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)
    fun setEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    fun mode(): String = prefs.getString(KEY_MODE, "system") ?: "system"
    fun setMode(v: String) = prefs.edit().putString(KEY_MODE, v).apply()

    fun customMs(): Int = prefs.getInt(KEY_CUSTOM_MS, DEFAULT_MS)
    fun setCustomMs(ms: Int) = prefs.edit().putInt(KEY_CUSTOM_MS, ms.coerceIn(5, 60)).apply()

    fun pressEnabled(): Boolean = prefs.getBoolean(KEY_PRESS, true)
    fun setPressEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_PRESS, v).apply()

    fun releaseEnabled(): Boolean = prefs.getBoolean(KEY_RELEASE, false)
    fun setReleaseEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_RELEASE, v).apply()

    // ── 触发（轮19.17：统一入口 + 节流）──

    /** 节流窗口：两次振动最小间隔（ms）。打字快时合并，避免"每键两振/每步一振"。 */
    /** 轮19.24：70ms → **40ms**。70ms 会在快速打字时丢掉振动（实测手感"粘滞不清脆"），
     *  40ms 仍能合并同一按键的重复事件，但保住了快打时的触感。 */
    private const val THROTTLE_MS = 40L
    private var lastVibrateAt = 0L

    /**
     * 手势/系统类振动的类型（轮19.17）：以前 ○ 环拖动、滑块、四向滑动直接用 Compose 的
     * `performHapticFeedback(...)`——**绕过「打字振动」总开关**，用户关了振动手势照样振。
     * 现在全部走这里，统一受总开关 + 节流约束。
     */
    enum class Type { PRESS, RELEASE, LONG_PRESS, STEP }

    /** 统一入口：所有振动都必须经过这里（总开关 + 节流 + 合并）。 */
    fun haptic(type: Type) {
        if (!enabled()) return
        when (type) {
            Type.PRESS -> if (!pressEnabled()) return
            Type.RELEASE -> if (!releaseEnabled()) return
            // 长按/步进类：不单独开关，但同样受总开关与节流约束
            Type.LONG_PRESS, Type.STEP -> Unit
        }
        vibrate()
    }

    /** 按下按键。 */
    fun press() = haptic(Type.PRESS)

    /** 抬起按键。 */
    fun release() = haptic(Type.RELEASE)

    private fun vibrate() {
        // 轮19.17：节流——报告实测 18.5h 内振动 7168 次 / 166s，其中大量是
        // 拖动类「每跨一步振一次」。70ms 窗口把重复事件合并，人手感知无差别。
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastVibrateAt < THROTTLE_MS) return
        lastVibrateAt = now
        val ms = if (mode() == "custom") customMs() else DEFAULT_MS
        val vib = vibrator ?: AZimeApplication.instance
            .getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vib?.let { vibrator = it } ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(ms.toLong())
        }
    }
}

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

    // ── 触发 ──

    /** 按下按键。 */
    fun press() {
        if (enabled() && pressEnabled()) vibrate()
    }

    /** 抬起按键。 */
    fun release() {
        if (enabled() && releaseEnabled()) vibrate()
    }

    private fun vibrate() {
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

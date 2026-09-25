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

    /**
     * 轮19.99：**系统键盘触感**模式需要宿主 View（走 `View.performHapticFeedback(KEYBOARD_TAP)` ✓，
     * 这条是各 ROM「键盘触感」的官方通道 —— Xime 默认走的就是它 ✓）。
     * 由 IME 服务在 onCreate 时挂上（lazy 取 decorView ✓）。
     */
    var hostViewProvider: (() -> android.view.View?)? = null

    /** 模式常量：keyboard = 系统键盘触感（默认）· system = 系统预定义轻点 · custom = 自定义 one-shot */
    const val MODE_KEYBOARD = "keyboard"
    const val MODE_SYSTEM = "system"
    const val MODE_CUSTOM = "custom"

    // ── 配置读写 ──
    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)
    fun setEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    fun mode(): String = prefs.getString(KEY_MODE, MODE_KEYBOARD) ?: MODE_KEYBOARD
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
    enum class Type { PRESS, RELEASE, LONG_PRESS, STEP, DISMISS }

    /** 统一入口：所有振动都必须经过这里（总开关 + 节流 + 合并）。 */
    fun haptic(type: Type) {
        if (!enabled()) return
        // 消亡提示：固定 30ms（比按键振感更明确），不受 press/release 子开关影响
        if (type == Type.DISMISS) {
            vibrate(forceMs = 30)
            return
        }
        when (type) {
            Type.PRESS -> if (!pressEnabled()) return
            Type.RELEASE -> if (!releaseEnabled()) return
            // 长按/步进/消亡提示：不单独开关，但同样受总开关与节流约束
            Type.LONG_PRESS, Type.STEP, Type.DISMISS -> Unit
        }
        vibrate()
    }

    /** 按下按键。 */
    fun press() = haptic(Type.PRESS)

    /** 抬起按键。 */
    fun release() = haptic(Type.RELEASE)

    private fun vibrate(forceMs: Int = 0) {
        // 轮19.17：节流——报告实测 18.5h 内振动 7168 次 / 166s，其中大量是
        // 拖动类「每跨一步振一次」。70ms 窗口把重复事件合并，人手感知无差别。
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastVibrateAt < THROTTLE_MS) return
        lastVibrateAt = now
        val ms = if (forceMs > 0) forceMs else if (mode() == "custom") customMs() else DEFAULT_MS
        // 轮19.97：振动器获取 —— Android 12+ 用 VibratorManager（对齐 Xime ✓）
        val vib = vibrator ?: run {
            val ctx = AZimeApplication.instance
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            v?.also { vibrator = it }
        } ?: return
        if (!vib.hasVibrator()) return

        // 轮19.97：**修「嗡嗡」** —— 小米( MIUI / HyperOS )的震动服务对「不带 usage 的裸 one-shot」
        // 会走通用马达长驱 ✗，线性马达上就是嗡一下 ✗。
        // 对齐 Xime 的做法：默认走**系统预定义的轻点波形**（与系统键盘同源，短促清脆 ✓）；
        // 仅「自定义」模式才发 one-shot，且幅度做上限（不再满驱 ✗）。
        val m = mode()
        val isCustom = forceMs > 0 || m == MODE_CUSTOM
        // 轮19.99：**系统键盘触感**（默认）—— 交给系统按「键盘触感」渲染，ROM 有专门波形 ✓
        // 这是「嗡嗡」的最彻底解法（与系统输入法同一条路 ✓）；取不到宿主 View 时退回 TICK ✓
        if (!isCustom && m == MODE_KEYBOARD) {
            val hv = runCatching { hostViewProvider?.invoke() }.getOrNull()
            if (hv != null) {
                runCatching {
                    hv.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                    )
                }
                if (hv.isAttachedToWindow) return
            }
            // 兜底：系统预定义轻点 ✓
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrateCompat(vib, VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            }
        }
        if (!isCustom && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateCompat(vib, VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 有幅度控制时才自定幅度（自定义 160 / 默认 90），否则交给系统默认 ✓
            val amplitude = if (vib.hasAmplitudeControl()) (if (isCustom) 160 else 90)
            else VibrationEffect.DEFAULT_AMPLITUDE
            vibrateCompat(vib, VibrationEffect.createOneShot(ms.toLong().coerceIn(5, 60), amplitude))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(ms.toLong())
        }
    }

    /**
     * 轮19.97：统一出口 —— Android 12+ 优先带 usage（键盘触感通道 ✓，ROM 对这条通道有专门波形）；
     * 常量名在各版本 API 里不完全一致，故用 runCatching 兜底，失败退回普通 vibrate ✓。
     */
    private fun vibrateCompat(vib: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ok = runCatching {
                // 轮19.104：**通道要传对** —— 之前传 12（AudioAttributes 的值）✗ ⇒ ROM 记成 "unknown usage 12" ✗
                // 实测系统键盘（搜狗）走的是 USAGE_TOUCH ✓；API 33+ 有更贴切的 USAGE_HARDWARE_FEEDBACK ✓
                val usage = if (Build.VERSION.SDK_INT >= 33) {
                    android.os.VibrationAttributes.USAGE_HARDWARE_FEEDBACK
                } else {
                    android.os.VibrationAttributes.USAGE_TOUCH
                }
                vib.vibrate(effect, android.os.VibrationAttributes.createForUsage(usage))
                true
            }.getOrDefault(false)
            if (ok) return
        }
        runCatching { vib.vibrate(effect) }
    }
}

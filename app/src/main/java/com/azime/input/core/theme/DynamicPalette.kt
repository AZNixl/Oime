package com.azime.input.core.theme

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.toArgb

/**
 * 轮19.35：Material You —— 从**系统壁纸**动态取色的调色板。
 *
 * Android 12（API 31）+ 才有 `dynamicLightColorScheme/dynamicDarkColorScheme`；
 * 低版本或取色失败时返回 null，调用方回落到风格自带的固定调色板。
 * 结果缓存，避免每次重组都做一次取色。
 */
object DynamicPalette {

    /**
     * 轮19.42：**必须用 toArgb()**。
     * 原来写的是 `color.value.toLong()`——那是 Compose 的 **packed ULong**（含色彩空间位），
     * 而 `Color(Long)` 构造按 **ARGB** 解释 ⇒ 得到垃圾色值
     * （本次 Material You「按键看不到 / 按空白无反应」的真因）。
     * 同时强制不透明（0xFF），避免动态色带 alpha 把键盘画成透明。
     */
    private fun opaque(c: androidx.compose.ui.graphics.Color): Long =
        (c.toArgb().toLong() and 0xFFFFFFFFL) or 0xFF000000L


    @Volatile private var paletteLight: StylePalette? = null
    @Volatile private var paletteDark: StylePalette? = null
    @Volatile private var inited = false

    fun get(context: Context, dark: Boolean): StylePalette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (!inited) {
            synchronized(this) {
                if (!inited) {
                    runCatching {
                        val l = androidx.compose.material3.dynamicLightColorScheme(context)
                        paletteLight = StylePalette(
                            bg = opaque(l.surfaceContainerLowest),
                            barBg = opaque(l.surfaceContainerLow),
                            keyBg = opaque(l.surfaceContainerLow),
                            funcKeyBg = opaque(l.surfaceContainerHigh),
                            text = opaque(l.onSurface),
                            subText = opaque(l.onSurfaceVariant),
                        )
                        val d = androidx.compose.material3.dynamicDarkColorScheme(context)
                        paletteDark = StylePalette(
                            bg = opaque(d.surfaceContainerLowest),
                            barBg = opaque(d.surfaceContainerLow),
                            keyBg = opaque(d.surfaceContainerLow),
                            funcKeyBg = opaque(d.surfaceContainerHigh),
                            text = opaque(d.onSurface),
                            subText = opaque(d.onSurfaceVariant),
                        )
                    }
                    inited = true
                }
            }
        }
        return if (dark) paletteDark else paletteLight
    }

    /** 取色得到的强调色（Material You 用壁纸 primary 作为强调色）。 */
    fun accent(context: Context, dark: Boolean): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching {
            val c = if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
            else androidx.compose.material3.dynamicLightColorScheme(context)
            opaque(c.primary)
        }.getOrNull()
    }
}

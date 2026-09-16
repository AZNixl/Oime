package com.azime.input.core.theme

import android.content.Context
import android.os.Build

/**
 * 轮19.35：Material You —— 从**系统壁纸**动态取色的调色板。
 *
 * Android 12（API 31）+ 才有 `dynamicLightColorScheme/dynamicDarkColorScheme`；
 * 低版本或取色失败时返回 null，调用方回落到风格自带的固定调色板。
 * 结果缓存，避免每次重组都做一次取色。
 */
object DynamicPalette {

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
                            bg = l.surfaceContainerLowest.value.toLong(),
                            barBg = l.surfaceContainerLow.value.toLong(),
                            keyBg = l.surfaceContainerLow.value.toLong(),
                            funcKeyBg = l.surfaceContainerHigh.value.toLong(),
                            text = l.onSurface.value.toLong(),
                            subText = l.onSurfaceVariant.value.toLong(),
                        )
                        val d = androidx.compose.material3.dynamicDarkColorScheme(context)
                        paletteDark = StylePalette(
                            bg = d.surfaceContainerLowest.value.toLong(),
                            barBg = d.surfaceContainerLow.value.toLong(),
                            keyBg = d.surfaceContainerLow.value.toLong(),
                            funcKeyBg = d.surfaceContainerHigh.value.toLong(),
                            text = d.onSurface.value.toLong(),
                            subText = d.onSurfaceVariant.value.toLong(),
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
            c.primary.value.toLong()
        }.getOrNull()
    }
}

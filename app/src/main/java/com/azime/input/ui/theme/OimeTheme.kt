package com.azime.input.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 轮19.49：**全局共用主题**。
 *
 * 与设置页（SettingsActivity）里那套一致：
 * 1. M3 默认配色是**淡紫色系**，卡片/容器按不同中性色角色取色 ⇒ 必须把**整套中性色**覆盖成灰阶；
 * 2. 界面风格（Miuix / Material）影响中性灰阶与页面底色；
 * 3. 强调色跟随键盘回车键颜色（`primary` / `primaryContainer`）。
 *
 * 之前只有设置页做了这件事，**键盘布局编辑器（及其子级页）没用** ⇒ 那里仍是默认紫色 ✗
 * （用户反馈：「这个界面及子级界面的配色没有和其他的一样跟随」）。
 */
@Composable
fun OimeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    val miuix = com.azime.input.core.theme.KeyboardTheme.isMiuix()

    val plainGray = when {
        miuix && dark -> Color(0xFF2C2C2E)
        miuix -> Color(0xFFFFFFFF)
        dark -> Color(0xFF26262A)
        else -> Color(0xFFF1F1F2)
    }
    val grayHigh = when {
        miuix && dark -> Color(0xFF333335)
        miuix -> Color(0xFFF7F8FA)
        dark -> Color(0xFF2C2C31)
        else -> Color(0xFFEAEAEC)
    }
    val grayHighest = when {
        miuix && dark -> Color(0xFF3A3A3C)
        miuix -> Color(0xFFE8EAED)
        dark -> Color(0xFF323238)
        else -> Color(0xFFE4E4E7)
    }
    val bg = when {
        miuix && dark -> Color(0xFF191919)
        miuix -> Color(0xFFF2F3F5)
        dark -> Color(0xFF1B1B1F)
        else -> Color(0xFFFFFFFF)
    }

    MaterialTheme(
        colorScheme = scheme.copy(
            background = bg,
            surface = bg,
            surfaceVariant = plainGray,
            surfaceContainerLowest = bg,
            surfaceContainerLow = plainGray,
            surfaceContainer = plainGray,
            surfaceContainerHigh = grayHigh,
            surfaceContainerHighest = grayHighest,
            secondaryContainer = grayHigh,
            tertiaryContainer = grayHigh,
            primary = com.azime.input.ui.keyboard.keyboardAccentActiveColor(dark),
            primaryContainer = com.azime.input.ui.keyboard.keyboardAccentKeyColor(dark),
            onPrimaryContainer = if (dark) Color(0xFFD7E3F4) else Color(0xFF202124),
        ),
        content = content,
    )
}

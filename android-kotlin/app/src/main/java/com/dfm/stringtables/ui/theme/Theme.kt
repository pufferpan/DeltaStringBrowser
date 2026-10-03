package com.dfm.stringtables.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/*
 * 主题：Material 3 Expressive · Blue 系 · 浅色 / 深色两套方案（跟随系统 / 浅色 / 深色）。
 * UI 中所有颜色一律通过下面这些“角色”引用，不直接写死色值 —— 因此切换方案即可全应用变色。
 */

// —— Blue 系浅色方案（规格给出主色；互补角色按同一蓝紫调和取色）——
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),

    secondary = Color(0xFF5A5C7C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF131C2B),

    tertiary = Color(0xFF90506F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EE),
    onTertiaryContainer = Color(0xFF2E1125),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E2E6),
    onSurfaceVariant = Color(0xFF44474E),

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEEEDF3),
    surfaceContainerHigh = Color(0xFFE9E8EF),
    surfaceContainerHighest = Color(0xFFE3E2E6),

    surfaceBright = Color(0xFFFAF9FD),
    surfaceDim = Color(0xFFDAD8E0),

    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFFA8C7FA),
    scrim = Color(0xFF000000),
)

// —— Blue 系深色方案（与 LightColors 角色一一对应：同一色系，亮主色 + 深色底）——
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF06305E),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),

    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293042),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),

    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF55203B),
    tertiaryContainer = Color(0xFF6E3B52),
    onTertiaryContainer = Color(0xFFFFD8EE),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),

    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),

    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF181C21),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),

    surfaceBright = Color(0xFF36393F),
    surfaceDim = Color(0xFF101418),

    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3134),
    inversePrimary = Color(0xFF0B57D0),
    scrim = Color(0xFF000000),
)

/*
 * 形状：克制圆角，不采用胶囊。
 * 额外说明：组件处显式指定（卡片 8dp、按钮 12dp、标签片 8dp、对话框 12dp）。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// 命名形状：克制圆角，避免胶囊。
val Shape4 = RoundedCornerShape(4.dp)
val Shape8 = RoundedCornerShape(8.dp)
val Shape12 = RoundedCornerShape(12.dp)
val Shape16 = RoundedCornerShape(16.dp)

/**
 * 非 Compose 版本的解析：按模式 + 系统深浅色决定最终呈现是否为深色。
 * （Activity onCreate 里设置系统栏样式时会用到。）
 */
fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * 当前模式是否呈现为深色。
 * SYSTEM 取系统外观，LIGHT/DARK 强制对应方案。
 */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = resolveDarkTheme(mode, isSystemInDarkTheme())

/**
 * 应用主题：按 [mode] 选择浅色 / 深色配色（默认跟随系统）。
 * 排版使用 M3 默认（Roboto）。
 */
@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}

/*
 * 动效：遵循 Material 3 Expressive 的 MotionScheme.standard() 令牌
 * （standard: 300ms 位移/缩放, 260ms 透明度/颜色, FastOutSlowIn / 强调曲线）。
 * 说明：material3 1.4.0 稳定版把 MotionScheme 标为 internal（尚未公开），
 * 故应用内按同一令牌实现：转场/按压反馈见 App.kt 与 Common.kt。
 */

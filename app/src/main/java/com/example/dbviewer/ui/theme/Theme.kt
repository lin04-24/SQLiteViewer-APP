package com.example.dbviewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

interface DbColorPalette {
    val Background: Color
    val Surface: Color
    val SurfaceElevated: Color
    val SurfaceHigh: Color
    val SurfaceHighest: Color
    val Divider: Color
    val Border: Color
    val Accent: Color
    val AccentSoft: Color
    val TextPrimary: Color
    val TextSecondary: Color
    val TextMuted: Color
    val Null: Color
    val Number: Color
    val Key: Color
    val Error: Color
    val Success: Color
}

/** Palette tuned to look like a dark database console: near-black canvas, quiet borders, one accent. */
object DbColors {
    private var mode = ThemeMode.DARK

    fun setMode(value: ThemeMode) { mode = value }

    val Background get() = palette(mode).Background
    val Surface get() = palette(mode).Surface
    val SurfaceElevated get() = palette(mode).SurfaceElevated
    val SurfaceHigh get() = palette(mode).SurfaceHigh
    val SurfaceHighest get() = palette(mode).SurfaceHighest
    val Divider get() = palette(mode).Divider
    val Border get() = palette(mode).Border
    val Accent get() = palette(mode).Accent
    val AccentSoft get() = palette(mode).AccentSoft
    val TextPrimary get() = palette(mode).TextPrimary
    val TextSecondary get() = palette(mode).TextSecondary
    val TextMuted get() = palette(mode).TextMuted
    val Null get() = palette(mode).Null
    val Number get() = palette(mode).Number
    val Key get() = palette(mode).Key
    val Error get() = palette(mode).Error
    val Success get() = palette(mode).Success

    private fun palette(themeMode: ThemeMode): DbColorPalette = when (themeMode) {
        ThemeMode.LIGHT -> DbLightColors
        ThemeMode.HIGH_CONTRAST -> DbHighContrastColors
        else -> DarkColors
    }
}

private object DarkColors : DbColorPalette {
    override val Background = Color(0xFF0F0F0F)
    override val Surface = Color(0xFF15171A)
    override val SurfaceElevated = Color(0xFF1A1D21)
    override val SurfaceHigh = Color(0xFF22262B)
    override val SurfaceHighest = Color(0xFF2A2F35)
    override val Divider = Color(0xFF25292E)
    override val Border = Color(0xFF30353B)
    override val Accent = Color(0xFF4C8DFF)
    override val AccentSoft = Color(0xFF1C2740)
    override val TextPrimary = Color(0xFFEDEEF0)
    override val TextSecondary = Color(0xFF9BA1A6)
    override val TextMuted = Color(0xFF6B7075)
    override val Null = Color(0xFF6B7075)
    override val Number = Color(0xFF7FD1B9)
    override val Key = Color(0xFFE3B341)
    override val Error = Color(0xFFFF6B6B)
    override val Success = Color(0xFF3ECF8E)
}

enum class ThemeMode {
    DARK, LIGHT, HIGH_CONTRAST, SYSTEM
}

@Composable
fun DbViewerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val resolvedMode = if (themeMode == ThemeMode.SYSTEM) {
        if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT
    } else {
        themeMode
    }
    DbColors.setMode(resolvedMode)
    val scheme = when (resolvedMode) {
        ThemeMode.LIGHT -> lightColorScheme(
            primary = DbLightColors.Accent,
            onPrimary = Color.White,
            primaryContainer = DbLightColors.AccentSoft,
            onPrimaryContainer = Color(0xFF0D47A1),
            secondary = DbLightColors.Success,
            onSecondary = Color.White,
            tertiary = DbLightColors.Key,
            background = DbLightColors.Background,
            onBackground = DbLightColors.TextPrimary,
            surface = DbLightColors.Surface,
            onSurface = DbLightColors.TextPrimary,
            surfaceVariant = DbLightColors.SurfaceElevated,
            onSurfaceVariant = DbLightColors.TextSecondary,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = DbLightColors.Surface,
            surfaceContainer = DbLightColors.SurfaceElevated,
            surfaceContainerHigh = DbLightColors.SurfaceHigh,
            surfaceContainerHighest = DbLightColors.SurfaceHighest,
            outline = DbLightColors.Border,
            outlineVariant = DbLightColors.Divider,
            error = DbLightColors.Error,
            onError = Color.White,
            errorContainer = Color(0xFFFFEBEE),
            onErrorContainer = Color(0xFFB71C1C),
            inverseSurface = DbLightColors.TextPrimary,
            inverseOnSurface = DbLightColors.Background,
            inversePrimary = Color(0xFF90CAF9),
            scrim = Color(0x80000000),
        )
        ThemeMode.HIGH_CONTRAST -> darkColorScheme(
            primary = DbHighContrastColors.Accent,
            onPrimary = Color(0xFF000000),
            primaryContainer = DbHighContrastColors.AccentSoft,
            onPrimaryContainer = Color(0xFFBBDEFB),
            secondary = DbHighContrastColors.Success,
            onSecondary = Color(0xFF000000),
            tertiary = DbHighContrastColors.Key,
            background = DbHighContrastColors.Background,
            onBackground = DbHighContrastColors.TextPrimary,
            surface = DbHighContrastColors.Surface,
            onSurface = DbHighContrastColors.TextPrimary,
            surfaceVariant = DbHighContrastColors.SurfaceElevated,
            onSurfaceVariant = DbHighContrastColors.TextSecondary,
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = DbHighContrastColors.Surface,
            surfaceContainer = DbHighContrastColors.SurfaceElevated,
            surfaceContainerHigh = DbHighContrastColors.SurfaceHigh,
            surfaceContainerHighest = DbHighContrastColors.SurfaceHighest,
            outline = DbHighContrastColors.Border,
            outlineVariant = DbHighContrastColors.Divider,
            error = DbHighContrastColors.Error,
            onError = Color(0xFF000000),
            errorContainer = Color(0xFF5D0000),
            onErrorContainer = Color(0xFFFFDAD6),
            inverseSurface = DbHighContrastColors.TextPrimary,
            inverseOnSurface = DbHighContrastColors.Background,
            inversePrimary = Color(0xFF0D47A1),
            scrim = Color(0xDD000000),
        )
        else -> darkColorScheme(
            primary = DbColors.Accent,
            onPrimary = Color(0xFF08101F),
            primaryContainer = DbColors.AccentSoft,
            onPrimaryContainer = Color(0xFFD6E4FF),
            secondary = DbColors.Success,
            onSecondary = Color(0xFF04160E),
            tertiary = DbColors.Key,
            background = DbColors.Background,
            onBackground = DbColors.TextPrimary,
            surface = DbColors.Surface,
            onSurface = DbColors.TextPrimary,
            surfaceVariant = DbColors.SurfaceElevated,
            onSurfaceVariant = DbColors.TextSecondary,
            surfaceContainerLowest = Color(0xFF0C0D0F),
            surfaceContainerLow = DbColors.Surface,
            surfaceContainer = DbColors.SurfaceElevated,
            surfaceContainerHigh = DbColors.SurfaceHigh,
            surfaceContainerHighest = DbColors.SurfaceHighest,
            outline = DbColors.Border,
            outlineVariant = DbColors.Divider,
            error = DbColors.Error,
            onError = Color(0xFF2A0606),
            errorContainer = Color(0xFF3A1414),
            onErrorContainer = Color(0xFFFFD6D6),
            inverseSurface = DbColors.TextPrimary,
            inverseOnSurface = DbColors.Background,
            inversePrimary = Color(0xFF1B4FA8),
            scrim = Color(0xCC000000),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Get current theme colors based on mode
 */
fun getThemeColors(themeMode: ThemeMode): Any {
    return when (themeMode) {
        ThemeMode.LIGHT -> object {
            val Background = DbLightColors.Background
            val Surface = DbLightColors.Surface
            val SurfaceElevated = DbLightColors.SurfaceElevated
            val SurfaceHigh = DbLightColors.SurfaceHigh
            val SurfaceHighest = DbLightColors.SurfaceHighest
            val Divider = DbLightColors.Divider
            val Border = DbLightColors.Border
            val Accent = DbLightColors.Accent
            val AccentSoft = DbLightColors.AccentSoft
            val TextPrimary = DbLightColors.TextPrimary
            val TextSecondary = DbLightColors.TextSecondary
            val TextMuted = DbLightColors.TextMuted
            val Null = DbLightColors.Null
            val Number = DbLightColors.Number
            val Key = DbLightColors.Key
            val Error = DbLightColors.Error
            val Success = DbLightColors.Success
        }
        ThemeMode.HIGH_CONTRAST -> object {
            val Background = DbHighContrastColors.Background
            val Surface = DbHighContrastColors.Surface
            val SurfaceElevated = DbHighContrastColors.SurfaceElevated
            val SurfaceHigh = DbHighContrastColors.SurfaceHigh
            val SurfaceHighest = DbHighContrastColors.SurfaceHighest
            val Divider = DbHighContrastColors.Divider
            val Border = DbHighContrastColors.Border
            val Accent = DbHighContrastColors.Accent
            val AccentSoft = DbHighContrastColors.AccentSoft
            val TextPrimary = DbHighContrastColors.TextPrimary
            val TextSecondary = DbHighContrastColors.TextSecondary
            val TextMuted = DbHighContrastColors.TextMuted
            val Null = DbHighContrastColors.Null
            val Number = DbHighContrastColors.Number
            val Key = DbHighContrastColors.Key
            val Error = DbHighContrastColors.Error
            val Success = DbHighContrastColors.Success
        }
        else -> DbColors
    }
}

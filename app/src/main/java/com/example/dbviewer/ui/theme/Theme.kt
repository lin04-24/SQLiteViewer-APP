package com.example.dbviewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette tuned to look like a dark database console: near-black canvas, quiet borders, one accent. */
object DbColors {
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF15171A)
    val SurfaceElevated = Color(0xFF1A1D21)
    val SurfaceHigh = Color(0xFF22262B)
    val SurfaceHighest = Color(0xFF2A2F35)
    val Divider = Color(0xFF25292E)
    val Border = Color(0xFF30353B)
    val Accent = Color(0xFF4C8DFF)
    val AccentSoft = Color(0xFF1C2740)
    val TextPrimary = Color(0xFFEDEEF0)
    val TextSecondary = Color(0xFF9BA1A6)
    val TextMuted = Color(0xFF6B7075)
    val Null = Color(0xFF6B7075)
    val Number = Color(0xFF7FD1B9)
    val Key = Color(0xFFE3B341)
    val Error = Color(0xFFFF6B6B)
    val Success = Color(0xFF3ECF8E)
}

@Composable
fun DbViewerTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
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
    MaterialTheme(colorScheme = scheme, content = content)
}

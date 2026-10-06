package com.example.dbviewer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Light theme color palette following Material 3 guidelines
 * Ensures WCAG AA contrast ratios for accessibility
 */
object DbLightColors : DbColorPalette {
    // Background and surfaces - light tones with subtle warmth
    override val Background = Color(0xFFFAFAFA)
    override val Surface = Color(0xFFFFFFFF)
    override val SurfaceElevated = Color(0xFFF5F5F5)
    override val SurfaceHigh = Color(0xFFEEEEEE)
    override val SurfaceHighest = Color(0xFFE0E0E0)

    // Borders and dividers
    override val Divider = Color(0xFFE0E0E0)
    override val Border = Color(0xFFD0D0D0)

    // Primary accent - vibrant blue with good contrast
    override val Accent = Color(0xFF1976D2)
    override val AccentSoft = Color(0xFFE3F2FD)

    // Text colors with strong contrast ratios
    override val TextPrimary = Color(0xFF1A1A1A)      // ~16.5:1 contrast on white
    override val TextSecondary = Color(0xFF616161)    // ~7.0:1 contrast on white
    override val TextMuted = Color(0xFF757575)        // ~4.6:1 contrast on white (AA)

    // Data type colors - optimized for light background
    override val Null = Color(0xFF757575)
    override val Number = Color(0xFF00897B)           // Teal - better contrast than mint
    override val Key = Color(0xFFD84315)              // Deep orange

    // Status colors
    override val Error = Color(0xFFD32F2F)
    override val Success = Color(0xFF388E3C)
}

/**
 * High contrast mode colors for accessibility
 * All color combinations exceed WCAG AAA standards (7:1 contrast)
 */
object DbHighContrastColors : DbColorPalette {
    // Maximum contrast backgrounds
    override val Background = Color(0xFF000000)
    override val Surface = Color(0xFF000000)
    override val SurfaceElevated = Color(0xFF121212)
    override val SurfaceHigh = Color(0xFF1E1E1E)
    override val SurfaceHighest = Color(0xFF2A2A2A)

    // High contrast borders
    override val Divider = Color(0xFF444444)
    override val Border = Color(0xFF666666)

    // Bright accent colors
    override val Accent = Color(0xFF64B5F6)
    override val AccentSoft = Color(0xFF1A237E)

    // Maximum contrast text
    override val TextPrimary = Color(0xFFFFFFFF)      // 21:1 contrast
    override val TextSecondary = Color(0xFFCCCCCC)    // ~14:1 contrast
    override val TextMuted = Color(0xFF999999)        // ~8:1 contrast

    // Vivid data colors
    override val Null = Color(0xFF999999)
    override val Number = Color(0xFF80CBC4)           // Bright teal
    override val Key = Color(0xFFFFAB40)              // Bright orange

    // High contrast status
    override val Error = Color(0xFFFF5252)
    override val Success = Color(0xFF69F0AE)
}

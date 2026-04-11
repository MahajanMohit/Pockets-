package com.zendeck.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Brand / semantic colors (theme-invariant) ─────────────────────────────────
val AccentTeal       = Color(0xFF00897B)
val AccentTealDim    = Color(0xFF00695C)
val SlateGray        = Color(0xFF607D8B)

// TTL urgency — same in both themes
val UrgencyFresh     = Color(0xFF1B5E20)   // Green 900
val UrgencyWarning   = Color(0xFF00695C)   // Teal 700
val UrgencyCritical  = Color(0xFF4A148C)   // Purple 900

// ── Theme-aware palette ───────────────────────────────────────────────────────
data class ZenDeckColors(
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val divider: Color,
    val slateGrayLight: Color,
)

/**
 * Dark theme — pure OLED black base, comfortable for reading in low light.
 * Cards float on a very dark surface with subtle borders.
 */
fun darkZenDeckColors() = ZenDeckColors(
    background     = Color(0xFF000000),
    surface        = Color(0xFF0D0D0D),
    cardBackground = Color(0xFF141414),
    cardBorder     = Color(0xFF262626),
    textPrimary    = Color(0xFFE8EAED),
    textSecondary  = Color(0xFF8A9BA8),
    textDisabled   = Color(0xFF4A5568),
    divider        = Color(0xFF1A1A1A),
    slateGrayLight = Color(0xFF8A9BA8),
)

/**
 * Light theme — clean, reader-friendly.
 * Inspired by read-later apps: white cards on a soft neutral background,
 * dark charcoal text for contrast without harshness.
 */
fun lightZenDeckColors() = ZenDeckColors(
    background     = Color(0xFFF2F2F7),   // iOS-style system gray 6
    surface        = Color(0xFFE5E5EA),   // iOS system gray 5
    cardBackground = Color(0xFFFFFFFF),   // pure white cards
    cardBorder     = Color(0xFFD1D1D6),   // subtle border
    textPrimary    = Color(0xFF1C1C1E),   // near-black (iOS label)
    textSecondary  = Color(0xFF6E6E73),   // iOS secondary label
    textDisabled   = Color(0xFFAEAEB2),   // iOS tertiary label
    divider        = Color(0xFFE0E0E5),
    slateGrayLight = Color(0xFF6E6E73),
)

val LocalZenDeckColors = compositionLocalOf { darkZenDeckColors() }

// ── Legacy top-level aliases (referenced in non-theming contexts) ─────────────
val OledBlack         = Color(0xFF000000)
val SurfaceDark       = Color(0xFF0D0D0D)
val CardBackground    = Color(0xFF141414)
val CardBorderDefault = Color(0xFF262626)
val TextPrimary       = Color(0xFFE8EAED)
val TextSecondary     = Color(0xFF8A9BA8)
val TextDisabled      = Color(0xFF4A5568)
val DividerColor      = Color(0xFF1A1A1A)
val SlateGrayLight    = Color(0xFF8A9BA8)

package com.zendeck.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Brand / semantic colors (theme-invariant) ─────────────────────────────────
// Brand accent — vivid emerald. (Name kept from the original teal for
// compatibility with existing call-sites.)
val AccentTeal       = Color(0xFF10C98D)
val AccentTealDim    = Color(0xFF0B9268)
val AccentViolet     = Color(0xFF8B7CF6)
val SlateGray        = Color(0xFF667085)

// TTL urgency ramp: fresh emerald → fading neutral → critical violet.
// Fresh items glow, mid-life items recede, about-to-expire items pop.
val UrgencyFresh     = Color(0xFF1F9D6E)
val UrgencyWarning   = Color(0xFF5D6675)
val UrgencyCritical  = Color(0xFF8B5CF6)

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
 * Dark theme — pure OLED black base with blue-tinted charcoal surfaces
 * (cool-toned darks read as "premium"; flat greys read as washed out).
 */
fun darkZenDeckColors() = ZenDeckColors(
    background     = Color(0xFF000000),
    surface        = Color(0xFF0E1013),
    cardBackground = Color(0xFF12151A),
    cardBorder     = Color(0xFF20242C),
    textPrimary    = Color(0xFFF2F4F7),
    textSecondary  = Color(0xFF98A2B3),
    textDisabled   = Color(0xFF5D6675),
    divider        = Color(0xFF181C22),
    slateGrayLight = Color(0xFF98A2B3),
)

/**
 * Light theme — crisp white cards on a cool near-white canvas,
 * ink-dark text (cool gray ramp, not warm iOS grays).
 */
fun lightZenDeckColors() = ZenDeckColors(
    background     = Color(0xFFF7F8FA),
    surface        = Color(0xFFEEF0F3),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder     = Color(0xFFE4E7EC),
    textPrimary    = Color(0xFF101828),
    textSecondary  = Color(0xFF667085),
    textDisabled   = Color(0xFF98A2B3),
    divider        = Color(0xFFEAECF0),
    slateGrayLight = Color(0xFF667085),
)

val LocalZenDeckColors = compositionLocalOf { darkZenDeckColors() }

// ── Legacy top-level aliases (referenced in non-theming contexts) ─────────────
val OledBlack         = Color(0xFF000000)
val SurfaceDark       = Color(0xFF0E1013)
val CardBackground    = Color(0xFF12151A)
val CardBorderDefault = Color(0xFF20242C)
val TextPrimary       = Color(0xFFF2F4F7)
val TextSecondary     = Color(0xFF98A2B3)
val TextDisabled      = Color(0xFF5D6675)
val DividerColor      = Color(0xFF181C22)
val SlateGrayLight    = Color(0xFF98A2B3)

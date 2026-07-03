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
    /** Solid surface for bottom sheets & dialogs — glass panels don't work as overlays. */
    val sheetBackground: Color,
    /** Translucent bottom-nav fill so the ambient glow shows through. */
    val navBackground: Color,
)

/**
 * Dark glass theme — OLED black canvas with ambient emerald/violet glows
 * (drawn by GlassBackground). Panels are translucent white so they pick up
 * the glow behind them like frosted glass.
 */
fun darkZenDeckColors() = ZenDeckColors(
    background      = Color(0xFF000000),
    surface         = Color(0x0FFFFFFF),   // 6% white — search field, chips
    cardBackground  = Color(0x14FFFFFF),   // 8% white — glass card fill
    cardBorder      = Color(0x21FFFFFF),   // 13% white — glass edge
    textPrimary     = Color(0xFFF2F4F7),
    textSecondary   = Color(0xFF9AA4B2),
    textDisabled    = Color(0xFF5D6675),
    divider         = Color(0x14FFFFFF),
    slateGrayLight  = Color(0xFF9AA4B2),
    sheetBackground = Color(0xFF15181F),
    navBackground   = Color(0xB3000000),   // 70% black — glow bleeds through
)

/**
 * Light glass theme — soft pastel glows on a cool near-white canvas,
 * milky white panels with bright edges.
 */
fun lightZenDeckColors() = ZenDeckColors(
    background      = Color(0xFFF4F6F9),
    surface         = Color(0x99FFFFFF),   // 60% white
    cardBackground  = Color(0xCCFFFFFF),   // 80% white — milk glass
    cardBorder      = Color(0xE6FFFFFF),   // bright glass edge
    textPrimary     = Color(0xFF101828),
    textSecondary   = Color(0xFF667085),
    textDisabled    = Color(0xFF98A2B3),
    divider         = Color(0x1F101828),
    slateGrayLight  = Color(0xFF667085),
    sheetBackground = Color(0xFFFFFFFF),
    navBackground   = Color(0xD9FFFFFF),   // 85% white
)

val LocalZenDeckColors = compositionLocalOf { darkZenDeckColors() }

// ── Legacy top-level aliases (referenced in non-theming contexts, e.g. widget) ─
val OledBlack         = Color(0xFF000000)
val SurfaceDark       = Color(0xFF0E1013)
val CardBackground    = Color(0xFF12151A)
val CardBorderDefault = Color(0xFF20242C)
val TextPrimary       = Color(0xFFF2F4F7)
val TextSecondary     = Color(0xFF9AA4B2)
val TextDisabled      = Color(0xFF5D6675)
val DividerColor      = Color(0xFF181C22)
val SlateGrayLight    = Color(0xFF9AA4B2)

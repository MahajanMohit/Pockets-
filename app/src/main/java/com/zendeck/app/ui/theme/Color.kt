package com.zendeck.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Invariant brand / semantic colors ────────────────────────────────────────
val AccentTeal = Color(0xFF00897B)
val AccentTealDim = Color(0xFF00695C)
val SlateGray = Color(0xFF607D8B)

// TTL Urgency colors (same in both themes)
val UrgencyFresh = Color(0xFF81C784)     // soft sage green  (Material Green 300)
val UrgencyWarning = Color(0xFFFFB74D)   // warm sandy amber (Material Orange 300)
val UrgencyCritical = Color(0xFFE57373)  // dusty rose red   (Material Red 300)

val SwipeGreenBackground = Color(0xFF1B5E20)
val SwipeRedBackground = Color(0xFFB71C1C)

// ── Theme-aware color palette ─────────────────────────────────────────────────
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

fun darkZenDeckColors() = ZenDeckColors(
    background    = Color(0xFF000000),
    surface       = Color(0xFF0D0D0D),
    cardBackground = Color(0xFF1A1A1A),
    cardBorder    = Color(0xFF2A2A2A),
    textPrimary   = Color(0xFFECEFF1),
    textSecondary = Color(0xFF90A4AE),
    textDisabled  = Color(0xFF546E7A),
    divider       = Color(0xFF1E1E1E),
    slateGrayLight = Color(0xFF90A4AE),
)

fun lightZenDeckColors() = ZenDeckColors(
    background    = Color(0xFFFAF7F2),   // warm cream
    surface       = Color(0xFFEEE9E0),   // warm off-white
    cardBackground = Color(0xFFFFFFFF),  // white cards float on cream
    cardBorder    = Color(0xFFB2DFDB),   // light teal (Material Teal 100)
    textPrimary   = Color(0xFF1C2B2A),   // dark teal-tinted
    textSecondary = Color(0xFF546E7A),
    textDisabled  = Color(0xFF90A4AE),
    divider       = Color(0xFFD6CFC6),   // warm gray
    slateGrayLight = Color(0xFF546E7A),
)

val LocalZenDeckColors = compositionLocalOf { darkZenDeckColors() }

// Legacy top-level aliases kept for non-theming uses (urgency colors, etc.)
val OledBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF0D0D0D)
val CardBackground = Color(0xFF1A1A1A)
val CardBorderDefault = Color(0xFF2A2A2A)
val TextPrimary = Color(0xFFECEFF1)
val TextSecondary = Color(0xFF90A4AE)
val TextDisabled = Color(0xFF546E7A)
val DividerColor = Color(0xFF1E1E1E)
val SlateGrayLight = Color(0xFF90A4AE)

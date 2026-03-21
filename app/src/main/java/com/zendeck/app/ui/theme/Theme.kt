package com.zendeck.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ZenDeckColorScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = OledBlack,
    primaryContainer = AccentTealDim,
    onPrimaryContainer = TextPrimary,
    secondary = SlateGray,
    onSecondary = TextPrimary,
    secondaryContainer = CardBackground,
    onSecondaryContainer = TextSecondary,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderDefault,
    error = UrgencyCritical,
    onError = TextPrimary
)

@Composable
fun ZenDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZenDeckColorScheme,
        typography = ZenDeckTypography,
        content = content
    )
}

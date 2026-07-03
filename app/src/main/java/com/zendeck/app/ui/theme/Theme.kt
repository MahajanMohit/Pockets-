package com.zendeck.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = Color(0xFF00281B),
    primaryContainer = AccentTealDim,
    onPrimaryContainer = Color(0xFFEAFBF4),
    secondary = SlateGray,
    onSecondary = Color(0xFFF2F4F7),
    secondaryContainer = Color(0xFF181C22),
    onSecondaryContainer = Color(0xFF98A2B3),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF0E1013),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF181C22),
    onSurfaceVariant = Color(0xFF98A2B3),
    outline = Color(0xFF20242C),
    error = UrgencyCritical,
    onError = Color(0xFFF2F4F7)
)

private val LightColorScheme = lightColorScheme(
    primary = AccentTealDim,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAE9),
    onPrimaryContainer = Color(0xFF064E38),
    secondary = SlateGray,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF0F3),
    onSecondaryContainer = Color(0xFF667085),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFEEF0F3),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFE4E7EC),
    onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFE4E7EC),
    error = UrgencyCritical,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun ZenDeckTheme(
    useDarkTheme: Boolean = true,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val zenDeckColors = if (useDarkTheme) darkZenDeckColors() else lightZenDeckColors()
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalZenDeckColors provides zenDeckColors,
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScale
        )
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
            typography = ZenDeckTypography,
            content = content
        )
    }
}

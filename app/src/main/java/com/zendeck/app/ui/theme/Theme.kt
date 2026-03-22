package com.zendeck.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = Color(0xFF000000),
    primaryContainer = AccentTealDim,
    onPrimaryContainer = Color(0xFFECEFF1),
    secondary = SlateGray,
    onSecondary = Color(0xFFECEFF1),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFF90A4AE),
    background = Color(0xFF000000),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF90A4AE),
    outline = Color(0xFF2A2A2A),
    error = UrgencyCritical,
    onError = Color(0xFFECEFF1)
)

private val LightColorScheme = lightColorScheme(
    primary = AccentTeal,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF1C2B2A),
    secondary = SlateGray,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEFF1),
    onSecondaryContainer = Color(0xFF546E7A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C2B2A),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF1C2B2A),
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFF546E7A),
    outline = Color(0xFFDDE3E7),
    error = UrgencyCritical,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun ZenDeckTheme(useDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    val zenDeckColors = if (useDarkTheme) darkZenDeckColors() else lightZenDeckColors()
    CompositionLocalProvider(LocalZenDeckColors provides zenDeckColors) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
            typography = ZenDeckTypography,
            content = content
        )
    }
}

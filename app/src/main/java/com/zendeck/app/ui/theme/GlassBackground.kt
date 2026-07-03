package com.zendeck.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The app-wide canvas for the glass theme: a dark (or near-white) base with
 * large, soft radial colour glows. Every panel above it is translucent, so
 * the glow bleeding through is what creates the frosted-glass depth.
 *
 * Draw this once behind a transparent Scaffold — it is a single static layer,
 * so it costs nothing during scroll.
 */
@Composable
fun GlassBackground(darkTheme: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Base canvas
                drawRect(if (darkTheme) Color(0xFF000000) else Color(0xFFF4F6F9))

                val emeraldAlpha = if (darkTheme) 0.16f else 0.30f
                val violetAlpha  = if (darkTheme) 0.14f else 0.26f
                val tealAlpha    = if (darkTheme) 0.07f else 0.14f

                // Emerald glow — upper left
                val c1 = Offset(size.width * 0.12f, size.height * 0.02f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(AccentTeal.copy(alpha = emeraldAlpha), Color.Transparent),
                        center = c1,
                        radius = size.width * 0.95f
                    )
                )

                // Violet glow — lower right
                val c2 = Offset(size.width * 0.95f, size.height * 0.92f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(AccentViolet.copy(alpha = violetAlpha), Color.Transparent),
                        center = c2,
                        radius = size.width * 1.0f
                    )
                )

                // Faint teal fill — middle right, ties the two glows together
                val c3 = Offset(size.width * 1.05f, size.height * 0.35f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(AccentTealDim.copy(alpha = tealAlpha), Color.Transparent),
                        center = c3,
                        radius = size.width * 0.8f
                    )
                )
            }
    )
}

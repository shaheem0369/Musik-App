/*
 * Musik - by Nikhil
 * Licensed Under GPL-3.0
 *
 * Liquid Glass design primitives: translucent surfaces with a solid specular rim and
 * a flat top highlight. Gradient-free by design: every fill is a single flat colour.
 */

package com.shaheem.yt.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shaheem.yt.constants.LiquidGlassIntensityKey
import com.shaheem.yt.constants.LiquidGlassKey
import com.shaheem.yt.utils.rememberPreference

/** True when the user has the Liquid Glass style enabled (default on). */
@Composable
fun rememberLiquidGlassEnabled(): Boolean {
    val enabled by rememberPreference(LiquidGlassKey, defaultValue = true)
    return enabled
}

/** Glass strength in 0.3..1.0 (default 0.7). */
@Composable
fun rememberLiquidGlassIntensity(): Float {
    val intensity by rememberPreference(LiquidGlassIntensityKey, defaultValue = 0.7f)
    return intensity.coerceIn(0.3f, 1f)
}

/** Specular rim: one solid hairline. Brighter on light themes, subtler on dark ones. */
fun Modifier.glassRim(
    shape: Shape,
    isDark: Boolean,
    intensity: Float = 0.7f,
    width: Dp = 1.dp,
): Modifier {
    val alpha = (if (isDark) 0.28f else 0.75f) * intensity
    return this.border(width = width, color = Color.White.copy(alpha = alpha), shape = shape)
}

/**
 * Frosted translucent surface: flat translucent fill, a solid hairline rim and a 1dp flat
 * highlight along the top edge. No gradients. Works on every API level and is cheap enough
 * for list cards.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    isDark: Boolean,
    intensity: Float = 0.7f,
    tint: Color = Color.Unspecified,
): Modifier {
    val base = if (tint.isSpecified) tint else Color.White
    val fill = base.copy(alpha = (if (isDark) 0.10f else 0.42f) * intensity)
    val highlight = Color.White.copy(alpha = (if (isDark) 0.30f else 0.70f) * intensity)
    return this
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawContent()
            val inset = size.minDimension * 0.18f
            drawLine(
                color = highlight,
                start = Offset(inset, 0.5.dp.toPx()),
                end = Offset(size.width - inset, 0.5.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
        .glassRim(shape, isDark, intensity)
}

/** A card that follows the user's Liquid Glass setting; falls back to a plain tonal card when off. */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val enabled = rememberLiquidGlassEnabled()
    val intensity = rememberLiquidGlassIntensity()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier.then(
            if (enabled) Modifier.liquidGlass(shape, isDark, intensity)
            else Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ),
        content = content,
    )
}

/**
 * Flat tinted backdrop for glass surfaces. Kept as a single solid colour (the theme background
 * with a light wash of [accent]) so there are no gradients; the name is kept for call sites.
 */
@Composable
fun AuroraBackground(
    accent: Color,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") secondary: Color = MaterialTheme.colorScheme.tertiary,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wash = accent.copy(alpha = if (isDark) 0.16f else 0.12f)
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .background(wash)
    )
}

/*
 * Musik - by Nikhil
 * Licensed Under GPL-3.0
 *
 * AI Music Assistant — "Ask AI DJ" prompt bar.
 * Dribbble reference: smrubel2724 — glass pill with ✨ sparkle icon.
 */

package com.shaheem.yt.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shaheem.yt.R
// auto_awesome is the sparkle / AI icon present in the drawable set
import com.shaheem.yt.ui.theme.AiMusicColors

/**
 * Glass pill "Ask AI DJ …" bar placed at the bottom of the Now Playing screen.
 *
 * Tapping [onClick] is a no-op in this release; the callback is wired up ready
 * for a future AI chat integration.
 */
@Composable
fun AiDjBar(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "aiBarScale",
    )

    val pillShape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(52.dp)
            .clip(pillShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = AiMusicColors.NeonViolet.copy(alpha = 0.35f),
                shape = pillShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // auto_awesome = sparkle / AI icon — tinted violet
            Icon(
                painter = painterResource(R.drawable.auto_awesome),
                contentDescription = null,
                tint = AiMusicColors.NeonViolet,
                modifier = Modifier.size(18.dp),
            )

            Text(
                text = "Ask AI DJ…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 15.sp,
            )
        }
    }
}

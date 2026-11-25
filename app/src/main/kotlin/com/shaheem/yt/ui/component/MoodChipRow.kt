/*
 * Musik - by Nikhil
 * Licensed Under GPL-3.0
 *
 * AI Music Assistant — horizontal mood/vibe selector chip row.
 * Dribbble reference: smrubel2724 AI Music Assistant UI.
 */

package com.shaheem.yt.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shaheem.yt.ui.theme.AiMusicColors

/** A single mood with an emoji label and an internal key. */
data class MoodItem(
    val key: String,
    val emoji: String,
    val label: String,
)

/** Preset moods shown on the Home screen. */
val DefaultMoods = listOf(
    MoodItem("deep_focus",  "🎯", "Deep Focus"),
    MoodItem("late_night",  "🌙", "Late Night"),
    MoodItem("energized",   "⚡", "Energized"),
    MoodItem("chill_wave",  "🌊", "Chill Wave"),
    MoodItem("dramatic",    "🎭", "Dramatic"),
    MoodItem("happy",       "😊", "Happy"),
    MoodItem("melancholic", "🌧️", "Melancholic"),
)

/**
 * Horizontally scrollable row of glass pill mood chips.
 * The selected chip glows with neon violet; unselected ones are translucent glass.
 *
 * @param moods    List of moods to display (defaults to [DefaultMoods]).
 * @param selected Key of the currently selected mood, or null for none.
 * @param onMoodSelected Called when a mood chip is tapped; passes the [MoodItem] or null to deselect.
 * @param contentPadding Horizontal padding around the entire row.
 */
@Composable
fun MoodChipRow(
    modifier: Modifier = Modifier,
    moods: List<MoodItem> = DefaultMoods,
    selected: String? = null,
    onMoodSelected: (MoodItem?) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val scrollState = rememberScrollState()
    val chipShape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        moods.forEach { mood ->
            val isSelected = mood.key == selected
            MoodChip(
                mood = mood,
                selected = isSelected,
                shape = chipShape,
                onClick = {
                    // Tap again to deselect
                    onMoodSelected(if (isSelected) null else mood)
                },
            )
        }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun MoodChip(
    mood: MoodItem,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            pressed   -> 0.92f
            selected  -> 1.04f
            else      -> 1.00f
        },
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "moodChipScale",
    )

    val bgColor by animateColorAsState(
        targetValue = if (selected)
            AiMusicColors.NeonViolet.copy(alpha = 0.22f)
        else
            Color.White.copy(alpha = 0.07f),
        label = "moodChipBg",
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected)
            AiMusicColors.NeonViolet.copy(alpha = 0.70f)
        else
            Color.White.copy(alpha = 0.12f),
        label = "moodChipBorder",
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
        label = "moodChipText",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(38.dp)
            .clip(shape)
            .background(bgColor)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 0.dp),
    ) {
        Text(
            text = mood.emoji,
            fontSize = 15.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = mood.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

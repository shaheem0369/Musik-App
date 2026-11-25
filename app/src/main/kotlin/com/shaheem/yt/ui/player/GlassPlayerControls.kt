/*
 * Musik - by Nikhil
 * Licensed Under GPL-3.0
 *
 * Liquid Glass now-playing controls. Flat colours only (no gradients): a solid, art-tinted
 * backdrop is drawn by PlayerBackground(GLASS) and everything here is translucent glass on top.
 */

package com.shaheem.yt.ui.player

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.navigation.NavController
import com.shaheem.yt.R
import com.shaheem.yt.ui.theme.AiMusicColors
import com.shaheem.yt.constants.PlayerHorizontalPadding
import com.shaheem.yt.extensions.togglePlayPause
import com.shaheem.yt.extensions.toggleRepeatMode
import com.shaheem.yt.models.MediaMetadata
import com.shaheem.yt.playback.PlayerConnection
import com.shaheem.yt.ui.component.BottomSheetPageState
import com.shaheem.yt.ui.component.BottomSheetState
import com.shaheem.yt.ui.component.MenuState
import com.shaheem.yt.ui.component.MusikLoader
import com.shaheem.yt.ui.component.glassRim
import com.shaheem.yt.ui.component.rememberLiquidGlassIntensity
import com.shaheem.yt.ui.menu.PlayerMenu
import com.shaheem.yt.ui.utils.ShowMediaInfo
import com.shaheem.yt.utils.makeTimeString

/** Flat translucent glass surface: solid fill + solid hairline rim. No gradients. */
@Composable
private fun Modifier.glassSurface(shape: Shape, pressed: Boolean = false): Modifier {
    val intensity = rememberLiquidGlassIntensity()
    // AI Music style: slightly more opaque fill for premium glass depth
    val fill = Color.White.copy(alpha = (if (pressed) 0.38f else 0.18f) * (0.5f + intensity * 0.5f))
    return this
        .clip(shape)
        .background(fill)
        .glassRim(shape = shape, isDark = true, intensity = intensity, width = 1.dp)
}

/** Round glass button with a springy press and haptic tick. */
@Composable
private fun GlassIconButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    tint: Color = Color.White,
    shape: Shape = CircleShape,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "glassBtnScale",
    )
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassSurface(shape, pressed)
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Plain (un-backed) transport icon, as in the reference design, with a springy press. */
@Composable
private fun TransportButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "transportScale",
    )
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(iconSize + 28.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Drop-in replacement for the standard controls block when Liquid Glass is on:
 * title + artist, glass like / menu buttons, orange waveform seek bar with elapsed and total time,
 * large transport row and a glass row for shuffle / repeat / share.
 */
@Composable
fun GlassPlayerControls(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    repeatMode: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    clipboardManager: ClipboardManager,
    context: Context,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true
    val shuffle by playerConnection.shuffleModeEnabled.collectAsState()

    // Title / artist + glass actions
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(
                mediaMetadata = mediaMetadata,
                textBackgroundColor = Color.White,
                navController = navController,
                state = state,
                clipboardManager = clipboardManager,
                context = context,
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(
                iconRes = if (liked) R.drawable.favorite else R.drawable.favorite_border,
                description = if (liked) "Remove from favourites" else "Add to favourites",
                onClick = playerConnection::toggleLike,
            )
            GlassIconButton(
                iconRes = R.drawable.more_horiz,
                description = "More options",
                onClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = {
                                mediaMetadata.id.let { bottomSheetPageState.show { ShowMediaInfo(it) } }
                            },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    MusikSeekBar(
        position = position,
        duration = duration,
        sliderPosition = sliderPosition,
        onValueChange = onSliderValueChange,
        onValueChangeFinished = onSliderValueChangeFinished,
        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
    )

    // Elapsed time and total duration, matching the reference.
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 2.dp),
    ) {
        Text(
            text = makeTimeString(sliderPosition ?: position),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
        )
        Text(
            text = if (duration != C.TIME_UNSET && duration > 0L)
                makeTimeString(duration)
            else "",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }

    Spacer(Modifier.height(18.dp))

    // Transport
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding),
    ) {
        TransportButton(
            iconRes = R.drawable.skip_previous,
            description = "Previous",
            enabled = canSkipPrevious,
            onClick = playerConnection::seekToPrevious,
            iconSize = 44.dp,
        )
        if (isLoading) {
            Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) { MusikLoader(size = 44.dp) }
        } else {
            TransportButton(
                iconRes = when {
                    playbackState == STATE_ENDED -> R.drawable.replay
                    isPlaying -> R.drawable.pause
                    else -> R.drawable.play
                },
                description = when {
                    playbackState == STATE_ENDED -> "Replay"
                    isPlaying -> "Pause"
                    else -> "Play"
                },
                enabled = true,
                onClick = {
                    if (playbackState == STATE_ENDED) {
                        playerConnection.player.seekTo(0, 0)
                        playerConnection.player.playWhenReady = true
                    } else {
                        playerConnection.player.togglePlayPause()
                    }
                },
                iconSize = 72.dp,
            )
        }
        TransportButton(
            iconRes = R.drawable.skip_next,
            description = "Next",
            enabled = canSkipNext,
            onClick = playerConnection::seekToNext,
            iconSize = 44.dp,
        )
    }

    Spacer(Modifier.height(10.dp))

    // Secondary glass row
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        GlassIconButton(
            iconRes = R.drawable.shuffle,
            description = "Shuffle",
            onClick = { playerConnection.player.shuffleModeEnabled = !shuffle },
            tint = Color.White.copy(alpha = if (shuffle) 1f else 0.5f),
            size = 44.dp,
        )
        GlassIconButton(
            iconRes = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                else -> R.drawable.repeat
            },
            description = "Repeat",
            onClick = { playerConnection.player.toggleRepeatMode() },
            tint = Color.White.copy(alpha = if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
            size = 44.dp,
        )
        GlassIconButton(
            iconRes = R.drawable.share,
            description = "Share",
            onClick = {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${mediaMetadata.id}")
                }
                context.startActivity(Intent.createChooser(intent, null))
            },
            size = 44.dp,
        )
    }

    Spacer(Modifier.height(16.dp))

    // AI Music Assistant — "Ask AI DJ" prompt bar (Dribbble reference)
    AiDjBar(onClick = { /* future: open AI chat */ })

    Spacer(Modifier.height(4.dp))
}

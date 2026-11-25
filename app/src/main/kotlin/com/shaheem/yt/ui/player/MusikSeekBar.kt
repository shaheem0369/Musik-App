package com.shaheem.yt.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.shaheem.yt.R
import kotlin.math.PI
import kotlin.math.sin

/** Full-width orange waveform treatment matching the supplied player reference. */
@Composable
internal fun MusikSeekBar(
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    onValueChange: (Long) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
    val fraction = if (total == 0L) 0f else
        ((sliderPosition ?: position).toDouble() / total).toFloat().coerceIn(0f, 1f)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)
    var width by remember { mutableFloatStateOf(1f) }
    val radius = with(LocalDensity.current) { 10.dp.toPx() }
    val label = stringResource(R.string.playback_position)
    fun toPosition(x: Float) = (((x - radius) / (width - radius * 2).coerceAtLeast(1f))
        .coerceIn(0f, 1f) * total).toLong()

    Canvas(
        modifier = modifier.fillMaxWidth().height(48.dp)
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { value ->
                    if (total == 0L) false else {
                        latestChange((value.coerceIn(0f, 1f) * total).toLong())
                        latestFinished()
                        true
                    }
                }
            }
            .pointerInput(total, radius) {
                if (total > 0) detectTapGestures { offset ->
                    latestChange(toPosition(offset.x))
                    latestFinished()
                }
            }
            .pointerInput(total, radius) {
                if (total > 0) detectDragGestures(
                    onDragStart = { latestChange(toPosition(it.x)) },
                    onDragEnd = { latestFinished() },
                    onDragCancel = { latestFinished() },
                ) { change, _ ->
                    change.consume()
                    latestChange(toPosition(change.position.x))
                }
            },
    ) {
        val start = radius
        val end = (size.width - radius).coerceAtLeast(start)
        val thumbX = start + (end - start) * fraction
        val baseline = size.height * 0.67f
        val trackWidth = 6.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.36f), Offset(start, baseline), Offset(end, baseline), trackWidth, StrokeCap.Round)
        if (fraction > 0f) {
            val playedWidth = thumbX - start
            listOf(Color(0xFFDC5200), Color(0xFFFF9C23), Color(0xFFFFE56A)).forEachIndexed { layer, color ->
                val path = Path().apply {
                    moveTo(start, baseline)
                    for (step in 0..64) {
                        val t = step / 64f
                        val wave = sin(t * PI).toFloat().coerceAtLeast(0f)
                        val height = (14 - layer * 4).dp.toPx() * wave * wave
                        lineTo(start + playedWidth * t, baseline - height)
                    }
                    lineTo(thumbX, baseline)
                    close()
                }
                drawPath(path, color)
            }
            drawLine(Color(0xFFFF6900), Offset(start, baseline), Offset(thumbX, baseline), trackWidth, StrokeCap.Round)
        }
        drawCircle(Color(0xFFF27400), radius, Offset(thumbX, baseline))
        drawCircle(Color.White, radius, Offset(thumbX, baseline), style = Stroke(2.dp.toPx()))
    }
}

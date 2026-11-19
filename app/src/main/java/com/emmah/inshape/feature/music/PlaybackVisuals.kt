package com.emmah.inshape.feature.music

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest

private const val PREVIEW_PHASE = 0.7f
private val FullTurn = (2 * PI).toFloat()
private val BarPeaks = intArrayOf(14, 22, 30, 24, 16)

@Composable
internal fun PlaybackRibbon(playing: Boolean, modifier: Modifier = Modifier) {
    val paths = remember { List(9) { Path() } }
    PlaybackCanvas(playing, 3_600, modifier.fillMaxWidth().height(152.dp)) { color, phase, amplitude ->
        val strokeWidth = 1.dp.toPx()
        val startX = strokeWidth / 2f
        val endX = size.width - startX
        val centerY = center.y
        if (amplitude == 0f) {
            drawLine(color.copy(alpha = 0.45f), Offset(startX, centerY), Offset(endX, centerY), strokeWidth, StrokeCap.Round)
            return@PlaybackCanvas
        }

        val steps = ceil(size.width / 3.dp.toPx()).toInt()
        val excursion = 36.dp.toPx() * amplitude
        paths.forEach { path ->
            path.rewind()
            path.moveTo(startX, centerY)
        }
        for (step in 1 until steps) {
            val x = step.toFloat() / steps
            val radians = PI.toFloat() * x
            val envelope = sin(radians) * (0.82f + 0.18f * cos(2f * radians - 0.6f))
            val carrierPhase = 3f * radians - phase
            val spread = 0.42f * sin(2f * radians + phase)
            val pixelX = startX + (endX - startX) * x
            paths.forEachIndexed { index, path ->
                val strand = index * 0.25f - 1f
                val carrier = 0.55f * sin(carrierPhase + 0.70f * strand)
                path.lineTo(pixelX, centerY + excursion * envelope * (carrier + strand * spread))
            }
        }
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        paths.forEachIndexed { index, path ->
            path.lineTo(endX, centerY)
            val opacity = 0.85f - 0.4f * abs(index * 0.25f - 1f)
            drawPath(path, color.copy(alpha = opacity), style = stroke)
        }
    }
}

@Composable
internal fun PlaybackBars(playing: Boolean, modifier: Modifier = Modifier) {
    PlaybackCanvas(playing, 1_600, modifier.size(56.dp)) { color, phase, amplitude ->
        val barWidth = 4.dp.toPx()
        val stride = 7.dp.toPx()
        val startX = (size.width - 32.dp.toPx()) / 2f
        val baseline = 40.dp.toPx()
        BarPeaks.forEachIndexed { index, peak ->
            val motion = 0.5f + 0.5f * cos(phase - PREVIEW_PHASE + (index - 2) * 0.8f)
            val height = barWidth + amplitude * (peak.dp.toPx() - barWidth) * motion
            drawRoundRect(
                color,
                topLeft = Offset(startX + index * stride, baseline - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun PlaybackCanvas(
    playing: Boolean,
    periodMillis: Int,
    modifier: Modifier = Modifier,
    draw: DrawScope.(color: Color, phase: Float, amplitude: Float) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val canAnimate = visible && durationScale > 0f
    val target = if (playing) 1f else 0f
    var phase by remember { mutableFloatStateOf(PREVIEW_PHASE) }
    var amplitude by remember { mutableFloatStateOf(target) }
    LaunchedEffect(lifecycle, canAnimate, playing, durationScale, periodMillis) {
        if (!canAnimate) {
            phase = PREVIEW_PHASE
            amplitude = target
            return@LaunchedEffect
        }
        if (!playing && amplitude == 0f) return@LaunchedEffect

        lifecycle.currentStateFlow.collectLatest { state ->
            if (!state.isAtLeast(Lifecycle.State.RESUMED)) {
                phase = PREVIEW_PHASE
                amplitude = target
                return@collectLatest
            }
            if (!playing && amplitude == 0f) return@collectLatest

            val startPhase = phase
            val startAmplitude = amplitude
            val settleMillis = if (playing) 220 else 280
            val startTime = withFrameNanos { it }
            val updateFrame: (Long) -> Unit = { time ->
                val elapsed = (time - startTime) / 1_000_000.0 / durationScale
                if (playing) phase = ((startPhase + elapsed / periodMillis * FullTurn) % FullTurn).toFloat()
                if (amplitude != target) {
                    val fraction = (elapsed / settleMillis).toFloat().coerceAtMost(1f)
                    amplitude = if (fraction == 1f) {
                        target
                    } else {
                        startAmplitude + (target - startAmplitude) * FastOutSlowInEasing.transform(fraction)
                    }
                }
            }
            while (playing || amplitude > 0f) {
                if (playing && amplitude == target) {
                    withInfiniteAnimationFrameNanos(updateFrame)
                } else {
                    withFrameNanos(updateFrame)
                }
            }
        }
    }

    val color = InShapeTheme.colors.text
    Canvas(modifier.onVisibilityChanged(minFractionVisible = 0f) { visible = it }) {
        if (canAnimate) draw(color, phase, amplitude) else draw(color, PREVIEW_PHASE, target)
    }
}

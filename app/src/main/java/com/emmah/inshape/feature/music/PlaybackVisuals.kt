package com.emmah.inshape.feature.music

import com.emmah.inshape.ui.theme.Dimens
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.flow.collectLatest

private const val PREVIEW_PHASE = 0.7f
private val FullTurn = (2 * PI).toFloat()

@Composable
internal fun PlaybackBars(playing: Boolean, modifier: Modifier = Modifier) {
    val restingColor = InShapeTheme.colors.textTertiary
    PlaybackCanvas(playing, 1_600, modifier.size(Dimens.button)) { color, phase, amplitude ->
        val scale = size.minDimension / Dimens.button.toPx()
        val barWidth = Dimens.innerTiny.toPx() * scale
        val stride = Dimens.playbackBarStride.toPx() * scale
        val startX = (size.width - Dimens.iconLarge.toPx() * scale) / 2f
        val baseline = Dimens.playbackBarBaseline.toPx() * scale
        Dimens.playbackBarHeights.forEachIndexed { index, peak ->
            val motion = 0.5f + 0.5f * cos(phase - PREVIEW_PHASE + (index - 2) * 0.8f)
            val fullHeight = peak.toPx() * scale - barWidth
            val height = barWidth + fullHeight * (.55f * (1 - amplitude) + amplitude * motion)
            drawRoundRect(
                lerp(restingColor, color, amplitude),
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

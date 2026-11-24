package com.emmah.inshape.feature.checkin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.ui.theme.BodyArtworkColors
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

internal fun bodyArtwork(back: Boolean) = if (back) backBodyArtwork else frontBodyArtwork

fun bodyRegionAt(x: Float, y: Float, back: Boolean): String? = bodyRegionAt(x, y, back, 0f)

private fun bodyRegionAt(x: Float, y: Float, back: Boolean, target: Float): String? {
    val figure = bodyArtwork(back)
    val pixelX = floor(x).toInt()
    val pixelY = floor(y).toInt()
    val visiblePart = figure.lastOrNull { it.hitArea?.contains(pixelX, pixelY) == true }
    if (visiblePart != null || target <= 0f) return visiblePart?.id
    return figure.mapNotNull { part ->
        val id = part.id ?: return@mapNotNull null
        val bounds = part.bounds
        val dx = x - bounds.centerX()
        val dy = y - bounds.centerY()
        if (abs(dx) <= maxOf(target, bounds.width()) / 2f && abs(dy) <= maxOf(target, bounds.height()) / 2f) {
            id to hypot(dx, dy)
        } else {
            null
        }
    }.minByOrNull { it.second }?.first
}

private fun bodyScale(width: Float, height: Float) = minOf(width / BODY_WIDTH, height / BODY_HEIGHT)

private fun bodyOrigin(width: Float, height: Float, scale: Float) =
    Offset((width - BODY_WIDTH * scale) / 2f, (height - BODY_HEIGHT * scale) / 2f)

@Composable
fun BodyMap(back: Boolean, observations: List<BodyObservation>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = InShapeTheme.colors
    val select by rememberUpdatedState(onSelect)
    Canvas(
        modifier.fillMaxWidth().height(Dimens.bodyHeight).pointerInput(back) {
            detectTapGestures { point ->
                val scale = bodyScale(size.width.toFloat(), size.height.toFloat())
                val origin = bodyOrigin(size.width.toFloat(), size.height.toFloat(), scale)
                val x = (point.x - origin.x) / scale
                val y = (point.y - origin.y) / scale
                val target = Dimens.touch.toPx() / scale
                bodyRegionAt(x, y, back, target)?.let(select)
            }
        }
    ) {
        val scale = bodyScale(size.width, size.height)
        val origin = bodyOrigin(size.width, size.height, scale)
        val haloWidth = minOf(size.width * .72f, Dimens.bodyHaloWidth.toPx())
        val haloHeight = BODY_HEIGHT * scale * .82f
        val halo = if (colors.isDark) {
            BodyArtworkColors.darkHalo
        } else {
            BodyArtworkColors.lightHalo
        }
        val haloCenter = Offset(size.width / 2f, origin.y + 168f * scale)
        // Draw a radial gradient in a circle and scale it to the halo's ellipse.
        withTransform({ scale(1f, haloHeight / haloWidth, pivot = haloCenter) }) {
            drawOval(
                Brush.radialGradient(listOf(halo, halo.copy(alpha = 0f)), haloCenter, haloWidth / 2f),
                topLeft = haloCenter - Offset(haloWidth / 2f, haloWidth / 2f),
                size = Size(haloWidth, haloWidth)
            )
        }
        withTransform({
            translate(origin.x, origin.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            val outline = Stroke(1.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val ink = if (colors.isDark) BodyArtworkColors.darkInk else BodyArtworkColors.lightInk
            val figure = bodyArtwork(back)
            figure.forEach { part ->
                part.fill?.let { source ->
                    val fill = if (!colors.isDark) {
                        source
                    } else if (source.red < .89f) {
                        BodyArtworkColors.darkFill
                    } else {
                        BodyArtworkColors.darkFillLight
                    }
                    drawPath(part.geometry, fill)
                }
                val stroke = if (part.detailInk != null && colors.isDark) BodyArtworkColors.darkDetail else part.detailInk ?: ink
                drawPath(part.geometry, stroke, style = outline)
                val observation = observations.find { it.region == part.id }
                when {
                    (observation?.pain ?: 0) > 0 -> drawPath(part.geometry, colors.ink)

                    (observation?.stiffness ?: 0) > 0 -> drawPath(
                        part.geometry,
                        colors.ink,
                        style = Stroke(Dimens.ring.toPx() / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

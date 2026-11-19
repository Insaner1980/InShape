package com.emmah.inshape.feature.checkin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlin.math.hypot

internal fun bodyArtwork(back: Boolean) = if (back) backBodyArtwork else frontBodyArtwork

fun bodyRegionAt(x: Float, y: Float, back: Boolean): String? = bodyArtwork(back).lastOrNull {
    it.hitArea?.contains(kotlin.math.floor(x).toInt(), kotlin.math.floor(y).toInt()) == true
}?.id

private fun bodyScale(width: Float, height: Float) = minOf(width / BODY_WIDTH, height / BODY_HEIGHT)

private fun bodyOrigin(width: Float, height: Float, scale: Float) =
    Offset((width - BODY_WIDTH * scale) / 2f, (height - BODY_HEIGHT * scale) / 2f)

@Composable
fun BodyMap(
    back: Boolean,
    observations: List<BodyObservation>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit = {}
) {
    val colors = InShapeTheme.colors
    val select by rememberUpdatedState(onSelect)
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
    ) {
        controls()
        Canvas(
            Modifier.fillMaxWidth().height(Dimens.bodyHeight).pointerInput(back) {
                detectTapGestures { point ->
                    val scale = bodyScale(size.width.toFloat(), size.height.toFloat())
                    val origin = bodyOrigin(size.width.toFloat(), size.height.toFloat(), scale)
                    val x = (point.x - origin.x) / scale
                    val y = (point.y - origin.y) / scale
                    val target = Dimens.touch.toPx() / scale
                    val region = bodyRegionAt(x, y, back) ?: bodyArtwork(back).mapNotNull { shape ->
                        val id = shape.id ?: return@mapNotNull null
                        val bounds = shape.bounds
                        val dx = x - bounds.centerX()
                        val dy = y - bounds.centerY()
                        if (kotlin.math.abs(dx) > maxOf(target, bounds.width()) / 2f ||
                            kotlin.math.abs(dy) > maxOf(target, bounds.height()) / 2f
                        ) {
                            null
                        } else {
                            id to hypot(dx, dy)
                        }
                    }.minByOrNull { it.second }?.first
                    region?.let(select)
                }
            }
        ) {
            val scale = bodyScale(size.width, size.height)
            val origin = bodyOrigin(size.width, size.height, scale)
            val haloWidth = minOf(size.width * .72f, 250.dp.toPx())
            val haloHeight = BODY_HEIGHT * scale * .82f
            val halo = if (colors.isDark) {
                Color(0xFF53665B).copy(alpha = .32f)
            } else {
                Color(0xFFE6EBDB).copy(alpha = .55f)
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
                val ink = if (colors.isDark) Color(0xFFA8B7AB) else Color(0xFF576057)
                val figure = bodyArtwork(back)
                figure.forEach { part ->
                    part.fill?.let { source ->
                        val fill = if (!colors.isDark) {
                            source
                        } else if (source.red < .89f) {
                            Color(0xFF34423D)
                        } else {
                            Color(0xFF405048)
                        }
                        drawPath(part.geometry, fill)
                    }
                    val stroke = if (part.detailInk != null && colors.isDark) Color(0xFF83978B) else part.detailInk ?: ink
                    drawPath(part.geometry, stroke, style = outline)
                }
                figure.forEach { part ->
                    val observation = observations.find { it.region == part.id }
                    when {
                        (observation?.pain ?: 0) > 0 -> drawPath(part.geometry, colors.ink)

                        (observation?.stiffness ?: 0) > 0 -> drawPath(
                            part.geometry,
                            colors.ink,
                            style = Stroke(2.dp.toPx() / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }
        Text(
            "Tap the body to add an area. A filled area means pain. An outline means stiffness only.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    }
    SelectField("Add body area", "Choose area", bodyRegions.map { it.label }, { label ->
        onSelect(
            bodyRegions.first {
                it.label ==
                    label
            }.id
        )
    })
}

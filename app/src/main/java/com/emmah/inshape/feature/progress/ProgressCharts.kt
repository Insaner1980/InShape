package com.emmah.inshape.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.emmah.inshape.R
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.displayDate
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Slot content: siblings use the caller's layout and spacing, so there is no single modifier root.
@Suppress("ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
fun ColumnScope.ExerciseChart(points: List<MetricPoint>, unit: String) {
    val colors = InShapeTheme.colors
    var selected by remember(points) { mutableIntStateOf(points.indexOfLast { it.value != null }) }
    var showData by remember { mutableStateOf(false) }
    val values = points.mapNotNull { it.value?.toFloat() }
    if (values.isEmpty()) {
        Text("No recorded values for this comparison.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val low = max(0f, floor(values.min() / 5f) * 5 - 5)
    val high = max(low + 5, ceil(values.max() / 5f) * 5)
    Row {
        Column(
            Modifier.height(Dimens.chartExercise),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(high, (high + low) / 2, low).forEach {
                Text(
                    it.toInt().toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
        Canvas(
            Modifier.weight(1f).height(Dimens.chartExercise).padding(Dimens.innerSmall).semantics {
                contentDescription =
                    "Exercise history, $unit. Use View data values for all recorded points."
            }.pointerInput(points) {
                detectTapGestures { tap ->
                    selected =
                        if (points.size ==
                            1
                        ) {
                            0
                        } else {
                            (tap.x / size.width * points.lastIndex).roundToInt().coerceIn(
                                points.indices
                            )
                        }
                }
            }
        ) {
            fun x(index: Int) = if (points.size ==
                1
            ) {
                size.width / 2
            } else {
                index.toFloat() / points.lastIndex * size.width
            }
            fun y(value: Float) = size.height - (value - low) / (high - low) * size.height
            drawChartGrid(colors.divider)
            points.forEachIndexed { i, point ->
                point.value?.let { value ->
                    if (i > 0 &&
                        points[i - 1].value != null
                    ) {
                        drawLine(
                            colors.ink,
                            Offset(
                                x(i - 1),
                                y(
                                    points[
                                        i -
                                            1
                                    ].value!!.toFloat()
                                )
                            ),
                            Offset(x(i), y(value.toFloat())),
                            Dimens.chartLine.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    val isolated = points.getOrNull(i - 1)?.value == null && points.getOrNull(i + 1)?.value == null
                    if (i == selected || i == points.indexOfLast { it.value != null } || isolated) {
                        drawCircle(
                            colors.ink,
                            Dimens.chartDot.toPx(),
                            Offset(x(i), y(value.toFloat()))
                        )
                    }
                }
            }
        }
    }
    ResponsiveRow {
        Text(
            points.first().date.displayDate(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            points.last().date.displayDate(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
    points.getOrNull(selected)?.let {
        Text(
            "${it.date.displayDate()}: ${it.value?.stripTrailingZeros()?.toPlainString()?.let { value ->
                "$value $unit"
            } ?: "Not recorded"}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
    TextAction(if (showData) "Hide data values" else "View data values", { showData = !showData })
    if (showData) {
        points.forEachIndexed { index, point ->
            val value = point.value?.stripTrailingZeros()?.toPlainString()?.let { "$it $unit" } ?: "Not recorded"
            KeyValueRow(point.date.displayDate(), value, numeric = true, onClick = { selected = index })
        }
    }
}

@Composable
fun ColumnScope.AttendanceChart(weeks: List<WeekCount>, goal: Int?, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = InShapeTheme.colors
    val maximum = maxOf(1, weeks.maxOfOrNull { it.count } ?: 0, goal ?: 0)
    if (goal != null) {
        Text(
            "Goal $goal",
            Modifier.fillMaxWidth(),
            style = InShapeType.nav,
            color = colors.textSecondary,
            textAlign = TextAlign.End
        )
    }
    Canvas(
        modifier.fillMaxWidth().height(
            Dimens.chartAttendance
        ).padding(top = Dimens.innerTiny).semantics {
            contentDescription =
                "Weekly workout counts. The current week is incomplete. Use View weekly counts for all values."
        }
    ) {
        val slot = size.width / weeks.size.coerceAtLeast(1)
        val gap = min(Dimens.chartGap.toPx(), slot * .3f)
        if (goal != null) {
            val y = size.height - size.height * goal / maximum
            drawLine(
                colors.surfaceStrong,
                Offset(0f, y),
                Offset(size.width, y),
                Dimens.chartOutline.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(Dimens.innerTiny.toPx(), Dimens.innerTiny.toPx())
                )
            )
        }
        weeks.forEachIndexed { index, week ->
            val height = max(Dimens.innerTiny.toPx(), size.height * week.count / maximum)
            val offset = Offset(slot * index + gap / 2, size.height - height)
            val dimensions = Size((slot - gap).coerceAtLeast(1f), height)
            if (week.week == weekStart(today) && week.count == 0) {
                drawLine(
                    colors.textSecondary,
                    Offset(offset.x, offset.y + height / 2),
                    Offset(offset.x + dimensions.width, offset.y + height / 2),
                    strokeWidth = height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dimens.innerTiny.toPx(), Dimens.chartLine.toPx()))
                )
            } else if (week.week == weekStart(today)) {
                drawRoundRect(
                    colors.textSecondary,
                    offset,
                    dimensions,
                    CornerRadius(Dimens.chartGap.toPx()),
                    style = Stroke(
                        Dimens.chartOutline.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(Dimens.innerTiny.toPx(), Dimens.chartLine.toPx())
                        )
                    )
                )
            } else {
                drawRoundRect(
                    if (week.count == 0 ||
                        (goal != null && week.count < goal)
                    ) {
                        colors.surfaceStrong
                    } else {
                        colors.ink
                    },
                    offset,
                    dimensions,
                    CornerRadius(Dimens.chartGap.toPx())
                )
            }
        }
    }
    if (weeks.isNotEmpty()) {
        ResponsiveRow {
            Text(
                weeks.first().week.displayDate("d MMMM"),
                Modifier.weight(1f),
                style = InShapeType.nav,
                color = colors.textSecondary
            )
            Text("This week, in progress", style = InShapeType.nav, color = colors.textSecondary)
        }
    }
    var details by remember { mutableStateOf(false) }
    TextAction(if (details) "Hide weekly counts" else "View weekly counts", { details = !details })
    if (details) {
        weeks.forEach {
            Text(
                "Week of ${it.week.displayDate()}: ${pluralStringResource(R.plurals.workout_count, it.count, it.count)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
fun ObservationChart(points: List<ObservationPoint>, modifier: Modifier = Modifier) {
    val colors = InShapeTheme.colors
    Canvas(
        modifier.fillMaxWidth().height(
            Dimens.chartObservation
        ).padding(Dimens.innerSmall).semantics {
            contentDescription =
                "Pain, solid line. Stiffness, dashed line. Scale 0 to 10. Use View values for all observations."
        }
    ) {
        fun x(index: Int) = if (points.size ==
            1
        ) {
            size.width / 2
        } else {
            index.toFloat() / points.lastIndex * size.width
        }
        fun y(value: Int) = size.height * (1 - value / 10f)
        drawChartGrid(colors.divider)
        listOf(false, true).forEach { stiffness ->
            val values = points.map { if (stiffness) it.stiffness else it.pain }
            val color = colors.ink
            val effect = if (stiffness) {
                PathEffect.dashPathEffect(
                    floatArrayOf(Dimens.chartDash.toPx(), Dimens.chartGap.toPx())
                )
            } else {
                null
            }
            values.forEachIndexed { index, value ->
                if (value != null) {
                    values.getOrNull(index - 1)?.let { previous ->
                        drawLine(
                            color,
                            Offset(x(index - 1), y(previous)),
                            Offset(x(index), y(value)),
                            Dimens.chartLine.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = effect
                        )
                    }
                    if (index == values.indexOfLast { it != null } ||
                        (values.getOrNull(index - 1) == null && values.getOrNull(index + 1) == null)
                    ) {
                        drawCircle(color, Dimens.chartDot.toPx(), Offset(x(index), y(value)))
                    }
                }
            }
        }
    }
}

@Composable
fun ObservationLegend(label: String, value: Int?, stiffness: Boolean, modifier: Modifier = Modifier) {
    val color = InShapeTheme.colors.ink
    Surface(modifier, shape = InShapeShapes.pill, color = InShapeTheme.colors.surface) {
        Row(
            Modifier.padding(Dimens.innerSmall),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
        ) {
            Canvas(Modifier.size(Dimens.inner, Dimens.innerSmall)) {
                drawLine(
                    color,
                    Offset(
                        0f,
                        size.height / 2
                    ),
                    Offset(size.width, size.height / 2),
                    Dimens.ring.toPx(),
                    pathEffect = if (stiffness) {
                        PathEffect.dashPathEffect(
                            floatArrayOf(Dimens.innerTiny.toPx(), Dimens.chartLine.toPx())
                        )
                    } else {
                        null
                    }
                )
            }
            Text(
                "$label, ${value?.let {
                    com.emmah.inshape.feature.checkin.bodyLevelOf(it)!!.label.lowercase()
                } ?: "none"}",
                style = MaterialTheme.typography.labelMedium,
                color = InShapeTheme.colors.textSecondary
            )
        }
    }
}

private fun DrawScope.drawChartGrid(color: Color) {
    drawLine(
        color,
        Offset(0f, size.height),
        Offset(size.width, size.height),
        Dimens.line.toPx()
    )
    drawLine(
        color,
        Offset(0f, size.height / 2),
        Offset(size.width, size.height / 2),
        Dimens.line.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(Dimens.innerTiny.toPx(), Dimens.innerTiny.toPx())
        )
    )
}

@Composable
fun ExerciseSparkline(points: List<MetricPoint>, modifier: Modifier = Modifier) {
    val values = points.mapNotNull { it.value?.toFloat() }
    val low = values.minOrNull() ?: return
    val span = values.max() - low
    if (span == 0f) return
    val colors = InShapeTheme.colors
    Canvas(modifier.size(Dimens.sparklineWidth, Dimens.sparklineHeight)) {
        fun point(index: Int, value: Float) = Offset(
            if (points.size == 1) size.width / 2 else index * size.width / points.lastIndex,
            size.height - Dimens.ring.toPx() - (value - low) / span * (size.height - 2 * Dimens.ring.toPx())
        )
        points.forEachIndexed { index, value ->
            if (index > 0 && value.value != null && points[index - 1].value != null) {
                drawLine(
                    colors.ink,
                    point(index - 1, points[index - 1].value!!.toFloat()),
                    point(index, value.value.toFloat()),
                    Dimens.ring.toPx(),
                    StrokeCap.Round
                )
            }
        }
    }
}

package com.emmah.inshape.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.emmah.inshape.R
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.ui.components.DisclosureRow
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.axisDate
import com.emmah.inshape.ui.theme.decimalDisplay
import com.emmah.inshape.ui.theme.formatDuration
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Slot content: siblings use the caller's layout and spacing, so there is no single modifier root.
@Suppress("ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
fun ColumnScope.ExerciseChart(points: List<MetricPoint>, basis: MeasurementBasis) {
    val colors = InShapeTheme.colors
    var showData by remember { mutableStateOf(false) }
    val values = points.mapNotNull { it.value?.toFloat() }
    if (values.isEmpty()) {
        Text(stringResource(R.string.no_comparison_data), style = MaterialTheme.typography.bodySmall)
    } else {
        // Duration points are stored in seconds. The midpoint is at least one minute from the endpoints.
        val timed = basis == MeasurementBasis.Time
        val step = if (timed) 120f else 5f
        val low = max(0f, floor(values.min() / step) * step - step)
        val high = max(low + step, ceil(values.max() / step) * step)
        val lastRecorded = points.indexOfLast { it.value != null }
        Row {
            Column(Modifier.height(Dimens.chartExercise), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(high, (high + low) / 2, low).forEach { tick ->
                    Text(
                        if (timed) formatDuration(tick.toLong()) else tick.toString().removeSuffix(".0").decimalDisplay(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
            val chartDescription = stringResource(R.string.exercise_chart_description, stringResource(basis.unitLabel))
            Canvas(
                Modifier.weight(1f).height(Dimens.chartExercise).padding(Dimens.innerSmall).semantics {
                    contentDescription = chartDescription
                }
            ) {
                fun x(index: Int) = if (points.size == 1) size.width / 2 else index.toFloat() / points.lastIndex * size.width
                fun y(value: Float) = size.height - (value - low) / (high - low) * size.height
                drawChartGrid(colors.divider)
                points.forEachIndexed { index, point ->
                    val value = point.value?.toFloat() ?: return@forEachIndexed
                    val previous = points.getOrNull(index - 1)?.value?.toFloat()
                    if (previous != null) {
                        drawLine(
                            colors.ink,
                            Offset(x(index - 1), y(previous)),
                            Offset(x(index), y(value)),
                            Dimens.chartLine.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    if (index == lastRecorded || (previous == null && points.getOrNull(index + 1)?.value == null)) {
                        drawCircle(colors.ink, Dimens.chartDot.toPx(), Offset(x(index), y(value)))
                    }
                }
            }
        }
        ResponsiveRow {
            Text(
                points.first().date.axisDate(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            if (points.first().date != points.last().date) {
                Text(points.last().date.axisDate(), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
    if (points.isNotEmpty()) {
        DisclosureRow(stringResource(R.string.values), showData, { showData = !showData })
        if (showData) {
            points.forEach { point ->
                KeyValueRow(
                    point.date.axisDate(),
                    point.value?.let { basis.progressValue(LocalContext.current, it) } ?: stringResource(R.string.not_recorded),
                    numeric = true,
                    modifier = Modifier.semantics(mergeDescendants = true) {}
                )
            }
        }
    }
}

@Composable
fun ColumnScope.AttendanceChart(weeks: List<WeekCount>, goal: Int?, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = InShapeTheme.colors
    val maximum = maxOf(1, weeks.maxOfOrNull { it.count } ?: 0, goal ?: 0)
    if (goal != null) {
        Text(
            stringResource(R.string.chart_goal, goal),
            Modifier.fillMaxWidth(),
            style = InShapeType.nav,
            color = colors.textSecondary,
            textAlign = TextAlign.End
        )
    }
    val chartDescription = stringResource(R.string.weekly_counts_description)
    Canvas(
        modifier.fillMaxWidth().height(
            Dimens.chartAttendance
        ).padding(top = Dimens.innerTiny).semantics {
            contentDescription =
                chartDescription
        }
    ) {
        val slot = size.width / weeks.size.coerceAtLeast(1)
        val gap = min(Dimens.chartGap.toPx(), slot * .3f)
        if (goal != null) {
            val y = size.height - size.height * goal / maximum
            drawLine(
                colors.divider,
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
                        colors.divider
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
                weeks.first().week.axisDate(),
                Modifier.weight(1f),
                style = InShapeType.nav,
                color = colors.textSecondary
            )
            Text(stringResource(R.string.this_week), style = InShapeType.nav, color = colors.textSecondary)
        }
    }
    var details by remember { mutableStateOf(false) }
    DisclosureRow(stringResource(R.string.weekly_counts), details, { details = !details })
    if (details) {
        weeks.forEach {
            KeyValueRow(
                stringResource(R.string.week_of_date, it.week.axisDate()),
                pluralStringResource(R.plurals.workout_count, it.count, it.count),
                modifier = Modifier.semantics(mergeDescendants = true) {}
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
    val colors = InShapeTheme.colors
    Canvas(modifier.size(Dimens.sparklineWidth, Dimens.sparklineHeight)) {
        val radius = Dimens.ring.toPx()
        fun point(index: Int, value: Float) = Offset(
            radius + index * (size.width - 2 * radius) / points.lastIndex,
            if (span == 0f) size.height / 2 else size.height - radius - (value - low) / span * (size.height - 2 * radius)
        )
        points.forEachIndexed { index, metricPoint ->
            val value = metricPoint.value?.toFloat() ?: return@forEachIndexed
            val previous = points.getOrNull(index - 1)?.value?.toFloat()
            if (previous != null) {
                drawLine(
                    colors.ink,
                    point(index - 1, previous),
                    point(index, value),
                    radius,
                    StrokeCap.Round
                )
            } else if (points.getOrNull(index + 1)?.value == null) {
                drawCircle(colors.ink, radius, point(index, value))
            }
        }
    }
}

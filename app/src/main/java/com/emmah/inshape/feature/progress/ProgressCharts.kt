package com.emmah.inshape.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import java.time.LocalDate
import kotlin.math.*

@Composable
fun ExerciseChart(points: List<MetricPoint>, unit: String) {
    val colors = InShapeTheme.colors
    var selected by remember(points) { mutableIntStateOf(points.indexOfLast { it.value != null }) }
    var showData by remember { mutableStateOf(false) }
    val values = points.mapNotNull { it.value?.toFloat() }
    if (values.isEmpty()) { Text("No recorded values for this comparison.", style = MaterialTheme.typography.bodySmall); return }
    val low = max(0f, floor(values.min() / 5f) * 5 - 5)
    val high = max(low + 5, ceil(values.max() / 5f) * 5)
    Row {
        Column(Modifier.height(Dimens.chartExercise), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(high, (high + low) / 2, low).forEach { Text(it.toInt().toString(), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
        }
        Canvas(Modifier.weight(1f).height(Dimens.chartExercise).padding(Dimens.innerSmall).semantics { contentDescription = "Exercise history, $unit. Use View data values for all recorded points." }.pointerInput(points) {
            detectTapGestures { tap -> selected = if (points.size == 1) 0 else (tap.x / size.width * points.lastIndex).roundToInt().coerceIn(points.indices) }
        }) {
            fun x(index: Int) = if (points.size == 1) size.width / 2 else index.toFloat() / points.lastIndex * size.width
            fun y(value: Float) = size.height - (value - low) / (high - low) * size.height
            drawLine(colors.divider, Offset(0f, size.height), Offset(size.width, size.height), Dimens.line.toPx())
            drawLine(colors.divider, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), Dimens.line.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dimens.innerTiny.toPx(), Dimens.innerTiny.toPx())))
            points.forEachIndexed { i, point -> point.value?.let { value ->
                if (i > 0 && points[i - 1].value != null) drawLine(colors.mark, Offset(x(i - 1), y(points[i - 1].value!!.toFloat())), Offset(x(i), y(value.toFloat())), Dimens.chartLine.toPx(), cap = StrokeCap.Round)
                if (i == selected || i == points.indexOfLast { it.value != null } || (points.getOrNull(i - 1)?.value == null && points.getOrNull(i + 1)?.value == null)) drawCircle(colors.mark, Dimens.chartDot.toPx(), Offset(x(i), y(value.toFloat())))
            } }
        }
    }
    ResponsiveRow { Text(points.first().date.displayDate(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary); Text(points.last().date.displayDate(), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
    points.getOrNull(selected)?.let { Text("${it.date.displayDate()}: ${it.value?.stripTrailingZeros()?.toPlainString()?.let { value -> "$value $unit" } ?: "Not recorded"}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
    TextAction(if (showData) "Hide data values" else "View data values", { showData = !showData })
    if (showData) points.forEachIndexed { index, point -> TextAction("${point.date.displayDate()}: ${point.value?.stripTrailingZeros()?.toPlainString()?.let { "$it $unit" } ?: "Not recorded"}", { selected = index }) }
}

@Composable
fun AttendanceChart(weeks: List<WeekCount>, goal: Int?, today: LocalDate) {
    val colors = InShapeTheme.colors
    val maximum = maxOf(1, weeks.maxOfOrNull { it.count } ?: 0, goal ?: 0)
    Canvas(Modifier.fillMaxWidth().height(Dimens.chartAttendance).padding(top = Dimens.innerTiny).semantics { contentDescription = "Weekly workout counts. The current week is incomplete. Use View weekly counts for all values." }) {
        val slot = size.width / weeks.size.coerceAtLeast(1)
        val gap = min(Dimens.chartGap.toPx(), slot * .3f)
        if (goal != null) {
            val y = size.height - size.height * goal / maximum
            drawLine(colors.muted, Offset(0f, y), Offset(size.width, y), Dimens.chartOutline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dimens.innerTiny.toPx(), Dimens.innerTiny.toPx())))
        }
        weeks.forEachIndexed { index, week ->
            val height = max(Dimens.innerTiny.toPx(), size.height * week.count / maximum)
            val offset = Offset(slot * index + gap / 2, size.height - height)
            val dimensions = Size((slot - gap).coerceAtLeast(1f), height)
            if (week.week == weekStart(today)) drawRoundRect(colors.textSecondary, offset, dimensions, CornerRadius(Dimens.chartGap.toPx()), style = Stroke(Dimens.chartOutline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dimens.innerTiny.toPx(), Dimens.chartLine.toPx()))))
            else drawRoundRect(if (week.count == 0 || (goal != null && week.count < goal)) colors.muted else colors.mark, offset, dimensions, CornerRadius(Dimens.chartGap.toPx()))
        }
    }
    if (weeks.isNotEmpty()) ResponsiveRow {
        Text(weeks.first().week.displayDate("d MMMM"), Modifier.weight(1f), style = InShapeType.nav, color = colors.textSecondary)
        Text("This week, in progress", style = InShapeType.nav, color = colors.textSecondary)
    }
    var details by remember { mutableStateOf(false) }
    TextAction(if (details) "Hide weekly counts" else "View weekly counts", { details = !details })
    if (details) weeks.forEach { Text("Week of ${it.week.displayDate()}: ${it.count} workouts", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
}

@Composable
fun ObservationChart(points: List<ObservationPoint>) {
    val colors = InShapeTheme.colors
    Canvas(Modifier.fillMaxWidth().height(Dimens.chartObservation).padding(Dimens.innerSmall).semantics { contentDescription = "Pain, solid line. Stiffness, dashed line. Scale 0 to 10. Use View values for all observations." }) {
        fun x(index: Int) = if (points.size == 1) size.width / 2 else index.toFloat() / points.lastIndex * size.width
        fun y(value: Int) = size.height * (1 - value / 10f)
        drawLine(colors.divider, Offset(0f, size.height), Offset(size.width, size.height), Dimens.line.toPx())
        drawLine(colors.divider, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), Dimens.line.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dimens.innerTiny.toPx(), Dimens.innerTiny.toPx())))
        listOf(false, true).forEach { stiffness ->
            val values = points.map { if (stiffness) it.stiffness else it.pain }
            val color = if (stiffness) colors.stiffness else colors.pain
            val effect = if (stiffness) PathEffect.dashPathEffect(floatArrayOf(Dimens.chartDash.toPx(), Dimens.chartGap.toPx())) else null
            values.forEachIndexed { index, value -> if (value != null) {
                values.getOrNull(index - 1)?.let { previous -> drawLine(color, Offset(x(index - 1), y(previous)), Offset(x(index), y(value)), Dimens.chartLine.toPx(), cap = StrokeCap.Round, pathEffect = effect) }
                if (index == values.indexOfLast { it != null } || (values.getOrNull(index - 1) == null && values.getOrNull(index + 1) == null)) drawCircle(color, Dimens.chartDot.toPx(), Offset(x(index), y(value)))
            } }
        }
    }
}

@Composable
fun ObservationLegend(label: String, value: Int?, stiffness: Boolean, modifier: Modifier = Modifier) {
    val color = if (stiffness) InShapeTheme.colors.stiffness else InShapeTheme.colors.pain
    Surface(modifier, shape = InShapeShapes.pill, color = InShapeTheme.colors.surfaceInset) {
        Row(Modifier.padding(Dimens.innerSmall), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            Canvas(Modifier.size(Dimens.inner, Dimens.innerSmall)) { drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), Dimens.ring.toPx(), pathEffect = if (stiffness) PathEffect.dashPathEffect(floatArrayOf(Dimens.innerTiny.toPx(), Dimens.chartLine.toPx())) else null) }
            Text("$label, ${value?.let { "now $it" } ?: "none"}", style = InShapeType.pill, color = InShapeTheme.colors.textSecondary)
        }
    }
}

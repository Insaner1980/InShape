package com.emmah.inshape.feature.progress

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.R
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.feature.checkin.bodyRegionLabel
import com.emmah.inshape.feature.checkin.bodyRegions
import com.emmah.inshape.ui.components.ArtworkHeader
import com.emmah.inshape.ui.components.DisclosureRow
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PillTone
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.axisDate
import com.emmah.inshape.ui.theme.finnishLocale
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.observationSummary
import com.emmah.inshape.ui.theme.shortDate
import java.math.BigDecimal
import java.time.LocalDate

private const val INITIAL_OBSERVATION_COUNT = 12

@Composable
fun ProgressScreen(
    data: JournalSnapshot,
    today: LocalDate,
    modifier: Modifier = Modifier,
    initialExerciseId: String? = null,
    header: @Composable () -> Unit = {}
) {
    val history = data.history
    var period by rememberSaveable { mutableIntStateOf(1) }
    val earliest =
        history.filter {
            it.qualifiesForAttendance
        }.minOfOrNull { LocalDate.parse(it.row.journalDate) }?.coerceAtMost(today)
            ?: today
    val range = DateRange(
        if (period == 2) {
            earliest
        } else {
            weekStart(today).minusWeeks(if (period == 0) 3 else 11)
        },
        today
    )
    Page(modifier) {
        header()
        val periods = listOf(stringResource(R.string.weeks_4), stringResource(R.string.weeks_12), stringResource(R.string.all))
        SegmentedControl(periods, period, { period = it })
        ProgressAttendance(history, range, today, data.preferences.weeklyGoal)
        ProgressExercises(data.exercises, history, range, initialExerciseId)
        ProgressBodyObservations(data.observations, history, range)
    }
}

@Composable
private fun ProgressAttendance(history: List<Workout>, range: DateRange, today: LocalDate, weeklyGoal: Int?) {
    val colors = InShapeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        val weeks = weeklyAttendance(history, range)
        SectionHeader(stringResource(R.string.workouts_per_week))
        if (history.none { it.qualifiesForAttendance && it.row.journalDate in range }) {
            ArtworkHeader(
                R.drawable.art_history_empty_training_log,
                Dimens.progressEmptyArtworkWidth,
                Dimens.progressEmptyArtworkHeight,
                Modifier.clip(InShapeShapes.card).background(ArtworkWash.Paper.brush()).padding(Dimens.inner)
            ) {
                Text(stringResource(R.string.no_workouts_period), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        } else {
            ArtworkHeader(
                R.drawable.art_history_stacked_plates,
                Dimens.progressAttendanceArtworkWidth,
                Dimens.progressAttendanceArtworkHeight,
                Modifier.clip(InShapeShapes.card).background(ArtworkWash.Equipment.brush()).padding(Dimens.inner),
                cropEnd = true
            ) {
                Column {
                    Text(
                        String.format(finnishLocale, "%.1f", averageWeeklyAttendance(history, range, today)),
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(stringResource(R.string.on_average), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
            if (weeks.first().week > range.start) {
                Text(
                    pluralStringResource(R.plurals.chart_latest_weeks, weeks.size, weeks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            AttendanceChart(weeks, weeklyGoal, today)
        }
    }
}

@Composable
private fun ProgressExercises(exercises: List<Exercise>, history: List<Workout>, range: DateRange, initialExerciseId: String?) {
    val available = history.flatMap { it.items }
        .filter { it.hasPerformedWork }
        .distinctBy { Triple(it.row.exerciseId, it.row.equipmentId, it.row.basis) }
    val exerciseChoices = available.map { it.row.exerciseId }.distinct()
    var selected by rememberSaveable(initialExerciseId) { mutableStateOf(initialExerciseId) }
    val selectedId = selected?.takeIf { it in exerciseChoices } ?: exerciseChoices.firstOrNull()
    var basisChoice by rememberSaveable(selectedId) { mutableStateOf<String?>(null) }
    var showExercise by rememberSaveable(initialExerciseId) { mutableStateOf(initialExerciseId != null) }
    if (!showExercise) {
        ProgressExerciseList(exercises, available, history, range) {
            selected = it
            basisChoice = null
            showExercise = true
        }
        return
    }

    ListRow(stringResource(R.string.all_exercises), onClick = { showExercise = false })
    val bases = available.filter { it.row.exerciseId == selectedId }
    val item = bases.find { it.comparisonKey() == basisChoice }
        ?: defaultComparison(exercises.find { it.id == selectedId }, bases)
    if (item == null) {
        EmptyState(stringResource(R.string.no_performed_exercise))
        return
    }

    val query = ExerciseMetricQuery(item.row.exerciseId, item.row.equipmentId, item.row.basis, range)
    ProgressExerciseDetail(
        history,
        query,
        exerciseSelector = {
            ProgressExerciseSelector(
                exercises,
                available,
                exerciseChoices,
                item.row.exerciseId,
                onSelect = { selected = it },
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
            )
        },
        comparisonSelector = { ProgressComparisonSelector(item, bases) { basisChoice = it } }
    )
}

private fun defaultComparison(exercise: Exercise?, recorded: List<WorkoutItem>): WorkoutItem? {
    if (exercise == null) return recorded.firstOrNull()
    return recorded.firstOrNull { it.row.basis == exercise.basis && it.row.equipmentId == exercise.equipmentId }
        ?: recorded.firstOrNull { it.row.basis == exercise.basis }
        ?: recorded.firstOrNull()
}

private fun WorkoutItem.comparisonKey(): String = "${row.equipmentId}:${row.basis}"

@Composable
private fun ProgressExerciseList(
    exercises: List<Exercise>,
    available: List<WorkoutItem>,
    history: List<Workout>,
    range: DateRange,
    onSelect: (String) -> Unit
) {
    if (available.isEmpty()) return
    val context = LocalContext.current
    val resources = LocalResources.current
    SectionHeader(stringResource(R.string.exercises))
    ListCard {
        available.groupBy { it.row.exerciseId }.forEach { (exerciseId, recorded) ->
            val exercise = exercises.find { it.id == exerciseId }
            val representative = defaultComparison(exercise, recorded) ?: return@forEach
            val basis = MeasurementBasis.fromKey(representative.row.basis)
            val query = ExerciseMetricQuery(exerciseId, representative.row.equipmentId, representative.row.basis, range)
            val series = calculateExerciseSeries(history, query)
            val values = series.mapNotNull { it.value }
            val latest = values.lastOrNull()
            val change = latest?.let { it - values.first() }
            val subtitle = latest?.let {
                val current = basis.progressValue(context, it)
                when {
                    change == null || change.signum() == 0 -> current

                    else -> resources.getString(
                        R.string.progress_change,
                        current,
                        resources.getString(change.direction()),
                        basis.progressChange(context, change.abs())
                    )
                }
            } ?: resources.getString(R.string.done).takeIf { series.isNotEmpty() }
            item {
                ListRow(
                    exercise?.name ?: representative.row.name,
                    subtitle = subtitle,
                    trailing = if (values.size >= 2) ({ ExerciseSparkline(series) }) else null,
                    onClick = { onSelect(exerciseId) }
                )
            }
        }
    }
}

@Composable
private fun ProgressExerciseSelector(
    exercises: List<Exercise>,
    available: List<WorkoutItem>,
    exerciseChoices: List<String>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = exerciseChoices.map { id ->
        exercises.find { it.id == id }?.name
            ?: available.first { it.row.exerciseId == id }.row.name
    }
    SelectField(
        stringResource(R.string.exercise),
        selectedId,
        exerciseChoices,
        onSelect,
        modifier,
        inline = true,
        optionLabel = { id ->
            val index = exerciseChoices.indexOf(id)
            val label = labels[index]
            if (labels.count { it == label } > 1) "$label (${index + 1})" else label
        }
    )
}

@Composable
private fun ProgressComparisonSelector(item: WorkoutItem, bases: List<WorkoutItem>, onSelect: (String) -> Unit) {
    if (bases.size <= 1) return
    val resources = LocalResources.current
    val basisLabels = bases.map {
        "${it.row.equipmentName.ifBlank {
            resources.getString(R.string.recorded_equipment)
        }}, ${resources.getString(MeasurementBasis.fromKey(it.row.basis).optionLabel)}"
    }
    SelectField(
        stringResource(R.string.compare),
        item.comparisonKey(),
        bases.map { it.comparisonKey() },
        onSelect,
        optionLabel = { id ->
            val index = bases.indexOfFirst { it.comparisonKey() == id }
            val label = basisLabels[index]
            if (basisLabels.count { it == label } > 1) "$label (${index + 1})" else label
        }
    )
}

@Composable
private fun ProgressExerciseDetail(
    history: List<Workout>,
    query: ExerciseMetricQuery,
    exerciseSelector: @Composable FlowRowScope.() -> Unit,
    comparisonSelector: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = InShapeTheme.colors
    val points = calculateExerciseSeries(history, query)
    val basis = MeasurementBasis.fromKey(query.basis)
    val recorded = points.mapNotNull { point -> point.value?.let { point.date to it } }
    val baseline = recorded.firstOrNull()
    val latest = recorded.lastOrNull()
    val change = if (baseline != null && latest != null) {
        (latest.second - baseline.second).takeUnless { it.signum() == 0 }
    } else {
        null
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        ResponsiveRow {
            exerciseSelector()
            if (change != null) {
                val sign = if (change.signum() > 0) "+" else "−"
                FlowRow(
                    Modifier.align(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Pill(sign + basis.progressChange(context, change.abs()), tone = PillTone.Accent)
                    Text(
                        stringResource(R.string.progress_since_date, checkNotNull(baseline).first.axisDate()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }
        if (latest != null) {
            val current = latest.second
            val best = if (basis == MeasurementBasis.Assistance) recorded.minOf { it.second } else recorded.maxOf { it.second }
            Column {
                Text(basis.progressValue(context, current), style = MaterialTheme.typography.displaySmall)
                if (current.compareTo(best) == 0) {
                    Text(stringResource(R.string.best_so_far), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }
        comparisonSelector()
        ExerciseChart(points, basis)
    }
}

internal fun MeasurementBasis.progressValue(context: Context, value: BigDecimal): String {
    val text = value.stripTrailingZeros().toPlainString()
    return when (this) {
        MeasurementBasis.Time -> formatDuration(value.min(Long.MAX_VALUE.toBigDecimal()).toLong())
        else -> formatValue(context, text)
    }
}

private fun MeasurementBasis.progressChange(context: Context, value: BigDecimal): String = if (this == MeasurementBasis.MachineLevel) {
    val count = value.toInt()
    context.resources.getQuantityString(R.plurals.level_count, count, count)
} else {
    progressValue(context, value)
}

@Composable
private fun ProgressBodyObservations(observations: List<BodyObservation>, history: List<Workout>, range: DateRange) {
    val resources = LocalResources.current
    val colors = InShapeTheme.colors
    var region by rememberSaveable { mutableStateOf(observations.firstOrNull()?.region ?: "lower_back") }
    var showBody by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable(region, range) { mutableStateOf(false) }
    DisclosureRow(stringResource(R.string.pain_and_stiffness), showBody, { showBody = !showBody })
    if (showBody) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            val area = bodyRegions.firstOrNull { it.id == region } ?: bodyRegions.first()
            SelectField(
                stringResource(R.string.body_area),
                area.id,
                bodyRegions.map { it.id },
                { region = it },
                inline = true,
                optionLabel = { id -> resources.getString(bodyRegionLabel(id)) }
            )
            val points = observationSeries(history, observations, area.id, range)
            if (points.isEmpty()) {
                Text(
                    stringResource(R.string.no_area_observations, stringResource(area.label).lowercase(finnishLocale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            } else {
                ListCard {
                    val visible = if (showAll) points else points.take(INITIAL_OBSERVATION_COUNT)
                    visible.forEach { point ->
                        item {
                            ListRow(
                                point.date.shortDate(),
                                modifier = Modifier.semantics(mergeDescendants = true) {},
                                subtitle = when (point.phase) {
                                    "BEFORE" -> stringResource(R.string.before_workout)
                                    "AFTER" -> stringResource(R.string.after_workout)
                                    else -> stringResource(R.string.next_day)
                                },
                                trailingValue = { Pill(observationSummary(LocalContext.current, point.observation)) }
                            )
                        }
                    }
                }
                if (!showAll &&
                    points.size > INITIAL_OBSERVATION_COUNT
                ) {
                    TextAction(stringResource(R.string.show_all), { showAll = true }, icon = Icons.AutoMirrored.Outlined.List)
                }
            }
        }
    }
}

private fun BigDecimal.direction(): Int = if (signum() > 0) R.string.progress_up else R.string.progress_down

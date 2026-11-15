package com.emmah.inshape.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.NumericInputResult
import com.emmah.inshape.data.parseDecimalInput
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.feature.checkin.bodyRegions
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.FilterPill
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PillTone
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.SectionLabel
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.displayDate
import java.time.LocalDate
import java.util.Locale

@Composable
fun ProgressScreen(
    data: JournalSnapshot,
    today: LocalDate,
    modifier: Modifier = Modifier,
    initialExerciseId: String? = null,
    header: @Composable () -> Unit = {}
) {
    val colors = InShapeTheme.colors
    val history = data.history
    val available = history.flatMap {
        it.items
    }.filter {
        it.hasPerformedWork
    }.distinctBy { Triple(it.row.exerciseId, it.row.equipmentId, it.row.basis) }
    var selected by rememberSaveable(initialExerciseId) {
        mutableStateOf(
            initialExerciseId
                ?: available.firstOrNull { it.completedSets.isNotEmpty() }?.row?.exerciseId
                ?: available.firstOrNull()?.row?.exerciseId
        )
    }
    var period by rememberSaveable { mutableStateOf("12 weeks") }
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var load by rememberSaveable { mutableStateOf("") }
    var minimumReps by rememberSaveable { mutableStateOf("10") }
    var basisChoice by rememberSaveable(selected) { mutableStateOf<String?>(null) }
    val exerciseChoices = available.map { it.row.exerciseId }.distinct()
    val selectedId = selected?.takeIf { it in exerciseChoices } ?: exerciseChoices.firstOrNull()
    val bases = available.filter { it.row.exerciseId == selectedId }
    val item =
        bases.find { "${it.row.equipmentId}:${it.row.basis}" == basisChoice } ?: bases.firstOrNull()
    val earliest =
        history.filter {
            it.qualifiesForAttendance
        }.minOfOrNull { LocalDate.parse(it.row.journalDate) }?.coerceAtMost(today)
            ?: today
    val range = DateRange(
        if (period ==
            "All"
        ) {
            earliest
        } else {
            weekStart(today).minusWeeks(if (period == "4 weeks") 3 else 11)
        },
        today
    )
    val options =
        listOf(
            "Weight / resistance",
            "Repetitions",
            "Reps at a fixed load",
            "Load at minimum reps",
            "Set count"
        )
    val parsedLoad = (parseDecimalInput(load) as? NumericInputResult.Valid)?.value
    var region by rememberSaveable {
        mutableStateOf(data.observations.firstOrNull()?.region ?: "lower_back")
    }
    var phase by rememberSaveable { mutableStateOf("AFTER") }
    var showExercise by rememberSaveable(initialExerciseId) { mutableStateOf(initialExerciseId != null) }
    var showBody by rememberSaveable { mutableStateOf(false) }
    var showValues by rememberSaveable { mutableStateOf(false) }
    Page(modifier) {
        header()
        ActionRow {
            listOf("4 weeks", "12 weeks", "All").forEach { value -> FilterPill(value, period == value, { period = value }) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            val weeks = weeklyAttendance(history, range)
            ResponsiveRow {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                ) {
                    Text(
                        "Workouts per week",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary
                    )
                    ResponsiveRow {
                        Text(
                            String.format(
                                Locale.ENGLISH,
                                "%.1f",
                                averageWeeklyAttendance(history, range, today)
                            ),
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            "on average",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
                data.preferences.weeklyGoal?.let {
                    Text("Goal $it", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
            if (history.none { it.qualifiesForAttendance && it.row.journalDate in range }) {
                Text(
                    "No workouts recorded in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            } else {
                if (weeks.first().week > range.start) {
                    Text(
                        "Chart shows the latest ${weeks.size} weeks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                AttendanceChart(weeks, data.preferences.weeklyGoal, today)
            }
        }
        if (!showExercise) {
            SectionLabel("Exercises", quiet = true)
            ListCard {
                available.distinctBy { it.row.exerciseId }.forEach { exercise ->
                    val series =
                        calculateExerciseSeries(
                            history,
                            ExerciseMetricQuery(
                                exercise.row.exerciseId,
                                exercise.row.equipmentId,
                                exercise.row.basis,
                                range,
                                ExerciseMetric.WEIGHT
                            )
                        )
                    val values = series.mapNotNull { it.value }
                    val latest = values.lastOrNull()
                    val change = if (values.size > 1) values.last() - values.first() else null
                    item {
                        ListRow(
                            exercise.row.name,
                            subtitle = latest?.let { value ->
                                "${value.stripTrailingZeros().toPlainString()} ${exercise.row.basis}" +
                                    (
                                        change?.takeIf {
                                            it.signum() != 0
                                        }?.let {
                                            val direction = if (it.signum() > 0) "up" else "down"
                                            val amount = it.abs().stripTrailingZeros().toPlainString()
                                            ", $direction $amount ${exercise.row.basis}"
                                        }
                                            ?: ""
                                        )
                            } ?: "Completion recorded",
                            trailing = { ExerciseSparkline(series) },
                            onClick = {
                                selected = exercise.row.exerciseId
                                showExercise =
                                    true
                            }
                        )
                    }
                }
            }
        } else {
            TextAction("All exercises", { showExercise = false })
        }
        if (showExercise) {
            if (item ==
                null
            ) {
                EmptyState(
                    "Your recorded progress",
                    "Record a completed exercise to start an exercise chart."
                )
            } else {
                val query =
                    ExerciseMetricQuery(
                        item.row.exerciseId,
                        item.row.equipmentId,
                        item.row.basis,
                        range,
                        ExerciseMetric.entries[metric],
                        parsedLoad,
                        minimumReps.toIntOrNull()?.coerceAtLeast(1) ?: 10
                    )
                val points = calculateExerciseSeries(history, query)
                val unit = when (query.metric) {
                    ExerciseMetric.REPS, ExerciseMetric.REPS_AT_LOAD -> "reps"

                    ExerciseMetric.SET_COUNT -> "sets"

                    else -> if (query.basis ==
                        "assistance_kg"
                    ) {
                        "kg assistance"
                    } else {
                        query.basis
                    }
                }
                val recorded = points.filter { it.value != null }
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                    val labels = exerciseChoices.map { id ->
                        data.exercises.find { it.id == id }?.name
                            ?: available.first { it.row.exerciseId == id }.row.name
                    }
                    ResponsiveRow {
                        SelectField(
                            "Exercise",
                            selectedId!!,
                            exerciseChoices,
                            { selected = it },
                            Modifier.weight(1f),
                            inline = true,
                            optionLabel = { id ->
                                val index = exerciseChoices.indexOf(id)
                                val label = labels[index]
                                if (labels.count { it == label } > 1) "$label (${index + 1})" else label
                            }
                        )
                        if (recorded.size >= 2) {
                            val change = recorded.last().value!! - recorded.first().value!!
                            if (change.signum() !=
                                0
                            ) {
                                Pill(
                                    "${if (change.signum() > 0) "+" else ""}${change.stripTrailingZeros().toPlainString()} $unit",
                                    tone = PillTone.Accent
                                )
                            }
                        }
                    }
                    recorded.lastOrNull()?.let { latest ->
                        ResponsiveRow {
                            Text(
                                "${latest.value!!.stripTrailingZeros().toPlainString()} $unit",
                                style = MaterialTheme.typography.displaySmall
                            )
                            if (recorded.size >=
                                2
                            ) {
                                Text(
                                    "was ${recorded.first().value!!.stripTrailingZeros().toPlainString()}" +
                                        " on ${recorded.first().date.displayDate()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                    SegmentedControl(
                        listOf("Weight", "Reps", "Sets"),
                        when (metric) {
                            0, 3 -> 0
                            1, 2 -> 1
                            else -> 2
                        },
                        {
                            metric =
                                listOf(0, 1, 4)[it]
                        }
                    )
                    SelectField("Comparison", options[metric], options, {
                        metric = options.indexOf(it)
                    })
                    val basisLabels = bases.map {
                        "${it.row.equipmentName.ifBlank { "Recorded equipment" }}, ${it.row.basis}"
                    }
                    if (bases.size >
                        1
                    ) {
                        SelectField(
                            "Compare",
                            "${item.row.equipmentId}:${item.row.basis}",
                            bases.map { "${it.row.equipmentId}:${it.row.basis}" },
                            { basisChoice = it },
                            optionLabel = { id ->
                                val index = bases.indexOfFirst { "${it.row.equipmentId}:${it.row.basis}" == id }
                                val label = basisLabels[index]
                                if (basisLabels.count { it == label } > 1) "$label (${index + 1})" else label
                            }
                        )
                    }
                    if (metric ==
                        2
                    ) {
                        NumberField(load, {
                            load = it
                        }, "Compare at ${item.row.basis}", decimal = true)
                    }
                    if (metric ==
                        3
                    ) {
                        NumberField(minimumReps, { minimumReps = it }, "Minimum repetitions")
                        Text(
                            "${query.minimumReps}+ reps",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (metric == 2 &&
                        parsedLoad == null
                    ) {
                        Text(
                            "Enter a load to compare repetitions.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ExerciseChart(points, unit)
                    }
                    if (metric ==
                        1
                    ) {
                        Text(
                            "Highest recorded reps per workout. Loads may differ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    if (metric ==
                        4
                    ) {
                        Text(
                            "Completed detailed sets only. Completion-only entries have no detailed sets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
                calculatePersonalBest(history, query)?.let { best ->
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                        ResponsiveRow {
                            Text(
                                if (best.previous ==
                                    null
                                ) {
                                    "First recorded result"
                                } else if (best.isNewRecord) {
                                    "New record in this period"
                                } else {
                                    "Best in this period"
                                },
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textSecondary
                            )
                            if (best.isNewRecord) Pill("New record", tone = PillTone.Accent)
                        }
                        Text(
                            "${best.point.value!!.stripTrailingZeros().toPlainString()} $unit",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            best.point.date.displayDate() + ", " + options[metric] +
                                if (metric ==
                                    3
                                ) {
                                    ", ${query.minimumReps}+ reps"
                                } else if (metric ==
                                    2
                                ) {
                                    ", at ${query.fixedLoad} ${query.basis}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        best.previous?.let {
                            Text(
                                "Previous best: ${it.value!!.stripTrailingZeros().toPlainString()} $unit on ${it.date.displayDate()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
        ListRow(
            "Pain and stiffness",
            subtitle = data.observations.mapNotNull { observation ->
                bodyRegions.find {
                    it.id ==
                        observation.region
                }?.label
            }.distinct().joinToString(", ").ifBlank { null },
            onClick = { showBody = !showBody }
        )
        if (showBody) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                val area = bodyRegions.firstOrNull { it.id == region } ?: bodyRegions.first()
                SelectField("Body area", area.label, bodyRegions.map { it.label }, { label ->
                    region =
                        bodyRegions.first { it.label == label }.id
                }, inline = true)
                Text(
                    "Scale 0 to 10",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                val phases = listOf("BEFORE", "AFTER", "NEXT_DAY")
                SegmentedControl(listOf("Before", "After", "Next day"), phases.indexOf(phase), {
                    phase =
                        phases[it]
                })
                val points = observationSeries(history, data.observations, region, phase, range)
                if (points.isEmpty()) {
                    Text(
                        "No observations for ${area.label.lowercase(Locale.ENGLISH)} in this period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                } else {
                    ResponsiveRow {
                        ObservationLegend("Pain", points.lastOrNull { it.pain != null }?.pain, false)
                        ObservationLegend(
                            "Stiffness",
                            points.lastOrNull {
                                it.stiffness != null
                            }?.stiffness,
                            true
                        )
                    }
                    ObservationChart(points)
                    TextAction(if (showValues) "Hide values" else "View values", {
                        showValues =
                            !showValues
                    })
                    if (showValues) {
                        points.forEach {
                            Text(
                                "${it.date.displayDate()}: pain ${it.pain?.let { score ->
                                    com.emmah.inshape.feature.checkin.bodyLevelOf(score)!!.label.lowercase()
                                } ?: "not recorded"}, stiffness " +
                                    "${it.stiffness?.let { score ->
                                        com.emmah.inshape.feature.checkin.bodyLevelOf(score)!!.label.lowercase()
                                    } ?: "not recorded"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.emmah.inshape.feature.progress

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.emmah.inshape.data.*
import com.emmah.inshape.feature.checkin.bodyRegions
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import java.time.LocalDate
import java.util.Locale

@Composable
fun ProgressScreen(data: JournalSnapshot, today: LocalDate, initialExerciseId: String? = null) {
    val colors = InShapeTheme.colors
    val history = data.history
    val available = history.flatMap { it.items }.filter { it.hasPerformedWork }.distinctBy { Triple(it.row.exerciseId, it.row.equipmentId, it.row.basis) }
    var selected by rememberSaveable(initialExerciseId) { mutableStateOf(initialExerciseId ?: available.firstOrNull { it.completedSets.isNotEmpty() }?.row?.exerciseId ?: available.firstOrNull()?.row?.exerciseId) }
    var period by rememberSaveable { mutableStateOf("12 weeks") }
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var load by rememberSaveable { mutableStateOf("") }
    var minimumReps by rememberSaveable { mutableStateOf("10") }
    var basisChoice by rememberSaveable(selected) { mutableStateOf<String?>(null) }
    val exerciseChoices = available.map { it.row.exerciseId }.distinct()
    val selectedId = selected?.takeIf { it in exerciseChoices } ?: exerciseChoices.firstOrNull()
    val bases = available.filter { it.row.exerciseId == selectedId }
    val item = bases.find { "${it.row.equipmentId}:${it.row.basis}" == basisChoice } ?: bases.firstOrNull()
    val earliest = history.filter { it.qualifiesForAttendance }.minOfOrNull { LocalDate.parse(it.row.journalDate) }?.coerceAtMost(today) ?: today
    val range = DateRange(if (period == "All") earliest else weekStart(today).minusWeeks(if (period == "4 weeks") 3 else 11), today)
    val options = listOf("Weight / resistance", "Repetitions", "Reps at a fixed load", "Load at minimum reps", "Set count")
    val parsedLoad = (parseDecimalInput(load) as? NumericInputResult.Valid)?.value
    var region by rememberSaveable { mutableStateOf(data.observations.firstOrNull()?.region ?: "lower_back") }
    var phase by rememberSaveable { mutableStateOf("AFTER") }
    var showValues by rememberSaveable { mutableStateOf(false) }
    Page {
        ScreenHeader("Progress")
        SegmentedControl(listOf("4 weeks", "12 weeks", "All"), listOf("4 weeks", "12 weeks", "All").indexOf(period), { period = listOf("4 weeks", "12 weeks", "All")[it] })
        InShapeCard {
            val weeks = weeklyAttendance(history, range)
            ResponsiveRow {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    Text("Workouts per week", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    ResponsiveRow {
                        Text(String.format(Locale.ENGLISH, "%.1f", averageWeeklyAttendance(weeks, range, today)), style = MaterialTheme.typography.displaySmall)
                        Text("on average", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
                data.preferences.weeklyGoal?.let { Pill("Goal $it", tone = PillTone.Accent) }
            }
            if (history.none { it.qualifiesForAttendance && it.row.journalDate in range }) Text("No workouts recorded in this period.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            else AttendanceChart(weeks, data.preferences.weeklyGoal, today)
        }
        if (item == null) EmptyState("Your recorded progress", "Record a completed exercise to start an exercise chart.")
        else {
            val query = ExerciseMetricQuery(item.row.exerciseId, item.row.equipmentId, item.row.basis, range, ExerciseMetric.entries[metric], parsedLoad, minimumReps.toIntOrNull()?.coerceAtLeast(1) ?: 10)
            val points = calculateExerciseSeries(history, query)
            val unit = when (query.metric) { ExerciseMetric.REPS, ExerciseMetric.REPS_AT_LOAD -> "reps"; ExerciseMetric.SET_COUNT -> "sets"; else -> if (query.basis == "assistance_kg") "kg assistance" else query.basis }
            val recorded = points.filter { it.value != null }
            InShapeCard {
                val labels = exerciseChoices.map { id -> data.exercises.find { it.id == id }?.name ?: available.first { it.row.exerciseId == id }.row.name }
                ResponsiveRow {
                    SelectField("Exercise", labels[exerciseChoices.indexOf(selectedId)], labels, { label -> selected = exerciseChoices[labels.indexOf(label)] }, Modifier.weight(1f), inline = true)
                    if (recorded.size >= 2) {
                        val change = recorded.last().value!! - recorded.first().value!!
                        if (change.signum() != 0) Pill("${if (change.signum() > 0) "+" else ""}${change.stripTrailingZeros().toPlainString()} $unit", tone = PillTone.Accent)
                    }
                }
                recorded.lastOrNull()?.let { latest -> ResponsiveRow {
                    Text("${latest.value!!.stripTrailingZeros().toPlainString()} $unit", style = MaterialTheme.typography.displaySmall)
                    if (recorded.size >= 2) Text("was ${recorded.first().value!!.stripTrailingZeros().toPlainString()} on ${recorded.first().date.displayDate()}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                } }
                SegmentedControl(listOf("Weight", "Reps", "Sets"), when (metric) { 0, 3 -> 0; 1, 2 -> 1; else -> 2 }, { metric = listOf(0, 1, 4)[it] }, onCard = true)
                SelectField("Comparison", options[metric], options, { metric = options.indexOf(it) })
                val basisLabels = bases.map { "${it.row.equipmentName.ifBlank { "Recorded equipment" }}, ${it.row.basis}" }
                if (bases.size > 1) SelectField("Compare", "${item.row.equipmentName.ifBlank { "Recorded equipment" }}, ${item.row.basis}", basisLabels, { label -> val chosen = bases[basisLabels.indexOf(label)]; basisChoice = "${chosen.row.equipmentId}:${chosen.row.basis}" })
                if (metric == 2) NumberField(load, { load = it }, "Compare at ${item.row.basis}", decimal = true)
                if (metric == 3) { NumberField(minimumReps, { minimumReps = it }, "Minimum repetitions"); Text("${query.minimumReps}+ reps", style = MaterialTheme.typography.bodySmall) }
                if (metric == 2 && parsedLoad == null) Text("Enter a load to compare repetitions.", style = MaterialTheme.typography.bodySmall) else ExerciseChart(points, unit)
                if (metric == 1) Text("Highest recorded reps per workout. Loads may differ.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                if (metric == 4) Text("Completed detailed sets only. Completion-only entries have no detailed sets.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            calculatePersonalBest(history, query)?.let { best -> InShapeCard {
                ResponsiveRow {
                    Text(if (best.previous == null) "First recorded result" else if (best.isNewRecord) "New record in this period" else "Best in this period", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    if (best.isNewRecord) Pill("New record", tone = PillTone.Accent)
                }
                Text("${best.point.value!!.stripTrailingZeros().toPlainString()} $unit", style = MaterialTheme.typography.headlineSmall)
                Text(best.point.date.displayDate() + ", " + options[metric] + if (metric == 3) ", ${query.minimumReps}+ reps" else if (metric == 2) ", at ${query.fixedLoad} ${query.basis}" else "", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                best.previous?.let { Text("Previous best: ${it.value!!.stripTrailingZeros().toPlainString()} $unit on ${it.date.displayDate()}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
            } }
        }
        InShapeCard {
            val area = bodyRegions.firstOrNull { it.id == region } ?: bodyRegions.first()
            SelectField("Body area", area.label, bodyRegions.map { it.label }, { label -> region = bodyRegions.first { it.label == label }.id }, inline = true)
            Text("Scale 0 to 10", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            val phases = listOf("BEFORE", "AFTER", "NEXT_DAY")
            SegmentedControl(listOf("Before", "After", "Next day"), phases.indexOf(phase), { phase = phases[it] }, onCard = true)
            val points = observationSeries(history, data.observations, region, phase, range)
            if (points.isEmpty()) Text("No observations for ${area.label.lowercase(Locale.ENGLISH)} in this period.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            else {
                ResponsiveRow {
                    ObservationLegend("Pain", points.lastOrNull { it.pain != null }?.pain, false)
                    ObservationLegend("Stiffness", points.lastOrNull { it.stiffness != null }?.stiffness, true)
                }
                ObservationChart(points)
                TextAction(if (showValues) "Hide values" else "View values", { showValues = !showValues })
                if (showValues) points.forEach { Text("${it.date.displayDate()}: pain ${it.pain?.toString() ?: "not recorded"}, stiffness ${it.stiffness?.toString() ?: "not recorded"}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
            }
        }
    }
}

package com.emmah.inshape.feature.assistant

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.emmah.inshape.R
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.feature.checkin.bodyRegionLabel
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.library.locationLabel
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.components.TextFieldKeyboard
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.ui.theme.longDate
import com.emmah.inshape.ui.theme.shortDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal enum class EvidenceMetric {
    SUMMARY,
    LAST,
    USUAL,
    PROGRESS,
    CHECK_IN
}

internal data class EvidenceSource(val workoutId: String, val date: String, val detail: String)
internal data class EvidenceResult(val finding: String, val sources: List<EvidenceSource>)
internal data class EvidenceRequest(
    val metric: EvidenceMetric,
    val range: DateRange,
    val exercise: Exercise? = null,
    val phase: String = "NEXT_DAY",
    val region: String? = null,
    val workoutId: String? = null
)

private val latestWorkout = compareByDescending<Workout> { it.row.journalDate }
    .thenByDescending { it.row.start ?: it.row.createdAt }.thenByDescending { it.row.id }

internal fun journalEvidence(context: Context, data: JournalSnapshot, request: EvidenceRequest): EvidenceResult {
    val visits = data.historyIn(request.range).sortedWith(latestWorkout).filter(Workout::qualifiesForAttendance)
        .filter { request.workoutId == null || it.row.id == request.workoutId }
    val exercise = request.exercise
    val uses = if (exercise == null) {
        emptyList()
    } else {
        visits.flatMap { workout ->
            workout.items.filter { item ->
                val row = item.row
                item.hasPerformedWork && row.exerciseId == exercise.id && row.equipmentId == exercise.equipmentId &&
                    row.basis == exercise.basis
            }.map { workout to it }
        }
    }
    return when (request.metric) {
        EvidenceMetric.SUMMARY -> workoutSummary(context, visits)

        EvidenceMetric.CHECK_IN -> checkInEvidence(context, data, visits, request)

        else -> if (exercise == null) {
            EvidenceResult(context.getString(R.string.evidence_choose_exercise), emptyList())
        } else {
            exerciseEvidence(context, uses, exercise, request.metric)
        }
    }
}

private fun workoutSummary(context: Context, visits: List<Workout>): EvidenceResult {
    val time = visits.mapNotNull { it.row.durationSeconds }
    val coverage = if (time.isEmpty()) {
        context.getString(R.string.duration_not_recorded)
    } else {
        context.resources.getQuantityString(
            R.plurals.evidence_duration_coverage,
            time.size,
            time.size,
            time.sum() / 60
        )
    }
    val finding = context.getString(
        R.string.evidence_workout_count,
        context.resources.getQuantityString(R.plurals.workout_count, visits.size, visits.size),
        coverage.lowercase().trimEnd('.')
    )
    return EvidenceResult(
        finding,
        visits.take(50).map { workout ->
            val done = workout.items.filter { it.hasPerformedWork }.joinToString(", ") { it.row.name }
            val skipped = workout.items.filter {
                !it.hasPerformedWork && it.row.disposition == "SKIPPED"
            }.joinToString(", ") { it.row.name }
            val empty = workout.items.filter { !it.hasPerformedWork && it.row.disposition != "SKIPPED" }
                .joinToString(", ") { it.row.name }
            val details = listOf(
                context.getString(R.string.evidence_done, done),
                skipped.takeIf(String::isNotBlank)?.let { context.getString(R.string.evidence_skipped, it) },
                empty.takeIf(String::isNotBlank)?.let { context.getString(R.string.evidence_not_performed, it) },
                workout.row.note.takeIf(String::isNotBlank)
            ).filterNotNull().joinToString("\n")
            workout.source(details)
        }
    )
}

private fun exerciseEvidence(
    context: Context,
    uses: List<Pair<Workout, WorkoutItem>>,
    exercise: Exercise,
    metric: EvidenceMetric
): EvidenceResult {
    if (uses.isEmpty()) return EvidenceResult(context.getString(R.string.evidence_no_matching_work), emptyList())
    val basis = MeasurementBasis.fromKey(exercise.basis)
    if (metric == EvidenceMetric.LAST) {
        val (workout, item) = uses.first()
        return lastVisitEvidence(context, workout, item, basis)
    }

    val sets = uses.filter { !it.second.row.plan.isWarmup() }.flatMap { (workout, item) ->
        item.completedSets.map { workout to it }
    }
    val sources = uses.map { it.first }.distinctBy { it.row.id }.take(50).map { it.source(it.row.title) }
    return if (metric == EvidenceMetric.USUAL) {
        usualLoadEvidence(context, basis, sets, sources)
    } else {
        val newest = uses.first()
        val oldest = uses.last()
        if (oldest.first.row.id == newest.first.row.id) {
            EvidenceResult(context.getString(R.string.evidence_need_two_visits), sources)
        } else {
            comparisonEvidence(context, basis, oldest, newest, sets)
        }
    }
}

private fun lastVisitEvidence(context: Context, workout: Workout, item: WorkoutItem, basis: MeasurementBasis): EvidenceResult {
    val duration = item.row.durationSeconds?.let { context.getString(R.string.saved_duration, it) }
    val sets = item.completedSets.joinToString(", ") { set ->
        listOfNotNull(
            set.load?.let { basis.formatValue(context, it) },
            set.reps?.let { context.getString(R.string.rep_value, it.toString()) }
        ).joinToString(" × ")
    }
    val measurements = listOfNotNull(duration, sets.takeIf(String::isNotBlank)).joinToString("\n")
        .ifBlank { context.getString(R.string.evidence_no_measured_set) }
    return EvidenceResult(
        context.getString(R.string.evidence_last_visit, LocalDate.parse(workout.row.journalDate).longDate()),
        listOf(workout.source(measurements))
    )
}

private fun usualLoadEvidence(
    context: Context,
    basis: MeasurementBasis,
    sets: List<Pair<Workout, SessionSet>>,
    sources: List<EvidenceSource>
): EvidenceResult {
    val loads = sets.mapNotNull { it.second.load?.toBigDecimalOrNull()?.stripTrailingZeros() }.groupingBy { it }.eachCount()
    val maximum = loads.values.maxOrNull() ?: return EvidenceResult(context.getString(R.string.evidence_no_load), sources)
    val modes = loads.filterValues { it == maximum }.keys.sorted().joinToString(", ") { basis.formatValue(context, it.toPlainString()) }
    val sessions = sets.filter { it.second.load != null }.map { it.first.row.id }.distinct().size
    val total = loads.values.sum()
    val resources = context.resources
    return EvidenceResult(
        context.getString(
            R.string.evidence_common_load,
            modes,
            resources.getQuantityString(R.plurals.set_count, maximum, maximum),
            resources.getQuantityString(R.plurals.set_count, total, total),
            resources.getQuantityString(R.plurals.workout_count, sessions, sessions)
        ),
        sources
    )
}

private fun comparisonEvidence(
    context: Context,
    basis: MeasurementBasis,
    oldestUse: Pair<Workout, WorkoutItem>,
    newestUse: Pair<Workout, WorkoutItem>,
    sets: List<Pair<Workout, SessionSet>>
): EvidenceResult {
    val (oldest, firstItem) = oldestUse
    val (newest, lastItem) = newestUse
    val before = sets.filter { it.first.row.id == oldest.row.id }.map { it.second }
    val after = sets.filter { it.first.row.id == newest.row.id }.map { it.second }
    val durations = if (basis == MeasurementBasis.Time) {
        val first = firstItem.row.durationSeconds
        val last = lastItem.row.durationSeconds
        if (first != null && last != null) context.getString(R.string.evidence_duration_change, first, last) else null
    } else {
        null
    }
    val finding = durations ?: sameLoadFinding(context, basis, before, after) ?: sameRepsFinding(context, basis, before, after)
        ?: context.getString(R.string.evidence_no_comparison)
    return EvidenceResult(
        context.getString(
            R.string.evidence_comparison,
            LocalDate.parse(oldest.row.journalDate).shortDate(),
            LocalDate.parse(newest.row.journalDate).shortDate(),
            finding.trimEnd('.'),
            before.size,
            after.size
        ),
        listOf(
            oldest.source(context.getString(R.string.evidence_first_workout)),
            newest.source(context.getString(R.string.evidence_last_workout))
        )
    )
}

private fun sameLoadFinding(context: Context, basis: MeasurementBasis, before: List<SessionSet>, after: List<SessionSet>): String? =
    before.firstNotNullOfOrNull { old ->
        if (old.reps == null || (old.load == null && basis != MeasurementBasis.Bodyweight)) return@firstNotNullOfOrNull null
        after.find { it.perSide == old.perSide && it.load == old.load && it.reps != null }?.let { new ->
            context.getString(
                R.string.evidence_same_load,
                old.load?.let { basis.formatValue(context, it) }.orEmpty(),
                old.reps.toString(),
                new.reps.toString()
            )
        }
    }

private fun sameRepsFinding(context: Context, basis: MeasurementBasis, before: List<SessionSet>, after: List<SessionSet>): String? =
    before.firstNotNullOfOrNull { old ->
        if (old.reps == null || old.load == null) return@firstNotNullOfOrNull null
        after.find { it.perSide == old.perSide && it.reps == old.reps && it.load != null }?.let { new ->
            context.getString(
                R.string.evidence_same_reps,
                old.reps.toString(),
                basis.formatValue(context, old.load),
                basis.formatValue(context, requireNotNull(new.load))
            )
        }
    }

private fun com.emmah.inshape.data.ExercisePlan?.isWarmup() = this?.warmup == true

private fun checkInEvidence(context: Context, data: JournalSnapshot, visits: List<Workout>, request: EvidenceRequest): EvidenceResult {
    val forms = visits.mapNotNull { workout -> workout.checkIns.find { it.phase == request.phase }?.let { workout to it } }
    val findings = forms.map { (workout, check) ->
        val observations = data.observations.filter { it.checkInId == check.id && (request.region == null || it.region == request.region) }
        val date = Instant.parse(check.observedAt).atZone(ZoneId.of(check.zoneId)).toLocalDate()
        val reported = observations.joinToString("\n") { value ->
            context.getString(
                R.string.evidence_body_values,
                context.getString(bodyRegionLabel(value.region)),
                value.pain?.toString() ?: context.getString(R.string.not_recorded).lowercase(),
                value.stiffness?.toString() ?: context.getString(R.string.not_recorded).lowercase()
            ) + value.note.takeIf(String::isNotBlank)?.let { "\n$it" }.orEmpty()
        }.ifBlank { context.getString(R.string.evidence_no_observations) }
        val note = listOf(check.feelings, check.note).filter(String::isNotBlank).joinToString("\n") {
            context.getString(R.string.evidence_recorded_text, it)
        }
        val description = listOf(reported, note).filter(String::isNotBlank).joinToString("\n")
        Triple(
            EvidenceSource(workout.row.id, date.toString(), description),
            observations.any { (it.stiffness ?: 0) > 0 },
            observations.any { (it.pain ?: 0) > 0 }
        )
    }
    val tally = findings.count { it.second }
    val pain = findings.count { it.third }
    val missing = visits.filter { visit ->
        forms.none { it.first.row.id == visit.row.id }
    }.map { it.source(context.getString(R.string.evidence_no_form)) }
    return EvidenceResult(
        context.resources.getQuantityString(R.plurals.evidence_pain_count, forms.size, tally, forms.size, pain, missing.size),
        (findings.map { it.first } + missing).sortedByDescending { it.date }.take(50)
    )
}

private fun Workout.source(detail: String) = EvidenceSource(row.id, row.journalDate, detail)

internal fun evidenceDateRange(question: String, today: LocalDate): DateRange {
    val lower = question.lowercase()
    val explicit = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").findAll(lower).take(2)
        .map { LocalDate.parse(it.value) }.toList()
    if (explicit.isNotEmpty()) {
        val end = explicit.last()
        requireUi(end >= explicit.first(), R.string.evidence_date_order)
        return DateRange(explicit.first(), end)
    }
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return when {
        "viime kuussa" in lower || "viime kuukausi" in lower -> YearMonth.from(today.minusMonths(1)).let {
            DateRange(it.atDay(1), it.atEndOfMonth())
        }

        "viime viikolla" in lower || "viime viikko" in lower -> DateRange(thisWeek.minusWeeks(1), thisWeek.minusDays(1))

        "tällä viikolla" in lower || "tämä viikko" in lower -> DateRange(thisWeek, today)

        else -> DateRange(today.minusDays(29), today)
    }
}

@Composable
internal fun JournalEvidenceScreen(
    data: JournalSnapshot,
    today: LocalDate,
    exerciseId: String?,
    workoutId: String?,
    onWorkout: (String) -> Unit
) {
    var question by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf<String?>(null) }
    var metric by rememberSaveable(exerciseId) { mutableStateOf(if (exerciseId == null) EvidenceMetric.SUMMARY else EvidenceMetric.LAST) }
    var selected by rememberSaveable(exerciseId) { mutableStateOf(exerciseId) }
    var showExercises by rememberSaveable { mutableStateOf(false) }
    var phase by rememberSaveable { mutableStateOf("NEXT_DAY") }
    val exercise = data.exercises.find { it.id == selected }
    val workoutDate = workoutId?.let { data.workout(it)?.row?.journalDate?.let(LocalDate::parse) }
    val focus = LocalFocusManager.current
    val submit = {
        if (question.isNotBlank()) {
            val lower = question.lowercase()
            metric = when {
                "jäyk" in lower || "kipu" in lower -> EvidenceMetric.CHECK_IN
                "yleensä" in lower || "tavall" in lower -> EvidenceMetric.USUAL
                "kehity" in lower || "parant" in lower -> EvidenceMetric.PROGRESS
                "viimeksi" in lower -> EvidenceMetric.LAST
                else -> metric
            }
            applied = question
            focus.clearFocus()
        }
    }
    Page {
        TextField(
            question,
            stringResource(R.string.question),
            { question = it.take(4000) },
            trailingIcon = {
                IconButton(submit, enabled = question.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.ask))
                }
            },
            keyboard = TextFieldKeyboard(
                options = KeyboardOptions(imeAction = ImeAction.Send),
                actions = KeyboardActions(onSend = { submit() })
            )
        )
        ListRow(stringResource(R.string.evidence_summary), onClick = {
            metric = EvidenceMetric.SUMMARY
            applied = ""
            focus.clearFocus()
        })
        if (metric != EvidenceMetric.SUMMARY && metric != EvidenceMetric.CHECK_IN) {
            OutlineButton(exercise?.name ?: stringResource(R.string.choose_exercise_equipment), { showExercises = !showExercises })
            if (showExercises) {
                ListCard {
                    data.exercises.forEach { candidate ->
                        item {
                            ListRow(
                                candidate.name.exerciseDisplayName(
                                    LocalContext.current
                                ),
                                subtitle = listOf(
                                    candidate.location.locationLabel(LocalContext.current),
                                    candidate.equipmentName.exerciseDisplayName(LocalContext.current)
                                ).filter(String::isNotBlank).joinToString(", "),
                                onClick = {
                                    selected = candidate.id
                                    showExercises = false
                                }
                            )
                        }
                    }
                }
            }
        }
        if (metric == EvidenceMetric.CHECK_IN) {
            OutlineButton(
                when (phase) {
                    "BEFORE" -> stringResource(R.string.before_workout)
                    "AFTER" -> stringResource(R.string.after_workout)
                    else -> stringResource(R.string.next_day)
                },
                {
                    phase = when (phase) {
                        "BEFORE" -> "AFTER"
                        "AFTER" -> "NEXT_DAY"
                        else -> "BEFORE"
                    }
                }
            )
        }
        applied?.let { query ->
            val range = runCatching { if (workoutDate == null) evidenceDateRange(query, today) else DateRange(workoutDate, workoutDate) }
                .getOrNull()
            if (range == null) {
                Text(stringResource(R.string.invalid_date_range))
            } else {
                val result =
                    journalEvidence(LocalContext.current, data, EvidenceRequest(metric, range, exercise, phase, workoutId = workoutId))
                Text(
                    stringResource(R.string.date_range, range.start.shortDate(), range.end.shortDate()),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(result.finding)
                if (result.sources.isNotEmpty()) {
                    ListCard {
                        result.sources.forEach { source ->
                            item {
                                ListRow(LocalDate.parse(source.date).shortDate(), subtitle = source.detail, onClick = {
                                    onWorkout(source.workoutId)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.emmah.inshape.feature.assistant

import com.emmah.inshape.R
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.ExercisePlan
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.testContext
import com.emmah.inshape.ui
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalEvidenceTest {
    @Test fun dateRangesIncludeCalendarBoundariesAndRejectReversedDays() {
        val today = LocalDate.parse("2024-03-06")
        val examples = mapOf(
            "viime kuussa" to ("2024-02-01" to "2024-02-29"),
            "viime viikolla" to ("2024-02-26" to "2024-03-03"),
            "tällä viikolla" to ("2024-03-04" to "2024-03-06"),
            "viimeksi" to ("2024-02-06" to "2024-03-06"),
            "2024-02-10" to ("2024-02-10" to "2024-02-10"),
            "2024-02-09–2024-02-11" to ("2024-02-09" to "2024-02-11")
        )
        examples.forEach { (question, bounds) ->
            assertEquals(question, DateRange(LocalDate.parse(bounds.first), LocalDate.parse(bounds.second)), evidenceDateRange(question, today))
        }
        assertThrows(IllegalArgumentException::class.java) { evidenceDateRange("2024-03-04 2024-03-01", today) }
    }

    @Test fun usualLoadUsesOnlyCompletedMatchingNonWarmupSetsAndMergesDecimalSpellings() {
        val (exercise, data) = matchingVisits("kg")
        val first = data.items.first()
        val last = data.items.last()
        val warmup = last.copy(id = "warmup", plan = ExercisePlan(warmup = true))
        val otherMachine = last.copy(id = "other-machine", equipmentId = "different-machine")
        val mixed = data.copy(
            items = data.items + listOf(warmup, otherMachine),
            sets = listOf(
                recordedSet(first, "30.0", 8),
                recordedSet(last, "30", 10),
                recordedSet(last, "50", 6),
                recordedSet(warmup, "10", 5),
                recordedSet(otherMachine, "100", 6),
                recordedSet(last, "70", 5).copy(completedAt = null)
            )
        )
        val context = testContext()
        val request = EvidenceRequest(EvidenceMetric.USUAL, fixtureRange, exercise)
        val result = journalEvidence(context, mixed, request)
        assertEquals(
            ui(
                R.string.evidence_common_load,
                MeasurementBasis.Weight.formatValue(context, "30"),
                context.resources.getQuantityString(R.plurals.set_count, 2, 2),
                context.resources.getQuantityString(R.plurals.set_count, 3, 3),
                context.resources.getQuantityString(R.plurals.workout_count, 2, 2)
            ),
            result.finding
        )
        assertEquals(data.workouts.reversed().map { it.id }, result.sources.map(EvidenceSource::workoutId))
        assertEquals(ui(R.string.evidence_no_load), journalEvidence(context, data, request).finding)
        assertEquals(ui(R.string.evidence_choose_exercise), journalEvidence(context, mixed, request.copy(exercise = null)).finding)
        assertEquals(
            ui(R.string.evidence_no_matching_work),
            journalEvidence(context, mixed, request.copy(exercise = exercise.copy(equipmentId = "not-used"))).finding
        )
    }

    @Test fun progressComparesOnlySetsWithComparableMeasurementAndSide() {
        val (exercise, data) = matchingVisits("kg")
        val before = data.items.first()
        val after = data.items.last()
        val context = testContext()
        val request = EvidenceRequest(EvidenceMetric.PROGRESS, fixtureRange, exercise)
        val load30 = MeasurementBasis.Weight.formatValue(context, "30")
        val load40 = MeasurementBasis.Weight.formatValue(context, "40")
        val cases = listOf(
            (recordedSet(after, "30", 12) to ui(R.string.evidence_same_load, load30, "8", "12")),
            (recordedSet(after, "40", 8) to ui(R.string.evidence_same_reps, "8", load30, load40)),
            (recordedSet(after, "30", 8).copy(perSide = true) to ui(R.string.evidence_no_comparison))
        )
        cases.forEach { (set, expected) ->
            val result = journalEvidence(context, data.copy(sets = listOf(recordedSet(before, "30", 8), set)), request)
            assertTrue(result.finding, result.finding.contains(expected.trimEnd('.')))
            assertEquals(data.workouts.map { it.id }, result.sources.map(EvidenceSource::workoutId))
        }
        val single = request.copy(workoutId = data.workouts.last().id)
        assertEquals(ui(R.string.evidence_need_two_visits), journalEvidence(context, data, single).finding)
    }

    @Test fun durationProgressUsesActualMeasuredSeconds() {
        val (exercise, data) = matchingVisits("duration")
        val measured = data.copy(items = data.items.mapIndexed { index, item ->
            item.copy(durationSeconds = if (index == 0) 45L else 72L, targetDurationSeconds = 90L)
        })
        val result = journalEvidence(testContext(), measured, EvidenceRequest(EvidenceMetric.PROGRESS, fixtureRange, exercise))
        assertTrue(result.finding.contains(ui(R.string.evidence_duration_change, 45L, 72L).trimEnd('.')))
    }

    @Test fun summaryCountsOnlyWorkedVisitsAndReportsHowManyHaveRecordedTime() {
        val timed = visit("2026-11-18", 125L)
        val untimed = visit("2026-11-19", null)
        val emptyVisit = visit("2026-11-20", 600L)
        val data = JournalSnapshot(
            workouts = listOf(timed, untimed, emptyVisit),
            items = listOf(performed(timed), performed(untimed))
        )
        val context = testContext()
        val result = journalEvidence(context, data, EvidenceRequest(EvidenceMetric.SUMMARY, evidenceDateRange("", LocalDate.parse("2026-11-20"))))
        val coverage = context.resources.getQuantityString(R.plurals.evidence_duration_coverage, 1, 1, 2)
        assertTrue(result.finding.contains(coverage.lowercase().trimEnd('.')))
        assertEquals(listOf(untimed.id, timed.id), result.sources.map(EvidenceSource::workoutId))
    }

    @Test fun checkInSummarySeparatesMissingFormAndFiltersObservationsByRegion() {
        val withForm = visit("2026-11-18", 125L)
        val withoutForm = visit("2026-11-19", null)
        val check = CheckIn(workoutId = withForm.id, phase = "NEXT_DAY", observedAt = "2026-11-20T12:00:00Z", zoneId = "UTC")
        val data = JournalSnapshot(
            workouts = listOf(withForm, withoutForm),
            items = listOf(performed(withForm), performed(withoutForm)),
            checkIns = listOf(check),
            observations = listOf(
                BodyObservation(check.id, "left_knee", pain = 0, stiffness = 2),
                BodyObservation(check.id, "right_shoulder", pain = 3)
            )
        )
        val context = testContext()
        val request = EvidenceRequest(
            EvidenceMetric.CHECK_IN,
            evidenceDateRange("", LocalDate.parse("2026-11-20")),
            region = "left_knee"
        )
        val result = journalEvidence(context, data, request)
        assertEquals(context.resources.getQuantityString(R.plurals.evidence_pain_count, 1, 1, 1, 0, 1), result.finding)
        assertEquals(listOf(withForm.id, withoutForm.id), result.sources.map(EvidenceSource::workoutId))
        assertEquals("2026-11-20", result.sources.first().date)
        assertTrue(result.sources.first().detail.contains(ui(R.string.region_left_knee)))
        assertEquals(ui(R.string.evidence_no_form), result.sources.last().detail)
    }

    @Test fun lastVisitUsesRecordedSecondsAndRetainsCompletedSets() {
        val date = LocalDate.of(2026, 11, 20)
        val timestamp = "2026-11-20T12:00:00Z"
        val exercise = Exercise(name = "Fixture timed exercise", basis = "duration")
        val workout = WorkoutEntity(
            journalDate = date.toString(),
            zoneId = "UTC",
            state = "COMPLETED",
            activeKey = null,
            createdAt = timestamp,
            modifiedAt = timestamp
        )
        val item = SessionItem(
            workoutId = workout.id,
            exerciseId = exercise.id,
            equipmentId = exercise.equipmentId,
            name = exercise.name,
            category = exercise.category,
            basis = exercise.basis,
            mode = "DURATION",
            disposition = "DONE",
            quickCompletedAt = timestamp,
            durationSeconds = 74L,
            targetDurationSeconds = 120L
        )
        val snapshot = JournalSnapshot(workouts = listOf(workout), items = listOf(item))
        val request = EvidenceRequest(EvidenceMetric.LAST, DateRange(date, date), exercise)
        val context = testContext()
        val recorded = ui(R.string.saved_duration, 74L)

        assertEquals(EvidenceSource(workout.id, date.toString(), recorded), journalEvidence(context, snapshot, request).sources.single())
        val missingDuration = snapshot.copy(items = listOf(item.copy(durationSeconds = null)))
        assertEquals(ui(R.string.evidence_no_measured_set), journalEvidence(context, missingDuration, request).sources.single().detail)

        val completedSet = SessionSet(itemId = item.id, reps = 8, completedAt = timestamp)
        val withSets = snapshot.copy(items = listOf(item.copy(quickCompletedAt = null)), sets = listOf(completedSet))
        assertEquals("$recorded\n${ui(R.string.rep_value, "8")}", journalEvidence(context, withSets, request).sources.single().detail)
    }

    private fun visit(date: String, seconds: Long?) = WorkoutEntity(
        journalDate = date,
        zoneId = "UTC",
        state = "COMPLETED",
        activeKey = null,
        durationSeconds = seconds,
        createdAt = "${date}T12:00:00Z",
        modifiedAt = "${date}T12:00:00Z"
    )

    private fun performed(visit: WorkoutEntity) = SessionItem(
        workoutId = visit.id,
        exerciseId = "fixture-exercise",
        equipmentId = "fixture-equipment",
        name = "Soutu",
        category = "STRENGTH",
        basis = "kg",
        disposition = "DONE",
        quickCompletedAt = visit.createdAt
    )

    private val fixtureRange = DateRange(LocalDate.parse("2026-11-16"), LocalDate.parse("2026-11-20"))

    private fun matchingVisits(basis: String): Pair<Exercise, JournalSnapshot> {
        val exercise = Exercise(name = "Soutu", equipmentId = "machine", basis = basis)
        val workouts = listOf(visit("2026-11-17", null), visit("2026-11-20", null))
        val items = workouts.map { performed(it).copy(exerciseId = exercise.id, equipmentId = exercise.equipmentId, basis = basis) }
        return exercise to JournalSnapshot(exercises = listOf(exercise), workouts = workouts, items = items)
    }

    private fun recordedSet(item: SessionItem, load: String, reps: Int) =
        SessionSet(itemId = item.id, load = load, reps = reps, perSide = false, completedAt = item.quickCompletedAt)
}

package com.emmah.inshape.feature.library

import com.emmah.inshape.R
import com.emmah.inshape.quantity
import com.emmah.inshape.ui
import com.emmah.inshape.testContext
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class LastRecordedValuesTest {
    private fun workout(
        date: String,
        basis: String = "kg",
        loads: List<String> = listOf("25", "27.5"),
        duration: Long? = null,
        reps: Int? = null
    ): Workout {
        val row =
            WorkoutEntity(
                journalDate = date,
                zoneId = "UTC",
                state = "COMPLETED",
                activeKey = null,
                createdAt = "2026-11-13T10:00:00Z",
                modifiedAt = "2026-11-13T10:00:00Z"
            )
        val item =
            SessionItem(
                workoutId = row.id,
                exerciseId = "exercise",
                equipmentId = "machine",
                name = "Exercise",
                category = "Machine",
                basis = basis,
                quickCompletedAt = if (loads.isEmpty()) "2026-11-13T10:00:00Z" else null,
                durationSeconds = duration
            )
        val sets =
            loads.map {
                SessionSet(itemId = item.id, load = it, reps = reps, completedAt = "2026-11-13T10:00:00Z")
            } +
                SessionSet(itemId = item.id, load = "999", suggestedLoad = "999")
        return Workout(row, listOf(WorkoutItem(item, sets)), emptyList())
    }

    @Test fun newestPerformedWorkoutWinsAndSuggestedSetsAreIgnored() {
        val older = workout("2026-11-10", loads = listOf("100"))
        val latest = workout("2026-11-13")
        assertEquals(ui(R.string.summary_best_load, quantity(R.plurals.set_count, 2), ui(R.string.weight_value, "27,5")), lastRecordedValues(testContext(), listOf(older, latest))["exercise"]?.value)
        assertEquals(
            "2026-11-13",
            lastRecordedValues(testContext(), listOf(latest, older))["exercise"]?.date.toString()
        )
        assertTrue(
            lastRecordedValues(
                testContext(),
                listOf(latest.copy(row = latest.row.copy(state = "ACTIVE")))
            ).isEmpty()
        )
    }

    @Test fun combinesIdenticalCompletedWeightedSetsAndKeepsTheWorkoutDate() {
        val completed = workout("2026-11-11", loads = List(3) { "25" }, reps = 10)
        val recorded = lastRecordedValues(testContext(), listOf(completed)).getValue("exercise")

        assertEquals(ui(R.string.set_summary_with_load, ui(R.string.set_summary_uniform, 3, 10), ui(R.string.weight_value, "25")), recorded.value)
        assertEquals(LocalDate.of(2026, 11, 11), recorded.date)
    }

    @Test fun supportsLevelDurationAndCompletionWithoutInventingLoads() {
        assertEquals(
            ui(R.string.set_summary_with_load, quantity(R.plurals.set_count, 1), ui(R.string.level_value, "4").lowercase()),
            lastRecordedValues(
                testContext(),
                listOf(workout("2026-11-13", "level", listOf("4")))
            )["exercise"]?.value
        )
        assertEquals(
            "10 min",
            lastRecordedValues(
                testContext(),
                listOf(workout("2026-11-13", "duration", emptyList(), 600))
            )["exercise"]?.value
        )
        assertEquals(
            ui(R.string.done),
            lastRecordedValues(
                testContext(),
                listOf(workout("2026-11-13", "bodyweight", emptyList()))
            )["exercise"]?.value
        )
        assertTrue(lastRecordedValues(testContext(), emptyList()).isEmpty())
    }

    @Test fun repeatedExerciseUsesTheLatestLoadedBasisAndRepsAreShownWithoutLoad() {
        val weight = workout("2026-11-13", loads = listOf("85")).items.single()
        val level = workout("2026-11-13", "level", listOf("8", "4")).items.single()
        val latest = level.copy(
            sets = level.sets.map { set ->
                if (set.completedAt == null) set else set.copy(completedAt = "2026-11-13T10:00:00.500Z")
            }
        )
        val mixed = workout("2026-11-13").copy(items = listOf(latest, weight))
        assertEquals(ui(R.string.summary_best_load, quantity(R.plurals.set_count, 2), ui(R.string.level_value, "8").lowercase()), lastRecordedValues(testContext(), listOf(mixed))["exercise"]?.value)

        val bodyweight = workout("2026-11-13", "bodyweight", emptyList())
        val item = bodyweight.items.single()
        val performed = item.copy(sets = listOf(SessionSet(itemId = item.row.id, reps = 12, completedAt = "2026-11-13T10:01:00Z")))
        assertEquals(ui(R.string.set_summary_uniform, 1, 12), lastRecordedValues(testContext(), listOf(bodyweight.copy(items = listOf(performed))))["exercise"]?.value)
    }
}

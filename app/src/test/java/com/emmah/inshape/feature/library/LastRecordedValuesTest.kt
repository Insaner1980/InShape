package com.emmah.inshape.feature.library

import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastRecordedValuesTest {
    private fun workout(date: String, basis: String = "kg", loads: List<String> = listOf("25", "27.5"), duration: Long? = null): Workout {
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
                SessionSet(itemId = item.id, load = it, completedAt = "2026-11-13T10:00:00Z")
            } +
                SessionSet(itemId = item.id, load = "999", suggestedLoad = "999")
        return Workout(row, listOf(WorkoutItem(item, sets)), emptyList())
    }

    @Test fun newestPerformedWorkoutWinsAndSuggestedSetsAreIgnored() {
        val older = workout("2026-11-10", loads = listOf("100"))
        val latest = workout("2026-11-13")
        assertEquals("27.5 kg", lastRecordedValues(listOf(older, latest))["exercise"]?.value)
        assertEquals(
            "2026-11-13",
            lastRecordedValues(listOf(latest, older))["exercise"]?.date.toString()
        )
        assertTrue(
            lastRecordedValues(
                listOf(latest.copy(row = latest.row.copy(state = "ACTIVE")))
            ).isEmpty()
        )
    }

    @Test fun supportsLevelDurationAndCompletionWithoutInventingLoads() {
        assertEquals(
            "Level 4",
            lastRecordedValues(
                listOf(workout("2026-11-13", "level", listOf("4")))
            )["exercise"]?.value
        )
        assertEquals(
            "10 min",
            lastRecordedValues(
                listOf(workout("2026-11-13", "duration", emptyList(), 600))
            )["exercise"]?.value
        )
        assertEquals(
            "Done",
            lastRecordedValues(
                listOf(workout("2026-11-13", "bodyweight", emptyList()))
            )["exercise"]?.value
        )
        assertTrue(lastRecordedValues(emptyList()).isEmpty())
    }
}

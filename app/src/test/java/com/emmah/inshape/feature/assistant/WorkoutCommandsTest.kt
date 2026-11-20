package com.emmah.inshape.feature.assistant

import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutCommandsTest {
    private val machine = SessionItem(
        workoutId = "test-workout",
        exerciseId = "test-exercise",
        equipmentId = "specific-machine",
        name = "Jalkaprässi",
        category = "Machine",
        basis = "kg"
    )

    @Test fun parsesFinnishDecimalsAndRequiresTheExactMachineHandle() {
        val proposal = """{"kind":"log","target":"item_1","sets":[{"load":"42,5","reps":"12","basis":"kg"}]}"""
        val review = workoutProposal(Json.parseToJsonElement(proposal).jsonObject, WorkoutItem(machine, emptyList()))
        assertEquals("42.5", review.sets.single().load)
        assertEquals("12", review.sets.single().reps)
        assertThrows(IllegalArgumentException::class.java) {
            workoutProposal(Json.parseToJsonElement(proposal.replace("item_1", "item_2")).jsonObject, WorkoutItem(machine, emptyList()))
        }
    }

    @Test fun correctionRetainsLoadForTheIdentifiedLastSet() {
        val set = SessionSet(itemId = machine.id, load = "42.5", reps = 12, completedAt = "2026-11-20T12:00:00Z")
        val proposal = """{"kind":"correct","target":"item_1","sets":[{"load":null,"reps":"11","basis":"kg"}]}"""
        val review = workoutProposal(Json.parseToJsonElement(proposal).jsonObject, WorkoutItem(machine, listOf(set)))
        assertEquals(set.id, review.correctSetId)
        assertEquals("42.5", review.sets.single().load)
        assertEquals("11", review.sets.single().reps)
        assertNull(review.value)
    }

    @Test fun rejectsUnsupportedUnitsAndInvalidReps() {
        listOf("lb" to "12", "kg" to "0", "kg" to "1.5").forEach { (basis, reps) ->
            val proposal = """{"kind":"log","target":"item_1","sets":[{"load":"42","reps":"$reps","basis":"$basis"}]}"""
            assertThrows(IllegalArgumentException::class.java) {
                workoutProposal(Json.parseToJsonElement(proposal).jsonObject, WorkoutItem(machine, emptyList()))
            }
        }
    }
}

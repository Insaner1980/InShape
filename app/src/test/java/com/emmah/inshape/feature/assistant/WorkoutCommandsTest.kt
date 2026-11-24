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
    @Test fun durationProposalRequiresATimedMovementAndKeepsWholeSeconds() {
        val payload = Json.parseToJsonElement("""{"kind":"duration","target":"item_1","durationSeconds":82}""").jsonObject
        val timed = WorkoutItem(machine.copy(basis = "duration"), emptyList())
        assertEquals(82L, workoutProposal(payload, timed).durationSeconds)
        assertThrows(IllegalArgumentException::class.java) { workoutProposal(payload, WorkoutItem(machine, emptyList())) }
        val tooLong = Json.parseToJsonElement("""{"kind":"duration","target":"item_1","durationSeconds":86401}""").jsonObject
        assertThrows(IllegalArgumentException::class.java) { workoutProposal(tooLong, timed) }
    }

    @Test fun correctionUsesExplicitOrPreviouslyRecordedSetDetails() {
        val old = SessionSet(
            itemId = machine.id, load = "18", reps = 10, rir = 3, perSide = true, completedAt = "2026-11-20T12:00:00Z"
        )
        val item = WorkoutItem(machine, listOf(old))
        val proposal = """{"kind":"correct","target":"item_1","sets":[{"load":null,"reps":null,"basis":"kg","rir":1}]}"""
        val corrected = workoutProposal(Json.parseToJsonElement(proposal).jsonObject, item)
        assertEquals(ReviewedSet("18", "10", 1, true), corrected.sets.single())
        assertThrows(IllegalArgumentException::class.java) {
            workoutProposal(Json.parseToJsonElement(proposal).jsonObject, WorkoutItem(machine, emptyList()))
        }
    }

    @Test fun reusingPreviousLoadNeedsAPreviouslyCompletedSet() {
        val payload = Json.parseToJsonElement(
            """{"kind":"log","target":"item_1","sets":[{"reps":"12","basis":"kg","usePreviousLoad":true}]}"""
        ).jsonObject
        val previous = SessionSet(itemId = machine.id, load = "27.5", reps = 8, completedAt = "2026-11-20T12:00:00Z")
        assertEquals("27.5", workoutProposal(payload, WorkoutItem(machine, listOf(previous))).sets.single().load)
        assertThrows(IllegalArgumentException::class.java) {
            workoutProposal(payload, WorkoutItem(machine, listOf(previous.copy(completedAt = null))))
        }
    }

    @Test fun memoryProposalsAcceptTrimmedTextAndClarificationsWithoutAMachineHandle() {
        val item = WorkoutItem(machine, emptyList())
        val preference = Json.parseToJsonElement("""{"kind":"preference","value":"  Käytä toistoja  "}""").jsonObject
        assertEquals("Käytä toistoja", workoutProposal(preference, item).value)
        val clarification = Json.parseToJsonElement("""{"kind":"clarification","question":"Mikä paino?"}""").jsonObject
        assertEquals("Mikä paino?", workoutProposal(clarification, item).question)
        val incomplete = Json.parseToJsonElement("""{"kind":"setting","target":"item_1","label":" ","value":"3"}""").jsonObject
        assertThrows(IllegalArgumentException::class.java) { workoutProposal(incomplete, item) }
    }

    @Test fun setReviewEnforcesMeasurementSpecificLimitsAndRir() {
        assertEquals(ReviewedSet("6", "12", 2), validateReviewedSet(ReviewedSet("6", "12", 2), "level"))
        assertEquals(ReviewedSet(null, "12"), validateReviewedSet(ReviewedSet(null, "12"), "bodyweight"))
        listOf(
            "level" to ReviewedSet("1.5", "8"),
            "level" to ReviewedSet("0", "8"),
            "bodyweight" to ReviewedSet("8", "8"),
            "duration" to ReviewedSet(null, "8"),
            "kg" to ReviewedSet("20", null),
            "kg" to ReviewedSet("20", "8", 21)
        ).forEach { (basis, set) ->
            assertThrows(IllegalArgumentException::class.java) { validateReviewedSet(set, basis) }
        }
    }

    private val machine = SessionItem(
        workoutId = "test-workout",
        exerciseId = "test-exercise",
        equipmentId = "specific-machine",
        name = "Jalkaprässi",
        category = "Machine",
        basis = "kg"
    )

    @Test fun parsesFinnishDecimalsAndRequiresTheExactMachineHandle() {
        val proposal = FINNISH_DECIMAL_WORKOUT_PROPOSAL
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

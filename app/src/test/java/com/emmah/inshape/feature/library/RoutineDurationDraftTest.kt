package com.emmah.inshape.feature.library

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDurationDraftTest {
    @Test fun restorationLeavesReviewedExactAndUnrelatedDurationsAlone() {
        val draft = RoutineItemDraft(exerciseId = "exercise", duration = "1")
        assertEquals(draft, draft.restoreDuration(null))
        assertEquals(draft, draft.restoreDuration(60))
        assertEquals(draft, draft.restoreDuration(30))
        val precise = draft.copy(originalDurationSeconds = 90)
        assertEquals(precise, precise.restoreDuration(30))
        val reviewed = draft.copy(durationReviewed = true)
        assertEquals(reviewed, reviewed.restoreDuration(90))
    }

    @Test fun unchangedDraftRetainsSubMinutePrecisionAfterPersistence() {
        listOf(30L, 90L).forEach { seconds ->
            val draft = RoutineItemDraft(
                exerciseId = "exercise",
                mode = "DURATION",
                duration = (seconds / 60).toString(),
                originalDurationSeconds = seconds,
                durationReviewed = true
            )
            val restored = Json.decodeFromString<RoutineItemDraft>(Json.encodeToString(draft))
            assertEquals(seconds, restored.preservedFractionalDuration)
            assertEquals(seconds, restored.copy(note = "Changed note").plannedDurationSeconds())
            val changed = restored.copy(duration = "2", originalDurationSeconds = null)
            assertNull(changed.preservedFractionalDuration)
            assertEquals(120L, changed.plannedDurationSeconds())
            assertTrue(runCatching { restored.copy(duration = "0", originalDurationSeconds = null).plannedDurationSeconds() }.isFailure)
        }
    }

    @Test fun legacyDraftRequiresReviewAndPreservesEitherExplicitChoice() {
        listOf(30L, 90L).forEach { seconds ->
            val payload = """{"exerciseId":"exercise","mode":"DURATION","duration":"${seconds / 60}","note":"Unsaved note"}"""
            val restored = Json.decodeFromString<RoutineItemDraft>(payload).restoreDuration(seconds)
            assertTrue(restored.requiresDurationReview)
            assertTrue(runCatching { restored.plannedDurationSeconds() }.isFailure)
            assertEquals("Unsaved note", restored.note)
            assertEquals(seconds, restored.copy(durationReviewed = true).plannedDurationSeconds())
            val edited = restored.copy(duration = "1", originalDurationSeconds = null, durationReviewed = true)
            assertEquals(60L, edited.restoreDuration(seconds).plannedDurationSeconds())
        }
    }

    @Test fun switchingAwayFromDurationIgnoresTheHiddenDurationDraft() {
        val invalid = RoutineItemDraft(exerciseId = "exercise", mode = "DURATION", duration = "0")
        val needsReview = invalid.copy(originalDurationSeconds = 30)

        listOf("SETS", "QUICK").forEach { mode ->
            assertNull(invalid.copy(mode = mode).plannedDurationSeconds())
            val switched = needsReview.copy(mode = mode)
            assertFalse(switched.requiresDurationReview)
            assertNull(switched.plannedDurationSeconds())
            assertTrue(switched.copy(mode = "DURATION").requiresDurationReview)
        }
    }
}

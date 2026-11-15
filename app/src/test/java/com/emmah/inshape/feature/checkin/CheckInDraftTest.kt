package com.emmah.inshape.feature.checkin

import com.emmah.inshape.data.CheckIn
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckInDraftTest {
    private val draft = CheckInDraft(
        CheckIn(workoutId = "workout", phase = "BEFORE", observedAt = "2026-11-13T08:00:00Z", zoneId = "UTC"),
        emptyList(),
        "Original",
        "2026-11-13 08:00",
        "Original"
    )

    @Test fun untouchedNoteRefreshesWhileExplicitDraftEditIsPreserved() {
        val restored = Json.decodeFromString<CheckInDraft>(Json.encodeToString(draft))
        assertEquals("Newer", restored.withCurrentWorkoutNote("Newer").sessionNote)
        assertEquals("Newer", restored.withCurrentWorkoutNote("Newer").originalSessionNote)
        val edited = restored.copy(sessionNote = "Unsaved edit")
        assertEquals(edited, edited.withCurrentWorkoutNote("Newer"))
    }

    @Test fun legacyDraftCannotClaimItWasBasedOnTheCurrentNote() {
        val legacy = Json.decodeFromString<CheckInDraft>(Json.encodeToString(draft.copy(originalSessionNote = null)))
        assertNull(legacy.withCurrentWorkoutNote("Newer").originalSessionNote)
        assertEquals("Original", legacy.withCurrentWorkoutNote("Newer").sessionNote)
    }
}

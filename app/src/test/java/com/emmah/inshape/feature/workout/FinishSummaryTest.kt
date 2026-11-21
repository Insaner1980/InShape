package com.emmah.inshape.feature.workout

import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.ui.theme.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishSummaryTest {
    private val end = Instant.parse("2026-11-13T10:00:00.123Z")

    @Test fun durationMatchesTheDisplayedClockMinutes() {
        val draft = FinishDraft("2026-11-13T11:15:10Z", "")
        val duration = requireNotNull(finishDuration(draft, "2026-11-13T10:42:50Z"))
        assertEquals("33 min", formatDuration(duration))
    }

    @Test fun restoredDraftRetainsFinishInstantAndEdits() {
        val first = finishDraft(null, end, "Original")
        val restored = finishDraft(Json.encodeToString(first.copy(note = "Edited")), end.plusSeconds(600), "Other")
        assertEquals(end.toString(), restored.end)
        assertEquals("Edited", restored.note)
        assertEquals(120L, finishDuration(restored, end.minusSeconds(65).toString()))
        assertEquals(3900L, finishDuration(restored.copy(unknownTimes = true, minutes = "65"), null))
        assertNull(finishDuration(restored.copy(unknownTimes = true), null))
        assertNull(finishDuration(restored.copy(unknownTimes = true, minutes = "9223372036854775807"), null))
    }

    @Test fun restoredDraftWithInvalidTimesKeepsTheEditedNoteAndMarksTimesUnknown() {
        listOf(
            FinishDraft("invalid", "Edited note"),
            FinishDraft(end.toString(), "Edited note", fixedStart = Instant.MAX.toString())
        ).forEach { draft ->
            val restored = finishDraft(Json.encodeToString(draft), end, "Old note")
            assertEquals(finishDraft(null, end, "Old note").copy(note = "Edited note", unknownTimes = true), restored)
        }
    }

    @Test fun recoveringInvalidTimesPreservesTheNoteConflictBaseline() {
        val draft = FinishDraft("invalid", "Edited note", originalNote = "Original note")
        val restored = finishDraft(Json.encodeToString(draft), end, "Newer saved note")
        assertEquals("Original note", restored.originalNote)
        assertEquals("Edited note", restored.note)
    }

    @Test fun reopeningAfterReturningOnlyRefreshesAnAutomaticEnd() {
        val later = end.plusSeconds(60)
        val fresh = finishDraft(null, end, "Original")
        assertNull(fresh.afterReturningToWorkout())

        val edited = fresh.copy(note = "Edited", fixedStart = end.minusSeconds(600).toString())
        val saved = requireNotNull(edited.afterReturningToWorkout())
        assertEquals(edited.copy(end = later.toString()), finishDraft(Json.encodeToString(saved), later, "Original"))

        val manual = edited.copy(endMode = FinishDraft.EndMode.Manual)
        val savedManual = requireNotNull(manual.afterReturningToWorkout())
        assertEquals(manual, finishDraft(Json.encodeToString(savedManual), later, "Original"))
    }

    @Test fun onlyUntouchedNotesFollowAConcurrentUpdate() {
        val original = finishDraft(null, end, "Original")
        val current = original.withCurrentWorkoutNote("Newer note")
        assertEquals("Newer note", current.note)
        assertEquals("Newer note", current.originalNote)
        val edited = original.copy(note = "Draft note")
        assertEquals(edited, edited.withCurrentWorkoutNote("Newer note"))
    }

    @Test fun rangeIncludesDatesOnlyWhenCrossingMidnight() {
        val zone = ZoneId.of("UTC")
        assertEquals("09:00–10:00", workoutTimeRange(end.minusSeconds(3600), end, zone))
        assertEquals(
            "to 12.11., 22:50–pe 13.11., 00:20",
            workoutTimeRange(Instant.parse("2026-11-12T22:50:00Z"), Instant.parse("2026-11-13T00:20:00Z"), zone)
        )
    }

    @Test fun noteCanMakeAnOtherwiseEmptyWorkoutSaveable() {
        val workout =
            Workout(
                WorkoutEntity(journalDate = "2026-11-13", zoneId = "UTC", createdAt = end.toString(), modifiedAt = end.toString()),
                emptyList(),
                emptyList()
            )
        assertTrue(nothingRecorded(workout, " "))
        assertFalse(nothingRecorded(workout, "A short walk"))
    }
}

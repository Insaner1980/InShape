package com.emmah.inshape.feature.today

import com.emmah.inshape.data.*
import org.junit.Assert.*
import org.junit.Test

class SuggestedRoutineTest {
    private fun workout(routine: String, date: String) = Workout(WorkoutEntity(sourceRoutineId = routine, journalDate = date, zoneId = "UTC", state = "COMPLETED", activeKey = null, createdAt = "2026-11-13T10:00:00Z", modifiedAt = "2026-11-13T10:00:00Z"), emptyList(), emptyList())
    private val routines = listOf(Routine("a", "A"), Routine("b", "B"), Routine("c", "C"))
    @Test fun emptyAndNeverDoneUseListOrder() {
        assertNull(suggestedRoutine(emptyList(), emptyList()))
        assertEquals("a", suggestedRoutine(routines, emptyList())?.id)
        assertEquals("b", suggestedRoutine(routines, listOf(workout("a", "2026-11-13")))?.id)
    }
    @Test fun comparesLatestFinishedDateAndKeepsTiesInListOrder() {
        val history = listOf(workout("a", "2026-11-01"), workout("a", "2026-11-13"), workout("b", "2026-11-10"), workout("c", "2026-11-10"))
        assertEquals("b", suggestedRoutine(routines, history)?.id)
        assertEquals("b", suggestedRoutine(routines, history + workout("b", "2026-11-14").let { it.copy(row = it.row.copy(state = "ACTIVE")) })?.id)
    }
}

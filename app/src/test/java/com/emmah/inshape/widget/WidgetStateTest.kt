package com.emmah.inshape.widget

import com.emmah.inshape.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetStateTest {
    @Test fun weekGoalResumeAndPerformedOnlyRemainConsistent() {
        fun workout(id: String, day: String, state: String = "COMPLETED") = WorkoutEntity(id = id, journalDate = day, zoneId = "UTC", createdAt = "2026-11-13T10:00:00Z", modifiedAt = "2026-11-13T10:00:00Z", state = state, activeKey = if (state == "ACTIVE") 1 else null)
        fun item(id: String, workout: String) = SessionItem(id = id, workoutId = workout, exerciseId = "e", equipmentId = "q", name = "Exercise", category = "Machine", basis = "kg", quickCompletedAt = "2026-11-13T10:00:00Z")
        val data = JournalSnapshot(workouts = listOf(workout("old", "2026-11-08"), workout("new", "2026-11-09"), workout("empty", "2026-11-10"), workout("active", "2026-11-13", "ACTIVE")), items = listOf(item("a", "old"), item("b", "new"), item("c", "active")), preferences = Preferences(weeklyGoal = 3))
        val state = widgetState(data, LocalDate.parse("2026-11-13"))
        assertEquals(LocalDate.parse("2026-11-09"), state.week)
        assertEquals(1, state.count); assertEquals(3, state.goal); assertTrue(state.resume)
        assertEquals(0, widgetState(data, LocalDate.parse("2026-11-16")).count)
        assertNull(widgetState(data.copy(preferences = Preferences()), LocalDate.parse("2026-11-13")).goal)
    }
}

package com.emmah.inshape.widget

import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.quantity
import com.emmah.inshape.ui
import com.emmah.inshape.testContext
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.WorkoutEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class WidgetStateTest {
    @Test fun idleWidgetUsesTheNextPopulatedRoutine() {
        val empty = Routine(title = "Empty routine")
        val ready = Routine(title = "Ready routine")
        val data = JournalSnapshot(
            routines = listOf(empty, ready),
            routineItems = listOf(RoutineItem(routineId = ready.id, exerciseId = "exercise"))
        )
        val today = LocalDate.parse("2026-11-13")
        assertEquals(ready.title, widgetState(testContext(), data, today).nextRoutineTitle)
        assertNull(widgetState(testContext(), data.copy(routineItems = emptyList()), today).nextRoutineTitle)
    }

    @Test fun weekGoalResumeAndPerformedOnlyRemainConsistent() {
        fun workout(id: String, day: String, state: String = "COMPLETED") = WorkoutEntity(
            id = id,
            journalDate = day,
            zoneId = "UTC",
            createdAt = "2026-11-13T10:00:00Z",
            modifiedAt = "2026-11-13T10:00:00Z",
            state = state,
            title = "Active workout",
            start = "2026-11-13T10:00:00Z",
            activeKey = if (state ==
                "ACTIVE"
            ) {
                1
            } else {
                null
            }
        )
        fun item(id: String, workout: String) = SessionItem(
            id = id,
            workoutId = workout,
            exerciseId = "e",
            equipmentId = "q",
            name = "Exercise",
            category = "Machine",
            basis = "kg",
            quickCompletedAt = "2026-11-13T10:00:00Z"
        )
        val data =
            JournalSnapshot(
                workouts = listOf(
                    workout("old", "2026-11-08"),
                    workout("new", "2026-11-09"),
                    workout("empty", "2026-11-10"),
                    workout("active", "2026-11-13", "ACTIVE")
                ),
                items = listOf(item("a", "old"), item("b", "new"), item("c", "active")),
                preferences = Preferences(weeklyGoal = 3)
            )
        val state = widgetState(testContext(), data, LocalDate.parse("2026-11-13"))
        assertEquals(LocalDate.parse("2026-11-09"), state.week)
        assertEquals(1, state.count)
        assertEquals(3, state.goal)
        assertTrue(state.resume)
        assertEquals("Active workout", state.activeTitle)
        assertEquals("2026-11-13T10:00:00Z", state.activeStart)
        assertEquals(false, state.timingNeedsReview)
        assertEquals(0, widgetState(testContext(), data, LocalDate.parse("2026-11-16")).count)
        assertNull(
            widgetState(testContext(), data.copy(preferences = Preferences()), LocalDate.parse("2026-11-13")).goal
        )
    }
}

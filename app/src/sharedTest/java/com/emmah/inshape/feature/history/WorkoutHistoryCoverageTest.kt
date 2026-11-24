package com.emmah.inshape.feature.history

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryCoverageTest : RedesignInteractionFixture() {
    @Test fun recordedWorkoutCanKeepADurationWithoutExactTimes() {
        val id = DemoFixtures.id("november:4")
        launch("HistoryNovember", "detail/$id")
        val original = checkNotNull(snapshot().workout(id))
        compose.onNodeWithContentDescription(ui(R.string.workout_options)).performClick()
        click(ui(R.string.edit_workout))
        click(ui(R.string.times_unknown))
        enter(R.string.duration_minutes, "34")
        enter(R.string.workout_note, "Exact start and finish forgotten")
        click(ui(R.string.save_correction))
        awaitWrite { snapshot().workout(id)?.row?.durationSeconds == 34 * 60L }

        val corrected = checkNotNull(snapshot().workout(id))
        assertNull(corrected.row.start)
        assertNull(corrected.row.end)
        assertEquals("Exact start and finish forgotten", corrected.row.note)
        assertEquals(original.items.map { it.completedSets }, corrected.items.map { it.completedSets })
    }

    @Test fun historicalSetEditingCanCloseBeforeDeletingTheWorkout() {
        val id = DemoFixtures.id("november:4")
        launch("HistoryNovember", "detail/$id")
        compose.onNodeWithContentDescription(ui(R.string.workout_options)).performClick()
        click(ui(R.string.edit_sets))
        val setOptions = compose.onAllNodesWithContentDescription(ui(R.string.set_options, 1))
        assertTrue(setOptions.fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription(ui(R.string.workout_options)).performClick()
        click(ui(R.string.finish_edit_sets))
        assertTrue(setOptions.fetchSemanticsNodes().isEmpty())

        click(ui(R.string.delete_workout))
        compose.onNodeWithText(ui(R.string.delete_workout_question)).assertExists()
        click(ui(R.string.cancel))
        assertNotNull(snapshot().workout(id))
        click(ui(R.string.delete_workout))
        click(ui(R.string.delete))
        awaitWrite { snapshot().workout(id) == null }
        assertTrue(snapshot().history.isNotEmpty())
    }

    private fun enter(label: Int, value: String) {
        compose.onNode(hasSetTextAction() and hasText(ui(label))).performScrollTo().performTextReplacement(value)
    }
}

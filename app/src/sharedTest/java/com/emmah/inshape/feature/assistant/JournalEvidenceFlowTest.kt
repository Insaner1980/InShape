package com.emmah.inshape.feature.assistant

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.shortDate
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalEvidenceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun localQuestionsCanSelectAnExerciseCycleCheckInPhasesAndOpenRecordedSources() {
        val date = LocalDate.parse("2026-11-20")
        val timestamp = "2026-11-20T12:00:00Z"
        val workout = WorkoutEntity(
            journalDate = date.toString(), zoneId = "UTC", state = "COMPLETED", activeKey = null,
            createdAt = timestamp, modifiedAt = timestamp
        )
        val exercise = Exercise(name = "Fixture pulley")
        val item = SessionItem(
            workoutId = workout.id, exerciseId = exercise.id, equipmentId = exercise.equipmentId,
            name = exercise.name, category = exercise.category, basis = exercise.basis, quickCompletedAt = timestamp
        )
        val snapshot = JournalSnapshot(exercises = listOf(exercise), workouts = listOf(workout), items = listOf(item))
        val opened = AtomicReference<String>()
        compose.setContent {
            InShapeTheme { JournalEvidenceScreen(snapshot, date, null, null, opened::set) }
        }
        compose.clickText(ui(R.string.evidence_summary))
        compose.clickText(date.shortDate())
        assertEquals(workout.id, opened.get())

        ask("Mikä on tavallinen paino?")
        compose.onNodeWithText(ui(R.string.evidence_choose_exercise)).performScrollTo().assertIsDisplayed()
        compose.clickText(ui(R.string.choose_exercise_equipment))
        compose.clickText(exercise.name)
        compose.onNodeWithText(ui(R.string.evidence_no_load)).performScrollTo().assertIsDisplayed()
        ask("Miten kehitys näkyy?")
        compose.onNodeWithText(ui(R.string.evidence_need_two_visits)).performScrollTo().assertIsDisplayed()

        ask("Kuinka paljon kipua on ollut?")
        compose.clickText(ui(R.string.next_day))
        compose.clickText(ui(R.string.before_workout))
        compose.clickText(ui(R.string.after_workout))
        compose.onNodeWithText(ui(R.string.evidence_no_form)).performScrollTo().assertIsDisplayed()

        ask("Kipu 2026-11-20 2026-11-19")
        compose.onNodeWithText(ui(R.string.invalid_date_range)).performScrollTo().assertIsDisplayed()
    }

    private fun ask(question: String) {
        compose.onNode(hasText(ui(R.string.question)) and hasSetTextAction()).performScrollTo().performTextReplacement(question)
        compose.onNodeWithContentDescription(ui(R.string.ask)).performSemanticsAction(SemanticsActions.OnClick) { action ->
            check(action())
        }
        compose.waitForIdle()
    }
}

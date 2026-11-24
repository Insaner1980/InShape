package com.emmah.inshape.feature.workout

import android.view.KeyEvent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.ProgramExerciseLink
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.program.ProgramAlternative
import com.emmah.inshape.feature.program.ProgramVisit
import com.emmah.inshape.feature.program.ProgramVisitMode
import com.emmah.inshape.ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutPlanCoverageTest : RedesignInteractionFixture() {
    @Test fun workoutPlanCanBeSavedBackToItsRoutineThenDiscarded() {
        val routineId = DemoFixtures.routineId
        val newExercise = Exercise(name = "Fixture band press")
        launch("HistoryNovember", "Today") { app ->
            app.repository.saveExercise(newExercise)
            app.repository.startWorkout(StartSource.Routine(routineId))
        }
        fun routineContainsAddedExercise() = snapshot().routineItems.any { it.routineId == routineId && it.exerciseId == newExercise.id }
        click(ui(R.string.resume_workout))
        click(ui(R.string.add_exercise))
        click(newExercise.name)
        awaitWrite { snapshot().active?.items?.any { it.row.exerciseId == newExercise.id } == true }
        click(ui(R.string.save_changes_routine))
        click(ui(R.string.cancel))
        assertFalse(routineContainsAddedExercise())
        click(ui(R.string.save_changes_routine))
        compose.onNode(hasText(ui(R.string.update_routine)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        awaitWrite { routineContainsAddedExercise() }

        click(ui(R.string.discard_workout))
        click(ui(R.string.cancel))
        assertNotNull(snapshot().active)
        click(ui(R.string.discard_workout))
        click(ui(R.string.discard))
        awaitWrite { snapshot().active == null }
        assertTrue(routineContainsAddedExercise())
    }

    @Test fun programAlternativeCanReuseLinkedEquipment() {
        val existing = Exercise(name = "Fixture linked alternative", equipmentName = "Gym machine")
        val alternative = openProgramAlternative(existing)
        click(existing.name)
        awaitWrite { snapshot().active?.currentItem()?.row?.exerciseId == existing.id }
        val state = snapshot()
        val item = checkNotNull(state.active?.currentItem())
        assertEquals(alternative.exerciseId, item.row.plan?.catalogExerciseId)
        assertEquals(existing.id, item.row.exerciseId)
        assertEquals(1, state.exercises.count { it.id == existing.id })
    }

    @Test fun programAlternativeCanCreateEquipmentFromItsTemplate() {
        val alternative = openProgramAlternative()
        click(alternative.label)
        awaitWrite { snapshot().active?.currentItem()?.row?.plan?.catalogExerciseId == alternative.exerciseId }
        val state = snapshot()
        val item = checkNotNull(state.active?.currentItem())
        assertEquals(alternative.name, state.exercises.single { it.id == item.row.exerciseId }.name)
        assertTrue(state.programLinks.any { it.catalogExerciseId == alternative.exerciseId && it.exerciseId == item.row.exerciseId })
    }

    @Test fun timedExerciseIsKeptWhenAnotherExerciseWithRecordedWorkIsRemoved() {
        val timed = Exercise(name = "Fixture timed walk", basis = "time")
        val recorded = Exercise(name = "Fixture recorded press")
        launch("Empty", "Today") { app ->
            val repo = app.repository
            repo.saveExercise(timed)
            repo.saveExercise(recorded)
            val workoutId = repo.startWorkout(StartSource.Empty)
            repo.addExercise(workoutId, timed.id)
            val recordedId = repo.addExercise(workoutId, recorded.id)
            val setId = repo.addSet(recordedId)
            repo.saveSetDraft(setId, "20", "10")
            repo.setSetCompleted(setId, true)
        }
        click(ui(R.string.resume_workout))
        compose.onNode(hasText(ui(R.string.performed_minutes_optional)) and hasSetTextAction())
            .performScrollTo().performTextReplacement("8")
        if (compose.onAllNodesWithText(ui(R.string.exercise_done)).fetchSemanticsNodes().isEmpty()) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
        click(ui(R.string.exercise_done))
        awaitWrite { snapshot().active?.currentItem()?.row?.exerciseId == recorded.id }

        fun removeRecorded() {
            compose.onNodeWithContentDescription(ui(R.string.named_options, recorded.name)).performScrollTo().performClick()
            click(ui(R.string.remove_exercise))
            compose.onNodeWithText(ui(R.string.delete_recorded_work_question)).assertExists()
        }
        removeRecorded()
        click(ui(R.string.cancel))
        assertEquals("20", snapshot().active?.currentItem()?.completedSets?.single()?.load)
        removeRecorded()
        click(ui(R.string.delete))
        awaitWrite { snapshot().active?.items?.size == 1 }
        val saved = checkNotNull(snapshot().active).items.single()
        assertEquals(timed.id, saved.row.exerciseId)
        assertEquals(480L, saved.row.durationSeconds)
        assertTrue(saved.hasPerformedWork)
    }

    private fun openProgramAlternative(existing: Exercise? = null): ProgramAlternative {
        lateinit var alternative: ProgramAlternative
        launch("HistoryNovember", "Today") { app ->
            val catalog = app.programs
            val base = catalog.programs.first()
            val day = base.sessions.first()
            val slot = day.exercises.first { it.alternatives.isNotEmpty() }
            alternative = slot.alternatives.first()
            val single = day.copy(exercises = listOf(slot), miniExerciseIds = listOf(slot.exerciseId))
            val program = base.copy(sequence = listOf(day.id), sessions = listOf(single))
            app.repository.enrollProgram(catalog.schemaVersion, program, mapOf(slot.exerciseId to null))
            if (existing != null) {
                app.repository.saveExercise(existing)
                app.database.journal().put(ProgramExerciseLink(alternative.exerciseId, existing.id))
            }
            val state = app.repository.snapshot()
            val enrollment = checkNotNull(state.programEnrollment)
            val exercise = state.programLinks.single { it.catalogExerciseId == slot.exerciseId }
            app.repository.startWorkout(
                StartSource.Program(
                    ProgramVisit(
                        enrollment,
                        single,
                        ProgramVisitMode.STANDARD,
                        choices = mapOf(slot.exerciseId to exercise.exerciseId),
                        sequence = program.sequence
                    )
                )
            )
        }
        click(ui(R.string.resume_workout))
        val current = checkNotNull(snapshot().active?.currentItem())
        val name = current.row.name.exerciseDisplayName(context)
        compose.onNodeWithContentDescription(ui(R.string.named_options, name)).performScrollTo().performClick()
        click(ui(R.string.swap_exercise))
        compose.onNodeWithText(ui(R.string.program_alternatives)).assertExists()
        return alternative
    }
}

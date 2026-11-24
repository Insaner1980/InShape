package com.emmah.inshape.feature.program

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.ProgramExerciseLink
import com.emmah.inshape.quantity
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgramFlowCoverageTest : RedesignInteractionFixture() {
    @Test fun equipmentSetupUsesTheChosenExerciseOnANarrowScreen() {
        val app = createContainer()
        val program = app.programs.programs.first().withSingleDayMovement()
        val slot = program.sessions.single().exercises.single()
        val exercise = Exercise(name = "Test machine", nickname = "Gym set-up")
        runBlocking {
            app.storageReady.await()
            app.repository.saveExercise(exercise)
        }
        val state = snapshot()
        val enrolled = AtomicBoolean()
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            ProgramTestViewport(320, 1f) {
                ProgramSetupScreen(program, state, vm, true, {}, { enrolled.set(true) }, {}, {})
            }
        }

        click(ui(R.string.use_program))
        compose.waitUntil(10_000) { compose.onAllNodesWithText(exercise.name).fetchSemanticsNodes().isNotEmpty() }
        click(exercise.name)
        compose.onNodeWithText(exercise.nickname).assertExists()
        click(ui(R.string.use_program))
        awaitWrite { enrolled.get() && snapshot().programEnrollment?.programId == program.id }
        assertTrue(snapshot().programLinks.any { it.catalogExerciseId == slot.exerciseId && it.exerciseId == exercise.id })
    }

    @Test fun shortWorkoutPassesTheSelectedSetCountToThePlannedVisit() {
        val app = createContainer()
        val catalog = app.programs
        val program = catalog.programs.first()
        val day = program.sessions.first()
        runBlocking {
            app.storageReady.await()
            app.repository.enrollProgram(catalog.schemaVersion, program, day.exercises.associate { it.exerciseId to null })
        }
        val state = snapshot()
        var started: ProgramVisit? = null
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            InShapeTheme {
                ProgramSetupScreen(program, state, vm, false, {}, {}, {}, { started = it })
            }
        }

        click(ui(R.string.program_mode_short))
        click(quantity(R.plurals.short_sets_per_exercise, 1))
        compose.onNodeWithText(quantity(R.plurals.short_sets_per_exercise, 2)).assertExists()
        click(ui(R.string.start_workout))
        compose.runOnIdle {
            val visit = checkNotNull(started)
            assertEquals(ProgramVisitMode.SHORT, visit.mode)
            assertEquals(day.slotsFor(ProgramVisitMode.SHORT).map { it.exerciseId }, visit.slots.map { it.exerciseId })
            assertEquals(2, visit.shortSets)
            assertTrue(visit.slots.all { visit.setCount(it.sets) == 2 })
        }
    }

    @Test fun finalProgramWeekCanReturnToTheFirstWeek() {
        val app = createContainer()
        val catalog = app.programs
        val program = catalog.programs.first()
        val day = program.sessions.first()
        runBlocking {
            app.storageReady.await()
            app.repository.enrollProgram(catalog.schemaVersion, program, day.exercises.associate { it.exerciseId to null })
            val enrollment = checkNotNull(app.repository.snapshot().programEnrollment)
            app.repository.setProgramWeek(enrollment.id, program.durationWeeks)
        }
        val state = snapshot()
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            InShapeTheme { ProgramSetupScreen(program, state, vm, false, {}, {}, {}, {}) }
        }

        compose.onNodeWithText(ui(R.string.program_week, program.durationWeeks, program.durationWeeks)).assertExists()
        compose.onNodeWithContentDescription(ui(R.string.workout_options)).performClick()
        click(ui(R.string.repeat_week_one))
        awaitWrite { snapshot().programEnrollment?.phaseWeek == 1 }
        val enrollment = checkNotNull(snapshot().programEnrollment)
        assertEquals(state.programEnrollment?.id, enrollment.id)
        assertEquals(state.programEnrollment?.nextDayIndex, enrollment.nextDayIndex)
    }

    @Test fun editedDayRequiresAMovementAndPersistsValidPersonalTargetsAtLargeText() {
        val app = createContainer()
        val program = app.programs.programs.first().withSingleDayMovement()
        val catalog = app.programs.copy(programs = listOf(program))
        val day = program.sessions.single()
        val slot = day.exercises.single()
        val name = "Personal test row"
        val exited = AtomicBoolean()
        runBlocking { app.storageReady.await() }
        val state = snapshot()
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            ProgramTestViewport(390, 2f) {
                ProgramDayEditor(catalog, program.id, day.id, state, vm) { exited.set(true) }
            }
        }

        val save = compose.onNode(hasText(ui(R.string.save)) and hasClickAction())
        val remove = ui(R.string.remove_named, slot.name)
        click(remove)
        save.assertIsNotEnabled()
        click(ui(R.string.editor_undo))
        compose.onNodeWithText(remove).assertExists()
        click(remove)
        click(ui(R.string.add_exercise))
        enter(ui(R.string.search_exercises), name)
        click(ui(R.string.editor_create_named, name))
        enter(ui(R.string.minimum_reps), "15")
        compose.onNodeWithText(ui(R.string.editor_bad_targets)).assertExists()
        compose.onNode(hasText(ui(R.string.add_exercise)) and hasClickAction()).assertIsNotEnabled()
        enter(ui(R.string.maximum_reps), "16")
        enter(ui(R.string.sets), "3")
        click(ui(R.string.add_exercise))
        click(ui(R.string.save))
        awaitWrite { exited.get() && snapshot().programDayOverrides.isNotEmpty() }
        val saved = snapshot()
        val personal = saved.programDayOverrides.single().day.exercises.single()
        assertEquals(saved.exercises.single { it.name == name }.id, personal.localExerciseId)
        assertEquals(3, personal.sets)
        assertEquals(ProgramReps(15, 16, false), personal.target)
    }

    @Test fun continuingAfterDiscardPromptPreservesDraftAtNarrowLargeText() {
        val app = createContainer()
        val program = app.programs.programs.first().withSingleDayMovement()
        val catalog = app.programs.copy(programs = listOf(program))
        val day = program.sessions.single()
        val slot = day.exercises.single()
        var exited = false
        runBlocking { app.storageReady.await() }
        val state = snapshot()
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            ProgramTestViewport(320, 2f) {
                ProgramDayEditor(catalog, program.id, day.id, state, vm) { exited = true }
            }
        }

        click(ui(R.string.remove_named, slot.name))
        click(ui(R.string.cancel))
        click(ui(R.string.editor_continue))
        compose.onNodeWithText(ui(R.string.editor_undo)).assertExists()
        compose.runOnIdle { assertFalse(exited) }
        click(ui(R.string.cancel))
        click(ui(R.string.discard))
        compose.runOnIdle { assertTrue(exited) }
        assertTrue(snapshot().programDayOverrides.isEmpty())
    }

    @Test fun pickerBlocksLinkedDuplicatesAndCanAddAnExistingExercise() {
        val app = createContainer()
        val catalog = app.programs
        val day = catalog.programs.first().withSingleDayMovement().sessions.single()
        val linked = Exercise(name = "Fixture linked movement")
        val available = Exercise(name = "Fixture side bridge")
        val links = listOf(ProgramExerciseLink(day.exercises.single().exerciseId, linked.id))
        var added: Pair<ProgramSlot, Exercise?>? = null
        compose.setContent {
            InShapeTheme {
                ProgramMovementPicker(catalog, links, listOf(linked, available), day, {}) { slot, created ->
                    added = slot to created
                }
            }
        }

        enter(ui(R.string.search_exercises), linked.name)
        compose.onNodeWithText(ui(R.string.already_added)).assertExists()
        compose.onNodeWithText(ui(R.string.editor_create_named, linked.name)).assertDoesNotExist()
        compose.onNode(hasText(linked.name) and hasText(ui(R.string.already_added)) and hasClickAction()).assertIsNotEnabled()
        click(ui(R.string.all))
        compose.onNode(hasText(ui(R.string.library)) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        enter(ui(R.string.search_exercises), available.name)
        compose.onNode(hasText(available.name) and !hasSetTextAction() and hasClickAction()).performScrollTo().performClick()
        click(ui(R.string.add_exercise))
        compose.runOnIdle {
            val (slot, created) = checkNotNull(added)
            assertNull(created)
            assertEquals(available.id, slot.localExerciseId)
            assertEquals(ProgramReps(8, 12, false), slot.target)
        }
    }

    @Test fun pickerCopiesTheCatalogPrescriptionWithoutCreatingAnExercise() {
        val catalog = createContainer().programs
        val day = catalog.programs.first().withSingleDayMovement().sessions.single()
        val otherProgram = catalog.programs[1]
        val otherDay = otherProgram.sessions.first()
        val slot = otherDay.exercises.first { it.exerciseId != day.exercises.single().exerciseId }
        var added: Pair<ProgramSlot, Exercise?>? = null
        compose.setContent {
            InShapeTheme {
                ProgramMovementPicker(catalog, emptyList(), emptyList(), day, {}) { chosen, create ->
                    added = chosen to create
                }
            }
        }

        click(ui(R.string.all))
        compose.onNode(hasText(otherProgram.name) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
        enter(ui(R.string.search_exercises), slot.name)
        compose.onNode(
            hasText(slot.name) and hasText(otherDay.name, substring = true) and hasClickAction()
        ).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(slot to null, added)
        }
    }

    private fun enter(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }

    private fun TrainingProgram.withSingleDayMovement(): TrainingProgram {
        val day = sessions.first()
        val slot = day.exercises.first()
        return copy(sequence = listOf(day.id), sessions = listOf(day.copy(miniExerciseIds = listOf(slot.exerciseId), exercises = listOf(slot))))
    }
}

@Composable
private fun ProgramTestViewport(widthDp: Int, fontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val window = LocalWindowInfo.current
    val sizedWindow = object : WindowInfo by window {
        override val containerDpSize = DpSize(widthDp.dp, window.containerDpSize.height)
    }
    CompositionLocalProvider(LocalWindowInfo provides sizedWindow, LocalDensity provides Density(density, fontScale)) {
        InShapeTheme { Box(Modifier.width(widthDp.dp)) { content() } }
    }
}

package com.emmah.inshape.feature.library

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryWorkflowCoverageTest : RedesignInteractionFixture() {
    @Test fun exerciseSearchCombinesNameAndLocationBeforeFilteringFavorites() {
        val favorite = Exercise(name = "Fixture row", location = "Home", favorite = true)
        val plain = Exercise(name = "Fixture pull", location = "Home")
        val archived = Exercise(name = "Fixture stretch", location = "Home", archived = true, favorite = true)
        val otherLocation = Exercise(name = "Fixture lift", location = "Sali", favorite = true)
        launch("LibraryRoutineExpanded", "Library") { app ->
            listOf(favorite, plain, archived, otherLocation).forEach { app.repository.saveExercise(it) }
        }

        click(ui(R.string.exercises))
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.search_exercises))).performTextReplacement("fixture Home")
        compose.onNodeWithText(favorite.name).assertExists()
        compose.onNodeWithText(plain.name).assertExists()
        compose.onNodeWithText(archived.name).assertDoesNotExist()
        compose.onNodeWithText(otherLocation.name).assertDoesNotExist()
        compose.onNodeWithContentDescription(ui(R.string.favorites_only)).performClick()
        compose.onNodeWithText(favorite.name).assertExists()
        compose.onNodeWithText(plain.name).assertDoesNotExist()
        click(favorite.name)
        compose.onNodeWithText(ui(R.string.no_performed_exercise)).performScrollTo().assertExists()
    }

    @Test fun routineExerciseChangesArePersistedAndCanBeRemoved() {
        val routineId = DemoFixtures.routineId
        val added = Exercise(name = "Fixture cable row", category = "Machine")
        launch("LibraryRoutineExpanded", "routine/$routineId") { app ->
            app.repository.saveExercise(added)
        }
        val initialItems = snapshot().routineItems.filter { it.routineId == routineId }

        click(ui(R.string.add_exercise))
        click(added.name)
        compose.waitUntil(10_000) {
            snapshot().routineItems.any { it.routineId == routineId && it.exerciseId == added.id }
        }
        val persisted = snapshot().routineItems.filter { it.routineId == routineId }
        assertEquals(initialItems.map { it.exerciseId } + added.id, persisted.map { it.exerciseId })

        val options = ui(R.string.named_options, added.name)
        compose.onNodeWithContentDescription(options).performScrollTo().performClick()
        click(ui(R.string.remove_exercise))
        compose.waitUntil(10_000) {
            snapshot().routineItems.none { it.routineId == routineId && it.exerciseId == added.id }
        }
        assertEquals(initialItems.map { it.exerciseId }, snapshot().routineItems.filter { it.routineId == routineId }.map { it.exerciseId })
    }

    @Test fun newRoutineRequiresANameAndSavesATimedExerciseWithItsNotes() {
        val name = "My duration routine"
        val exercise = Exercise(name = "Fixture evening walk", basis = "time")
        launch("LibraryRoutineExpanded", "routine/new") { app -> app.repository.saveExercise(exercise) }

        click(ui(R.string.done))
        compose.onNodeWithText(ui(R.string.routine_name_required)).assertExists()
        enter(R.string.routine_name, name)
        enter(R.string.description_optional, "Easy recovery")
        enter(R.string.estimated_minutes_optional, "30")
        click(ui(R.string.done))
        compose.onNodeWithText(ui(R.string.create_routine)).assertIsNotEnabled()
        click(ui(R.string.add_exercise))
        click(exercise.name)
        compose.onNodeWithContentDescription(ui(R.string.named_options, exercise.name)).performScrollTo().performClick()
        click(ui(R.string.edit_targets_note))
        compose.onNodeWithContentDescription(ui(R.string.log_as) + ", " + ui(R.string.sets)).performScrollTo().performClick()
        click(ui(R.string.time))
        enter(R.string.planned_minutes, "18")
        enter(R.string.plan_note, "Keep an easy pace")
        click(ui(R.string.done))
        click(ui(R.string.create_routine))

        awaitWrite { snapshot().routines.any { it.title == name } }
        val saved = snapshot()
        val routine = saved.routines.single { it.title == name }
        val target = saved.routineItems.single { it.routineId == routine.id }
        assertEquals("Easy recovery", routine.description)
        assertEquals(30, routine.durationMinutes)
        assertEquals(exercise.id, target.exerciseId)
        assertEquals("DURATION", target.mode)
        assertEquals(1080L, target.durationSeconds)
        assertEquals("Keep an easy pace", target.note)
        assertTrue(target.sets.isEmpty())
    }

    @Test fun existingRoutineCanBeDuplicatedWithItsExerciseOrderIntact() {
        val originalId = DemoFixtures.routineId
        launch("LibraryRoutineExpanded", "routine/$originalId")
        val before = snapshot()
        val original = before.routines.single { it.id == originalId }
        val originalItems = before.routineItems.filter { it.routineId == original.id }.sortedBy { it.position }
        compose.onNodeWithContentDescription(ui(R.string.routine_options)).performClick()
        click(ui(R.string.duplicate))

        awaitWrite { snapshot().routines.size > before.routines.size }
        val after = snapshot()
        val newId = after.routines.single { routine -> before.routines.none { it.id == routine.id } }.id
        val duplicated = after.routineItems.filter { it.routineId == newId }.sortedBy { it.position }
        assertEquals(originalItems.map { it.exerciseId }, duplicated.map { it.exerciseId })
        assertEquals(originalItems.map { it.sets }, duplicated.map { it.sets })
        assertEquals(original, after.routines.single { it.id == original.id })
    }

    private fun enter(label: Int, value: String) {
        compose.onNode(hasSetTextAction() and hasText(ui(label))).performScrollTo().performTextReplacement(value)
    }
}

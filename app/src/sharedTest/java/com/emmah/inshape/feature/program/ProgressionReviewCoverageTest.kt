package com.emmah.inshape.feature.program

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.SaveState
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.PlannedReps
import com.emmah.inshape.data.ProgramWorkoutSource
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressionReviewCoverageTest : RedesignInteractionFixture() {
    @Test fun reviewRequiresAPositiveIncrementAndSavesDecimalSuggestedLoads() {
        openReview()

        val confirm = compose.onNodeWithText(ui(R.string.confirm_load_increase))
        confirm.assertIsNotEnabled()
        enterIncrement("0")
        confirm.assertIsNotEnabled()
        enterIncrement("2,5")
        compose.onNodeWithText(ui(R.string.load_increase_proposal, "22,5")).assertExists()
        click(ui(R.string.confirm_load_increase))

        awaitWrite { snapshot().active?.items?.single()?.sets?.all { it.suggestedLoad == "22.5" && it.rawLoad == "22.5" } == true }
        val item = checkNotNull(snapshot().active).items.single()
        assertEquals(2, item.sets.size)
        assertTrue(item.completedSets.isEmpty())
    }

    @Test fun reviewCanIncreaseTheMachinesWholeLevel() {
        openReview("level", "4")
        enterIncrement("1")
        compose.onNodeWithText(ui(R.string.load_increase_proposal, "5")).assertExists()
        click(ui(R.string.confirm_load_increase))

        awaitWrite { snapshot().active?.items?.single()?.sets?.all { it.rawLoad == "5" } == true }
        assertEquals(listOf("5", "5"), checkNotNull(snapshot().active).items.single().sets.map { it.suggestedLoad })
    }

    @Test fun changedWorkoutRejectsAQueuedLoadIncrease() {
        openReview()
        queueReviewThen {
            val repository = journalViewModel.repository
            val active = checkNotNull(repository.snapshot().active)
            repository.saveWorkoutNote(active.row.id, "Workout updated while reviewing")
        }

        assertRejected(R.string.workout_changed_review)
        val state = checkNotNull(snapshot().active)
        assertEquals("Workout updated while reviewing", state.row.note)
    }

    @Test fun rejectsStaleHistory() {
        openReview()
        val revision = checkNotNull(snapshot().active).row.revision
        queueReviewThen {
            val repository = journalViewModel.repository
            val historicalSet = repository.snapshot().history.first().items.single().completedSets.first()
            repository.correctCompletedSet(historicalSet.id, "18", "12", "")
        }

        assertRejected(R.string.planned_sets_changed)
        assertEquals(revision, checkNotNull(snapshot().active).row.revision)
    }

    private fun queueReviewThen(edit: suspend () -> Unit) {
        enterIncrement("1")
        val release = CompletableDeferred<Unit>()
        journalViewModel.write { release.await() }
        try {
            click(ui(R.string.confirm_load_increase))
            runBlocking { edit() }
        } finally {
            release.complete(Unit)
        }
    }

    private fun assertRejected(message: Int) {
        awaitWrite { journalViewModel.saveState.value is SaveState.Failed }
        val failure = journalViewModel.saveState.value as SaveState.Failed
        assertEquals(ui(message), failure.message)
        assertTrue(checkNotNull(snapshot().active).items.single().sets.all { it.suggestedLoad == null && it.rawLoad.isEmpty() })
    }

    private fun enterIncrement(amount: String) {
        val field = hasText(ui(R.string.smallest_increment)) and hasSetTextAction()
        compose.onNode(field).performScrollTo().performTextReplacement(amount)
    }

    private fun openReview(basis: String = "kg", load: String = "20") {
        val currentId = newId()
        val exercise = Exercise(name = "Fixture progression exercise", basis = basis)
        val source = ProgramWorkoutSource("fixture", "training", 1, "A", 0, 1, "STANDARD")
        launch("Empty", "workout/$currentId") { app ->
            app.repository.saveExercise(exercise)
            val dao = app.database.journal()
            val dates = listOf("2026-11-09", "2026-11-11", "2026-11-13")
            dates.forEachIndexed { index, date ->
                val previous = index < dates.lastIndex
                val instant = "${date}T09:00:00Z"
                val workout = WorkoutEntity(
                    id = if (previous) newId() else currentId,
                    journalDate = date,
                    zoneId = app.clock.zone.id,
                    state = if (previous) "COMPLETED" else "ACTIVE",
                    activeKey = if (previous) null else 1,
                    start = instant,
                    end = instant.takeIf { previous },
                    createdAt = instant,
                    modifiedAt = instant,
                    program = source
                )
                dao.insertWorkout(workout)
                val item = SessionItem(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    equipmentId = exercise.equipmentId,
                    name = exercise.name,
                    category = exercise.category,
                    basis = basis,
                    disposition = if (previous) "DONE" else "IN_PROGRESS"
                )
                dao.put(item)
                repeat(2) { position ->
                    dao.put(
                        SessionSet(
                            itemId = item.id,
                            position = position,
                            load = load.takeIf { previous },
                            reps = 12.takeIf { previous },
                            completedAt = instant.takeIf { previous },
                            plannedReps = PlannedReps(8, 12)
                        )
                    )
                }
            }
        }
        click(ui(R.string.review_load_increase))
    }
}

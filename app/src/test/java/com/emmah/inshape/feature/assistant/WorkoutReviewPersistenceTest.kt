package com.emmah.inshape.feature.assistant

import com.emmah.inshape.testContext
import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.PlannedReps
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.createTestDatabase
import com.emmah.inshape.data.newId
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutReviewPersistenceTest {
    @Test fun assistantLogInheritsPlannedSideUnlessExplicitlyOverridden() = runBlocking {
        val db = createTestDatabase()
        try {
            val repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val exercise = Exercise(name = "Fixture split squat")
            repo.saveExercise(exercise)
            val workoutId = repo.startWorkout(StartSource.Empty)
            val itemId = repo.addExercise(workoutId, exercise.id)
            val ids = List(2) { repo.addSet(itemId) }
            val dao = db.journal()
            dao.sets().filter { it.id in ids }.forEach { set ->
                dao.put(set.copy(plannedReps = PlannedReps(8, 12, perSide = true)))
            }
            val command = AssistantDraft("fixture", newId(), "Fixture")
            dao.put(command)
            val proposed = listOf(ReviewedSet("20", "10"), ReviewedSet("20", "10", perSide = false))
            val revision = requireNotNull(repo.snapshot().active).row.revision
            val review = WorkoutReviewDraft(revision, workoutId, itemId, null, WorkoutReview("log", proposed))

            assertEquals(ids, repo.confirmWorkoutReview(command, review))
            assertEquals(listOf(true, false), repo.snapshot().active!!.items.single().completedSets.map { it.perSide })
        } finally {
            db.close()
        }
    }

    @Test fun staleReviewWritesNothingAndRepeatedConfirmationReturnsTheSameSets() = runBlocking {
        val db = createTestDatabase()
        try {
            val repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val exercise = Exercise(name = "Fixture leg press")
            repo.saveExercise(exercise)
            val workoutId = repo.startWorkout(StartSource.Empty)
            val itemId = repo.addExercise(workoutId, exercise.id)
            val setId = repo.addSet(itemId)
            val dao = db.journal()
            val proposal = WorkoutReview("log", listOf(ReviewedSet("42,5", "12"), ReviewedSet("42,5", "10")))
            val command = AssistantDraft("fixture", newId(), "Fixture")
            val before = requireNotNull(repo.snapshot().active)
            val preview = WorkoutReviewDraft(before.row.revision, workoutId, itemId, null, proposal)
            dao.put(command)
            assertTrue(repo.saveAssistantReview(command, preview))
            repo.saveSetDraft(setId, "35", "8")
            assertTrue(runCatching { repo.confirmWorkoutReview(command, preview) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(emptyList<String>(), repo.snapshot().active!!.items.single().completedSets.map { it.id })
            val revised = preview.copy(revision = repo.snapshot().active!!.row.revision)
            assertTrue(repo.saveAssistantReview(command, revised))
            val ids = repo.confirmWorkoutReview(command, revised)
            assertEquals(listOf(setId, ids.last()), ids)
            assertEquals(ids, repo.confirmWorkoutReview(command, revised))
            assertEquals(listOf("42.5", "42.5"), repo.snapshot().active!!.items.single().completedSets.map { it.load })
        } finally {
            db.close()
        }
    }
}

package com.emmah.inshape.feature.assistant

import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.PlannedReps
import com.emmah.inshape.data.Setting
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.createTestDatabase
import com.emmah.inshape.data.newId
import com.emmah.inshape.testContext
import java.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutReviewPersistenceTest {
    @Test fun workoutReviewSavesCorrectedDurationAndRejectsSubsequentReceiptConflict() = runBlocking {
        val db = createTestDatabase()
        try {
            val repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val exercise = Exercise(name = "Timed exercise", basis = "duration")
            repo.saveExercise(exercise)
            val workoutId = repo.startWorkout(StartSource.Empty)
            val itemId = repo.addExercise(workoutId, exercise.id)
            val draft = AssistantDraft("fixture", newId(), "Kirjaa aika")
            val revision = requireNotNull(repo.snapshot().active).row.revision
            db.journal().put(draft)
            val review = WorkoutReviewDraft(revision, workoutId, itemId, null, WorkoutReview("duration", durationSeconds = 83L))

            assertEquals(listOf(itemId), repo.confirmWorkoutReview(draft, review))
            assertEquals(83L, repo.snapshot().items.single().durationSeconds)
            assertNull(db.journal().assistantDraft(draft.key))
            val conflict = review.copy(proposal = review.proposal.copy(durationSeconds = 96L))
            assertTrue(runCatching { repo.confirmWorkoutReview(draft, conflict) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(83L, repo.snapshot().items.single().durationSeconds)
        } finally {
            db.close()
        }
    }

    @Test fun memoryProposalsUpdateTheSameMachineAndKeepVisitAndGlobalScopeDistinct() = runBlocking {
        val db = createTestDatabase()
        try {
            val repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val exercise = Exercise(name = "Soutu", settings = listOf(Setting("Seat", "2")))
            repo.saveExercise(exercise)
            val workoutId = repo.startWorkout(StartSource.Empty)
            val itemId = repo.addExercise(workoutId, exercise.id)
            repo.saveItemNote(itemId, "Existing note")
            val dao = db.journal()

            suspend fun confirm(proposal: WorkoutReview) {
                val source = repo.snapshot().exercises.single()
                val command = AssistantDraft("fixture", newId(), "Fixture")
                val revision = requireNotNull(repo.snapshot().active).row.revision
                val draft = WorkoutReviewDraft(revision, workoutId, itemId, fingerprint(Json.encodeToString(source)), proposal)
                dao.put(command)
                assertTrue(repo.saveAssistantReview(command, draft))
                assertEquals(listOf(itemId), repo.confirmWorkoutReview(command, draft))
                assertFalse(repo.saveAssistantReview(command, draft))
            }

            confirm(WorkoutReview("setting", label = "seat", value = "4"))
            assertEquals(listOf(Setting("seat", "4")), repo.snapshot().exercises.single().settings)
            confirm(WorkoutReview("nickname", value = "Vetolaite"))
            assertEquals("Vetolaite", repo.snapshot().exercises.single().nickname)
            confirm(WorkoutReview("visitNote", value = "Used the narrow grip"))
            assertEquals("Existing note\nUsed the narrow grip", repo.snapshot().items.single().note)
            confirm(WorkoutReview("preference", value = "Use Finnish names"))
            confirm(WorkoutReview("future", value = "Try the wide grip"))
            val byKind = repo.snapshot().assistantMemories.associateBy { it.kind }
            assertNull(byKind.getValue("preference").exerciseId)
            assertNull(byKind.getValue("preference").creationWorkoutId)
            assertEquals(exercise.id, byKind.getValue("future").exerciseId)
            assertEquals(workoutId, byKind.getValue("future").creationWorkoutId)
            assertEquals("Try the wide grip", byKind.getValue("future").text)
        } finally {
            db.close()
        }
    }

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
            val proposal = WorkoutReview("log", listOf(ReviewedSet("42,5", "12"), ReviewedSet("42,5", "10"), ReviewedSet("42,5", "8")))
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
            assertEquals(setId, ids.first())
            assertEquals(ids, repo.confirmWorkoutReview(command, revised))
            val completed = repo.snapshot().active!!.items.single().completedSets
            assertEquals(ids, completed.map { it.id })
            assertEquals(listOf(0, 1, 2), completed.map { it.position })
            assertEquals(listOf(12, 10, 8), completed.map { it.reps })
            assertEquals(List(3) { "42.5" }, completed.map { it.load })
        } finally {
            db.close()
        }
    }
}

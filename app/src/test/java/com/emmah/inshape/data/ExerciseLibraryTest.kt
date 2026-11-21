package com.emmah.inshape.data

import com.emmah.inshape.testContext
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseLibraryTest {
    private lateinit var db: JournalDatabase
    private lateinit var repo: JournalRepository

    @Before fun setup() {
        db = createTestDatabase()
        repo =
            JournalRepository(db, Clock.systemUTC(), testContext())
    }

    @After fun close() = db.close()

    @Test fun combinesNicknameCategoryLocationAndFavoriteFilters() = runBlocking {
        val a = Exercise(name = "Row", nickname = "Blue machine", favorite = true)
        val b =
            Exercise(name = "Row", nickname = "Blue home row", location = "Home", favorite = true)
        repo.saveExercise(a)
        repo.saveExercise(b)
        assertNotEquals(a.equipmentId, b.equipmentId)
        assertEquals(
            listOf(a.id),
            repo.observeExercises(ExerciseQuery("blue", "Machine", "Gym", true)).first().map {
                it.id
            }
        )
        repo.archiveExercise(a.id)
        assertEquals(
            emptyList<Exercise>(),
            repo.observeExercises(ExerciseQuery("blue", "Machine", "Gym", true)).first()
        )
    }

    @Test fun duplicateRoutineHasNewIdsAndRemovalPreservesSession() = runBlocking {
        val exercise = Exercise(name = "Stretch", category = "Mobility", basis = "duration")
        repo.saveExercise(exercise)
        val routine = Routine(title = "Mobility")
        repo.saveRoutine(
            routine,
            listOf(
                RoutineItem(
                    routineId = routine.id,
                    exerciseId = exercise.id,
                    mode = "DURATION",
                    durationSeconds = 180
                )
            )
        )
        val copiedId = repo.duplicateRoutine(routine.id)
        val snapshot = repo.snapshot()
        assertNotEquals(routine.id, copiedId)
        assertEquals(2, snapshot.routineItems.map { it.id }.distinct().size)
        val workoutId = repo.startWorkout(StartSource.Routine(routine.id))
        repo.removeRoutine(routine.id)
        assertEquals("Stretch", repo.snapshot().workout(workoutId)!!.items.single().row.name)
        assertFalse(repo.snapshot().workout(workoutId)!!.hasPerformedWork)
    }
}

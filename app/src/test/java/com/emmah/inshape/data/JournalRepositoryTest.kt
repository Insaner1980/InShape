package com.emmah.inshape.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalRepositoryTest {
    private lateinit var db: JournalDatabase
    private lateinit var repo: JournalRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JournalDatabase::class.java).allowMainThreadQueries().build()
        repo = JournalRepository(db, Clock.fixed(Instant.parse("2026-11-13T08:42:00Z"), ZoneId.of("Europe/Helsinki")))
    }
    @After fun close() = db.close()
    @Test fun reopeningOldActiveSessionRequiresTimingReviewWithoutStartingAgain() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        val nextDay = JournalRepository(db, Clock.fixed(Instant.parse("2026-11-14T08:00:00Z"), ZoneId.of("Europe/Helsinki")))
        assertEquals(id, nextDay.snapshot().active!!.row.id)
        assertTrue(nextDay.snapshot().active!!.row.timingNeedsReview)
        assertEquals("2026-11-13", nextDay.snapshot().active!!.row.journalDate)
    }
    @Test fun savingSessionPlanPreservesQuickAndDurationModes() = runBlocking {
        val exercise = Exercise(name = "Mobility"); repo.saveExercise(exercise)
        val routine = Routine(title = "Mixed modes")
        repo.saveRoutine(routine, listOf("QUICK", "DURATION", "SETS").map { RoutineItem(routineId = routine.id, exerciseId = exercise.id, mode = it) })
        val workout = repo.startWorkout(StartSource.Routine(routine.id))
        repo.saveSessionPlanToRoutine(workout, routine.id)
        assertEquals(listOf("QUICK", "DURATION", "SETS"), repo.snapshot().routineItems.sortedBy { it.position }.map { it.mode })
    }
    @Test fun removingOriginalExerciseKeepsAlternativeAndBackupValid() = runBlocking {
        val original = Exercise(name = "Pulldown"); val alternative = Exercise(name = "Row")
        repo.saveExercise(original); repo.saveExercise(alternative)
        val workout = repo.startWorkout(StartSource.Empty)
        val first = repo.addExercise(workout, original.id)
        val replacement = repo.useAlternative(first, alternative.id)
        repo.removeItem(first)
        val snapshot = repo.snapshot()
        assertEquals(replacement, snapshot.items.single().id)
        assertNull(snapshot.items.single().alternativeOf)
        com.emmah.inshape.transfer.validateBackupData(snapshot)
    }
    @Test fun historicalTimingCorrectionUpdatesStoredOffsets() = runBlocking {
        val id = repo.createPastWorkout("2026-06-01", "Past workout")
        val before = repo.snapshot().workout(id)!!.row
        repo.correctWorkout(id, before.revision, before.title, before.journalDate, "Note", WorkoutTiming(start = "2026-06-01T10:00:00Z", end = "2026-06-01T11:00:00Z", acknowledged = true))
        val corrected = repo.snapshot().workout(id)!!.row
        assertEquals("+03:00", corrected.startOffset); assertEquals("+03:00", corrected.endOffset)
    }

    @Test fun startsOnceAndSuggestionsNeverCountAsWork() = runBlocking {
        val exercise = Exercise(name = "Lat pulldown", equipmentName = "Machine A")
        repo.saveExercise(exercise)
        val routine = Routine(title = "Full body A")
        repo.saveRoutine(routine, listOf(RoutineItem(routineId = routine.id, exerciseId = exercise.id, sets = listOf(SetTarget("27.5", 10), SetTarget("27.5", 9)))))
        val first = repo.startWorkout(StartSource.Routine(routine.id))
        assertEquals(first, repo.startWorkout(StartSource.Empty))
        val workout = repo.snapshot().workout(first)!!
        assertFalse(workout.hasPerformedWork)
        assertEquals(2, workout.items.single().sets.size)
        assertTrue(workout.items.single().sets.all { it.completedAt == null })
    }

    @Test fun keepsPartialWorkOnOriginalExerciseWhenUsingAlternative() = runBlocking {
        val original = Exercise(name = "Pulldown", equipmentName = "A")
        val alternative = Exercise(name = "Row", equipmentName = "B")
        repo.saveExercise(original); repo.saveExercise(alternative)
        val id = repo.startWorkout(StartSource.Empty)
        val item = repo.addExercise(id, original.id)
        val set = repo.addSet(item)
        repo.saveSetDraft(set, "27,5", "10")
        repo.setSetCompleted(set, true)
        val other = repo.useAlternative(item, alternative.id)
        val saved = repo.snapshot().workout(id)!!
        assertEquals("27.5", saved.items.first { it.row.id == item }.sets.single().load)
        assertEquals(original.id, saved.items.first { it.row.id == item }.row.exerciseId)
        assertFalse(saved.items.first { it.row.id == other }.hasPerformedWork)
        assertEquals(item, saved.items.first { it.row.id == other }.row.alternativeOf)
    }

    @Test fun finishingIsIdempotentAndRepeatingResetsPerformedState() = runBlocking {
        val exercise = Exercise(name = "Mobility", category = "Mobility", basis = "duration")
        repo.saveExercise(exercise)
        val id = repo.startWorkout(StartSource.Empty)
        val item = repo.addExercise(id, exercise.id)
        repo.markItemDone(item, 300)
        val timing = WorkoutTiming(end = "2026-11-13T09:00:00Z", durationSeconds = 1080, acknowledged = true)
        repo.finishWorkout(id, timing); repo.finishWorkout(id, timing)
        assertEquals(1, repo.snapshot().workouts.count { it.state == "COMPLETED" })
        val repeated = repo.startWorkout(StartSource.Repeat(id))
        assertNotEquals(id, repeated)
        assertFalse(repo.snapshot().workout(repeated)!!.hasPerformedWork)
        assertNull(repo.snapshot().workout(repeated)!!.items.single().row.durationSeconds)
    }

    @Test fun historicalSnapshotsSurviveLibraryChanges() = runBlocking {
        val exercise = Exercise(name = "Old name", equipmentName = "Machine A", settings = listOf(Setting("Seat", "4")))
        repo.saveExercise(exercise)
        val id = repo.startWorkout(StartSource.Empty)
        repo.addExercise(id, exercise.id)
        repo.saveExercise(exercise.copy(name = "New name", archived = true, settings = listOf(Setting("Seat", "6"))))
        val row = repo.snapshot().workout(id)!!.items.single().row
        assertEquals("Old name", row.name)
        assertEquals("Seat: 4", row.setup)
    }

    @Test fun rejectsInvalidCorrectionWithoutChangingCompletedValues() = runBlocking {
        val exercise = Exercise(name = "Lift"); repo.saveExercise(exercise)
        val workoutId = repo.startWorkout(StartSource.Empty)
        val item = repo.addExercise(workoutId, exercise.id)
        val set = repo.addSet(item)
        repo.saveSetDraft(set, "0", "10"); repo.setSetCompleted(set, true)
        try { repo.correctCompletedSet(set, "-1", "0"); fail("Invalid correction accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("0", repo.snapshot().sets.single().load)
        assertEquals(10, repo.snapshot().sets.single().reps)
        repo.setSetCompleted(set, false)
        assertFalse(repo.snapshot().workout(workoutId)!!.hasPerformedWork)
    }

    @Test fun scoresKeepMissingZeroAndPhasesDistinct() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        repo.saveCheckIn(CheckIn(workoutId = id, phase = "BEFORE", observedAt = "2026-11-13T08:42:00Z", zoneId = "Europe/Helsinki"), listOf(BodyObservation(region = "right_knee", pain = 0)))
        val snap = repo.snapshot()
        assertEquals(0, snap.observations.single().pain)
        assertNull(snap.observations.single().stiffness)
        assertEquals("BEFORE", snap.checkIns.single().phase)
    }
}

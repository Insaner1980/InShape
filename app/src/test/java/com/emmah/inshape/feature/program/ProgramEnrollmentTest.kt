package com.emmah.inshape.feature.program

import com.emmah.inshape.testContext
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.WorkoutTiming
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramEnrollmentTest {
    @Test fun performedMiniAdvancesOnceAndAFreeVisitDoesNotConsumeADay() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val repo = JournalRepository(db, Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC), testContext())
            val catalog = ProgramCatalog.read(context.assets.open("programs/beginner_fi_v1.json").bufferedReader().use { it.readText() })
            val program = catalog.programs.last()
            val first = program.sessions.first()
            repo.enrollProgram(catalog.schemaVersion, program, first.exercises.associate { it.exerciseId to null })
            val enrollment = requireNotNull(repo.snapshot().programEnrollment)
            val mini = first.exercises.filter { it.exerciseId in first.miniExerciseIds }
            val links = repo.snapshot().programLinks.associate { it.catalogExerciseId to it.exerciseId }
            val visit = ProgramVisit(
                enrollment, first, ProgramVisitMode.SHORT, 1, mini.associate { it.exerciseId to links[it.exerciseId] }, program.sequence
            )
            val id = repo.startWorkout(StartSource.Program(visit))
            assertEquals(id, repo.startWorkout(StartSource.Empty))
            val workout = requireNotNull(repo.snapshot().workout(id))
            assertEquals(4, workout.items.size)
            assertEquals(mini.map { it.exerciseId }, workout.items.map { it.row.plan?.catalogExerciseId })
            assertNull(workout.items.first().sets.single().reps)
            val set = workout.items.first().sets.single()
            assertEquals(10..12, set.plannedReps!!.let { it.min..it.max })
            repo.saveSetDraft(set.id, "40", "12")
            repo.setSetCompleted(set.id, true)
            val timing = WorkoutTiming(end = "2026-11-20T12:01:00Z")
            repo.finishWorkout(id, timing)
            repo.finishWorkout(id, timing)
            assertEquals(1, repo.snapshot().programEnrollment?.nextDayIndex)
            val free = repo.startWorkout(StartSource.Empty)
            assertNull(repo.snapshot().workout(free)?.row?.program)
            repo.finishWorkout(free, timing, "Short visit", expectedNote = "")
            assertEquals(1, repo.snapshot().programEnrollment?.nextDayIndex)
            assertTrue(repo.snapshot().history.single { it.row.id == id }.hasPerformedWork)
        } finally {
            db.close()
        }
    }
}

package com.emmah.inshape.feature.program

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.testContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramEnrollmentTest {
    @Test fun personalDaysUseLocalIdentityAndRejectStaleChangesWithoutTouchingTheWorkout() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val repo = JournalRepository(db, Clock.systemUTC(), testContext())
            val catalog = ProgramCatalog.read(context.assets.open("programs/beginner_fi_v1.json").bufferedReader().use { it.readText() })
            val program = catalog.programs.first { it.sessions.size >= 2 }
            val first = program.sessions.first { it.id == program.sequence.first() }
            val second = program.sessions.first { it.id != first.id }
            val copied = catalog.programs.flatMap { it.sessions }.flatMap { it.exercises }.first { it.alternatives.isNotEmpty() }
            val local = Exercise(name = copied.name)
            val personal = personalProgramSlot(local, 3, ProgramReps(9, 13, true))
            val edited = first.withExercises(listOf(personal, copied))
            val other = second.withExercises(second.exercises.takeLast(1))
            assertEquals(listOf(other.exercises.single().exerciseId), other.miniExerciseIds)
            assertTrue(personal.exerciseId in edited.miniExerciseIds)
            assertEquals(program, catalog.personalProgram(program.id, emptyList()))
            repo.saveProgramDays(catalog, program.id, listOf(edited), emptyList(), listOf(local))
            val initial = repo.snapshot().programDayOverrides
            val effective = requireNotNull(catalog.personalProgram(program.id, initial))
            assertEquals(second, effective.sessions.first { it.id == second.id })
            val choices = edited.exercises.associate { it.exerciseId to it.localExerciseId }
            assertThrows(com.emmah.inshape.ui.UiException::class.java) {
                runBlocking { repo.enrollProgram(catalog.schemaVersion, program, emptyMap()) }
            }
            repo.enrollProgram(catalog.schemaVersion, effective, choices, expectedOverride = initial.single())
            val enrollment = requireNotNull(repo.snapshot().programEnrollment)
            val oldVisit = ProgramVisit(
                enrollment,
                edited,
                ProgramVisitMode.STANDARD,
                choices = choices,
                sequence = program.sequence,
                expectedOverride = initial.single()
            )
            val revised = edited.withExercises(listOf(copied, personal))
            repo.saveProgramDays(catalog, program.id, listOf(revised, other), initial, emptyList())
            val revisedRows = repo.snapshot().programDayOverrides
            assertEquals(2, revisedRows.size)
            assertEquals(enrollment, repo.snapshot().programEnrollment)
            assertThrows(com.emmah.inshape.ui.UiException::class.java) {
                runBlocking { repo.startWorkout(StartSource.Program(oldVisit)) }
            }
            val stray = Exercise(name = "Peruttu")
            val stale = edited.withExercises(listOf(personalProgramSlot(stray, 1, ProgramReps(1, 1, false))))
            assertThrows(com.emmah.inshape.ui.UiException::class.java) {
                runBlocking { repo.saveProgramDays(catalog, program.id, listOf(stale), initial, listOf(stray)) }
            }
            assertTrue(repo.snapshot().exercises.none { it.id == stray.id })
            val visit = oldVisit.copy(day = revised, expectedOverride = revisedRows.single { it.dayId == first.id })
            val workoutId = repo.startWorkout(StartSource.Program(visit))
            val workout = requireNotNull(repo.snapshot().workout(workoutId))
            assertEquals(listOf(copied, personal), workout.items.map { it.row.plan?.sourceSlot })
            assertEquals(local.id, workout.items.last().row.exerciseId)
            assertEquals(3, workout.items.last().sets.size)
            assertEquals(9..13, workout.items.last().sets.first().plannedReps!!.let { it.min..it.max })
            repo.saveProgramDays(catalog, program.id, listOf(first), revisedRows, emptyList())
            assertEquals(workout, repo.snapshot().workout(workoutId))
            assertEquals(other.id, repo.snapshot().programDayOverrides.single().dayId)
        } finally {
            db.close()
        }
    }

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
                enrollment,
                first,
                ProgramVisitMode.SHORT,
                1,
                mini.associate { it.exerciseId to links[it.exerciseId] },
                program.sequence
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

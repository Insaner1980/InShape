package com.emmah.inshape.data

import com.emmah.inshape.R
import com.emmah.inshape.ui.UiException
import com.emmah.inshape.ui
import com.emmah.inshape.testContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalRepositoryTest {
    private lateinit var db: JournalDatabase
    private lateinit var repo: JournalRepository

    @Before fun setup() {
        db = createTestDatabase()
        repo =
            JournalRepository(
                db,
                Clock.fixed(Instant.parse("2026-11-13T08:42:00Z"), ZoneId.of("Europe/Helsinki")),
                testContext()
            )
    }

    @After fun close() = db.close()

    @Test fun startUsesOneInstantForTheWorkoutDateAndTimestamps() = runBlocking {
        val beforeMidnight = Instant.parse("2026-11-13T21:59:59Z")
        val afterMidnight = beforeMidnight.plusSeconds(1)
        val reads = AtomicInteger()
        val clock = object : Clock() {
            override fun getZone(): ZoneId = repo.clock.zone
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
            override fun instant(): Instant = if (reads.getAndIncrement() < 2) beforeMidnight else afterMidnight
        }
        JournalRepository(db, clock, testContext()).startWorkout(StartSource.Empty)
        val workout = db.journal().workouts().single()
        assertEquals("2026-11-13", workout.journalDate)
        assertEquals(beforeMidnight.toString(), workout.start)
        assertEquals(beforeMidnight.toString(), workout.createdAt)
        assertEquals(beforeMidnight.toString(), workout.modifiedAt)
    }

    @Test fun deletingWorkoutRemovesOnlyItsFinishAndCheckInDrafts() = runBlocking {
        val removed = repo.startWorkout(StartSource.Empty)
        val retained = repo.createPastWorkout("2026-11-10", "Keep")
        val retainedDraft = EditorDraft("checkin:$retained:AFTER", "checkin", "{}", repo.clock.instant().toString())
        val removedDrafts = listOf("BEFORE", "AFTER", "NEXT_DAY").map {
            EditorDraft("checkin:$removed:$it", "checkin", "{}", repo.clock.instant().toString())
        } + EditorDraft("finish:$removed", "finish", "{}", repo.clock.instant().toString())
        (removedDrafts + retainedDraft).forEach { repo.saveEditorDraft(it) }

        repo.deleteWorkout(removed)

        assertEquals(listOf(retainedDraft), repo.snapshot().drafts)
    }

    @Test fun appendingAfterDeletionKeepsItemsAndSetsInOrder() = runBlocking {
        val exercise = Exercise(name = "Row")
        repo.saveExercise(exercise)
        val workoutId = repo.startWorkout(StartSource.Empty)
        val items = List(3) { repo.addExercise(workoutId, exercise.id) }
        repo.removeItem(items.first())
        val appended = repo.addExercise(workoutId, exercise.id)
        assertEquals(items.drop(1) + appended, repo.snapshot().workout(workoutId)!!.items.map { it.row.id })
        assertEquals(3, repo.snapshot().items.first { it.id == appended }.position)
        val sets = List(3) { repo.addSet(appended) }
        repo.saveSetDraft(sets.last(), "42", "8")
        repo.removeSet(sets.first())
        val next = repo.addSet(appended)
        val saved = repo.snapshot().workout(workoutId)!!.items.last().sets
        assertEquals(sets.drop(1) + next, saved.map { it.id })
        assertEquals(3, saved.last().position)
        assertEquals("42", saved.last().rawLoad)
        assertEquals("8", saved.last().rawReps)
    }

    @Test fun noteAndDiscomfortUpdatesPreserveEachOther() = runBlocking {
        val exercise = Exercise(name = "Row")
        repo.saveExercise(exercise)
        val workout = repo.startWorkout(StartSource.Empty)
        val item = repo.addExercise(workout, exercise.id)
        repo.saveItemNote(item, discomfort = true)
        repo.saveItemNote(item, note = "Keep shoulders down")
        val noted = repo.snapshot().items.single { it.id == item }
        assertTrue(noted.discomfort)
        assertEquals("Keep shoulders down", noted.note)

        repo.saveItemNote(item, discomfort = false)
        val updated = repo.snapshot().items.single { it.id == item }
        assertFalse(updated.discomfort)
        assertEquals("Keep shoulders down", updated.note)
    }

    @Test fun staleCheckInNoteCannotOverwriteAnotherPhaseAndRollsBack() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        repo.saveWorkoutNote(id, "Newer note")
        val check = CheckIn(workoutId = id, phase = "BEFORE", observedAt = "2026-11-13T08:42:00Z", zoneId = "UTC")
        repo.saveCheckIn(check, emptyList())
        assertEquals("Newer note", repo.snapshot().workout(id)!!.row.note)
        val result = runCatching { repo.saveCheckIn(check.copy(energy = "LOW"), emptyList(), "Stale edit", "Old note") }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertNull(repo.snapshot().checkIns.single().energy)
        assertEquals("Newer note", repo.snapshot().workout(id)!!.row.note)
        repo.saveCheckIn(check, emptyList(), "Reviewed edit", "Newer note")
        assertEquals("Reviewed edit", repo.snapshot().workout(id)!!.row.note)
    }

    @Test fun reopeningOldActiveSessionRequiresTimingReviewWithoutStartingAgain() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        val nextDay =
            JournalRepository(
                db,
                Clock.fixed(Instant.parse("2026-11-14T08:00:00Z"), ZoneId.of("Europe/Helsinki")),
                testContext()
            )
        assertEquals(id, nextDay.snapshot().active!!.row.id)
        assertTrue(nextDay.snapshot().active!!.row.timingNeedsReview)
        assertEquals("2026-11-13", nextDay.snapshot().active!!.row.journalDate)
    }

    @Test fun savingSessionPlanPreservesQuickAndDurationModes() = runBlocking {
        val exercise = Exercise(name = "Mobility")
        repo.saveExercise(exercise)
        val routine = Routine(title = "Mixed modes")
        repo.saveRoutine(
            routine,
            listOf("QUICK", "DURATION", "SETS").map {
                RoutineItem(routineId = routine.id, exerciseId = exercise.id, mode = it)
            }
        )
        val workout = repo.startWorkout(StartSource.Routine(routine.id))
        repo.saveSessionPlanToRoutine(workout, routine.id)
        assertEquals(
            listOf("QUICK", "DURATION", "SETS"),
            repo.snapshot().routineItems.sortedBy {
                it.position
            }.map { it.mode }
        )
    }

    @Test fun removingOriginalExerciseKeepsAlternativeAndBackupValid() = runBlocking {
        val original = Exercise(name = "Pulldown")
        val alternative = Exercise(name = "Row")
        repo.saveExercise(original)
        repo.saveExercise(alternative)
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
        repo.correctWorkout(
            id,
            before.revision,
            before.title,
            before.journalDate,
            "Note",
            WorkoutTiming(
                start = "2026-06-01T10:00:00Z",
                end = "2026-06-01T11:00:00Z",
                acknowledged = true
            )
        )
        val corrected = repo.snapshot().workout(id)!!.row
        assertEquals("+03:00", corrected.startOffset)
        assertEquals("+03:00", corrected.endOffset)
    }

    @Test fun startsOnceAndSuggestionsNeverCountAsWork() = runBlocking {
        val exercise = Exercise(name = "Lat pulldown", equipmentName = "Machine A")
        repo.saveExercise(exercise)
        val routine = Routine(title = "Full body A")
        repo.saveRoutine(
            routine,
            listOf(
                RoutineItem(
                    routineId = routine.id,
                    exerciseId = exercise.id,
                    sets = listOf(SetTarget("27.5", 10), SetTarget("27.5", 9))
                )
            )
        )
        repo.archiveExercise(exercise.id)
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
        repo.saveExercise(original)
        repo.saveExercise(alternative)
        val id = repo.startWorkout(StartSource.Empty)
        val item = repo.addExercise(id, original.id)
        val set = repo.addSet(item)
        repo.saveSetDraft(set, "27,5", "10")
        repo.setSetCompleted(set, true)
        val other = repo.useAlternative(item, alternative.id, requireCurrent = true)
        val saved = repo.snapshot().workout(id)!!
        assertEquals("27.5", saved.items.first { it.row.id == item }.sets.single().load)
        assertEquals(original.id, saved.items.first { it.row.id == item }.row.exerciseId)
        assertFalse(saved.items.first { it.row.id == other }.hasPerformedWork)
        assertEquals(item, saved.items.first { it.row.id == other }.row.alternativeOf)
        val staleSwap = runCatching { repo.useAlternative(item, alternative.id, requireCurrent = true) }
        assertEquals(R.string.replacement_changed, (staleSwap.exceptionOrNull() as? UiException)?.text)
        repo.markItemDone(other, null)
        repo.finishWorkout(id, WorkoutTiming(clearTimes = true))
        val repeated = repo.startWorkout(StartSource.Repeat(id))
        val plan = repo.snapshot().workout(repeated)!!.items
        assertEquals(listOf(alternative.id), plan.map { it.row.exerciseId })
        assertFalse(plan.single().hasPerformedWork)
    }

    @Test fun finishingIsIdempotentAndRepeatingResetsPerformedState() = runBlocking {
        val exercise = Exercise(name = "Mobility", category = "Mobility", basis = "duration")
        repo.saveExercise(exercise)
        val id = repo.startWorkout(StartSource.Empty)
        val item = repo.addExercise(id, exercise.id)
        repo.markItemDone(item, 300)
        val timing =
            WorkoutTiming(end = "2026-11-13T09:00:00Z", durationSeconds = 1080, acknowledged = true)
        repo.finishWorkout(id, timing)
        repo.finishWorkout(id, timing)
        assertEquals(1, repo.snapshot().workouts.count { it.state == "COMPLETED" })
        val repeated = repo.startWorkout(StartSource.Repeat(id))
        assertNotEquals(id, repeated)
        assertFalse(repo.snapshot().workout(repeated)!!.hasPerformedWork)
        assertNull(repo.snapshot().workout(repeated)!!.items.single().row.durationSeconds)
    }

    @Test fun finishingPreservesNotesSavedByAnotherEditor() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        val timing = WorkoutTiming(end = "2026-11-13T09:00:00Z", acknowledged = true)
        repo.saveWorkoutNote(id, "Newer note")
        val result = runCatching { repo.finishWorkout(id, timing, note = "Draft note", expectedNote = "") }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        val active = requireNotNull(repo.snapshot().workout(id))
        assertEquals("ACTIVE", active.row.state)
        assertEquals("Newer note", active.row.note)

        repo.finishWorkout(id, timing, note = "Reviewed note", expectedNote = "Newer note")
        repo.finishWorkout(id, timing, note = "Stale note", expectedNote = "")
        assertEquals("Reviewed note", repo.snapshot().history.single().row.note)
    }

    @Test fun historicalSnapshotsSurviveLibraryChanges() = runBlocking {
        val exercise =
            Exercise(
                name = "Old name",
                equipmentName = "Machine A",
                settings = listOf(Setting("Seat", "4"))
            )
        repo.saveExercise(exercise)
        val id = repo.startWorkout(StartSource.Empty)
        repo.addExercise(id, exercise.id)
        repo.saveExercise(
            exercise.copy(
                name = "New name",
                archived = true,
                settings = listOf(Setting("Seat", "6"))
            )
        )
        val row = repo.snapshot().workout(id)!!.items.single().row
        assertEquals("Old name", row.name)
        assertEquals("Seat: 4", row.setup)
    }

    @Test fun rejectsInvalidCorrectionWithoutChangingCompletedValues() = runBlocking {
        val exercise = Exercise(name = "Lift")
        repo.saveExercise(exercise)
        val workoutId = repo.startWorkout(StartSource.Empty)
        val item = repo.addExercise(workoutId, exercise.id)
        val set = repo.addSet(item)
        repo.saveSetDraft(set, "0", "10")
        repo.setSetCompleted(set, true)
        try {
            repo.correctCompletedSet(set, "-1", "0")
            fail("Invalid correction accepted")
        } catch (
            _: IllegalArgumentException
        ) { }
        assertEquals("0", repo.snapshot().sets.single().load)
        assertEquals(10, repo.snapshot().sets.single().reps)
        repo.setSetCompleted(set, false)
        assertFalse(repo.snapshot().workout(workoutId)!!.hasPerformedWork)
    }

    @Test fun scoresKeepMissingZeroAndPhasesDistinct() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        repo.saveCheckIn(
            CheckIn(
                workoutId = id,
                phase = "BEFORE",
                observedAt = "2026-11-13T08:42:00Z",
                zoneId = "Europe/Helsinki"
            ),
            listOf(BodyObservation(region = "right_knee", pain = 0))
        )
        val snap = repo.snapshot()
        assertEquals(0, snap.observations.single().pain)
        assertNull(snap.observations.single().stiffness)
        assertEquals("BEFORE", snap.checkIns.single().phase)
    }

    @Test fun validatesLibraryEditsBeforeReplacingSavedExerciseAndClearingItsDraft() = runBlocking {
        val alternative = Exercise(name = "Cable row")
        val exercise = Exercise(name = "Dumbbell row")
        repo.saveExercise(alternative)
        repo.saveExercise(exercise)
        val draft = EditorDraft("exercise:${exercise.id}", "exercise", "{}", repo.clock.instant().toString())
        repo.saveEditorDraft(draft)
        val before = repo.snapshot()

        listOf(
            "blank name" to exercise.copy(name = " "),
            "itself as the alternative" to exercise.copy(alternatives = listOf(exercise.id)),
            "duplicate alternative" to exercise.copy(alternatives = listOf(alternative.id, alternative.id)),
            "removed alternative" to exercise.copy(alternatives = listOf(newId())),
            "malformed address" to exercise.copy(links = listOf(SavedLink("Guide", "https://example.com/my guide"))),
            "partial address" to exercise.copy(links = listOf(SavedLink("Guide", "https:/guide"))),
            "relative address" to exercise.copy(links = listOf(SavedLink("Guide", "guide.html")))
        ).forEach { (reason, invalid) ->
            assertTrue(reason, runCatching { repo.saveExercise(invalid) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(before, repo.snapshot())

        val edited = exercise.copy(
            alternatives = listOf(alternative.id),
            links = listOf(SavedLink("Guide", "https://example.com/guide"))
        )
        assertEquals(exercise.id, repo.saveExercise(edited))
        assertEquals(edited, repo.snapshot().exercises.single { it.id == exercise.id })
        assertTrue(repo.snapshot().drafts.isEmpty())

        val workout = repo.startWorkout(StartSource.Empty)
        repo.archiveExercise(exercise.id)
        val archived = runCatching { repo.addExercise(workout, exercise.id) }
        assertEquals(R.string.archived_exercise, (archived.exceptionOrNull() as? UiException)?.text)
        assertTrue(repo.snapshot().items.isEmpty())
    }

    @Test fun invalidRoutineEditsPreserveRevisionAndItems() = runBlocking {
        val exercise = Exercise(name = "Row")
        repo.saveExercise(exercise)
        val routine = Routine(title = "Strength", durationMinutes = 30)
        val item = RoutineItem(routineId = routine.id, exerciseId = exercise.id, durationSeconds = 60)
        repo.saveRoutine(routine, listOf(item))
        val before = repo.snapshot()

        val invalidEdits = listOf(
            routine.copy(title = " ") to item,
            routine.copy(durationMinutes = 0) to item,
            routine to item.copy(durationSeconds = 0),
            routine to item.copy(sets = listOf(SetTarget(reps = 0)))
        )
        invalidEdits.forEach { (invalidRoutine, invalidItem) ->
            val result = runCatching { repo.saveRoutine(invalidRoutine, listOf(invalidItem)) }
            assertTrue("Rejected $invalidRoutine with $invalidItem", result.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(before, repo.snapshot())

        val updated = item.copy(sets = listOf(SetTarget(), SetTarget(reps = 8), SetTarget(load = "20")))
        repo.saveRoutine(routine, listOf(updated))
        val saved = repo.snapshot()
        assertEquals(2L, saved.routines.single().revision)
        assertEquals(listOf(updated), saved.routineItems)
    }

    @Test fun savingAnEditorDraftKeepsTheFavoriteToggledWhileItWasOpen() = runBlocking {
        val exercise = Exercise(name = "Row")
        repo.saveExercise(exercise)
        val draft = EditorDraft("exercise:new", "exercise", "{}", repo.clock.instant().toString())
        repo.saveEditorDraft(draft)

        repo.setExerciseFavorite(exercise.id, true)
        assertEquals(listOf(draft), repo.snapshot().drafts)

        repo.saveExercise(exercise.copy(name = "Seated row"), draftId = draft.id, preserveFavorite = true)
        val saved = repo.snapshot()
        assertEquals(exercise.copy(name = "Seated row", favorite = true), saved.exercises.single())
        assertTrue(saved.drafts.isEmpty())
    }

    @Test fun repeatingWorkoutUsesCorrectedResultsAndKeepsUntouchedTargets() = runBlocking {
        val exercise = Exercise(name = "Row")
        repo.saveExercise(exercise)
        val routine = Routine(title = "Strength")
        val targets = listOf(SetTarget(reps = 8), SetTarget(load = "20"), SetTarget())
        repo.saveRoutine(routine, listOf(RoutineItem(routineId = routine.id, exerciseId = exercise.id, sets = targets)))

        val workout = repo.startWorkout(StartSource.Routine(routine.id))
        val original = requireNotNull(repo.snapshot().active).items.single()
        val set = original.sets.first().id
        val correction = runCatching { repo.correctCompletedSet(set, "25", "") }
        assertTrue(correction.exceptionOrNull() is IllegalArgumentException)
        repo.setSetCompleted(set, true)
        repo.correctCompletedSet(set, "25,5", "")
        val performed = requireNotNull(repo.snapshot().active).items.single().completedSets.single()
        assertEquals("25.5", performed.load)
        assertEquals("25,5", performed.rawLoad)
        assertNull(performed.reps)
        assertEquals(
            R.string.edit_completed_set,
            (runCatching {
                repo.saveSetDraft(set, "10", "10")
            }.exceptionOrNull() as? UiException)?.text
        )
        repo.markItemDone(original.row.id, null)
        assertNull(requireNotNull(repo.snapshot().active).items.single().row.quickCompletedAt)
        repo.finishWorkout(workout, WorkoutTiming(clearTimes = true))

        val repeated = repo.startWorkout(StartSource.Repeat(workout))
        val session = requireNotNull(repo.snapshot().workout(repeated))
        assertEquals(workout, session.row.sourceWorkoutId)
        assertFalse(session.hasPerformedWork)
        val sets = session.items.single().sets
        assertEquals(listOf("25.5", "20", null), sets.map { it.suggestedLoad })
        assertEquals(listOf("25.5", "20", ""), sets.map { it.rawLoad })
        assertTrue(sets.all { it.suggestedReps == null && it.rawReps.isEmpty() && it.load == null })
    }

    @Test fun skippingForNowMovesExercisesBackAndRecordsThemOnFinish() = runBlocking {
        val exercise = Exercise(name = "Row")
        repo.saveExercise(exercise)
        val id = repo.startWorkout(StartSource.Empty)
        val (first, second, third) = List(3) { repo.addExercise(id, exercise.id) }
        repo.moveItem(third, -1)
        assertEquals(listOf(first, third, second), requireNotNull(repo.snapshot().active).items.map { it.row.id })

        repo.skipItemForNow(first)
        val deferred = requireNotNull(repo.snapshot().active)
        assertEquals(listOf(third, second, first), deferred.items.map { it.row.id })
        assertEquals(third, deferred.row.currentItemId)
        repo.skipItemForNow(third)
        assertEquals(second, repo.snapshot().active?.row?.currentItemId)

        repo.markItemDone(second, 60)
        repo.saveItemNote(second, "Tight shoulder", discomfort = true)
        val done = requireNotNull(repo.snapshot().active).items.first { it.row.id == second }.row
        assertEquals(60L, done.durationSeconds)
        assertEquals("Tight shoulder", done.note)
        assertTrue(done.discomfort)
        assertEquals(R.string.confirm_delete_work, (runCatching { repo.removeItem(second) }.exceptionOrNull() as? UiException)?.text)
        repo.reopenItem(second)
        val reopened = requireNotNull(repo.snapshot().active)
        val row = reopened.items.first { it.row.id == second }.row
        assertEquals(second, reopened.row.currentItemId)
        assertEquals("IN_PROGRESS", row.disposition)
        assertNull(row.quickCompletedAt)
        assertNull(row.durationSeconds)
        assertEquals(done.note, row.note)

        repo.markItemDone(first, null)
        repo.skipItemForNow(second)
        assertEquals(third, repo.snapshot().active?.row?.currentItemId)
        repo.removeItem(first, confirmPerformed = true)
        assertEquals(listOf(third, second), requireNotNull(repo.snapshot().active).items.map { it.row.id })
        repo.saveWorkoutNote(id, "Training notes")
        repo.finishWorkout(id, WorkoutTiming(clearTimes = true))
        assertTrue(requireNotNull(repo.snapshot().workout(id)).items.all { it.row.disposition == "SKIPPED" })
    }

    @Test fun recordsInferredLiveTimingAndFiltersCompletedJournalDates() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        assertEquals(
            R.string.empty_workout_save,
            (runCatching {
                repo.finishWorkout(id, WorkoutTiming())
            }.exceptionOrNull() as? UiException)?.text
        )
        repo.saveWorkoutNote(id, "Training notes")
        val before = repo.snapshot()
        listOf(WorkoutTiming(end = "2026-11-13T08:00:00Z"), WorkoutTiming(clearTimes = true, durationSeconds = 0)).forEach {
            assertTrue(runCatching { repo.finishWorkout(id, it) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(before, repo.snapshot())

        val later = JournalRepository(db, Clock.fixed(repo.clock.instant().plusSeconds(1_800), repo.clock.zone), testContext())
        later.finishWorkout(id, WorkoutTiming(journalDate = "2026-11-12"), confirmCurrentTiming = true)
        val saved = requireNotNull(later.snapshot().workout(id)).row
        assertEquals("2026-11-12", saved.journalDate)
        assertEquals(later.clock.instant().toString(), saved.end)
        assertEquals(1_800L, saved.durationSeconds)
        assertEquals("INTERVAL", saved.durationSource)
        assertTrue(saved.timingConfirmed)
        later.correctWorkout(
            id,
            saved.revision,
            saved.title,
            saved.journalDate,
            "Corrected note",
            WorkoutTiming(saved.start, saved.end, saved.durationSeconds, acknowledged = true)
        )
        assertEquals("INTERVAL", later.snapshot().history.single().row.durationSource)

        val past = LocalDate.parse(saved.journalDate)
        val today = LocalDate.now(later.clock)
        assertEquals(listOf(id), later.observeWorkouts(DateRange(past, past)).first().map { it.row.id })
        assertTrue(later.observeWorkouts(DateRange(today, today)).first().isEmpty())
    }

    @Test fun resumingPreviousDayRequiresTimingReviewAndCanClearOldTimes() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        repo.saveWorkoutNote(id, "Recorded yesterday")
        val nextDay = JournalRepository(db, Clock.fixed(repo.clock.instant().plusSeconds(86_400), repo.clock.zone), testContext())
        assertEquals(id, nextDay.startWorkout(StartSource.Empty))
        assertTrue(db.journal().workouts().single().timingNeedsReview)
        assertEquals(
            R.string.review_resumed_timing,
            (runCatching {
                nextDay.finishWorkout(id, WorkoutTiming(end = nextDay.clock.instant().toString()), confirmCurrentTiming = true)
            }.exceptionOrNull() as? UiException)?.text
        )
        nextDay.finishWorkout(id, WorkoutTiming(clearTimes = true, acknowledged = true))
        val saved = nextDay.snapshot().history.single().row
        assertEquals("2026-11-13", saved.journalDate)
        assertNull(saved.start)
        assertNull(saved.end)
        assertNull(saved.durationSeconds)
        assertEquals("UNKNOWN", saved.durationSource)
        assertFalse(saved.timingConfirmed)
        assertFalse(saved.timingNeedsReview)
    }

    @Test fun checkInReplacementValidatesScoresAndRetainsTheExistingPhaseIdentity() = runBlocking {
        val id = repo.startWorkout(StartSource.Empty)
        val check = CheckIn(workoutId = id, phase = "AFTER", observedAt = repo.clock.instant().toString(), zoneId = "UTC", effort = 0)
        val observation = BodyObservation(region = "right_knee", pain = 4)
        repo.saveCheckIn(check, listOf(observation))
        val before = repo.snapshot()

        listOf(
            check.copy(phase = "BEFORE") to listOf(observation),
            check.copy(effort = -1) to listOf(observation),
            check.copy(effort = 11) to listOf(observation),
            check to listOf(observation.copy(pain = -1)),
            check to listOf(observation.copy(pain = 11)),
            check to listOf(observation.copy(stiffness = -1)),
            check to listOf(observation.copy(stiffness = 11)),
            check to listOf(observation, observation.copy(pain = 0))
        ).forEach { (invalidCheck, invalidObservations) ->
            assertTrue(runCatching { repo.saveCheckIn(invalidCheck, invalidObservations) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(before, repo.snapshot())

        val replacement = BodyObservation(region = "left_shoulder", stiffness = 10)
        repo.saveCheckIn(check.copy(id = newId(), effort = 10, energy = "GOOD"), listOf(replacement))
        val saved = repo.snapshot()
        assertEquals(check.copy(effort = 10, energy = "GOOD"), saved.checkIns.single())
        assertEquals(listOf(replacement.copy(checkInId = check.id)), saved.observations)
    }

    @Test fun staleCorrectionCannotReplaceManuallyRecordedWorkoutTiming() = runBlocking {
        val id = repo.createPastWorkout("2026-11-01", "Training")
        val before = requireNotNull(repo.snapshot().workout(id)).row
        val timing = WorkoutTiming(start = "2026-11-01T08:00:00Z", durationSeconds = 1_800, acknowledged = true)
        repo.correctWorkout(id, before.revision, "Training", before.journalDate, "Added duration", timing)
        val saved = requireNotNull(repo.snapshot().workout(id)).row
        assertEquals(1_800L, saved.durationSeconds)
        assertEquals("MANUAL", saved.durationSource)
        assertFalse(saved.timingConfirmed)
        assertNull(saved.endOffset)
        assertEquals(
            R.string.timing_changed,
            (runCatching {
                repo.correctWorkout(id, before.revision, "Stale title", before.journalDate, "", WorkoutTiming())
            }.exceptionOrNull() as? UiException)?.text
        )
        assertEquals(saved, repo.snapshot().workout(id)?.row)
    }
}

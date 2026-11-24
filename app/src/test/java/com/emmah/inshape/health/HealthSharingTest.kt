package com.emmah.inshape.health

import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.data.createTestDatabase
import com.emmah.inshape.testContext
import com.emmah.inshape.ui
import com.emmah.inshape.ui.UiException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthSharingTest {
    lateinit var db: JournalDatabase
    lateinit var repo: JournalRepository
    lateinit var service: HealthSharing
    private val gateway = FakeGateway()

    @Before fun setup() {
        db = createTestDatabase()
        repo =
            JournalRepository(
                db,
                Clock.fixed(Instant.parse("2026-11-13T08:00:00Z"), ZoneId.of("UTC")),
                testContext()
            )
        service = HealthSharing(repo, gateway, testContext())
    }

    @After fun close() = db.close()
    private suspend fun completed(shareWorkouts: Boolean = true): String {
        repo.updatePreferences(Preferences(shareWorkouts = shareWorkouts))
        val exercise = Exercise(name = "Mobility", category = "Mobility")
        repo.saveExercise(exercise)
        val id = repo.startWorkout(StartSource.Empty)
        repo.markItemDone(repo.addExercise(id, exercise.id), 60)
        repo.finishWorkout(id, WorkoutTiming(end = "2026-11-13T08:10:00Z", acknowledged = true))
        return id
    }

    @Test fun interruptedAcknowledgementRetriesSameIdentityAndEditsIncreaseVersion() = runBlocking {
        val id = completed()
        gateway.failAfterWrite = true
        assertFalse(service.sync())
        val first = gateway.rows.values.single()
        assertTrue(repo.snapshot().outbox.isNotEmpty())
        assertTrue(service.sync())
        assertEquals(1, gateway.rows.size)
        assertEquals(first.version, gateway.rows.values.single().version)
        repo.saveWorkoutNote(id, "Private note must not leave the journal")
        assertTrue(service.sync())
        assertTrue(gateway.rows.values.single().version > first.version)
        assertEquals("inshape:workout:$id", gateway.rows.keys.single())
    }

    @Test fun revocationRetainsDeletionAndDisablingStopsPendingUpserts() = runBlocking {
        val id = completed()
        service.sync()
        gateway.allowed = false
        repo.updatePreferences(Preferences(shareWorkouts = false))
        repo.deleteWorkout(id)
        assertFalse(service.sync())
        assertEquals("DELETE", repo.snapshot().outbox.single().operation)
        assertEquals(1, gateway.rows.size)
        gateway.allowed = true
        assertTrue(service.sync())
        assertTrue(gateway.rows.isEmpty())
        completed()
        repo.updatePreferences(Preferences())
        service.sync()
        assertTrue(gateway.rows.isEmpty())
    }

    @Test fun restoredRecordsStayHeldUntilSelectedReconciliation() = runBlocking {
        val id = completed()
        service.sync()
        val state = repo.snapshot().healthStates.single()
        db.journal().put(state.copy(restoredHold = true, platformId = null, clientVersion = 1))
        gateway.rows[state.clientRecordId] =
            gateway.rows.getValue(state.clientRecordId).copy(version = 80)
        repo.saveWorkoutNote(id, "Changed after restore")
        service.sync()
        assertEquals(80L, gateway.rows.getValue(state.clientRecordId).version)
        service.reconcile(id)
        service.sync()
        assertEquals(81L, gateway.rows.getValue(state.clientRecordId).version)
        assertFalse(repo.snapshot().healthStates.single().restoredHold)
    }

    @Test fun anEditDuringExternalWriteRemainsQueuedAndRequestsAnotherWorkerAttempt() = runBlocking {
        val id = completed()
        gateway.afterWrite = { repo.saveWorkoutNote(id, "Concurrent correction") }
        assertFalse(service.sync())
        assertEquals(1, repo.snapshot().outbox.size)
        assertTrue(service.sync())
        assertTrue(repo.snapshot().outbox.isEmpty())
        assertEquals(1, gateway.rows.size)
    }

    @Test fun clearingTimingDropsOnlyTheAffectedUpsertAndItsError() = runBlocking {
        val affected = completed()
        val removed = completed()
        val retained = completed()
        service.removeShared(removed)
        gateway.allowed = false
        assertFalse(service.sync())
        val row = repo.snapshot().workout(affected)!!.row

        repo.correctWorkout(affected, row.revision, row.title, row.journalDate, row.note, WorkoutTiming())

        val snapshot = repo.snapshot()
        assertEquals(mapOf(removed to "DELETE", retained to "UPSERT"), snapshot.outbox.associate { it.workoutId to it.operation })
        val state = snapshot.healthStates.single { it.workoutId == affected }
        assertEquals("SHARE_UNCONFIRMED", state.status)
        assertNull(state.error)
        repo.deleteWorkout(affected)
        assertEquals("DELETE", repo.snapshot().outbox.single { it.workoutId == affected }.operation)
    }

    @Test fun syncDiscardsAnIneligibleUpdateLeftByAnOlderAppVersion() = runBlocking {
        val id = completed()
        assertTrue(service.sync())
        repo.saveWorkoutNote(id, "Updated")
        val row = repo.snapshot().workout(id)!!.row
        db.journal().put(row.copy(timingConfirmed = false))
        gateway.allowed = false

        assertTrue(service.sync())

        val snapshot = repo.snapshot()
        assertTrue(snapshot.outbox.isEmpty())
        assertEquals("SHARED", snapshot.healthStates.single().status)
        assertNull(snapshot.healthStates.single().error)
        assertEquals(1, gateway.rows.size)
    }

    @Test fun aFailedOlderWriteDoesNotAttachItsErrorToTheNewerQueuedRevision() = runBlocking {
        val id = completed()
        val original = repo.snapshot().outbox.single()
        gateway.afterWrite = { repo.saveWorkoutNote(id, "Corrected during upload") }
        gateway.failAfterWrite = true

        assertFalse(service.sync())

        val snapshot = repo.snapshot()
        val queued = snapshot.outbox.single()
        assertTrue(queued.revision > original.revision)
        assertEquals(0, queued.attempts)
        assertNull(queued.error)
        assertEquals("PENDING", snapshot.healthStates.single().status)
        assertNull(snapshot.healthStates.single().error)
        assertTrue(service.sync())
        assertEquals(queued.revision, gateway.rows.getValue(queued.clientRecordId).version)
    }

    @Test fun cancellingAWriteLeavesItsQueuedOperationAndShareStateUntouched() = runBlocking {
        completed()
        val original = repo.snapshot()
        val entered = CompletableDeferred<Unit>()
        gateway.beforeCanWrite = {
            entered.complete(Unit)
            awaitCancellation()
        }
        val sync = launch { service.sync() }
        entered.await()
        sync.cancelAndJoin()

        val snapshot = repo.snapshot()
        assertEquals(original.outbox, snapshot.outbox)
        assertEquals(original.healthStates, snapshot.healthStates)
        assertTrue(gateway.rows.isEmpty())
    }

    @Test fun providerFailureWithoutAMessageLeavesAUsefulErrorAndOtherWorkoutsSync() = runBlocking {
        val first = completed()
        val second = completed()
        gateway.beforeCanWrite = {
            gateway.beforeCanWrite = null
            throw IOException()
        }

        assertFalse(service.sync())

        val snapshot = repo.snapshot()
        val failed = snapshot.outbox.single()
        val failureMessage = ui(R.string.health_retry_failed)
        val successfulId = (setOf(first, second) - failed.workoutId).single()
        val failedState = snapshot.healthStates.single { it.workoutId == failed.workoutId }
        assertEquals(1, failed.attempts)
        assertEquals(failureMessage, failed.error)
        assertEquals("PENDING", failedState.status)
        assertEquals(failureMessage, failedState.error)
        assertEquals("SHARED", snapshot.healthStates.single { it.workoutId == successfulId }.status)
        assertEquals(setOf("inshape:workout:$successfulId"), gateway.rows.keys)
    }

    @Test fun explicitRemovalKeepsTheLocalWorkoutAndHoldsFutureEdits() = runBlocking {
        val id = completed()
        assertTrue(service.sync())
        val shared = repo.snapshot().healthStates.single()
        gateway.allowed = false

        service.removeShared(id)
        val pending = repo.snapshot()
        val removal = pending.outbox.single()
        assertEquals("DELETE", removal.operation)
        assertEquals(shared.clientRecordId, removal.clientRecordId)
        assertEquals(shared.clientVersion + 1, removal.revision)
        assertTrue(pending.healthStates.single().restoredHold)
        assertFalse(service.sync())
        assertEquals(setOf(shared.clientRecordId), gateway.rows.keys)

        gateway.allowed = true
        assertTrue(service.sync())
        val removed = repo.snapshot().healthStates.single()
        assertEquals("REMOVED", removed.status)
        assertEquals(0L, removed.acknowledgedRevision)
        assertNull(removed.platformId)
        assertNull(removed.error)
        assertTrue(gateway.rows.isEmpty())
        repo.saveWorkoutNote(id, "Kept in the local journal")
        val edited = repo.snapshot()
        assertEquals("Kept in the local journal", edited.workout(id)?.row?.note)
        assertTrue(edited.outbox.isEmpty())
    }

    @Test fun reconciliationRequiresOptInAndQueuesASelectedLocalWorkout() = runBlocking {
        val id = completed(shareWorkouts = false)
        val original = repo.snapshot()
        val disabled = runCatching { service.reconcile(id) }.exceptionOrNull()
        assertTrue(disabled is IllegalArgumentException)
        assertEquals(R.string.health_turn_on_sharing, (disabled as? UiException)?.text)
        assertTrue(repo.snapshot().outbox.isEmpty())

        repo.updatePreferences(Preferences(shareWorkouts = true))
        service.reconcile(id)

        val queued = repo.snapshot()
        val state = queued.healthStates.single()
        assertEquals("PENDING", state.status)
        assertFalse(state.restoredHold)
        assertEquals("inshape:workout:$id", state.clientRecordId)
        assertEquals(original.workout(id)!!.row.revision + 1, state.clientVersion)
        assertEquals(state.clientVersion, queued.outbox.single().revision)
    }

    @Test fun reconciliationRejectsAWorkoutEditedWhileReadingItsExternalVersion() = runBlocking {
        val id = completed()
        assertTrue(service.sync())
        val shared = repo.snapshot().healthStates.single()
        val restored = shared.copy(restoredHold = true, platformId = null)
        db.journal().put(restored)
        gateway.beforeReadVersion = { repo.saveWorkoutNote(id, "Changed during reconciliation") }

        val failure = runCatching { service.reconcile(id) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(R.string.health_workout_changed, (failure as? UiException)?.text)
        val snapshot = repo.snapshot()
        assertEquals(restored, snapshot.healthStates.single())
        assertTrue(snapshot.outbox.isEmpty())
        assertEquals("Changed during reconciliation", snapshot.workout(id)?.row?.note)
    }

    private class FakeGateway : HealthGateway {
        var allowed = true
        var failAfterWrite = false
        var afterWrite: (suspend () -> Unit)? = null
        var beforeCanWrite: (suspend () -> Unit)? = null
        var beforeReadVersion: (suspend () -> Unit)? = null
        val rows = mutableMapOf<String, SharedWorkout>()
        override suspend fun canWrite(): Boolean {
            beforeCanWrite?.invoke()
            return allowed
        }
        override suspend fun upsert(record: SharedWorkout): String {
            if ((rows[record.clientId]?.version ?: -1) <
                record.version
            ) {
                rows[record.clientId] = record
            }
            val callback = afterWrite
            afterWrite = null
            callback?.invoke()
            if (failAfterWrite) {
                failAfterWrite = false
                throw IOException("Interrupted after external write")
            }
            return record.clientId
        }
        override suspend fun delete(clientId: String) {
            rows.remove(clientId)
        }
        override suspend fun existingVersion(workout: WorkoutEntity, clientId: String): Long {
            beforeReadVersion?.invoke()
            return rows[clientId]?.version ?: 0
        }
    }

    @Test fun disablingSharingWaitsForInFlightWriteAndPreventsLaterWrites() = runBlocking {
        val id = completed()
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        gateway.beforeCanWrite = {
            entered.complete(Unit)
            proceed.await()
        }
        val sync = launch { service.sync() }
        entered.await()
        val disable = launch(start = CoroutineStart.UNDISPATCHED) { repo.updatePreferences(Preferences(shareWorkouts = false)) }
        try {
            assertFalse(disable.isCompleted)
        } finally {
            proceed.complete(Unit)
        }
        sync.join()
        disable.join()
        assertFalse(repo.snapshot().preferences.shareWorkouts)
        val version = gateway.rows.values.single().version
        repo.saveWorkoutNote(id, "After disabling")
        service.sync()
        assertEquals(version, gateway.rows.values.single().version)
        assertTrue(repo.snapshot().outbox.isEmpty())
    }
}

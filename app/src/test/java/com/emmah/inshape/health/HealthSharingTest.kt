package com.emmah.inshape.health

import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutTiming
import com.emmah.inshape.data.createTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                Clock.fixed(Instant.parse("2026-11-13T08:00:00Z"), ZoneId.of("UTC"))
            )
        service = HealthSharing(repo, gateway)
    }

    @After fun close() = db.close()
    private suspend fun completed(): String {
        repo.updatePreferences(Preferences(shareWorkouts = true))
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
    private class FakeGateway : HealthGateway {
        var allowed = true
        var failAfterWrite = false
        var afterWrite: (suspend () -> Unit)? = null
        var beforeCanWrite: (suspend () -> Unit)? = null
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
                throw java.io.IOException("Interrupted after external write")
            }
            return record.clientId
        }
        override suspend fun delete(clientId: String) {
            rows.remove(clientId)
        }
        override suspend fun existingVersion(workout: WorkoutEntity, clientId: String) = rows[clientId]?.version ?: 0
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

package com.emmah.inshape.health

import androidx.room.withTransaction
import com.emmah.inshape.data.HealthOutbox
import com.emmah.inshape.data.HealthShareState
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

data class SharedWorkout(
    val clientId: String,
    val version: Long,
    val start: String,
    val end: String,
    val zone: String,
    val mobility: Boolean
)
interface HealthGateway {
    suspend fun canWrite(): Boolean
    suspend fun upsert(record: SharedWorkout): String
    suspend fun delete(clientId: String)
    suspend fun existingVersion(workout: WorkoutEntity, clientId: String): Long
}

class HealthSharing(private val repository: JournalRepository, private val gateway: HealthGateway) {
    val lock = repository.healthSharingLock
    private val dao = repository.db.journal()
    suspend fun sync(): Boolean = lock.withLock {
        var complete = true
        for (requested in repository.snapshot().outbox) {
            val succeeded = syncRequested(requested)
            complete = complete && succeeded
        }
        complete && repository.snapshot().outbox.isEmpty()
    }

    // Provider failures remain queued; cancellation must still stop the worker.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun syncRequested(requested: HealthOutbox): Boolean {
        val snapshot = repository.snapshot()
        val pending = snapshot.outbox.find { it.workoutId == requested.workoutId } ?: return true
        val outdated = pending.revision != requested.revision || pending.operation != requested.operation
        val workout = snapshot.workout(pending.workoutId)
        val state = snapshot.healthStates.find { it.workoutId == pending.workoutId }
        val held = pending.operation == "UPSERT" && (!snapshot.preferences.shareWorkouts || state?.restoredHold == true)
        return if (outdated || held) {
            true
        } else {
            try {
                val platformId = writeToGateway(pending, workout)
                acknowledge(pending, workout, platformId)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                repository.db.withTransaction {
                    val latest = dao.outbox().find { it.workoutId == pending.workoutId }
                    if (latest?.revision == pending.revision &&
                        latest.operation == pending.operation
                    ) {
                        val message =
                            failure.message
                                ?: "Health Connect could not finish this operation. Retry when access is available."
                        dao.put(latest.copy(attempts = latest.attempts + 1, error = message))
                        dao.healthStates().find {
                            it.workoutId == pending.workoutId
                        }?.let { dao.put(it.copy(status = "PENDING", error = message)) }
                    }
                }
                false
            }
        }
    }

    private suspend fun writeToGateway(pending: HealthOutbox, workout: Workout?): String? {
        check(gateway.canWrite()) {
            "Health Connect access is unavailable. " +
                if (pending.operation == "DELETE") "The shared copy has not yet been removed." else "Sharing is pending."
        }
        return if (pending.operation ==
            "DELETE"
        ) {
            gateway.delete(pending.clientRecordId)
            null
        } else {
            require(eligible(workout)) {
                "Review actual start and end times before sharing. Any earlier shared summary is unchanged."
            }
            val row = workout!!.row
            gateway.upsert(
                SharedWorkout(
                    pending.clientRecordId,
                    pending.revision,
                    row.start!!,
                    row.end!!,
                    row.zoneId,
                    workout.items.filter { it.hasPerformedWork }.all {
                        it.row.category in
                            setOf("Mobility", "Stretching")
                    }
                )
            )
        }
    }

    private suspend fun acknowledge(pending: HealthOutbox, workout: Workout?, platformId: String?) {
        repository.db.withTransaction {
            val latestPending = dao.outbox().find { it.workoutId == pending.workoutId }
            val latestState =
                dao.healthStates().find { it.workoutId == pending.workoutId }
                    ?: HealthShareState(pending.workoutId)
            val same =
                latestPending?.revision == pending.revision &&
                    latestPending.operation == pending.operation
            dao.put(
                latestState.copy(
                    clientVersion = maxOf(latestState.clientVersion, pending.revision),
                    acknowledgedRevision = if (pending.operation ==
                        "DELETE"
                    ) {
                        0
                    } else {
                        maxOf(latestState.acknowledgedRevision, workout!!.row.revision)
                    },
                    platformId = platformId,
                    status = if (!same) {
                        latestState.status
                    } else if (pending.operation ==
                        "DELETE"
                    ) {
                        "REMOVED"
                    } else {
                        "SHARED"
                    },
                    error = if (same) null else latestState.error
                )
            )
            if (same) dao.deleteOutbox(pending.workoutId)
        }
    }

    suspend fun reconcile(workoutId: String) = lock.withLock {
        val snapshot = repository.snapshot()
        val workout = snapshot.workout(workoutId)
        require(snapshot.preferences.shareWorkouts) { "Enable workout sharing first." }
        require(eligible(workout)) { "Confirm valid start and end times first." }
        val state =
            snapshot.healthStates.find { it.workoutId == workoutId }
                ?: HealthShareState(workoutId, restoredHold = true)
        val existing = gateway.existingVersion(workout!!.row, state.clientRecordId)
        repository.db.withTransaction {
            val current = repository.snapshot()
            require(
                current.preferences.shareWorkouts &&
                    current.workout(workoutId)?.row?.revision == workout.row.revision
            ) {
                "This workout changed. Review it and try again."
            }
            val version = maxOf(existing, state.clientVersion, workout.row.revision) + 1
            dao.put(
                state.copy(
                    clientVersion = version,
                    restoredHold = false,
                    status = "PENDING",
                    error = null
                )
            )
            dao.put(HealthOutbox(workoutId, state.clientRecordId, version, "UPSERT"))
        }
    }
    suspend fun removeShared(workoutId: String) = lock.withLock {
        repository.db.withTransaction {
            val state = dao.healthStates().first { it.workoutId == workoutId }
            dao.put(
                state.copy(
                    clientVersion = state.clientVersion + 1,
                    restoredHold = true,
                    status = "PENDING",
                    error = "Removal is pending until Health Connect access is available."
                )
            )
            dao.put(
                HealthOutbox(workoutId, state.clientRecordId, state.clientVersion + 1, "DELETE")
            )
        }
    }
    companion object {
        fun eligible(workout: Workout?): Boolean = workout != null && workout.qualifiesForAttendance && workout.row.timingConfirmed &&
            workout.row.start != null && workout.row.end != null &&
            Instant.parse(workout.row.end).isAfter(Instant.parse(workout.row.start))
    }
}

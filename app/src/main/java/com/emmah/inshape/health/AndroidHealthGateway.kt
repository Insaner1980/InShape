package com.emmah.inshape.health

import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.R
import kotlinx.coroutines.CancellationException
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.WorkoutEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class HealthCapability(val label: Int, val permission: String) {
    SHARE(
        R.string.health_share,
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    ),
    STEPS(R.string.steps_today, HealthPermission.getReadPermission(StepsRecord::class)),
    SLEEP(R.string.sleep_last_night, HealthPermission.getReadPermission(SleepSessionRecord::class)),
    HEART(R.string.health_latest_heart, HealthPermission.getReadPermission(HeartRateRecord::class));

    fun enabled(p: Preferences) = when (this) {
        SHARE -> p.shareWorkouts
        STEPS -> p.readSteps
        SLEEP -> p.readSleep
        HEART -> p.readHeartRate
    }
    fun change(p: Preferences, enabled: Boolean) = when (this) {
        SHARE -> p.copy(shareWorkouts = enabled)
        STEPS -> p.copy(readSteps = enabled)
        SLEEP -> p.copy(readSleep = enabled)
        HEART -> p.copy(readHeartRate = enabled)
    }
}

data class HeartSample(val beats: Long, val time: Instant, val source: String)
data class HealthContext(
    val steps: Long? = null,
    val sleepSeconds: Long? = null,
    val heart: HeartSample? = null,
    val heartSources: List<String> = emptyList()
)
data class HealthWindows(val dayStart: Instant, val sleepStart: Instant, val sleepEnd: Instant, val heartStart: Instant)
fun healthWindows(now: ZonedDateTime) = HealthWindows(
    now.toLocalDate().atStartOfDay(now.zone).toInstant(),
    now.toLocalDate().minusDays(1).atTime(18, 0).atZone(now.zone).toInstant(),
    minOf(now.toInstant(), now.toLocalDate().atTime(12, 0).atZone(now.zone).toInstant()),
    now.toInstant().minus(Duration.ofHours(24))
)
fun selectHeart(samples: List<HeartSample>, selected: String?): HeartSample? {
    val origins = samples.map { it.source }.distinct()
    val origin = selected ?: origins.singleOrNull() ?: return null
    return samples.filter { it.source == origin }.maxByOrNull { it.time }
}

class AndroidHealthGateway(private val context: Context) : HealthGateway {
    fun availability() = HealthConnectClient.getSdkStatus(context)
    private fun client(): HealthConnectClient {
        check(availability() == HealthConnectClient.SDK_AVAILABLE) {
            "Health Connect is unavailable or needs an update on this device."
        }
        return HealthConnectClient.getOrCreate(context)
    }
    suspend fun grants(): Set<String> = if (availability() ==
        HealthConnectClient.SDK_AVAILABLE
    ) {
        client().permissionController.getGrantedPermissions()
    } else {
        emptySet()
    }
    override suspend fun canWrite() = HealthCapability.SHARE.permission in grants()
    override suspend fun upsert(record: SharedWorkout): String {
        val start = Instant.parse(record.start)
        val end = Instant.parse(record.end)
        val zone = ZoneId.of(record.zone)
        return client().insertRecords(
            listOf(
                ExerciseSessionRecord(
                    startTime = start,
                    endTime = end,
                    startZoneOffset = start.atZone(zone).offset,
                    endZoneOffset = end.atZone(zone).offset,
                    exerciseType = if (record.mobility) {
                        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
                    } else {
                        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                    },
                    title = context.getString(if (record.mobility) R.string.category_mobility else R.string.health_strength_training),
                    metadata = Metadata.manualEntry(
                        clientRecordId = record.clientId,
                        clientRecordVersion = record.version
                    )
                )
            )
        ).recordIdsList.single()
    }
    override suspend fun delete(clientId: String) {
        client().deleteRecords(
            ExerciseSessionRecord::class,
            recordIdsList = emptyList(),
            clientRecordIdsList = listOf(clientId)
        )
    }
    override suspend fun existingVersion(workout: WorkoutEntity, clientId: String): Long {
        requireUi(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in grants(), R.string.health_read_access)
        requireUi(android.os.Build.VERSION.SDK_INT >= 34, R.string.health_historical_reads)
        // Timing may have been corrected since the backup. Search only our own origin and match the stable client identity.
        var token: String? = null
        var version = 0L
        do {
            val page = client().readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    TimeRangeFilter.after(Instant.EPOCH),
                    dataOriginFilter = setOf(DataOrigin(context.packageName)),
                    pageSize = 100,
                    pageToken = token
                )
            )
            page.records.filter { it.metadata.clientRecordId == clientId }.forEach {
                version =
                    maxOf(version, it.metadata.clientRecordVersion)
            }
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return version
    }
    suspend fun readContext(preferences: Preferences, now: ZonedDateTime): HealthContext {
        if (availability() != HealthConnectClient.SDK_AVAILABLE) return HealthContext()
        val api = client()
        val granted = grants()
        val windows = healthWindows(now)
        val steps = value(preferences, granted, HealthCapability.STEPS) {
            if (windows.dayStart == now.toInstant()) null else api.aggregate(
                AggregateRequest(
                    setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(windows.dayStart, now.toInstant())
                )
            )[StepsRecord.COUNT_TOTAL]
        }
        val sleep = value(preferences, granted, HealthCapability.SLEEP) {
            api.aggregate(
                AggregateRequest(
                    setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    TimeRangeFilter.between(windows.sleepStart, windows.sleepEnd)
                )
            )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.seconds
        }
        val samples = value(preferences, granted, HealthCapability.HEART) {
            latestHeartSamples(api, windows.heartStart, now.toInstant())
        }.orEmpty()
        return HealthContext(steps, sleep, selectHeart(samples.values.toList(), preferences.heartRateSource), samples.keys.sorted())
    }

    private suspend fun latestHeartSamples(api: HealthConnectClient, start: Instant, end: Instant): Map<String, HeartSample> {
        var token: String? = null
        val latest = mutableMapOf<String, HeartSample>()
        do {
            val page = api.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    TimeRangeFilter.between(start, end),
                    pageSize = 100,
                    pageToken = token
                )
            )
            updateHeartSamples(page.records, latest, start, end)
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return latest
    }

    private suspend fun <T> value(
        preferences: Preferences,
        granted: Set<String>,
        capability: HealthCapability,
        read: suspend () -> T?
    ): T? {
        if (!capability.enabled(preferences) || capability.permission !in granted) return null
        return try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun updateHeartSamples(records: List<HeartRateRecord>, latest: MutableMap<String, HeartSample>, start: Instant, end: Instant) {
        records.forEach { record ->
            record.samples.filter {
                it.time in
                    start..end
            }.forEach { sample ->
                val origin = record.metadata.dataOrigin.packageName
                if (latest[origin]?.time?.isBefore(sample.time) !=
                    false
                ) {
                    latest[origin] =
                        HeartSample(sample.beatsPerMinute, sample.time, origin)
                }
            }
        }
    }
}

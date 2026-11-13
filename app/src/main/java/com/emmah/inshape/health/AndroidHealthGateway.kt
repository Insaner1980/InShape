package com.emmah.inshape.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import com.emmah.inshape.data.*
import java.time.*

enum class HealthCapability(val label: String, val permission: String) {
    SHARE("Share completed workouts", HealthPermission.getWritePermission(ExerciseSessionRecord::class)),
    STEPS("Steps today", HealthPermission.getReadPermission(StepsRecord::class)),
    SLEEP("Sleep last night", HealthPermission.getReadPermission(SleepSessionRecord::class)),
    HEART("Latest recorded heart rate", HealthPermission.getReadPermission(HeartRateRecord::class));
    fun enabled(p: Preferences) = when (this) { SHARE -> p.shareWorkouts; STEPS -> p.readSteps; SLEEP -> p.readSleep; HEART -> p.readHeartRate }
    fun change(p: Preferences, enabled: Boolean) = when (this) { SHARE -> p.copy(shareWorkouts = enabled); STEPS -> p.copy(readSteps = enabled); SLEEP -> p.copy(readSleep = enabled); HEART -> p.copy(readHeartRate = enabled) }
}

data class HealthValue(val text: String, val period: String, val sources: List<String> = emptyList(), val error: String? = null)
data class HeartSample(val beats: Long, val time: Instant, val source: String)
data class HealthContext(val steps: HealthValue? = null, val sleep: HealthValue? = null, val heart: HealthValue? = null, val heartSources: List<String> = emptyList())
data class HealthWindows(val dayStart: Instant, val sleepStart: Instant, val sleepEnd: Instant, val heartStart: Instant)
fun healthWindows(now: ZonedDateTime) = HealthWindows(now.toLocalDate().atStartOfDay(now.zone).toInstant(),
    now.toLocalDate().minusDays(1).atTime(18, 0).atZone(now.zone).toInstant(),
    minOf(now.toInstant(), now.toLocalDate().atTime(12, 0).atZone(now.zone).toInstant()), now.toInstant().minus(Duration.ofHours(24)))
fun selectHeart(samples: List<HeartSample>, selected: String?): HeartSample? {
    val origins = samples.map { it.source }.distinct()
    val origin = selected ?: origins.singleOrNull() ?: return null
    return samples.filter { it.source == origin }.maxByOrNull { it.time }
}

class AndroidHealthGateway(private val context: Context) : HealthGateway {
    fun availability() = HealthConnectClient.getSdkStatus(context)
    private fun client(): HealthConnectClient {
        check(availability() == HealthConnectClient.SDK_AVAILABLE) { "Health Connect is unavailable or needs an update on this device." }
        return HealthConnectClient.getOrCreate(context)
    }
    suspend fun grants(): Set<String> = if (availability() == HealthConnectClient.SDK_AVAILABLE) client().permissionController.getGrantedPermissions() else emptySet()
    override suspend fun canWrite() = HealthCapability.SHARE.permission in grants()
    override suspend fun upsert(record: SharedWorkout): String {
        val start = Instant.parse(record.start); val end = Instant.parse(record.end); val zone = ZoneId.of(record.zone)
        return client().insertRecords(listOf(ExerciseSessionRecord(startTime = start, endTime = end,
            startZoneOffset = start.atZone(zone).offset, endZoneOffset = end.atZone(zone).offset,
            exerciseType = if (record.mobility) ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING else ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = if (record.mobility) "Mobility" else "Strength training",
            metadata = Metadata.manualEntry(clientRecordId = record.clientId, clientRecordVersion = record.version)))).recordIdsList.single()
    }
    override suspend fun delete(clientId: String) { client().deleteRecords(ExerciseSessionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientId)) }
    override suspend fun existingVersion(workout: WorkoutEntity, clientId: String): Long {
        require(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in grants()) { "Allow exercise-read access to reconcile this selected workout. It remains on hold." }
        check(android.os.Build.VERSION.SDK_INT >= 34) { "This provider limits historical reads. Reconcile restored workouts on Android 14 or later; this record remains on hold." }
        // Timing may have been corrected since the backup. Search only our own origin and match the stable client identity.
        var token: String? = null; var version = 0L
        do {
            val page = client().readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,
                TimeRangeFilter.after(Instant.EPOCH),
                dataOriginFilter = setOf(DataOrigin(context.packageName)), pageSize = 100, pageToken = token))
            page.records.filter { it.metadata.clientRecordId == clientId }.forEach { version = maxOf(version, it.metadata.clientRecordVersion) }
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return version
    }
    suspend fun readContext(preferences: Preferences, now: ZonedDateTime): HealthContext {
        val granted = grants(); val windows = healthWindows(now); val api = if (availability() == HealthConnectClient.SDK_AVAILABLE) client() else null
        suspend fun value(capability: HealthCapability, period: String, read: suspend () -> HealthValue): HealthValue? {
            if (!capability.enabled(preferences)) return null
            if (api == null || capability.permission !in granted) return HealthValue("Access not available", period, error = "Allow this capability in Settings to read it.")
            return try { read() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { HealthValue("Could not read data", period, error = "Open Health Connect settings and try again.") }
        }
        val stepsPeriod = "${now.toLocalDate()}, local midnight to ${now.toLocalTime().withSecond(0).withNano(0)}"
        val steps = value(HealthCapability.STEPS, stepsPeriod) {
            if (windows.dayStart == now.toInstant()) HealthValue("No data yet", stepsPeriod)
            else {
                val result = api!!.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(windows.dayStart, now.toInstant())))
                HealthValue(result[StepsRecord.COUNT_TOTAL]?.let { "$it steps" } ?: "No data yet", stepsPeriod, result.dataOrigins.map { it.packageName }.sorted())
            }
        }
        val sleepPeriod = "${windows.sleepStart.atZone(now.zone).toLocalDateTime()} to ${windows.sleepEnd.atZone(now.zone).toLocalDateTime()}"
        val sleep = value(HealthCapability.SLEEP, sleepPeriod) {
            val result = api!!.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(windows.sleepStart, windows.sleepEnd)))
            HealthValue(result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let { "${it.toHours()} h ${it.toMinutes() % 60} min recorded" } ?: "No data yet", sleepPeriod, result.dataOrigins.map { it.packageName }.sorted())
        }
        var sources = emptyList<String>()
        val heart = value(HealthCapability.HEART, "Last 24 hours") {
            var token: String? = null
            val latest = mutableMapOf<String, HeartSample>()
            do {
                val page = api!!.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(windows.heartStart, now.toInstant()), pageSize = 100, pageToken = token))
                page.records.forEach { record -> record.samples.filter { it.time in windows.heartStart..now.toInstant() }.forEach { sample ->
                    val origin = record.metadata.dataOrigin.packageName
                    if (latest[origin]?.time?.isBefore(sample.time) != false) latest[origin] = HeartSample(sample.beatsPerMinute, sample.time, origin)
                } }
                token = page.pageToken
            } while (!token.isNullOrEmpty())
            sources = latest.keys.sorted()
            val sample = selectHeart(latest.values.toList(), preferences.heartRateSource)
            if (sample == null) HealthValue(if (sources.size > 1 && preferences.heartRateSource == null) "Choose a source in Settings" else "No recent data", "Last 24 hours")
            else HealthValue("${sample.beats} bpm", "Recorded ${sample.time.atZone(now.zone).toLocalDateTime()}", listOf(sample.source))
        }
        return HealthContext(steps, sleep, heart, sources)
    }
}

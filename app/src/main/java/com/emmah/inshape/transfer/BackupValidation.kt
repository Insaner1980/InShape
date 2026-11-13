package com.emmah.inshape.transfer

import com.emmah.inshape.data.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.time.*
import java.util.UUID

internal fun validateBackupData(data: JournalSnapshot) {
    fun uuid(value: String) { require(UUID.fromString(value).toString() == value.lowercase()) { "Invalid record identity" } }
    fun ids(values: List<String>): Set<String> { values.forEach(::uuid); require(values.size == values.toSet().size) { "Duplicate record identity" }; return values.toSet() }
    fun optionalInstant(value: String?) { value?.let(Instant::parse) }
    fun duration(value: Long?) { require(value == null || value > 0) { "Invalid duration" } }
    fun target(load: String?, reps: Int?) { if (load != null || reps != null) validatedSetValues(load ?: "", reps?.toString() ?: "") }
    val exercises = ids(data.exercises.map { it.id }); val routines = ids(data.routines.map { it.id }); val workouts = ids(data.workouts.map { it.id })
    val items = ids(data.items.map { it.id }); ids(data.sets.map { it.id }); val checkIns = ids(data.checkIns.map { it.id })
    ids(data.photos.map { it.id }); ids(data.routineItems.map { it.id }); ids(data.healthStates.map { it.workoutId })
    require(data.workouts.count { it.activeKey != null } <= 1) { "More than one active workout" }
    data.exercises.forEach { exercise ->
        uuid(exercise.equipmentId); require(exercise.name.isNotBlank()); require(exercise.alternatives.distinct().size == exercise.alternatives.size)
        require(exercise.alternatives.all { it in exercises && it != exercise.id })
        exercise.links.forEach { val uri = URI(it.url); require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) }
    }
    data.photos.forEach { photo -> require(photo.exerciseId in exercises && photo.position >= 0 && photo.byteLength in 1..MAX_PHOTO_BYTES); require(photo.checksum.matches(Regex("[a-f0-9]{64}"))); extensionFor(photo.mediaType) }
    data.routines.forEach { require(it.title.isNotBlank()); require(it.durationMinutes == null || it.durationMinutes > 0); require(it.revision > 0) }
    data.routineItems.forEach { require(it.routineId in routines && it.exerciseId in exercises && it.position >= 0); require(it.mode in setOf("SETS", "DURATION", "QUICK")); duration(it.durationSeconds); it.sets.forEach { set -> target(set.load, set.reps) } }
    data.workouts.forEach { workout ->
        LocalDate.parse(workout.journalDate); ZoneId.of(workout.zoneId); Instant.parse(workout.createdAt); Instant.parse(workout.modifiedAt)
        optionalInstant(workout.start); optionalInstant(workout.end); workout.startOffset?.let(ZoneOffset::of); workout.endOffset?.let(ZoneOffset::of)
        require(workout.start == null || workout.end == null || Instant.parse(workout.end) > Instant.parse(workout.start)); duration(workout.durationSeconds)
        require(workout.state in setOf("ACTIVE", "DRAFT", "COMPLETED") && workout.kind in setOf("LIVE", "PAST"))
        require((workout.activeKey == 1) == (workout.state == "ACTIVE") && workout.activeKey in listOf(null, 1))
        require(!workout.timingConfirmed || (workout.start != null && workout.end != null)); require(workout.revision > 0)
        require(workout.currentItemId == null || data.items.any { it.id == workout.currentItemId && it.workoutId == workout.id })
        require(workout.durationSource in setOf("UNKNOWN", "MANUAL", "INTERVAL"))
    }
    data.items.forEach { item ->
        require(item.workoutId in workouts && item.exerciseId in exercises && item.position >= 0); uuid(item.equipmentId)
        require(item.disposition in setOf("PLANNED", "IN_PROGRESS", "DONE", "SKIPPED", "INCOMPLETE"))
        duration(item.durationSeconds); duration(item.targetDurationSeconds); optionalInstant(item.quickCompletedAt); require(item.mode in setOf("SETS", "DURATION", "QUICK"))
        require(item.alternativeOf == null || data.items.any { it.id == item.alternativeOf && it.workoutId == item.workoutId && it.id != item.id })
    }
    data.sets.forEach { set ->
        require(set.itemId in items && set.position >= 0); target(set.suggestedLoad, set.suggestedReps)
        if (set.completedAt != null) { Instant.parse(set.completedAt); validatedSetValues(set.load ?: "", set.reps?.toString() ?: "") }
        else require(set.load == null && set.reps == null) { "Draft set has performed values" }
    }
    require(data.checkIns.map { it.workoutId to it.phase }.distinct().size == data.checkIns.size)
    data.checkIns.forEach { check ->
        require(check.workoutId in workouts && check.phase in setOf("BEFORE", "AFTER", "NEXT_DAY")); Instant.parse(check.observedAt); ZoneId.of(check.zoneId)
        require(check.energy == null || check.energy in setOf("LOW", "OK", "GOOD")); require(check.effort == null || (check.phase == "AFTER" && check.effort in 0..10))
    }
    val regions = setOf("neck", "upper_back", "lower_back", "chest", "abdomen") + listOf("shoulder", "upper_arm", "elbow", "forearm_wrist", "hip", "thigh", "knee", "calf_shin", "ankle_foot").flatMap { part -> listOf("left_$part", "right_$part") }
    require(data.observations.map { it.checkInId to it.region }.distinct().size == data.observations.size)
    data.observations.forEach { require(it.checkInId in checkIns && it.region in regions); require(it.pain == null || it.pain in 0..10); require(it.stiffness == null || it.stiffness in 0..10) }
    val preferences = data.preferences
    require(preferences.id == 1 && (preferences.weeklyGoal == null || preferences.weeklyGoal > 0))
    require(preferences.reminderDays.all { it in 1..7 } && preferences.reminderDays.distinct().size == preferences.reminderDays.size); LocalTime.parse(preferences.reminderTime)
    require(data.drafts.map { it.id }.distinct().size == data.drafts.size)
    data.drafts.forEach {
        require(it.id.isNotBlank() && it.kind in setOf("exercise", "routine", "checkin", "timing", "duration") && it.version == 1 && it.payload.length <= 1_048_576)
        Instant.parse(it.modifiedAt)
        if (it.kind == "duration") require(it.payload.length <= 64) else Json.parseToJsonElement(it.payload)
    }
    data.healthStates.forEach { require(it.clientRecordId == "inshape:workout:${it.workoutId}" && it.clientVersion > 0 && it.acknowledgedRevision >= 0) }
    ids(data.outbox.map { it.workoutId })
    data.outbox.forEach { require(it.clientRecordId == "inshape:workout:${it.workoutId}" && it.revision > 0 && it.operation in setOf("UPSERT", "DELETE")) }
}

internal const val MAX_PHOTO_BYTES = 32L * 1024 * 1024
internal fun extensionFor(mime: String): String = when (mime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/heif", "image/heic" -> "heic"; "image/gif" -> "gif"; else -> error("Unsupported image format") }

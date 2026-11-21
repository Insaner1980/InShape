package com.emmah.inshape.transfer

import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.parseWorkoutInstant
import com.emmah.inshape.data.validatedSetValues
import com.emmah.inshape.feature.workout.FinishDraft
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json

internal fun validateBackupData(data: JournalSnapshot) {
    validatePrograms(data)
    val exercises = ids(data.exercises.map { it.id })
    val routines = ids(data.routines.map { it.id })
    val workouts = ids(data.workouts.map { it.id })
    data.items.forEach { uuid(it.id) }
    val itemWorkouts = data.items.associate { it.id to it.workoutId }
    require(itemWorkouts.size == data.items.size) { "Duplicate record identity" }
    ids(data.sets.map { it.id })
    val checkIns = ids(data.checkIns.map { it.id })
    ids(data.photos.map { it.id })
    ids(data.routineItems.map { it.id })
    ids(data.healthStates.map { it.workoutId })
    require(data.workouts.count { it.activeKey != null } <= 1) { "More than one active workout" }
    validateExercises(data, exercises)
    validatePhotos(data, exercises)
    validateRoutines(data)
    validateRoutineItems(data, routines, exercises)
    validateWorkouts(data, itemWorkouts)
    validateItems(data, workouts, exercises, itemWorkouts)
    validateSets(data, itemWorkouts.keys)
    validateCheckIns(data, workouts)
    validateObservations(data, checkIns)
    validatePreferences(data)
    validateDrafts(data)
    validateHealthState(data)
}

private fun validatePrograms(data: JournalSnapshot) {
    val catalogKey = Regex("[a-z0-9_-]{1,128}")
    data.programEnrollment?.let { enrollment ->
        require(enrollment.slot == 1 && enrollment.catalogVersion > 0 && enrollment.programId.matches(catalogKey))
        uuid(enrollment.id)
        LocalDate.parse(enrollment.baselineDate)
        require(enrollment.phaseWeek in 1..8 && enrollment.nextDayIndex >= 0)
    }
    require(data.programLinks.distinct().size == data.programLinks.size)
    val exercises = data.exercises.map { it.id }.toSet()
    data.programLinks.forEach { require(it.catalogExerciseId.matches(catalogKey) && it.exerciseId in exercises) }
    ids(data.assistantMemories.map { it.id })
    data.assistantMemories.forEach { memory ->
        require(
            memory.kind in setOf("preference", "reminder", "future") && memory.text.length in 1..1000 && memory.revision > 0 &&
                if (memory.kind == "preference") memory.exerciseId == null else memory.exerciseId in exercises
        )
        memory.creationWorkoutId?.let(::uuid)
        optionalInstant(memory.acknowledgedAt)
    }
    data.workouts.mapNotNull { it.program }.forEach { program ->
        uuid(program.enrollmentId)
        require(program.programId.matches(catalogKey) && program.catalogVersion > 0 && program.day.isNotBlank())
        require(program.dayIndex in 0 until program.rotationSize && program.mode in setOf("STANDARD", "SHORT", "LIGHT"))
    }
    (data.items.mapNotNull { it.plan } + data.routineItems.mapNotNull { it.plan }).forEach { plan ->
        require(plan.restSeconds == null || plan.restSeconds > 0)
        plan.group?.let { require(it.key.isNotBlank() && it.type.isNotBlank() && it.rounds > 0) }
    }
    data.sets.forEach { set ->
        require(set.rir == null || set.completedAt != null && set.rir in 0..20)
        require(set.perSide == null || set.completedAt != null)
    }
}

private fun uuid(value: String) {
    require(UUID.fromString(value).toString() == value.lowercase()) {
        "Invalid record identity"
    }
}
private fun ids(values: List<String>): Set<String> {
    values.forEach(::uuid)
    require(values.size == values.toSet().size) { "Duplicate record identity" }
    return values.toSet()
}
private fun optionalInstant(value: String?) {
    value?.let(Instant::parse)
}
private fun duration(value: Long?) {
    require(value == null || value > 0) { "Invalid duration" }
}
private fun target(load: String?, reps: Int?) {
    if (load != null ||
        reps != null
    ) {
        validatedSetValues(load ?: "", reps?.toString() ?: "")
    }
}

private fun validateExercises(data: JournalSnapshot, exercises: Set<String>) {
    data.exercises.forEach { exercise ->
        uuid(exercise.equipmentId)
        require(exercise.name.isNotBlank())
        require(exercise.alternatives.distinct().size == exercise.alternatives.size)
        require(exercise.alternatives.all { it in exercises && it != exercise.id })
        exercise.links.forEach {
            val uri = URI(it.url)
            require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank())
        }
    }
}

private fun validatePhotos(data: JournalSnapshot, exercises: Set<String>) {
    data.photos.forEach { photo ->
        require(
            photo.exerciseId in exercises && photo.position >= 0 &&
                photo.byteLength in 1..MAX_PHOTO_BYTES
        )
        require(photo.checksum.matches(Regex("[a-f0-9]{64}")))
        extensionFor(photo.mediaType)
    }
}

private fun validateRoutines(data: JournalSnapshot) {
    data.routines.forEach {
        require(it.title.isNotBlank())
        require(it.durationMinutes == null || it.durationMinutes > 0)
        require(it.revision > 0)
    }
}

private fun validateRoutineItems(data: JournalSnapshot, routines: Set<String>, exercises: Set<String>) {
    data.routineItems.forEach {
        require(it.routineId in routines && it.exerciseId in exercises && it.position >= 0)
        require(it.mode in setOf("SETS", "DURATION", "QUICK"))
        duration(it.durationSeconds)
        it.sets.forEach { set -> target(set.load, set.reps) }
    }
}

private fun validateWorkouts(data: JournalSnapshot, itemWorkouts: Map<String, String>) {
    data.workouts.forEach { workout ->
        LocalDate.parse(workout.journalDate)
        ZoneId.of(workout.zoneId)
        Instant.parse(workout.createdAt)
        Instant.parse(workout.modifiedAt)
        val start = workout.start?.let(::parseWorkoutInstant)
        val end = workout.end?.let(::parseWorkoutInstant)
        workout.startOffset?.let(ZoneOffset::of)
        workout.endOffset?.let(ZoneOffset::of)
        require(start == null || end == null || end.isAfter(start))
        duration(workout.durationSeconds)
        require(
            workout.state in setOf("ACTIVE", "DRAFT", "COMPLETED") &&
                workout.kind in setOf("LIVE", "PAST")
        )
        require(
            (workout.activeKey == 1) == (workout.state == "ACTIVE") &&
                workout.activeKey in listOf(null, 1)
        )
        require(!workout.timingConfirmed || (workout.start != null && workout.end != null))
        require(workout.revision > 0)
        require(workout.currentItemId == null || itemWorkouts[workout.currentItemId] == workout.id)
        require(workout.durationSource in setOf("UNKNOWN", "MANUAL", "INTERVAL"))
    }
}

private fun validateItems(data: JournalSnapshot, workouts: Set<String>, exercises: Set<String>, itemWorkouts: Map<String, String>) {
    data.items.forEach { item ->
        require(item.workoutId in workouts && item.exerciseId in exercises && item.position >= 0)
        uuid(item.equipmentId)
        require(
            item.disposition in setOf("PLANNED", "IN_PROGRESS", "DONE", "SKIPPED", "INCOMPLETE")
        )
        duration(item.durationSeconds)
        duration(item.targetDurationSeconds)
        optionalInstant(item.quickCompletedAt)
        require(item.mode in setOf("SETS", "DURATION", "QUICK"))
        require(
            item.alternativeOf == null ||
                (item.alternativeOf != item.id && itemWorkouts[item.alternativeOf] == item.workoutId)
        )
    }
}

private fun validateSets(data: JournalSnapshot, items: Set<String>) {
    data.sets.forEach { set ->
        require(set.itemId in items && set.position >= 0)
        target(set.suggestedLoad, set.suggestedReps)
        if (set.completedAt !=
            null
        ) {
            Instant.parse(set.completedAt)
            validatedSetValues(
                set.load ?: "",
                set.reps?.toString() ?: ""
            )
        } else {
            require(set.load == null && set.reps == null) {
                "Draft set has performed values"
            }
        }
    }
}

private fun validateCheckIns(data: JournalSnapshot, workouts: Set<String>) {
    require(data.checkIns.map { it.workoutId to it.phase }.distinct().size == data.checkIns.size)
    data.checkIns.forEach { check ->
        require(check.workoutId in workouts && check.phase in setOf("BEFORE", "AFTER", "NEXT_DAY"))
        Instant.parse(check.observedAt)
        ZoneId.of(check.zoneId)
        require(check.energy == null || check.energy in setOf("LOW", "OK", "GOOD"))
        require(check.effort == null || (check.phase == "AFTER" && check.effort in 0..10))
    }
}

private fun validateObservations(data: JournalSnapshot, checkIns: Set<String>) {
    val regions =
        setOf("neck", "upper_back", "lower_back", "chest", "abdomen") +
            listOf("shoulder", "upper_arm", "elbow", "forearm_wrist", "hip", "thigh", "knee", "calf_shin", "ankle_foot").flatMap { part ->
                listOf("left_$part", "right_$part")
            }
    require(
        data.observations.map { it.checkInId to it.region }.distinct().size ==
            data.observations.size
    )
    data.observations.forEach {
        require(it.checkInId in checkIns && it.region in regions)
        require(it.pain == null || it.pain in 0..10)
        require(it.stiffness == null || it.stiffness in 0..10)
    }
}

private fun validatePreferences(data: JournalSnapshot) {
    val preferences = data.preferences
    require(preferences.id == 1 && (preferences.weeklyGoal == null || preferences.weeklyGoal > 0))
    require(
        preferences.reminderDays.all { it in 1..7 } &&
            preferences.reminderDays.distinct().size == preferences.reminderDays.size
    )
    LocalTime.parse(preferences.reminderTime)
}

private fun validateDrafts(data: JournalSnapshot) {
    require(data.drafts.map { it.id }.distinct().size == data.drafts.size)
    data.drafts.forEach {
        require(
            it.id.isNotBlank() &&
                it.kind in setOf("exercise", "routine", "checkin", "timing", "duration", "finish") &&
                it.version == 1 &&
                it.payload.length <= 1_048_576
        )
        require(it.id.startsWith("finish:") == (it.kind == "finish")) { "Mismatched finish draft kind" }
        Instant.parse(it.modifiedAt)
        when (it.kind) {
            "duration" -> require(it.payload.length <= 64)
            "finish" -> Json.decodeFromString<FinishDraft>(it.payload).validateTimestamps()
            else -> Json.parseToJsonElement(it.payload)
        }
    }
}

private fun validateHealthState(data: JournalSnapshot) {
    data.healthStates.forEach {
        require(
            it.clientRecordId == "inshape:workout:${it.workoutId}" && it.clientVersion > 0 &&
                it.acknowledgedRevision >= 0
        )
    }
    ids(data.outbox.map { it.workoutId })
    data.outbox.forEach {
        require(
            it.clientRecordId == "inshape:workout:${it.workoutId}" && it.revision > 0 &&
                it.operation in setOf("UPSERT", "DELETE")
        )
    }
}

internal const val MAX_PHOTO_BYTES = 32L * 1024 * 1024
internal fun extensionFor(mime: String): String = when (mime) {
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/heif", "image/heic" -> "heic"
    "image/gif" -> "gif"
    else -> error("Unsupported image format")
}

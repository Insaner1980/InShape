package com.emmah.inshape.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.*

class JournalRepository(val db: JournalDatabase, val clock: Clock) {
    private val dao = db.journal()
    private fun now() = clock.instant().toString()
    suspend fun snapshot(): JournalSnapshot = db.withTransaction {
        val today = LocalDate.now(clock).toString()
        val workouts = dao.workouts().map { if (it.state == "ACTIVE" && it.journalDate != today) it.copy(timingNeedsReview = true) else it }
        JournalSnapshot(dao.exercises(), dao.photos(), dao.routines(), dao.routineItems(), workouts, dao.items(), dao.sets(), dao.checkIns(), dao.observations(), dao.preferences() ?: Preferences(), dao.drafts(), dao.healthStates(), dao.outbox())
    }
    val changes: Flow<JournalSnapshot> = db.invalidationTracker.createFlow("Exercise", "PhotoAsset", "Routine", "RoutineItem", "WorkoutEntity", "SessionItem", "SessionSet", "CheckIn", "BodyObservation", "Preferences", "EditorDraft", "HealthShareState", "HealthOutbox").map { snapshot() }
    fun observeWorkout(id: String): Flow<Workout?> = changes.map { it.workout(id) }
    fun observeActiveWorkout(): Flow<Workout?> = changes.map { it.active }
    fun observeWorkouts(range: DateRange): Flow<List<Workout>> = changes.map { s -> s.history.filter { it.row.journalDate in range } }
    fun observeExercises(query: ExerciseQuery): Flow<List<Exercise>> = changes.map { s -> s.exercises.filter {
        !it.archived && (query.text.isBlank() || listOf(it.name, it.nickname, it.equipmentName).any { name -> name.contains(query.text, true) }) &&
            (query.category == null || it.category == query.category) && (query.location == null || it.location == query.location) && (!query.favoritesOnly || it.favorite)
    } }
    suspend fun saveExercise(exercise: Exercise): String = db.withTransaction {
        require(exercise.name.isNotBlank()) { "Enter an exercise name" }
        require(exercise.alternatives.distinct().size == exercise.alternatives.size && exercise.id !in exercise.alternatives) { "Choose distinct alternatives" }
        val ids = dao.exercises().map { it.id }.toSet()
        require(exercise.alternatives.all { it in ids }) { "An alternative no longer exists" }
        exercise.links.forEach { link ->
            val uri = runCatching { java.net.URI(link.url) }.getOrNull()
            require(uri?.scheme?.lowercase() in listOf("http", "https") && !uri?.host.isNullOrBlank()) { "Links must be valid HTTP or HTTPS addresses" }
        }
        dao.put(exercise); dao.deleteDraft("exercise:${exercise.id}"); exercise.id
    }
    suspend fun archiveExercise(id: String) = db.withTransaction { dao.put(dao.exercises().first { it.id == id }.copy(archived = true)) }
    suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): String = db.withTransaction {
        require(routine.title.isNotBlank()) { "Enter a routine name" }
        require(routine.durationMinutes == null || routine.durationMinutes > 0) { "Duration must be positive" }
        items.forEach { item ->
            require(item.durationSeconds == null || item.durationSeconds > 0) { "Duration must be positive" }
            item.sets.forEach { set -> if (set.load != null || set.reps != null) validatedSetValues(set.load ?: "", set.reps?.toString() ?: "") }
        }
        dao.put(routine.copy(revision = (dao.routines().find { it.id == routine.id }?.revision ?: 0) + 1))
        dao.clearRoutineItems(routine.id)
        items.forEachIndexed { index, item -> dao.put(item.copy(routineId = routine.id, position = index)) }
        dao.deleteDraft("routine:${routine.id}"); routine.id
    }
    suspend fun duplicateRoutine(id: String): String = db.withTransaction {
        val routine = dao.routines().first { it.id == id }
        val copy = routine.copy(id = newId(), title = "${routine.title} copy", revision = 1)
        saveRoutine(copy, dao.routineItems().filter { it.routineId == id }.map { it.copy(id = newId(), routineId = copy.id) })
    }
    suspend fun removeRoutine(id: String) = dao.deleteRoutine(id)
    suspend fun startWorkout(source: StartSource): String = db.withTransaction {
        val data = snapshot()
        data.active?.let {
            if (it.row.journalDate != LocalDate.now(clock).toString()) dao.put(it.row.copy(timingNeedsReview = true))
            return@withTransaction it.row.id
        }
        val routine = (source as? StartSource.Routine)?.let { selected -> data.routines.first { it.id == selected.id } }
        val repeated = (source as? StartSource.Repeat)?.let { selected -> data.workout(selected.id) ?: error("Workout not found") }
        require(repeated == null || repeated.row.state == "COMPLETED") { "Only saved workouts can be repeated" }
        val time = ZonedDateTime.now(clock)
        val row = WorkoutEntity(title = routine?.title ?: repeated?.row?.title ?: "Workout", sourceRoutineId = routine?.id,
            sourceWorkoutId = repeated?.row?.id, journalDate = time.toLocalDate().toString(), start = now(), zoneId = time.zone.id,
            startOffset = time.offset.id, createdAt = now(), modifiedAt = now())
        dao.insertWorkout(row)
        if (routine != null) data.routineItems.filter { it.routineId == routine.id }.sortedBy { it.position }.forEach { plan ->
            val itemId = addExercise(row.id, plan.exerciseId)
            val item = dao.items().first { it.id == itemId }
            dao.put(item.copy(note = plan.note, targetDurationSeconds = plan.durationSeconds, mode = plan.mode))
            plan.sets.forEachIndexed { i, target -> dao.put(SessionSet(itemId = itemId, position = i, suggestedLoad = target.load, suggestedReps = target.reps, rawLoad = target.load ?: "", rawReps = target.reps?.toString() ?: "")) }
        }
        repeated?.items?.forEachIndexed { i, old ->
            val item = old.row.copy(id = newId(), workoutId = row.id, position = i, disposition = "PLANNED", alternativeOf = null, quickCompletedAt = null, durationSeconds = null, discomfort = false)
            dao.put(item)
            old.sets.forEachIndexed { index, set ->
                val load = if (set.completedAt != null) set.load else set.suggestedLoad
                val reps = if (set.completedAt != null) set.reps else set.suggestedReps
                dao.put(SessionSet(itemId = item.id, position = index, suggestedLoad = load, suggestedReps = reps, rawLoad = load ?: "", rawReps = reps?.toString() ?: ""))
            }
        }
        val latest = dao.workouts().first { it.id == row.id }
        dao.put(latest.copy(currentItemId = dao.items().firstOrNull { it.workoutId == row.id }?.id))
        row.id
    }
    suspend fun addExercise(workoutId: String, exerciseId: String): String = db.withTransaction {
        val exercise = dao.exercises().first { it.id == exerciseId }
        require(!exercise.archived) { "This exercise is archived" }
        val workout = dao.workouts().first { it.id == workoutId }
        val item = SessionItem(workoutId = workoutId, exerciseId = exerciseId, equipmentId = exercise.equipmentId,
            name = exercise.name, equipmentName = exercise.equipmentName, category = exercise.category, basis = exercise.basis,
            setup = exercise.settings.joinToString(", ") { "${it.label}: ${it.value}" }, position = dao.items().count { it.workoutId == workoutId })
        dao.put(item)
        dao.put(workout.copy(currentItemId = workout.currentItemId ?: item.id))
        changed(workoutId); item.id
    }
    suspend fun addSet(itemId: String): String = db.withTransaction {
        val sets = dao.sets().filter { it.itemId == itemId }
        val previous = sets.lastOrNull()
        val row = SessionSet(itemId = itemId, position = sets.size, suggestedLoad = previous?.load ?: previous?.suggestedLoad,
            suggestedReps = previous?.reps ?: previous?.suggestedReps, rawLoad = previous?.rawLoad ?: "", rawReps = previous?.rawReps ?: "")
        dao.put(row); changed(item(itemId).workoutId); row.id
    }
    suspend fun saveSetDraft(setId: String, load: String, reps: String) = db.withTransaction {
        val row = set(setId)
        require(row.completedAt == null) { "Use Edit to correct a completed set" }
        dao.put(row.copy(rawLoad = load, rawReps = reps)); changed(item(row.itemId).workoutId)
    }
    suspend fun setSetCompleted(setId: String, completed: Boolean) = db.withTransaction {
        val row = set(setId)
        if (completed && row.completedAt != null) return@withTransaction
        val values = if (completed) validatedSetValues(row.rawLoad, row.rawReps) else null to null
        dao.put(row.copy(load = values.first, reps = values.second, completedAt = if (completed) now() else null))
        val item = item(row.itemId)
        dao.put(item.copy(disposition = if (item.disposition == "PLANNED") "IN_PROGRESS" else item.disposition))
        changed(item.workoutId)
    }
    suspend fun correctCompletedSet(setId: String, load: String, reps: String) = db.withTransaction {
        val row = set(setId); require(row.completedAt != null)
        val values = validatedSetValues(load, reps)
        dao.put(row.copy(load = values.first, reps = values.second, rawLoad = load, rawReps = reps)); changed(item(row.itemId).workoutId)
    }
    suspend fun removeSet(id: String) = db.withTransaction { val row = set(id); dao.deleteSet(id); changed(item(row.itemId).workoutId) }
    suspend fun useAlternative(itemId: String, exerciseId: String): String = db.withTransaction {
        val original = item(itemId)
        require(original.exerciseId != exerciseId) { "Choose a different exercise" }
        val next = addExercise(original.workoutId, exerciseId)
        dao.put(original.copy(disposition = "SKIPPED"))
        dao.put(item(next).copy(alternativeOf = itemId))
        val ordered = dao.items().filter { it.workoutId == original.workoutId && it.id != next }.sortedBy { it.position }.toMutableList()
        ordered.add(ordered.indexOfFirst { it.id == itemId } + 1, item(next))
        ordered.forEachIndexed { i, row -> dao.put(row.copy(position = i)) }
        val workout = dao.workouts().first { it.id == original.workoutId }
        dao.put(workout.copy(currentItemId = next)); changed(original.workoutId); next
    }
    suspend fun markItemDone(itemId: String, durationSeconds: Long?) = db.withTransaction {
        require(durationSeconds == null || durationSeconds > 0) { "Duration must be positive" }
        val row = item(itemId)
        val hasSets = dao.sets().any { it.itemId == itemId && it.completedAt != null }
        dao.put(row.copy(disposition = "DONE", durationSeconds = durationSeconds, quickCompletedAt = if (!hasSets) now() else null))
        selectNext(row.workoutId); changed(row.workoutId)
    }
    suspend fun reopenItem(id: String) = db.withTransaction {
        val row = item(id); dao.put(row.copy(disposition = "IN_PROGRESS", quickCompletedAt = null, durationSeconds = null))
        val workout = dao.workouts().first { it.id == row.workoutId }; dao.put(workout.copy(currentItemId = id)); changed(row.workoutId)
    }
    suspend fun skipItem(id: String) = db.withTransaction { val row = item(id); dao.put(row.copy(disposition = "SKIPPED")); selectNext(row.workoutId); changed(row.workoutId) }
    suspend fun deferItem(id: String) = db.withTransaction {
        val row = item(id)
        val rows = dao.items().filter { it.workoutId == row.workoutId }.sortedBy { it.position }
        require(rows.any { it.id != id && it.disposition in listOf("PLANNED", "IN_PROGRESS") }) { "No other exercise can move ahead of this one" }
        (rows.filter { it.id != id } + row).forEachIndexed { index, value -> dao.put(value.copy(position = index)) }
        selectNext(row.workoutId); changed(row.workoutId)
    }
    suspend fun moveItem(id: String, offset: Int) = db.withTransaction {
        val row = item(id); val rows = dao.items().filter { it.workoutId == row.workoutId }.sortedBy { it.position }.toMutableList()
        val old = rows.indexOfFirst { it.id == id }; val target = (old + offset).coerceIn(rows.indices)
        rows.removeAt(old); rows.add(target, row); rows.forEachIndexed { index, value -> dao.put(value.copy(position = index)) }; changed(row.workoutId)
    }
    suspend fun removeItem(id: String, confirmPerformed: Boolean = false) = db.withTransaction {
        val row = item(id)
        val performed = dao.sets().any { it.itemId == id && it.completedAt != null } || row.quickCompletedAt != null
        require(!performed || confirmPerformed) { "Confirm deleting the recorded work" }
        dao.items().filter { it.alternativeOf == id }.forEach { dao.put(it.copy(alternativeOf = null)) }
        dao.deleteItem(id); selectNext(row.workoutId); changed(row.workoutId)
    }
    suspend fun saveItemNote(id: String, note: String, discomfort: Boolean) = db.withTransaction { val row = item(id); dao.put(row.copy(note = note, discomfort = discomfort)); changed(row.workoutId) }
    suspend fun finishWorkout(workoutId: String, timing: WorkoutTiming) = db.withTransaction {
        val workout = snapshot().workout(workoutId) ?: error("Workout not found")
        if (workout.row.state == "COMPLETED") return@withTransaction
        require(workout.hasPerformedWork || workout.row.note.isNotBlank()) { "Nothing recorded yet. Add work or a note, or discard this workout." }
        require(!workout.row.timingNeedsReview || timing.acknowledged) { "Review timing for this resumed workout" }
        val start = if (timing.clearTimes) null else timing.start ?: workout.row.start
        val end = if (timing.clearTimes) null else timing.end ?: if (workout.row.kind == "LIVE" && !workout.row.timingNeedsReview) now() else null
        validateTiming(start, end, timing.durationSeconds)
        val date = timing.journalDate ?: workout.row.journalDate; LocalDate.parse(date)
        val duration = timing.durationSeconds ?: if (timing.inferDuration && start != null && end != null && !workout.row.timingNeedsReview) Duration.between(Instant.parse(start), Instant.parse(end)).seconds.takeIf { it > 0 } else null
        dao.put(workout.row.copy(state = "COMPLETED", activeKey = null, start = start, end = end, journalDate = date,
            startOffset = start?.let { Instant.parse(it).atZone(ZoneId.of(workout.row.zoneId)).offset.id },
            endOffset = end?.let { Instant.parse(it).atZone(ZoneId.of(workout.row.zoneId)).offset.id }, durationSeconds = duration,
            durationSource = if (timing.durationSeconds != null) "MANUAL" else if (duration != null) "INTERVAL" else "UNKNOWN",
            timingConfirmed = timing.acknowledged && start != null && end != null, timingNeedsReview = false))
        workout.items.filter { it.row.disposition in listOf("PLANNED", "IN_PROGRESS") }.forEach { dao.put(it.row.copy(disposition = "INCOMPLETE")) }
        changed(workoutId)
    }
    suspend fun saveWorkoutNote(id: String, note: String) = db.withTransaction { val row = dao.workouts().first { it.id == id }; dao.put(row.copy(note = note)); changed(id) }
    suspend fun saveCheckIn(checkIn: CheckIn, observations: List<BodyObservation>, sessionNote: String? = null) = db.withTransaction {
        require(checkIn.phase in listOf("BEFORE", "AFTER", "NEXT_DAY"))
        require(checkIn.effort == null || checkIn.effort in 0..10)
        require(checkIn.phase == "AFTER" || checkIn.effort == null) { "Effort belongs to the After check-in" }
        require(checkIn.energy == null || checkIn.energy in listOf("LOW", "OK", "GOOD"))
        Instant.parse(checkIn.observedAt); ZoneId.of(checkIn.zoneId)
        require(observations.map { it.region }.distinct().size == observations.size)
        observations.forEach { require(it.pain == null || it.pain in 0..10); require(it.stiffness == null || it.stiffness in 0..10) }
        val existing = dao.checkIns().find { it.workoutId == checkIn.workoutId && it.phase == checkIn.phase }
        val saved = checkIn.copy(id = existing?.id ?: checkIn.id)
        dao.put(saved); dao.clearObservations(saved.id)
        observations.forEach { dao.put(it.copy(checkInId = saved.id)) }
        if (sessionNote != null) dao.put(dao.workouts().first { it.id == saved.workoutId }.copy(note = sessionNote))
        dao.deleteDraft("checkin:${saved.workoutId}:${saved.phase}"); changed(saved.workoutId)
    }
    suspend fun saveEditorDraft(draft: EditorDraft) = dao.put(draft)
    suspend fun deleteEditorDraft(id: String) = dao.deleteDraft(id)
    suspend fun updatePreferences(preferences: Preferences) = db.withTransaction {
        require(preferences.weeklyGoal == null || preferences.weeklyGoal > 0)
        require(preferences.reminderDays.all { it in 1..7 }); LocalTime.parse(preferences.reminderTime)
        dao.put(preferences)
        if (!preferences.shareWorkouts) dao.cancelUpserts()
    }
    suspend fun deleteWorkout(id: String) = db.withTransaction {
        val state = dao.healthStates().find { it.workoutId == id }
        if (state != null && (state.acknowledgedRevision > 0 || state.status == "PENDING")) dao.put(HealthOutbox(id, state.clientRecordId, state.clientVersion + 1, "DELETE"))
        else dao.deleteOutbox(id)
        dao.deleteWorkout(id)
    }
    suspend fun createPastWorkout(date: String, title: String): String = db.withTransaction {
        LocalDate.parse(date); require(title.isNotBlank())
        val row = WorkoutEntity(title = title, kind = "PAST", state = "DRAFT", activeKey = null, journalDate = date, zoneId = clock.zone.id, createdAt = now(), modifiedAt = now())
        dao.insertWorkout(row); row.id
    }
    suspend fun correctWorkout(id: String, expectedRevision: Long, title: String, date: String, note: String, timing: WorkoutTiming) = db.withTransaction {
        val row = dao.workouts().first { it.id == id }
        require(row.revision == expectedRevision) { "This workout changed. Reopen it and review your correction." }
        require(title.isNotBlank()); LocalDate.parse(date); validateTiming(timing.start, timing.end, timing.durationSeconds)
        dao.put(row.copy(title = title, journalDate = date, note = note, start = timing.start, end = timing.end, durationSeconds = timing.durationSeconds,
            startOffset = timing.start?.let { Instant.parse(it).atZone(ZoneId.of(row.zoneId)).offset.id },
            endOffset = timing.end?.let { Instant.parse(it).atZone(ZoneId.of(row.zoneId)).offset.id },
            durationSource = if (timing.durationSeconds == null) "UNKNOWN" else "MANUAL", timingConfirmed = timing.acknowledged && timing.start != null && timing.end != null))
        changed(id)
    }
    suspend fun saveSessionPlanToRoutine(workoutId: String, routineId: String) = db.withTransaction {
        val snapshot = snapshot(); val workout = snapshot.workout(workoutId) ?: error("Workout not found")
        val routine = snapshot.routines.first { it.id == routineId }
        saveRoutine(routine, workout.items.map { item -> RoutineItem(routineId = routineId, exerciseId = item.row.exerciseId, position = item.row.position,
            mode = item.row.mode, durationSeconds = item.row.targetDurationSeconds, note = item.row.note, sets = item.sets.map { SetTarget(it.load ?: it.suggestedLoad, it.reps ?: it.suggestedReps) }) })
    }
    private suspend fun item(id: String) = dao.items().first { it.id == id }
    private suspend fun set(id: String) = dao.sets().first { it.id == id }
    private suspend fun selectNext(id: String) {
        val row = dao.workouts().first { it.id == id }
        dao.put(row.copy(currentItemId = dao.items().filter { it.workoutId == id && it.disposition in listOf("PLANNED", "IN_PROGRESS") }.minByOrNull { it.position }?.id))
    }
    private suspend fun changed(id: String) {
        val row = dao.workouts().first { it.id == id }
        val updated = row.copy(revision = row.revision + 1, modifiedAt = now()); dao.put(updated)
        val share = dao.healthStates().find { it.workoutId == id }
        if (updated.state == "COMPLETED" && dao.preferences()?.shareWorkouts == true && share?.restoredHold != true && updated.timingConfirmed && snapshot().workout(id)!!.hasPerformedWork) {
            val version = maxOf(updated.revision, (share?.clientVersion ?: 0) + 1)
            val state = (share ?: HealthShareState(id)).copy(clientVersion = version, status = "PENDING", error = null)
            dao.put(state); dao.put(HealthOutbox(id, state.clientRecordId, version, "UPSERT"))
        }
    }
    private fun validateTiming(start: String?, end: String?, duration: Long?) {
        val begin = start?.let(Instant::parse); val finish = end?.let(Instant::parse)
        require(begin == null || finish == null || finish.isAfter(begin)) { "End time must be after start time" }
        require(duration == null || duration > 0) { "Duration must be positive or not recorded" }
    }
}

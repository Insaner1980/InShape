package com.emmah.inshape.data

import android.content.Context
import androidx.room.withTransaction
import com.emmah.inshape.R
import com.emmah.inshape.feature.program.ProgramCatalog
import com.emmah.inshape.feature.program.ProgramDay
import com.emmah.inshape.feature.program.ProgramVisit
import com.emmah.inshape.feature.program.localExerciseId
import com.emmah.inshape.health.HealthSharing
import com.emmah.inshape.ui.UiException
import com.emmah.inshape.ui.requireUi
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// One transaction coordinator owns the journal aggregate and its related writes.
@Suppress("LargeClass", "TooManyFunctions")
class JournalRepository(val db: JournalDatabase, val clock: Clock, private val context: Context) {
    internal val healthSharingLock = Mutex()
    private val dao = db.journal()
    private fun now() = clock.instant().toString()
    suspend fun snapshot(): JournalSnapshot = db.withTransaction {
        val today = LocalDate.now(clock).toString()
        val workouts = dao.workouts().map {
            if (it.state == "ACTIVE" &&
                it.journalDate != today
            ) {
                it.copy(timingNeedsReview = true)
            } else {
                it
            }
        }
        JournalSnapshot(
            dao.exercises(),
            dao.photos(),
            dao.routines(),
            dao.routineItems(),
            workouts,
            dao.items(),
            dao.sets(),
            dao.checkIns(),
            dao.observations(),
            dao.preferences() ?: Preferences(), dao.drafts(), dao.healthStates(), dao.outbox(),
            dao.programEnrollment(), dao.programLinks(), dao.assistantMemories(), dao.programDayOverrides()
        )
    }
    val changes: Flow<JournalSnapshot> = db.invalidationTracker.createFlow(
        "Exercise",
        "PhotoAsset",
        "Routine",
        "RoutineItem",
        "WorkoutEntity",
        "SessionItem",
        "SessionSet",
        "CheckIn",
        "BodyObservation",
        "Preferences",
        "EditorDraft",
        "HealthShareState",
        "HealthOutbox",
        "ProgramEnrollment",
        "ProgramExerciseLink",
        "ProgramDayOverride",
        "AssistantMemory"
    ).map {
        snapshot()
    }
    fun observeWorkout(id: String): Flow<Workout?> = changes.map { it.workout(id) }
    fun observeActiveWorkout(): Flow<Workout?> = changes.map { it.active }
    fun observeWorkouts(range: DateRange): Flow<List<Workout>> = changes.map { it.historyIn(range) }
    fun observeExercises(query: ExerciseQuery): Flow<List<Exercise>> = changes.map { s ->
        s.exercises.filter {
            !it.archived &&
                (
                    query.text.isBlank() ||
                        listOf(it.name, it.nickname, it.equipmentName).any { name ->
                            name.contains(query.text, true)
                        }
                    ) &&
                (query.category == null || it.category == query.category) &&
                (query.location == null || it.location == query.location) &&
                (!query.favoritesOnly || it.favorite)
        }
    }
    suspend fun saveExercise(exercise: Exercise, draftId: String = "exercise:${exercise.id}", preserveFavorite: Boolean = false): String =
        db.withTransaction {
            requireUi(exercise.name.isNotBlank(), R.string.exercise_name_required)
            requireUi(
                exercise.alternatives.distinct().size == exercise.alternatives.size &&
                    exercise.id !in exercise.alternatives,
                R.string.distinct_alternatives
            )
            val exercises = dao.exercises()
            val ids = exercises.map { it.id }.toSet()
            requireUi(exercise.alternatives.all { it in ids }, R.string.alternative_missing)
            requireUi(exercise.links.all { validExerciseLink(it.url) }, R.string.invalid_web_address)
            val existingFavorite = if (preserveFavorite) exercises.find { it.id == exercise.id }?.favorite else null
            dao.put(exercise.copy(favorite = existingFavorite ?: exercise.favorite))
            dao.deleteDraft(draftId)
            exercise.id
        }
    suspend fun setExerciseFavorite(id: String, favorite: Boolean) = db.withTransaction {
        val exercise = dao.exercises().first { it.id == id }
        dao.put(exercise.copy(favorite = favorite))
    }
    suspend fun archiveExercise(id: String) = db.withTransaction {
        dao.put(
            dao.exercises().first {
                it.id ==
                    id
            }.copy(archived = true)
        )
    }
    suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>, draftId: String = "routine:${routine.id}"): String =
        db.withTransaction {
            requireUi(routine.title.isNotBlank(), R.string.routine_name_required)
            requireUi(routine.durationMinutes == null || routine.durationMinutes > 0, R.string.positive_duration)
            validateRoutineGroups(items)
            items.forEach { item ->
                requireUi(item.durationSeconds == null || item.durationSeconds > 0, R.string.positive_duration)
                item.sets.forEach { set ->
                    requireUi(set.reps == null || set.plannedReps == null, R.string.exact_reps_or_range)
                    if (set.load != null ||
                        set.reps != null
                    ) {
                        validatedSetValues(set.load ?: "", set.reps?.toString() ?: "")
                    }
                }
            }
            dao.put(
                routine.copy(
                    revision =
                        (dao.routines().find { it.id == routine.id }?.revision ?: 0) + 1
                )
            )
            dao.clearRoutineItems(routine.id)
            items.forEachIndexed { index, item ->
                dao.put(item.copy(routineId = routine.id, position = index))
            }
            dao.deleteDraft(draftId)
            routine.id
        }
    suspend fun duplicateRoutine(id: String): String = db.withTransaction {
        val routine = dao.routines().first { it.id == id }
        val copy = routine.copy(id = newId(), title = context.getString(R.string.routine_copy, routine.title), revision = 1)
        saveRoutine(
            copy,
            dao.routineItems().filter {
                it.routineId == id
            }.map { it.copy(id = newId(), routineId = copy.id) }
        )
    }
    suspend fun removeRoutine(id: String) = dao.deleteRoutine(id)

    suspend fun saveProgramDays(
        catalog: ProgramCatalog,
        programId: String,
        edited: List<ProgramDay>,
        expected: List<ProgramDayOverride>,
        created: List<Exercise>
    ) = db.withTransaction {
        val base = requireNotNull(catalog.program(programId))
        val current = dao.programDayOverrides()
        val exercises = dao.exercises().associateBy { it.id }
        val used = edited.flatMap { day -> day.exercises.mapNotNull { it.localExerciseId } }.toSet()
        val added = created.filter { it.id in used }
        require(added.distinctBy { it.id }.size == added.size && edited.distinctBy { it.id }.size == edited.size)
        added.forEach { exercise ->
            require(exercise.id !in exercises && !exercise.archived && exercise.name.isNotBlank())
            saveExercise(exercise)
        }
        edited.forEach { day ->
            val original = base.sessions.single { it.id == day.id }
            val prior = expected.singleOrNull {
                it.catalogVersion == catalog.schemaVersion && it.programId == programId && it.dayId == day.id
            }
            verifyProgramDay(current, catalog.schemaVersion, programId, day.id, prior)
            val record = ProgramDayOverride(catalog.schemaVersion, programId, day.id, day)
            record.validate(exercises.keys + added.map { it.id })
            val previousLocalIds = prior?.day?.exercises.orEmpty().mapNotNull { it.localExerciseId }
            val newlySelected = day.exercises.mapNotNull { it.localExerciseId } - previousLocalIds.toSet()
            requireUi(newlySelected.none { exercises[it]?.archived == true }, R.string.exercise_changed_choices)
            if (day == original) dao.deleteProgramDayOverride(catalog.schemaVersion, programId, day.id) else dao.put(record)
        }
    }

    private fun verifyProgramDay(
        overrides: List<ProgramDayOverride>,
        version: Int,
        programId: String,
        dayId: String,
        expected: ProgramDayOverride?
    ) {
        val stored = overrides.singleOrNull { it.catalogVersion == version && it.programId == programId && it.dayId == dayId }
        requireUi(stored == expected, R.string.program_day_changed)
    }

    suspend fun enrollProgram(
        catalogVersion: Int,
        program: com.emmah.inshape.feature.program.TrainingProgram,
        choices: Map<String, String?>,
        expectedEnrollment: ProgramEnrollment? = null,
        expectedOverride: ProgramDayOverride? = null
    ) = db.withTransaction {
        requireUi(dao.programEnrollment() == expectedEnrollment, R.string.program_changed_equipment)
        val day = program.sessions.first { it.id == program.sequence.first() }
        verifyProgramDay(dao.programDayOverrides(), catalogVersion, program.id, day.id, expectedOverride)
        requireUi(expectedOverride == null || expectedOverride.day == day, R.string.program_day_changed)
        resolveProgramChoices(day.exercises, choices)
        dao.put(ProgramEnrollment(programId = program.id, catalogVersion = catalogVersion, baselineDate = LocalDate.now(clock).toString()))
    }

    suspend fun setProgramWeek(id: String, week: Int) = db.withTransaction {
        val enrollment = dao.programEnrollment()
        requireUi(enrollment?.id == id && week in 1..8, R.string.program_changed)
        dao.put(enrollment.copy(phaseWeek = week))
    }

    private suspend fun resolveProgramChoices(
        slots: List<com.emmah.inshape.feature.program.ProgramSlot>,
        choices: Map<String, String?>
    ): Map<String, String> {
        requireUi(choices.keys == slots.map { it.exerciseId }.toSet(), R.string.choose_equipment_each)
        val existing = dao.exercises().associateBy { it.id }
        return slots.associate { slot ->
            val selected = choices.getValue(slot.exerciseId)
            requireUi(slot.localExerciseId == null || selected == slot.localExerciseId, R.string.exercise_changed_choices)
            val exercise = if (selected == null) {
                com.emmah.inshape.feature.program.newProgramExercise(slot).also { dao.put(it) }
            } else {
                existing[selected]?.takeUnless { it.archived } ?: throw UiException(R.string.exercise_changed_choices)
            }
            if (slot.localExerciseId == null) dao.put(ProgramExerciseLink(slot.exerciseId, exercise.id))
            slot.exerciseId to exercise.id
        }
    }

    suspend fun startWorkout(source: StartSource): String = db.withTransaction {
        val data = snapshot()
        data.active?.let {
            if (it.row.journalDate !=
                LocalDate.now(clock).toString()
            ) {
                dao.put(it.row.copy(timingNeedsReview = true))
            }
            return@withTransaction it.row.id
        }
        val routine = (source as? StartSource.Routine)?.let { selected ->
            data.routines.first {
                it.id ==
                    selected.id
            }
        }
        val repeated = (source as? StartSource.Repeat)?.let { selected ->
            data.workout(selected.id)
                ?: throw UiException(R.string.workout_missing)
        }
        requireUi(repeated == null || repeated.row.state == "COMPLETED", R.string.repeat_saved_only)
        val visit = (source as? StartSource.Program)?.visit
        val program = visit?.let {
            verifyProgramDay(
                data.programDayOverrides,
                it.enrollment.catalogVersion,
                it.enrollment.programId,
                it.day.id,
                it.expectedOverride
            )
            requireUi(it.expectedOverride == null || it.expectedOverride.day == it.day, R.string.program_day_changed)
            requireUi(
                data.programEnrollment == it.enrollment && it.day.id == it.sequence.getOrNull(it.enrollment.nextDayIndex),
                R.string.program_day_changed
            )
            ProgramWorkoutSource(
                it.enrollment.id,
                it.enrollment.programId,
                it.enrollment.catalogVersion,
                it.day.id,
                it.enrollment.nextDayIndex,
                it.sequence.size,
                it.mode.name
            )
        }
        val time = ZonedDateTime.now(clock)
        val timestamp = time.toInstant().toString()
        val row = WorkoutEntity(
            title = visit?.day?.name ?: routine?.title ?: repeated?.row?.title ?: context.getString(R.string.workout),
            sourceRoutineId = routine?.id,
            sourceWorkoutId = repeated?.row?.id,
            journalDate = time.toLocalDate().toString(),
            start = timestamp,
            zoneId = time.zone.id,
            startOffset = time.offset.id,
            createdAt = timestamp,
            modifiedAt = timestamp,
            program = program
        )
        dao.insertWorkout(row)
        if (routine != null) addRoutineItems(row.id, data.routineItems.filter { it.routineId == routine.id })
        if (visit != null) addProgramItems(row.id, visit)
        copyWorkoutItems(repeated, row)
        val latest = dao.workouts().first { it.id == row.id }
        dao.put(latest.copy(currentItemId = dao.items().firstOrNull { it.workoutId == row.id }?.id))
        row.id
    }

    private suspend fun addRoutineItems(workoutId: String, plans: List<RoutineItem>) {
        plans.sortedBy { it.position }.forEach { plan ->
            val itemId = addExercise(workoutId, plan.exerciseId, allowArchived = true)
            val item = dao.items().first { it.id == itemId }
            dao.put(
                item.copy(
                    note = plan.note,
                    targetDurationSeconds = plan.durationSeconds,
                    mode = plan.mode,
                    plan = plan.plan
                )
            )
            plan.sets.forEachIndexed { index, target ->
                dao.put(
                    SessionSet(
                        itemId = itemId,
                        position = index,
                        suggestedLoad = target.load,
                        suggestedReps = target.reps,
                        plannedReps = target.plannedReps,
                        rawLoad = target.load ?: "",
                        rawReps = target.reps?.toString() ?: ""
                    )
                )
            }
        }
    }

    private suspend fun addProgramItems(workoutId: String, visit: ProgramVisit) {
        val slots = visit.slots
        val links = resolveProgramChoices(slots, visit.choices)
        slots.forEach { slot ->
            val itemId = addExercise(workoutId, links.getValue(slot.exerciseId), allowArchived = false)
            dao.put(
                item(itemId).copy(
                    note = slot.instruction,
                    plan = ExercisePlan(
                        restSeconds = slot.restSeconds,
                        catalogExerciseId = slot.exerciseId,
                        originalCatalogExerciseId = slot.exerciseId,
                        slotKey = "${visit.day.id}:${slot.exerciseId}",
                        optional = slot.optional,
                        sourceSlot = slot
                    )
                )
            )
            repeat(visit.setCount(slot.sets)) { index ->
                dao.put(
                    SessionSet(
                        itemId = itemId,
                        position = index,
                        plannedReps = PlannedReps(slot.target.min, slot.target.max, slot.target.perSide)
                    )
                )
            }
        }
    }

    private suspend fun copyWorkoutItems(repeated: Workout?, row: WorkoutEntity) {
        repeated?.items?.filter { it.row.disposition != "SKIPPED" }?.forEachIndexed { i, old ->
            val item = old.row.copy(
                id = newId(),
                workoutId = row.id,
                position = i,
                disposition = "PLANNED",
                alternativeOf = null,
                quickCompletedAt = null,
                durationSeconds = null,
                discomfort = false
            )
            dao.put(item)
            old.sets.forEachIndexed { index, set ->
                val load = if (set.completedAt != null) set.load else set.suggestedLoad
                val reps = if (set.plannedReps != null) {
                    null
                } else if (set.completedAt != null) {
                    set.reps
                } else {
                    set.suggestedReps
                }
                dao.put(
                    SessionSet(
                        itemId = item.id,
                        position = index,
                        suggestedLoad = load,
                        suggestedReps = reps,
                        plannedReps = set.plannedReps,
                        rawLoad =
                            load ?: "",
                        rawReps = reps?.toString() ?: ""
                    )
                )
            }
        }
    }

    suspend fun addExercise(workoutId: String, exerciseId: String): String = db.withTransaction {
        addExercise(workoutId, exerciseId, allowArchived = false)
    }

    private suspend fun addExercise(workoutId: String, exerciseId: String, allowArchived: Boolean): String {
        val exercise = dao.exercises().first { it.id == exerciseId }
        requireUi(allowArchived || !exercise.archived, R.string.archived_exercise)
        val workout = dao.workouts().first { it.id == workoutId }
        val item =
            SessionItem(
                workoutId = workoutId, exerciseId = exerciseId, equipmentId = exercise.equipmentId,
                name = exercise.name, equipmentName = exercise.equipmentName, category = exercise.category, basis = exercise.basis,
                setup = exercise.settings.joinToString(", ") {
                    "${it.label}: ${it.value}"
                },
                position = (dao.items().filter { it.workoutId == workoutId }.maxOfOrNull { it.position } ?: -1) + 1
            )
        dao.put(item)
        dao.put(workout.copy(currentItemId = workout.currentItemId ?: item.id))
        changed(workoutId)
        return item.id
    }
    suspend fun addSet(itemId: String): String = db.withTransaction {
        val sets = dao.sets().filter { it.itemId == itemId }
        val previous = sets.maxByOrNull { it.position }
        val row = SessionSet(
            itemId = itemId,
            position = (previous?.position ?: -1) + 1,
            suggestedLoad =
                previous?.load ?: previous?.suggestedLoad,
            suggestedReps = previous?.reps ?: previous?.suggestedReps,
            rawLoad =
                previous?.rawLoad ?: "",
            rawReps = previous?.rawReps ?: ""
        )
        dao.put(row)
        changed(item(itemId).workoutId)
        row.id
    }
    suspend fun saveSetDraft(setId: String, load: String, reps: String) = db.withTransaction {
        val row = set(setId)
        requireUi(row.completedAt == null, R.string.edit_completed_set)
        dao.put(row.copy(rawLoad = load, rawReps = reps))
        changed(item(row.itemId).workoutId)
    }
    suspend fun setSetCompleted(setId: String, completed: Boolean) = db.withTransaction {
        val row = set(setId)
        if (completed && row.completedAt != null) return@withTransaction
        val values = if (completed) validatedSetValues(row.rawLoad, row.rawReps) else null to null
        dao.put(
            row.copy(
                load = values.first,
                reps = values.second,
                completedAt = if (completed) now() else null,
                rir = if (completed) row.rir else null,
                perSide = if (completed) row.perSide ?: row.plannedReps?.perSide?.takeIf { it } else null
            )
        )
        val item = item(row.itemId)
        dao.put(
            item.copy(
                disposition = if (item.disposition ==
                    "PLANNED"
                ) {
                    "IN_PROGRESS"
                } else {
                    item.disposition
                }
            )
        )
        changed(item.workoutId)
    }
    suspend fun correctCompletedSet(setId: String, load: String, reps: String, rir: String? = null) = db.withTransaction {
        val row = set(setId)
        requireNotNull(row.completedAt) { "Failed requirement." }
        val values = validatedSetValues(load, reps)
        val effort = when {
            rir == null -> row.rir
            rir.isBlank() -> null
            else -> rir.toIntOrNull()?.takeIf { it in 0..20 } ?: throw UiException(R.string.reported_rir_range)
        }
        dao.put(
            row.copy(load = values.first, reps = values.second, rawLoad = load, rawReps = reps, rir = effort)
        )
        changed(item(row.itemId).workoutId)
    }
    suspend fun removeSet(id: String) = db.withTransaction {
        val row = set(id)
        dao.deleteSet(id)
        changed(item(row.itemId).workoutId)
    }
    suspend fun useAlternative(itemId: String, exerciseId: String, requireCurrent: Boolean = false): String = db.withTransaction {
        val original = item(itemId)
        if (requireCurrent) {
            val workout = snapshot().workout(original.workoutId)!!
            val current = workout.currentItem()
            requireUi(workout.row.activeKey == 1 && current?.row?.id == itemId, R.string.replacement_changed)
        }
        requireUi(original.exerciseId != exerciseId, R.string.choose_different_exercise)
        val next = addExercise(original.workoutId, exerciseId)
        dao.put(original.copy(disposition = "SKIPPED"))
        dao.put(
            item(next).copy(
                alternativeOf = itemId,
                plan = original.plan?.copy(catalogExerciseId = null)
            )
        )
        dao.sets().filter { it.itemId == itemId && it.completedAt == null }.forEachIndexed { index, target ->
            dao.put(SessionSet(itemId = next, position = index, suggestedReps = target.suggestedReps, plannedReps = target.plannedReps))
        }
        val ordered = dao.items().filter {
            it.workoutId == original.workoutId && it.id != next
        }.toMutableList()
        ordered.add(ordered.indexOfFirst { it.id == itemId } + 1, item(next))
        ordered.forEachIndexed { i, row -> dao.put(row.copy(position = i)) }
        val workout = dao.workouts().first { it.id == original.workoutId }
        dao.put(workout.copy(currentItemId = next))
        changed(original.workoutId)
        next
    }

    internal suspend fun useProgramAlternative(
        itemId: String,
        planned: com.emmah.inshape.feature.program.ProgramSlot,
        selection: com.emmah.inshape.feature.program.ProgramAlternative,
        exerciseId: String?
    ): String = db.withTransaction {
        val original = item(itemId)
        requireUi(
            original.plan?.originalCatalogExerciseId == planned.exerciseId && selection in planned.alternatives,
            R.string.program_exercise_changed
        )
        val chosen = if (exerciseId == null) {
            com.emmah.inshape.feature.program.newAlternativeExercise(selection).also { dao.put(it) }
        } else {
            requireNotNull(dao.exercises().find { it.id == exerciseId && !it.archived })
        }
        val id = useAlternative(itemId, chosen.id, requireCurrent = true)
        dao.put(ProgramExerciseLink(selection.exerciseId, chosen.id))
        dao.put(item(id).copy(plan = original.plan.copy(catalogExerciseId = selection.exerciseId)))
        val copied = dao.sets().filter { it.itemId == id }.sortedBy { it.position }
        val workout = dao.workouts().first { it.id == original.workoutId }
        val override = selection.target
        if (override != null) {
            val prescribed = when (workout.program?.mode) {
                "SHORT" -> copied.size
                "LIGHT" -> ((selection.sets ?: planned.sets) - 1).coerceAtLeast(1)
                else -> selection.sets ?: copied.size
            }
            val remaining = minOf(prescribed, copied.size)
            copied.forEach { dao.deleteSet(it.id) }
            repeat(remaining) { index ->
                dao.put(SessionSet(itemId = id, position = index, plannedReps = PlannedReps(override.min, override.max, override.perSide)))
            }
        }
        changed(original.workoutId)
        id
    }
    suspend fun markItemDone(itemId: String, durationSeconds: Long?, draftId: String? = null) = db.withTransaction {
        requireUi(durationSeconds == null || durationSeconds > 0, R.string.positive_duration)
        val row = item(itemId)
        val hasSets = dao.sets().any { it.itemId == itemId && it.completedAt != null }
        dao.put(
            row.copy(
                disposition = "DONE",
                durationSeconds = durationSeconds,
                quickCompletedAt = if (!hasSets) now() else null
            )
        )
        selectNext(row.workoutId)
        changed(row.workoutId)
        draftId?.let { dao.deleteDraft(it) }
    }
    suspend fun reopenItem(id: String) = db.withTransaction {
        val row = item(id)
        dao.put(
            row.copy(disposition = "IN_PROGRESS", quickCompletedAt = null, durationSeconds = null)
        )
        val workout = dao.workouts().first { it.id == row.workoutId }
        dao.put(workout.copy(currentItemId = id))
        changed(row.workoutId)
    }
    suspend fun skipItemForNow(id: String) = db.withTransaction {
        val row = item(id)
        val workout = dao.workouts().first { it.id == row.workoutId }
        requireUi(workout.state != "COMPLETED" && row.isPending, R.string.exercise_no_longer_pending)
        val rows = dao.items().filter { it.workoutId == row.workoutId }
        if (rows.any { it.id != id && it.isPending }) {
            (rows.filter { it.id != id } + row.copy(disposition = "INCOMPLETE"))
                .forEachIndexed { index, value -> dao.put(value.copy(position = index)) }
        } else {
            dao.put(row.copy(disposition = "SKIPPED"))
        }
        selectNext(row.workoutId)
        changed(row.workoutId)
    }
    suspend fun moveItem(id: String, offset: Int) = db.withTransaction {
        val row = item(id)
        val rows = dao.items().filter { it.workoutId == row.workoutId }.toMutableList()
        val old = rows.indexOfFirst { it.id == id }
        val target = (old + offset).coerceIn(rows.indices)
        rows.removeAt(old)
        rows.add(target, row)
        rows.forEachIndexed { index, value -> dao.put(value.copy(position = index)) }
        changed(row.workoutId)
    }
    suspend fun removeItem(id: String, confirmPerformed: Boolean = false) = db.withTransaction {
        val row = item(id)
        val performed =
            dao.sets().any { it.itemId == id && it.completedAt != null } ||
                row.quickCompletedAt != null
        requireUi(!performed || confirmPerformed, R.string.confirm_delete_work)
        dao.items().filter {
            it.alternativeOf == id
        }.forEach { dao.put(it.copy(alternativeOf = null)) }
        dao.deleteItem(id)
        selectNext(row.workoutId)
        changed(row.workoutId)
    }
    suspend fun saveItemNote(id: String, note: String? = null, discomfort: Boolean? = null) = db.withTransaction {
        val row = item(id)
        dao.put(row.copy(note = note ?: row.note, discomfort = discomfort ?: row.discomfort))
        changed(row.workoutId)
    }
    suspend fun finishWorkout(
        id: String,
        timing: WorkoutTiming,
        note: String? = null,
        expectedNote: String? = null,
        confirmCurrentTiming: Boolean = false
    ) = db.withTransaction {
        val workout = snapshot().workout(id) ?: throw UiException(R.string.workout_missing)
        if (workout.row.state == "COMPLETED") return@withTransaction
        val savedNote = resolveFinishNote(workout.row.note, note, expectedNote)
        requireUi(workout.hasPerformedWork || savedNote.isNotBlank(), R.string.empty_workout_save)
        requireUi(!workout.row.timingNeedsReview || timing.acknowledged, R.string.review_resumed_timing)
        val start = if (timing.clearTimes) null else timing.start ?: workout.row.start
        val end = workout.row.endOnFinish(timing)
        validateTiming(start, end, timing.durationSeconds)
        val date = timing.journalDate ?: workout.row.journalDate
        LocalDate.parse(date)
        val duration = completedDuration(timing, start, end, workout.row.timingNeedsReview)
        dao.put(
            workout.row.copy(
                state = "COMPLETED", activeKey = null, start = start, end = end, journalDate = date,
                note = savedNote,
                startOffset = start?.let {
                    Instant.parse(it).atZone(ZoneId.of(workout.row.zoneId)).offset.id
                },
                endOffset = end?.let {
                    Instant.parse(it).atZone(ZoneId.of(workout.row.zoneId)).offset.id
                },
                durationSeconds = duration,
                durationSource = completedDurationSource(timing.durationSeconds, duration),
                timingConfirmed = (timing.acknowledged || confirmCurrentTiming) && start != null && end != null,
                timingNeedsReview = false
            )
        )
        workout.pendingItems.forEach {
            val disposition = if (it.row.disposition == "INCOMPLETE") "SKIPPED" else "INCOMPLETE"
            dao.put(it.row.copy(disposition = disposition))
        }
        advanceProgram(workout)
        changed(id)
    }

    private suspend fun advanceProgram(workout: Workout) {
        val source = workout.row.program
        val enrollment = dao.programEnrollment()
        if (source == null || enrollment == null) return
        if (enrollment.id == source.enrollmentId && enrollment.nextDayIndex == source.dayIndex && workout.hasPerformedWork) {
            dao.put(enrollment.copy(nextDayIndex = (source.dayIndex + 1) % source.rotationSize))
        }
    }

    private fun WorkoutEntity.endOnFinish(timing: WorkoutTiming): String? = when {
        timing.clearTimes -> null
        timing.end != null -> timing.end
        kind == "LIVE" && !timingNeedsReview -> now()
        else -> null
    }

    private fun resolveFinishNote(current: String, note: String?, expectedNote: String?): String {
        if (note == null) return current
        requireUi(note == current || expectedNote == current, R.string.finish_note_conflict)
        return note
    }

    private fun completedDurationSource(manual: Long?, interval: Long?): String = when {
        manual != null -> "MANUAL"
        interval != null -> "INTERVAL"
        else -> "UNKNOWN"
    }

    private fun completedDuration(timing: WorkoutTiming, start: String?, end: String?, needsReview: Boolean): Long? {
        val canInfer = timing.inferDuration && !needsReview
        return timing.durationSeconds ?: if (canInfer && start != null && end != null) {
            Duration.between(Instant.parse(start), Instant.parse(end)).seconds.takeIf { it > 0 }
        } else {
            null
        }
    }

    suspend fun saveWorkoutNote(id: String, note: String) = db.withTransaction {
        val row = dao.workouts().first {
            it.id ==
                id
        }
        dao.put(row.copy(note = note))
        changed(id)
    }
    suspend fun saveCheckIn(
        checkIn: CheckIn,
        observations: List<BodyObservation>,
        sessionNote: String? = null,
        expectedSessionNote: String? = null
    ) = db.withTransaction {
        require(checkIn.phase in listOf("BEFORE", "AFTER", "NEXT_DAY"))
        require(checkIn.effort == null || checkIn.effort in 0..10)
        requireUi(checkIn.phase == "AFTER" || checkIn.effort == null, R.string.effort_after_workout)
        require(checkIn.energy == null || checkIn.energy in listOf("LOW", "OK", "GOOD"))
        Instant.parse(checkIn.observedAt)
        ZoneId.of(checkIn.zoneId)
        require(observations.map { it.region }.distinct().size == observations.size)
        observations.forEach {
            require(it.pain == null || it.pain in 0..10)
            require(it.stiffness == null || it.stiffness in 0..10)
        }
        val existing = dao.checkIns().find {
            it.workoutId == checkIn.workoutId &&
                it.phase == checkIn.phase
        }
        val saved = checkIn.copy(id = existing?.id ?: checkIn.id)
        dao.put(saved)
        dao.clearObservations(saved.id)
        observations.forEach { dao.put(it.copy(checkInId = saved.id)) }
        if (sessionNote !=
            null
        ) {
            val workout = dao.workouts().first { it.id == saved.workoutId }
            requireUi(workout.note == expectedSessionNote, R.string.edit_note_conflict)
            dao.put(workout.copy(note = sessionNote))
        }
        dao.deleteDraft("checkin:${saved.workoutId}:${saved.phase}")
        changed(saved.workoutId)
    }
    suspend fun saveEditorDraft(draft: EditorDraft) = dao.put(draft)
    suspend fun deleteEditorDraft(id: String) = dao.deleteDraft(id)
    suspend fun updatePreferences(preferences: Preferences) = healthSharingLock.withLock {
        db.withTransaction {
            require(preferences.weeklyGoal == null || preferences.weeklyGoal > 0)
            require(preferences.reminderDays.all { it in 1..7 })
            LocalTime.parse(preferences.reminderTime)
            dao.put(preferences)
            if (!preferences.shareWorkouts) dao.cancelUpserts()
        }
    }
    suspend fun deleteWorkout(id: String) = db.withTransaction {
        val state = dao.healthStates().find { it.workoutId == id }
        if (state != null &&
            (state.acknowledgedRevision > 0 || state.status in setOf("PENDING", "SHARE_UNCONFIRMED"))
        ) {
            dao.put(
                HealthOutbox(
                    id,
                    state.clientRecordId,
                    state.clientVersion + 1,
                    "DELETE"
                )
            )
        } else {
            dao.deleteOutbox(id)
        }
        dao.deleteDraft("finish:$id")
        dao.deleteDraft("timing:$id")
        for (phase in listOf("BEFORE", "AFTER", "NEXT_DAY")) {
            dao.deleteDraft("checkin:$id:$phase")
        }
        dao.deleteWorkout(id)
    }
    suspend fun createPastWorkout(date: String, title: String): String = db.withTransaction {
        LocalDate.parse(date)
        require(title.isNotBlank())
        val row =
            WorkoutEntity(
                title = title,
                kind = "PAST",
                state = "DRAFT",
                activeKey = null,
                journalDate = date,
                zoneId = clock.zone.id,
                createdAt = now(),
                modifiedAt = now()
            )
        dao.insertWorkout(row)
        row.id
    }
    suspend fun correctWorkout(id: String, expectedRevision: Long, title: String, date: String, note: String, timing: WorkoutTiming) =
        db.withTransaction {
            val row = dao.workouts().first { it.id == id }
            requireUi(row.revision == expectedRevision, R.string.timing_changed)
            require(title.isNotBlank())
            LocalDate.parse(date)
            validateTiming(timing.start, timing.end, timing.durationSeconds)
            dao.put(
                row.copy(
                    title = title,
                    journalDate = date,
                    note = note,
                    start = timing.start,
                    end = timing.end,
                    durationSeconds = timing.durationSeconds,
                    startOffset = timing.start?.let {
                        Instant.parse(it).atZone(ZoneId.of(row.zoneId)).offset.id
                    },
                    endOffset = timing.end?.let {
                        Instant.parse(it).atZone(ZoneId.of(row.zoneId)).offset.id
                    },
                    durationSource = when {
                        timing.durationSeconds == null -> "UNKNOWN"
                        timing.durationSeconds == row.durationSeconds -> row.durationSource
                        else -> "MANUAL"
                    },
                    timingConfirmed =
                        timing.acknowledged && timing.start != null && timing.end != null
                )
            )
            changed(id)
        }
    suspend fun saveSessionPlanToRoutine(workoutId: String, routineId: String, expectedRevision: Long? = null) = db.withTransaction {
        val snapshot = snapshot()
        val workout =
            snapshot.workout(workoutId) ?: throw UiException(R.string.workout_missing)
        val routine = snapshot.routines.first { it.id == routineId }
        requireUi(expectedRevision == null || routine.revision == expectedRevision, R.string.routine_changed)
        saveRoutine(
            routine,
            workout.routinePlanItems.mapIndexed { index, item ->
                item.routineTarget(routineId, index).copy(id = newId())
            }
        )
    }
    private suspend fun item(id: String) = dao.items().first { it.id == id }
    private suspend fun set(id: String) = dao.sets().first { it.id == id }
    private suspend fun selectNext(id: String) {
        val row = dao.workouts().first { it.id == id }
        dao.put(
            row.copy(
                currentItemId = dao.items().firstOrNull { it.workoutId == id && it.isPending }?.id
            )
        )
    }
    private suspend fun changed(id: String) {
        val row = dao.workouts().first { it.id == id }
        val updated = row.copy(revision = row.revision + 1, modifiedAt = now())
        dao.put(updated)
        val share = dao.healthStates().find { it.workoutId == id }
        val sharingAllowed = dao.preferences()?.shareWorkouts == true && share?.restoredHold != true
        val eligible = updated.state == "COMPLETED" && updated.timingConfirmed &&
            HealthSharing.eligible(snapshot().workout(id))
        if (eligible && sharingAllowed) {
            val version = maxOf(updated.revision, (share?.clientVersion ?: 0) + 1)
            val state = (
                share ?: HealthShareState(
                    id
                )
                ).copy(clientVersion = version, status = "PENDING", error = null)
            dao.put(state)
            dao.put(HealthOutbox(id, state.clientRecordId, version, "UPSERT"))
        } else if (!eligible && updated.state == "COMPLETED") {
            cancelIneligibleShare(id)
        }
    }
    internal suspend fun cancelIneligibleShare(id: String) {
        if (dao.cancelUpsert(id) == 0) return
        val share = dao.healthStates().find { it.workoutId == id }
        if (share?.status == "PENDING") {
            val status = if (share.acknowledgedRevision > 0) "SHARED" else "SHARE_UNCONFIRMED"
            dao.put(share.copy(status = status, error = null))
        }
    }
    private fun validateTiming(start: String?, end: String?, duration: Long?) {
        val begin = start?.let(::parseWorkoutInstant)
        val finish = end?.let(::parseWorkoutInstant)
        requireUi(begin == null || finish == null || finish.isAfter(begin), R.string.finish_after_start)
        requireUi(duration == null || duration > 0, R.string.valid_or_unknown_duration)
    }
}

internal fun validExerciseLink(address: String): Boolean {
    val uri = runCatching { java.net.URI(address) }.getOrNull()
    return uri?.scheme?.lowercase() in listOf("http", "https") && !uri?.host.isNullOrBlank()
}

package com.emmah.inshape.demo

import androidx.room.withTransaction
import com.emmah.inshape.AppContainer
import com.emmah.inshape.data.*
import java.time.*
import java.util.UUID

object DemoFixtures {
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-13T09:15:00Z"), ZoneId.of("Europe/Helsinki"))
    fun id(label: String) = UUID.nameUUIDFromBytes("inshape-demo:$label".toByteArray()).toString()
    val activeId = id("active"); val routineId = id("full-body"); val pulldownId = id("Lat pulldown")
    val names = listOf("TodayWithActiveWorkout", "ActiveWorkoutPartiallyDone", "ExerciseWithSetup", "CheckInAfter", "ProgressWithHistory", "HistoryNovember", "LibraryRoutineExpanded", "SettingsConfigured", "Empty", "SparseHistory", "FailedSave")
    fun route(state: String): String = when (state) {
        "ActiveWorkoutPartiallyDone" -> "workout/$activeId"
        "ExerciseWithSetup" -> "exercise/$pulldownId"
        "CheckInAfter" -> "checkin/$activeId/AFTER"
        "ProgressWithHistory" -> "Progress?exercise=$pulldownId"
        "SparseHistory" -> "Progress"
        "HistoryNovember" -> "History"
        "LibraryRoutineExpanded" -> "Library"
        "SettingsConfigured" -> "Settings"
        else -> "Today"
    }
    suspend fun load(container: AppContainer, state: String) {
        require(!container.integrationsEnabled && state in names)
        container.storageReady.await()
        val now = clock.instant().toString(); val zone = clock.zone.id
        val exerciseNames = listOf("Warm-up bike", "Leg press", "Seated row", "Chest press", "Lat pulldown", "Leg curl", "Hip flexor stretch", "Shoulder mobility", "Assisted pull-up machine", "Band pulldown, home")
        val exercises = exerciseNames.mapIndexed { index, name -> Exercise(id = id(name), equipmentId = id("machine:$name"), name = name,
            category = when(index) { 0 -> "Warm-up"; 6, 7 -> "Mobility"; else -> "Machine" }, basis = when(index) { 0, 6, 7 -> "duration"; 8 -> "assistance_kg"; else -> "kg" },
            location = if (index == 9) "Home" else "Gym", equipmentName = if (index == 4) "Pulldown machine" else "", favorite = index == 4,
            muscles = if (index == 4) "Back, arms" else "", settings = if (index == 4) listOf(Setting("Seat height", "4"), Setting("Thigh pad", "2"), Setting("Handle", "Wide bar"), Setting("Backrest", "None")) else emptyList(),
            instructions = if (index == 4) "Sit tall, chest up. Pull the bar to the upper chest with elbows pointing down. Let it rise slowly. Stop if the shoulder pinches." else "",
            alternatives = if (index == 4) listOf(id(exerciseNames[8]), id(exerciseNames[9])) else emptyList(),
            links = if (index == 4) listOf(SavedLink("Exercise information", "https://www.nhs.uk/live-well/exercise/")) else emptyList()) }
        val routines = listOf(Routine(routineId, "Full body A", durationMinutes = 50), Routine(id("short"), "Short 20 min", "For tired days", 20), Routine(id("mobility"), "Mobility at home", "No equipment", 15))
        val routineItems = routines.flatMapIndexed { r, routine ->
            val indices = when (r) { 0 -> (0..6).toList(); 1 -> listOf(0, 1, 2, 4); else -> listOf(6, 7, 6, 7, 6, 7) }
            indices.mapIndexed { position, index -> RoutineItem(id("plan:$r:$position"), routine.id, exercises[index].id, position, mode = if (index in listOf(0, 6, 7)) "DURATION" else "SETS", durationSeconds = if (index in listOf(0, 6, 7)) 300 else null,
                sets = if (index in listOf(0, 6, 7)) emptyList() else List(3) { SetTarget(if (index == 4) "27.5" else "25", 10) }) }
        }
        val workouts = mutableListOf<WorkoutEntity>(); val items = mutableListOf<SessionItem>(); val sets = mutableListOf<SessionSet>(); val checks = mutableListOf<CheckIn>(); val observations = mutableListOf<BodyObservation>()
        fun addWorkout(label: String, date: LocalDate, minutes: Int, load: String, full: Boolean = true) {
            val start = date.atTime(10, 0).atZone(clock.zone).toInstant(); val end = start.plusSeconds(minutes * 60L); val workoutId = id(label)
            workouts += WorkoutEntity(workoutId, "Full body A", sourceRoutineId = routineId, state = "COMPLETED", activeKey = null, journalDate = date.toString(), start = start.toString(), end = end.toString(), zoneId = zone,
                startOffset = start.atZone(clock.zone).offset.id, endOffset = end.atZone(clock.zone).offset.id, durationSeconds = minutes * 60L, durationSource = "MANUAL", timingConfirmed = true, createdAt = start.toString(), modifiedAt = end.toString())
            val indices = if (full) (0..6).toList() else listOf(0)
            indices.forEachIndexed { pos, index ->
                val exercise = exercises[index]; val itemId = id("$label:item:$pos")
                val quick = index in listOf(0, 6, 7)
                items += SessionItem(itemId, workoutId, exercise.id, exercise.equipmentId, exercise.name, exercise.equipmentName, exercise.category, exercise.basis,
                    setup = exercise.settings.joinToString(", ") { "${it.label} ${it.value}" }, position = pos, disposition = "DONE", quickCompletedAt = if (quick) end.toString() else null, durationSeconds = if (quick) 300 else null,
                    note = if (index == 4) "Felt good. Keep shoulders down." else "")
                if (!quick) repeat(3) { number -> sets += SessionSet(id("$label:set:$pos:$number"), itemId, number, rawLoad = if (index == 4) load else "25", rawReps = "10", load = if (index == 4) load else "25", reps = 10, completedAt = end.toString()) }
            }
            val after = CheckIn(id("$label:after"), workoutId, "AFTER", end.toString(), zone, "GOOD", 6)
            val before = CheckIn(id("$label:before"), workoutId, "BEFORE", start.toString(), zone, "OK")
            checks += listOf(before, after)
            observations += listOf(BodyObservation(before.id, "lower_back", 5, 7), BodyObservation(after.id, "lower_back", 3, 4))
        }
        if (state == "ProgressWithHistory") {
            val counts = listOf(1, 2, 2, 1, 3, 2, 2, 2); val loads = listOf("20", "20", "22.5", "22.5", "22.5", "25", "25", "27.5")
            val first = LocalDate.parse("2026-11-09").minusWeeks(7)
            counts.forEachIndexed { week, count -> repeat(count) { visit -> addWorkout("progress:$week:$visit", first.plusWeeks(week.toLong()).plusDays(if (visit == count - 1) 4 else visit.toLong()), 45, loads[week], full = visit == count - 1) } }
        } else {
            listOf(2, 4, 6, 9, 11).forEachIndexed { index, day -> addWorkout("november:$day", LocalDate.of(2026, 11, day), listOf(45, 63, 25, 45, 52)[index], listOf("20", "22.5", "22.5", "22.5", "25")[index]) }
        }
        if (state !in setOf("ProgressWithHistory", "HistoryNovember", "SparseHistory", "Empty")) {
            workouts += WorkoutEntity(activeId, "Full body A", sourceRoutineId = routineId, journalDate = "2026-11-13", start = "2026-11-13T08:42:00Z", zoneId = zone, startOffset = "+02:00", currentItemId = id("active:item:4"), createdAt = "2026-11-13T08:42:00Z", modifiedAt = now)
            exercises.take(8).forEachIndexed { index, exercise ->
                val itemId = id("active:item:$index"); val quick = index in listOf(0, 6, 7)
                items += SessionItem(itemId, activeId, exercise.id, exercise.equipmentId, exercise.name, exercise.equipmentName, exercise.category, exercise.basis,
                    setup = exercise.settings.joinToString(", ") { "${it.label} ${it.value}" }, position = index, disposition = if (index < 4) "DONE" else "PLANNED",
                    quickCompletedAt = if (index == 0) now else null, durationSeconds = if (index == 0) 300 else null, targetDurationSeconds = if (quick) 300 else null)
                if (!quick) repeat(3) { number ->
                    val completed = index < 4 || (index == 4 && number < 2); val load = if (index == 4) "27.5" else "25"; val reps = if (index == 4 && number == 1) 9 else 10
                    sets += SessionSet(id("active:set:$index:$number"), itemId, number, suggestedLoad = load, suggestedReps = 10, rawLoad = load, rawReps = reps.toString(), load = if (completed) load else null, reps = if (completed) reps else null, completedAt = if (completed) now else null)
                }
            }
            val before = CheckIn(id("active:before"), activeId, "BEFORE", "2026-11-13T08:40:00Z", zone, "OK")
            val after = CheckIn(id("active:after"), activeId, "AFTER", now, zone, "GOOD", 6)
            checks += listOf(before, after)
            observations += listOf(BodyObservation(before.id, "lower_back", 5, 7), BodyObservation(after.id, "lower_back", 3, 4), BodyObservation(after.id, "right_knee", 1, 3))
        }
        container.database.withTransaction {
            val dao = container.database.journal()
            dao.clearWorkouts(); dao.clearRoutines(); dao.clearPhotos(); dao.clearExercises(); dao.clearDrafts(); dao.clearHealthStates(); dao.clearOutbox()
            dao.put(if (state == "Empty") Preferences() else Preferences(name = "Emma", weeklyGoal = 3, remindersEnabled = state == "SettingsConfigured", reminderDays = listOf(1, 3, 5), reminderTime = "17:30", remindersHeld = state == "SettingsConfigured"))
            if (state != "Empty") {
                exercises.forEach { dao.put(it) }; routines.forEach { dao.put(it) }; routineItems.forEach { dao.put(it) }
                val selectedWorkouts = if (state == "SparseHistory") workouts.take(1) else workouts
                val selectedIds = selectedWorkouts.map { it.id }.toSet(); val selectedItems = items.filter { it.workoutId in selectedIds }; val itemIds = selectedItems.map { it.id }.toSet()
                selectedWorkouts.forEach { dao.insertWorkout(it) }; selectedItems.forEach { dao.put(it) }; sets.filter { it.itemId in itemIds }.forEach { dao.put(it) }
                val selectedChecks = checks.filter { it.workoutId in selectedIds }; val checkIds = selectedChecks.map { it.id }.toSet()
                selectedChecks.forEach { dao.put(it) }; observations.filter { it.checkInId in checkIds }.forEach { dao.put(it) }
            }
        }
    }
}

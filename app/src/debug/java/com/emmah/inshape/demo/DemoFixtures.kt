package com.emmah.inshape.demo

import android.content.Context
import androidx.room.withTransaction
import com.emmah.inshape.AppContainer
import com.emmah.inshape.R
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.SavedLink
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.Setting
import com.emmah.inshape.data.WorkoutEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object DemoFixtures {
    const val FULL_BODY = "Koko vartalo A"
    private const val PULLDOWN = "Ylätalja"
    private val durationExercises = setOf(0, 6, 7)

    val clock: Clock = Clock.fixed(
        Instant.parse("2026-11-13T09:15:00Z"),
        ZoneId.of("Europe/Helsinki")
    )
    fun id(label: String) = UUID.nameUUIDFromBytes("inshape-demo:$label".toByteArray()).toString()
    val activeId = id("active")
    val routineId = id("full-body")
    val pulldownId = id(PULLDOWN)
    val names =
        listOf(
            "TodayWithActiveWorkout",
            "ActiveWorkoutPartiallyDone",
            "FinishSummary",
            "ExerciseWithSetup",
            "CheckInAfter",
            "ProgressWithHistory",
            "HistoryNovember",
            "LibraryRoutineExpanded",
            "SettingsConfigured",
            "Empty",
            "SparseHistory",
            "FailedSave",
            "MusicHome",
            "MusicPlaylist",
            "WorkoutWithMusic",
            "TodayStrength",
            "TodayMobility",
            "TodayShort",
            "ProgressWithoutAttendance",
            "ProgramWorkout"
        )
    private val artworkRoutines = mapOf("TodayStrength" to routineId, "TodayMobility" to id("mobility"), "TodayShort" to id("short"))
    fun route(state: String): String = when (state) {
        "FinishSummary" -> "finish/$activeId"
        "ActiveWorkoutPartiallyDone", "WorkoutWithMusic" -> "workout/$activeId"
        "MusicHome" -> "Music"
        "MusicPlaylist" -> "music-playlist/${DemoMusic.NIGHT_DRIVE_ID}"
        "ExerciseWithSetup" -> "exercise/$pulldownId"
        "CheckInAfter" -> "checkin/$activeId/AFTER"
        "ProgressWithHistory" -> "History?view=progress&exercise=$pulldownId"
        "SparseHistory", "ProgressWithoutAttendance" -> "History?view=progress"
        "HistoryNovember" -> "History"
        "ProgramWorkout" -> "program-next/rauhallinen-aloitus"
        "LibraryRoutineExpanded" -> "routine/$routineId"
        "SettingsConfigured" -> "Settings"
        else -> "Today"
    }
    suspend fun load(container: AppContainer, state: String) {
        require(!container.integrationsEnabled && state in names)
        container.storageReady.await()

        val exercises = demoExercises(container.context)
        val routines = listOf(
            Routine(routineId, FULL_BODY, durationMinutes = 50),
            Routine(id("short"), "Lyhyt 20 min", "Väsyneille päiville", 20),
            Routine(id("mobility"), "Liikkuvuutta kotona", "Ilman välineitä", 15)
        )
        val routineItems = demoRoutineItems(routines, exercises)
        val activeRoutine = artworkRoutines[state]
        val workouts = demoWorkouts(state, exercises)
        writeFixtures(container, state, activeRoutine, exercises, routines, routineItems, workouts)
        if (state == "ProgramWorkout") {
            val catalog = container.programs
            val program = catalog.programs.first()
            val choices = program.sessions.first().exercises.associate { slot ->
                slot.exerciseId to exercises.firstOrNull { it.name == slot.name }?.id
            }
            container.repository.enrollProgram(catalog.schemaVersion, program, choices)
        }
    }

    private fun demoExercises(context: Context): List<Exercise> {
        // The old labels remain the keys used to generate existing exercise and equipment IDs.
        val keys = listOf(
            "Lämmittelypyörä",
            "Jalkaprässi",
            "Vaakasoutu laitteessa",
            "Rintapunnerruslaite",
            PULLDOWN,
            "Reiden koukistus istuen",
            "Lonkan koukistajan venytys",
            "Olkapään liikkuvuus",
            "Avustettu leuanveto",
            "Ylätalja kuminauhalla"
        )
        return keys.mapIndexed { index, key ->
            val exercise = Exercise(
                id = id(key),
                equipmentId = id("machine:$key"),
                name = when (key) {
                    "Lämmittelypyörä" -> context.getString(R.string.exercise_warm_up_bike)
                    "Rintapunnerruslaite" -> context.getString(R.string.exercise_chest_press)
                    "Ylätalja kuminauhalla" -> context.getString(R.string.exercise_band_pulldown)
                    else -> key
                },
                category = when (index) {
                    0 -> "Warm-up"
                    6, 7 -> "Mobility"
                    else -> "Machine"
                },
                basis = when (index) {
                    0, 6, 7 -> "duration"
                    8 -> "assistance_kg"
                    else -> "kg"
                },
                location = if (index == 9) "Koti" else "Sali"
            )
            if (index == 4) {
                exercise.copy(
                    equipmentName = PULLDOWN,
                    favorite = true,
                    muscles = "Selkä ja kädet",
                    settings = listOf(
                        Setting("Istuinkorkeus", "4"),
                        Setting("Reisituki", "2"),
                        Setting("Kahva", "Leveä tanko"),
                        Setting("Selkänoja", "Ei mitään")
                    ),
                    instructions = "Istu ryhdikkäästi. Vedä tanko ylärinnalle ja pidä kyynärpäät alaspäin. Palauta tanko hitaasti.",
                    alternatives = listOf(id(keys[8]), id(keys[9])),
                    links = listOf(SavedLink("Liikkeen tiedot", "https://www.nhs.uk/live-well/exercise/"))
                )
            } else {
                exercise
            }
        }
    }

    private fun demoRoutineItems(routines: List<Routine>, exercises: List<Exercise>): List<RoutineItem> =
        routines.flatMapIndexed { routineIndex, routine ->
            val indices = when (routineIndex) {
                0 -> (0..6).toList()
                1 -> listOf(0, 1, 2, 4)
                else -> listOf(6, 7, 6, 7, 6, 7)
            }
            indices.mapIndexed { position, index ->
                val quick = index in durationExercises
                val load = if (index == 4) "27.5" else "25"
                RoutineItem(
                    id("plan:$routineIndex:$position"),
                    routine.id,
                    exercises[index].id,
                    position,
                    mode = if (quick) "DURATION" else "SETS",
                    durationSeconds = if (quick) 300 else null,
                    sets = if (quick) emptyList() else List(3) { SetTarget(load, 10) }
                )
            }
        }

    private class WorkoutFixtures {
        val workouts = mutableListOf<WorkoutEntity>()
        val items = mutableListOf<SessionItem>()
        val sets = mutableListOf<SessionSet>()
        val checks = mutableListOf<CheckIn>()
        val observations = mutableListOf<BodyObservation>()
    }

    private fun demoWorkouts(state: String, exercises: List<Exercise>): WorkoutFixtures {
        val fixtures = WorkoutFixtures()
        if (state == "ProgressWithHistory") {
            val counts = listOf(1, 2, 2, 1, 3, 2, 2, 2)
            val loads = listOf("20", "20", "22.5", "22.5", "22.5", "25", "25", "27.5")
            val first = LocalDate.parse("2026-11-09").minusWeeks(7)
            counts.forEachIndexed { week, count ->
                repeat(count) { visit ->
                    val full = visit == count - 1
                    fixtures.addCompletedWorkout(
                        exercises,
                        "progress:$week:$visit",
                        first.plusWeeks(week.toLong()).plusDays(if (full) 4L else visit.toLong()),
                        45,
                        loads[week],
                        full = full
                    )
                }
            }
        } else {
            listOf(2, 4, 6, 9, 11).forEachIndexed { index, day ->
                fixtures.addCompletedWorkout(
                    exercises,
                    "november:$day",
                    LocalDate.of(2026, 11, day),
                    listOf(45, 63, 25, 45, 52)[index],
                    listOf("20", "22.5", "22.5", "22.5", "25")[index]
                )
            }
        }
        if (state !in artworkRoutines &&
            state !in setOf("ProgressWithHistory", "HistoryNovember", "SparseHistory", "Empty", "ProgressWithoutAttendance")
        ) {
            fixtures.addActiveWorkout(exercises, state == "CheckInAfter")
        }
        return fixtures
    }

    private fun WorkoutFixtures.addCompletedWorkout(
        exercises: List<Exercise>,
        label: String,
        date: LocalDate,
        minutes: Int,
        pulldownLoad: String,
        full: Boolean = true
    ) {
        val zone = clock.zone
        val start = date.atTime(10, 0).atZone(zone).toInstant()
        val end = start.plusSeconds(minutes * 60L)
        val workoutId = id(label)
        workouts += WorkoutEntity(
            workoutId,
            FULL_BODY,
            sourceRoutineId = routineId,
            state = "COMPLETED",
            activeKey = null,
            journalDate = date.toString(),
            start = start.toString(),
            end = end.toString(),
            zoneId = zone.id,
            startOffset = start.atZone(zone).offset.id,
            endOffset = end.atZone(zone).offset.id,
            durationSeconds = minutes * 60L,
            durationSource = "MANUAL",
            timingConfirmed = true,
            createdAt = start.toString(),
            modifiedAt = end.toString()
        )
        val indices = if (full) 0..6 else 0..0
        indices.forEachIndexed { position, index ->
            val itemId = id("$label:item:$position")
            val quick = index in durationExercises
            items += demoItem(exercises[index], itemId, workoutId, position).copy(
                disposition = "DONE",
                quickCompletedAt = if (quick) end.toString() else null,
                durationSeconds = if (quick) 300 else null,
                note = if (index == 4) "Tuntui hyvältä. Pidä hartiat alhaalla." else ""
            )
            if (!quick) {
                val load = if (index == 4) pulldownLoad else "25"
                repeat(3) { number ->
                    sets += SessionSet(
                        id("$label:set:$position:$number"),
                        itemId,
                        number,
                        rawLoad = load,
                        rawReps = "10",
                        load = load,
                        reps = 10,
                        completedAt = end.toString()
                    )
                }
            }
        }
        val after = CheckIn(id("$label:after"), workoutId, "AFTER", end.toString(), zone.id, "GOOD", 6)
        val before = CheckIn(id("$label:before"), workoutId, "BEFORE", start.toString(), zone.id, "OK")
        checks += listOf(before, after)
        observations += listOf(
            BodyObservation(before.id, "lower_back", 5, 7),
            BodyObservation(after.id, "lower_back", 3, 4)
        )
    }

    private fun WorkoutFixtures.addActiveWorkout(exercises: List<Exercise>, withAfter: Boolean) {
        val now = clock.instant().toString()
        val zone = clock.zone.id
        workouts += WorkoutEntity(
            activeId,
            FULL_BODY,
            sourceRoutineId = routineId,
            journalDate = "2026-11-13",
            start = clock.instant().minusSeconds(32 * 60 + 15).toString(),
            zoneId = zone,
            startOffset = "+02:00",
            currentItemId = id("active:item:4"),
            createdAt = "2026-11-13T08:42:00Z",
            modifiedAt = now
        )
        exercises.take(8).forEachIndexed { index, exercise ->
            val itemId = id("active:item:$index")
            val quick = index in durationExercises
            items += demoItem(exercise, itemId, activeId, index).copy(
                disposition = if (index < 4) "DONE" else "PLANNED",
                quickCompletedAt = if (index == 0) now else null,
                durationSeconds = if (index == 0) 300 else null,
                targetDurationSeconds = if (quick) 300 else null
            )
            if (!quick) sets += activeSets(index, itemId, now)
        }
        val before = CheckIn(id("active:before"), activeId, "BEFORE", "2026-11-13T08:40:00Z", zone, "OK")
        val after = CheckIn(id("active:after"), activeId, "AFTER", now, zone, "GOOD", 6)
        checks += before
        observations += BodyObservation(before.id, "lower_back", 5, 7)
        if (withAfter) {
            checks += after
            observations += listOf(
                BodyObservation(after.id, "lower_back", 3, 4),
                BodyObservation(after.id, "right_knee", 1, 3)
            )
        }
    }

    private fun activeSets(index: Int, itemId: String, now: String): List<SessionSet> = List(3) { number ->
        val completed = index < 4 || (index == 4 && number < 2)
        val load = if (index == 4) "27.5" else "25"
        val reps = if (index == 4 && number == 1) 9 else 10
        SessionSet(
            id("active:set:$index:$number"),
            itemId,
            number,
            suggestedLoad = load,
            suggestedReps = 10,
            rawLoad = load,
            rawReps = reps.toString(),
            load = if (completed) load else null,
            reps = if (completed) reps else null,
            completedAt = if (completed) now else null
        )
    }

    private suspend fun writeFixtures(
        container: AppContainer,
        state: String,
        selectedRoutine: String?,
        exercises: List<Exercise>,
        routines: List<Routine>,
        routineItems: List<RoutineItem>,
        fixtures: WorkoutFixtures
    ) {
        container.database.withTransaction {
            val dao = container.database.journal()
            dao.clearWorkouts()
            dao.clearProgramEnrollment()
            dao.clearProgramLinks()
            dao.clearProgramDayOverrides()
            dao.clearRoutines()
            dao.clearPhotos()
            dao.clearExercises()
            dao.clearDrafts()
            dao.clearHealthStates()
            dao.clearOutbox()
            val configured = state == "SettingsConfigured"
            dao.put(
                if (state == "Empty") {
                    Preferences()
                } else {
                    Preferences(
                        name = "Emma",
                        weeklyGoal = 3,
                        remindersEnabled = configured,
                        reminderDays = listOf(1, 3, 5),
                        reminderTime = "17:30",
                        remindersHeld = configured
                    )
                }
            )
            if (state == "Empty") return@withTransaction

            exercises.forEach { dao.put(it) }
            routines.filter { selectedRoutine == null || it.id == selectedRoutine }.forEach { dao.put(it) }
            routineItems.filter { selectedRoutine == null || it.routineId == selectedRoutine }.forEach { dao.put(it) }
            val workouts = when {
                state == "SparseHistory" -> fixtures.workouts.take(1)
                state == "ProgressWithoutAttendance" || selectedRoutine != null -> emptyList()
                else -> fixtures.workouts
            }
            val workoutIds = workouts.map { it.id }.toSet()
            val items = fixtures.items.filter { it.workoutId in workoutIds }
            val itemIds = items.map { it.id }.toSet()
            workouts.forEach { dao.insertWorkout(it) }
            items.forEach { dao.put(it) }
            fixtures.sets.filter { it.itemId in itemIds }.forEach { dao.put(it) }
            val checks = fixtures.checks.filter { it.workoutId in workoutIds }
            val checkIds = checks.map { it.id }.toSet()
            checks.forEach { dao.put(it) }
            fixtures.observations.filter { it.checkInId in checkIds }.forEach { dao.put(it) }
        }
    }
}

private fun demoItem(exercise: Exercise, itemId: String, workoutId: String, position: Int) = SessionItem(
    itemId,
    workoutId,
    exercise.id,
    exercise.equipmentId,
    exercise.name,
    exercise.equipmentName,
    exercise.category,
    exercise.basis,
    setup = exercise.settings.joinToString(", ") { "${it.label} ${it.value}" },
    position = position
)

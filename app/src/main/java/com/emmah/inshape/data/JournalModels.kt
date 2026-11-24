package com.emmah.inshape.data

import com.emmah.inshape.feature.program.ProgramVisit
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class JournalSnapshot(
    val exercises: List<Exercise> = emptyList(),
    val photos: List<PhotoAsset> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val routineItems: List<RoutineItem> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val items: List<SessionItem> = emptyList(),
    val sets: List<SessionSet> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val observations: List<BodyObservation> = emptyList(),
    val preferences: Preferences = Preferences(),
    val drafts: List<EditorDraft> = emptyList(),
    val healthStates: List<HealthShareState> = emptyList(),
    val outbox: List<HealthOutbox> = emptyList(),
    val programEnrollment: ProgramEnrollment? = null,
    val programLinks: List<ProgramExerciseLink> = emptyList(),
    val assistantMemories: List<AssistantMemory> = emptyList(),
    val programDayOverrides: List<ProgramDayOverride> = emptyList()
) {
    private fun hydrate(rows: List<WorkoutEntity>): List<Workout> {
        if (rows.isEmpty()) return emptyList()
        val workoutIds = rows.asSequence().map { it.id }.toSet()
        val workoutItems = items.filter { it.workoutId in workoutIds }
        val itemIds = workoutItems.asSequence().map { it.id }.toSet()
        val itemsByWorkout = workoutItems.groupBy { it.workoutId }
        val setsByItem = sets.asSequence().filter { it.itemId in itemIds }.groupBy { it.itemId }
        val checkInsByWorkout = checkIns.asSequence().filter { it.workoutId in workoutIds }.groupBy { it.workoutId }
        return rows.map { row ->
            Workout(
                row,
                itemsByWorkout[row.id].orEmpty().sortedBy { it.position }.map { item ->
                    WorkoutItem(item, setsByItem[item.id].orEmpty().sortedBy { it.position })
                },
                checkInsByWorkout[row.id].orEmpty()
            )
        }
    }

    fun workout(id: String): Workout? = workouts.find { it.id == id }?.let { hydrate(listOf(it)).single() }
    val active: Workout? get() = workouts.find { it.activeKey == 1 }?.let { hydrate(listOf(it)).single() }
    val history: List<Workout> get() = historyIn(null)

    fun historyIn(range: DateRange?): List<Workout> = hydrate(
        workouts.filter { it.state == "COMPLETED" && (range == null || it.journalDate in range) }
    )
}

data class Workout(val row: WorkoutEntity, val items: List<WorkoutItem>, val checkIns: List<CheckIn>) {
    internal val routinePlanItems: List<WorkoutItem>
        get() {
            val substituted = items.mapNotNull { it.row.alternativeOf }.toSet()
            return items.filterNot { it.row.id in substituted }
        }
    val hasPerformedWork: Boolean get() = items.any { it.hasPerformedWork }
    val qualifiesForAttendance: Boolean get() = row.state == "COMPLETED" && hasPerformedWork
    val isPastEntry: Boolean get() = row.kind == "PAST"
    val doneCount: Int get() = items.count { it.row.disposition == "DONE" }
    val performedCount: Int get() = items.count { it.hasPerformedWork }
    internal val pendingItems: List<WorkoutItem>
        get() = if (row.state == "COMPLETED") emptyList() else items.filter { it.row.isPending }

    internal fun currentItem(pending: List<WorkoutItem> = pendingItems): WorkoutItem? =
        pending.find { it.row.id == row.currentItemId } ?: pending.firstOrNull()
}

// An incomplete item in an open workout was deferred and can still be performed.
internal val SessionItem.isPending: Boolean
    get() = disposition in setOf("PLANNED", "IN_PROGRESS", "INCOMPLETE")

data class WorkoutItem(val row: SessionItem, val sets: List<SessionSet>) {
    val completedSets: List<SessionSet> get() = sets.filter { it.completedAt != null }
    val hasPerformedWork: Boolean get() = sets.any { it.completedAt != null } || row.quickCompletedAt != null

    internal fun routineTarget(routineId: String, position: Int) = RoutineItem(
        id = "",
        routineId = routineId,
        exerciseId = row.exerciseId,
        position = position,
        mode = row.mode,
        durationSeconds = row.targetDurationSeconds,
        note = row.note,
        sets = sets.map {
            SetTarget(it.load ?: it.suggestedLoad, if (it.plannedReps == null) it.reps ?: it.suggestedReps else null, it.plannedReps)
        },
        plan = row.plan
    )
}
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: String): Boolean = LocalDate.parse(date) in start..end
}
sealed interface StartSource {
    data object Empty : StartSource
    data class Routine(val id: String) : StartSource
    data class Repeat(val id: String) : StartSource
    data class Program(val visit: ProgramVisit) : StartSource
}
data class WorkoutTiming(
    val start: String? = null,
    val end: String? = null,
    val durationSeconds: Long? = null,
    val acknowledged: Boolean = false,
    val journalDate: String? = null,
    val clearTimes: Boolean = false,
    val inferDuration: Boolean = true
)
data class ExerciseQuery(
    val text: String = "",
    val category: String? = null,
    val location: String? = null,
    val favoritesOnly: Boolean = false
)

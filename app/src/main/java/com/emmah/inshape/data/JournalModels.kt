package com.emmah.inshape.data

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
    val outbox: List<HealthOutbox> = emptyList()
) {
    fun workout(id: String): Workout? = workouts.find { it.id == id }?.let { row ->
        Workout(
            row,
            items.filter { it.workoutId == id }.sortedBy { it.position }.map { item ->
                WorkoutItem(
                    item,
                    sets.filter {
                        it.itemId ==
                            item.id
                    }.sortedBy { it.position }
                )
            },
            checkIns.filter { it.workoutId == id }
        )
    }
    val active: Workout? get() = workouts.find { it.activeKey == 1 }?.let { workout(it.id) }
    val history: List<Workout> get() = workouts.filter {
        it.state == "COMPLETED"
    }.mapNotNull { workout(it.id) }
}

data class Workout(val row: WorkoutEntity, val items: List<WorkoutItem>, val checkIns: List<CheckIn>) {
    val hasPerformedWork: Boolean get() = items.any { it.hasPerformedWork }
    val qualifiesForAttendance: Boolean get() = row.state == "COMPLETED" && hasPerformedWork
    val isPastEntry: Boolean get() = row.kind == "PAST"
    val doneCount: Int get() = items.count { it.row.disposition == "DONE" }
    val performedCount: Int get() = items.count { it.hasPerformedWork }
}
data class WorkoutItem(val row: SessionItem, val sets: List<SessionSet>) {
    val completedSets: List<SessionSet> get() = sets.filter { it.completedAt != null }
    val hasPerformedWork: Boolean get() = completedSets.isNotEmpty() || row.quickCompletedAt != null
}
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: String): Boolean = LocalDate.parse(date) in start..end
}
sealed interface StartSource {
    data object Empty : StartSource
    data class Routine(val id: String) : StartSource
    data class Repeat(val id: String) : StartSource
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

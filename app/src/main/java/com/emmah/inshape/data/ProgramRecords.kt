package com.emmah.inshape.data

import com.emmah.inshape.R
import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
@Entity
data class ProgramEnrollment(
    @PrimaryKey val slot: Int = 1,
    val id: String = newId(),
    val programId: String,
    val catalogVersion: Int,
    val baselineDate: String,
    val phaseWeek: Int = 1,
    val nextDayIndex: Int = 0
)

@Serializable
@Entity(primaryKeys = ["catalogExerciseId", "exerciseId"])
data class ProgramExerciseLink(val catalogExerciseId: String, val exerciseId: String)

@Serializable
data class ProgramWorkoutSource(
    val enrollmentId: String,
    val programId: String,
    val catalogVersion: Int,
    val day: String,
    val dayIndex: Int,
    val rotationSize: Int,
    val mode: String
)

@Serializable
data class ExercisePlan(
    val restSeconds: Int? = null,
    val catalogExerciseId: String? = null,
    val originalCatalogExerciseId: String? = null,
    val slotKey: String? = null,
    val optional: Boolean = false,
    val warmup: Boolean = false,
    val group: PlanGroup? = null
)

@Serializable
data class PlanGroup(val key: String, val type: String, val rounds: Int)

@Serializable
data class PlannedReps(val min: Int, val max: Int, val perSide: Boolean = false) {
    init { require(min > 0 && max >= min) { "Invalid rep range" } }

    fun localizedDisplay(context: Context): String = context.getString(
        if (perSide) R.string.rep_target_per_side else R.string.rep_value,
        if (min == max) min.toString() else "$min–$max"
    )
}

class ProgramConverters {
    @TypeConverter fun encodeReps(value: PlannedReps?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter fun decodeReps(value: String?): PlannedReps? = value?.let { Json.decodeFromString<PlannedReps>(it) }

    @TypeConverter fun encodePlan(value: ExercisePlan?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter fun decodePlan(value: String?): ExercisePlan? = value?.let { Json.decodeFromString<ExercisePlan>(it) }

    @TypeConverter fun encodeProgram(value: ProgramWorkoutSource?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter fun decodeProgram(value: String?): ProgramWorkoutSource? =
        value?.let { Json.decodeFromString<ProgramWorkoutSource>(it) }
}

internal fun validateRoutineGroups(items: List<RoutineItem>) {
    val groups = items.withIndex().filter { it.value.plan?.group != null }.groupBy { it.value.plan!!.group!!.key }
    groups.values.forEach { members ->
        val group = members.first().value.plan!!.group!!
        require(group.key.isNotBlank() && group.type.isNotBlank() && group.rounds > 0 && members.size >= 2) {
            "A group needs at least two exercises and a positive round count."
        }
        require(members.all { it.value.plan?.group == group } && members.last().index - members.first().index + 1 == members.size) {
            "Keep grouped exercises together and give them the same round count."
        }
    }
}

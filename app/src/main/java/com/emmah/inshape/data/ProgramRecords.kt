package com.emmah.inshape.data

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.emmah.inshape.R
import com.emmah.inshape.feature.program.MAX_PROGRAM_SETS
import com.emmah.inshape.feature.program.ProgramDay
import com.emmah.inshape.feature.program.ProgramReps
import com.emmah.inshape.feature.program.ProgramSlot
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
@Entity(primaryKeys = ["catalogVersion", "programId", "dayId"])
data class ProgramDayOverride(val catalogVersion: Int, val programId: String, val dayId: String, val day: ProgramDay)

private val programKey = Regex("[a-zA-Z0-9_-]{1,128}")

internal fun ProgramDayOverride.validate(exerciseIds: Set<String>) {
    require(catalogVersion > 0 && programId.matches(programKey) && dayId.matches(programKey))
    require(day.id == dayId && day.name.isNotBlank() && day.exercises.isNotEmpty())
    val slots = day.exercises.map { it.exerciseId }.toSet()
    require(slots.size == day.exercises.size)
    require(day.miniExerciseIds.isNotEmpty() && day.miniExerciseIds.all { it in slots })
    require(day.miniExerciseIds.distinct().size == day.miniExerciseIds.size)
    day.exercises.forEach { it.validate(exerciseIds) }
}

internal fun ProgramSlot.validate(exerciseIds: Set<String>) {
    fun validTarget(reps: ProgramReps) = reps.min > 0 && reps.max >= reps.min

    require(
        validProgramExerciseId(exerciseId, exerciseIds) && name.isNotBlank() &&
            sets in 1..MAX_PROGRAM_SETS && restSeconds > 0 && validTarget(target)
    )
    require(alternatives.distinctBy { it.exerciseId }.size == alternatives.size)
    alternatives.forEach {
        require(validProgramExerciseId(it.exerciseId, exerciseIds) && it.exerciseId != exerciseId && it.name.isNotBlank())
        require((it.sets == null || it.sets in 1..MAX_PROGRAM_SETS) && (it.target == null || validTarget(it.target)))
    }
}

private fun validProgramExerciseId(id: String, exerciseIds: Set<String>): Boolean =
    id.matches(programKey) && (!id.startsWith("local_") || id.removePrefix("local_") in exerciseIds)

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
    val group: PlanGroup? = null,
    val sourceSlot: ProgramSlot? = null
)

@Serializable
data class PlanGroup(val key: String, val type: String, val rounds: Int)

@Serializable
data class PlannedReps(val min: Int, val max: Int, val perSide: Boolean = false) {
    init {
        require(min > 0 && max >= min) { "Invalid rep range" }
    }

    fun localizedDisplay(context: Context): String = context.getString(
        if (perSide) R.string.rep_target_per_side else R.string.rep_value,
        if (min == max) min.toString() else "$min–$max"
    )
}

class ProgramConverters {
    @TypeConverter fun encodeDay(value: ProgramDay): String = Json.encodeToString(value)

    @TypeConverter fun decodeDay(value: String): ProgramDay = Json.decodeFromString<ProgramDay>(value)

    @TypeConverter fun encodeReps(value: PlannedReps?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter fun decodeReps(value: String?): PlannedReps? = value?.let { Json.decodeFromString<PlannedReps>(it) }

    @TypeConverter fun encodePlan(value: ExercisePlan?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter fun decodePlan(value: String?): ExercisePlan? = value?.let { Json.decodeFromString<ExercisePlan>(it) }

    @TypeConverter fun encodeProgram(value: ProgramWorkoutSource?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter fun decodeProgram(value: String?): ProgramWorkoutSource? = value?.let { Json.decodeFromString<ProgramWorkoutSource>(it) }
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

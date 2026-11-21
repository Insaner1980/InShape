package com.emmah.inshape.feature.program

import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.ProgramEnrollment

enum class ProgramVisitMode { STANDARD, SHORT, LIGHT }

internal fun ProgramDay.slotsFor(mode: ProgramVisitMode): List<ProgramSlot> =
    exercises.filter { mode != ProgramVisitMode.SHORT || it.exerciseId in miniExerciseIds }

data class ProgramVisit(
    val enrollment: ProgramEnrollment,
    val day: ProgramDay,
    val mode: ProgramVisitMode,
    val shortSets: Int = 1,
    val choices: Map<String, String?>,
    val sequence: List<String>
) {
    val slots: List<ProgramSlot>
        get() = day.slotsFor(mode)

    fun setCount(normalCount: Int) = when (mode) {
        ProgramVisitMode.STANDARD -> normalCount
        ProgramVisitMode.SHORT -> shortSets.also { require(it in 1..2) }
        ProgramVisitMode.LIGHT -> (normalCount - 1).coerceAtLeast(1)
    }
}

internal fun newProgramExercise(slot: ProgramSlot) = Exercise(
    name = slot.name,
    category = when (slot.equipment) {
        "talja" -> "Cable"
        "laite" -> "Machine"
        "kehonpaino", "matto" -> "Bodyweight"
        else -> "Free weights"
    },
    basis = if (slot.equipment in setOf("kehonpaino", "matto")) "bodyweight" else "kg",
    instructions = slot.instruction
)

internal fun newAlternativeExercise(alternative: ProgramAlternative): Exercise {
    val bodyweight = alternative.exerciseId in setOf("dead_bug", "supported_bench_squat", "wall_push_up", "floor_glute_bridge")
    val category = when {
        bodyweight -> "Bodyweight"
        alternative.exerciseId.startsWith("dumbbell_") || alternative.exerciseId.contains("_dumbbell_") ||
            alternative.exerciseId == "bench_goblet_squat" -> "Free weights"
        alternative.exerciseId == "low_cable_row" -> "Cable"
        else -> "Machine"
    }
    return Exercise(name = alternative.name, category = category, basis = if (bodyweight) "bodyweight" else "kg")
}

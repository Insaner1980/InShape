package com.emmah.inshape.feature.program

import com.emmah.inshape.data.Exercise

internal const val MAX_PROGRAM_SETS = 100

internal val ProgramSlot.localExerciseId: String?
    get() = exerciseId.takeIf { it.startsWith("local_") }?.removePrefix("local_")

internal fun personalProgramSlot(exercise: Exercise, sets: Int, reps: ProgramReps): ProgramSlot {
    require(exercise.name.isNotBlank() && sets in 1..MAX_PROGRAM_SETS && reps.min > 0 && reps.max >= reps.min)
    return ProgramSlot(
        exerciseId = "local_${exercise.id}",
        name = exercise.name.trim(),
        equipment = when (exercise.category) {
            "Cable" -> "talja"
            "Machine" -> "laite"
            "Bodyweight" -> "kehonpaino"
            else -> exercise.equipmentName
        },
        sets = sets,
        target = reps,
        restSeconds = 90,
        optional = false,
        primary = false,
        alternatives = emptyList(),
        instruction = exercise.instructions
    )
}

internal fun ProgramDay.withExercises(slots: List<ProgramSlot>): ProgramDay {
    val oldIds = exercises.map { it.exerciseId }.toSet()
    val ids = slots.map { it.exerciseId }
    val mini = ids.filter { it in miniExerciseIds || it !in oldIds }
    return copy(exercises = slots, miniExerciseIds = mini.ifEmpty { ids.take(1) })
}

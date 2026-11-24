package com.emmah.inshape.feature.program

import com.emmah.inshape.data.ProgramDayOverride
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProgramCatalog(
    val schemaVersion: Int,
    val title: String,
    val notice: String,
    val warmup: ProgramWarmup,
    val coolDown: ProgramCoolDown,
    val effort: String,
    val progression: ProgramProgression,
    val scheduling: ProgramScheduling,
    val safety: String,
    val sources: List<ProgramReference>,
    val programs: List<TrainingProgram>
) {
    fun program(id: String): TrainingProgram? = programs.find { it.id == id }

    fun personalProgram(programId: String, overrides: List<ProgramDayOverride>): TrainingProgram? {
        val base = program(programId) ?: return null
        val days = overrides.filter { it.catalogVersion == schemaVersion && it.programId == programId }.associateBy { it.dayId }
        return if (days.isEmpty()) base else base.copy(sessions = base.sessions.map { days[it.id]?.day ?: it })
    }

    fun session(programId: String, dayKey: String): ProgramDay? = program(programId)?.sessions?.find { it.id == dayKey }

    fun guideName(key: String): String? {
        val slots = programs.asSequence().flatMap { it.sessions }.flatMap { it.exercises }
        return slots.firstOrNull { it.exerciseId == key }?.name
            ?: slots.flatMap { it.alternatives }.firstOrNull { it.exerciseId == key }?.name
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun read(source: String): ProgramCatalog = json
            .decodeFromString<ProgramCatalog>(source)
            .also { require(it.schemaVersion == 1) }
    }
}

@Serializable
data class TrainingProgram(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val recommendedWeeklySessions: Int,
    val allowedWeeklySessions: List<Int>,
    val estimatedSessionMinutes: ProgramMinutes,
    val durationWeeks: Int,
    val sequence: List<String>,
    val sessions: List<ProgramDay>,
    val phases: List<ProgramPhase>
)

@Serializable data class ProgramMinutes(val min: Int, val max: Int)

@Serializable data class ProgramPhase(val weeks: List<Int>, val sets: String, val effort: String, val goal: String)

@Serializable data class ProgramDay(val id: String, val name: String, val miniExerciseIds: List<String>, val exercises: List<ProgramSlot>)

@Serializable
data class ProgramSlot(
    val exerciseId: String,
    val name: String,
    val equipment: String,
    val sets: Int,
    val target: ProgramReps,
    val restSeconds: Int,
    val optional: Boolean,
    val primary: Boolean,
    val alternatives: List<ProgramAlternative>,
    val instruction: String
) {
    internal fun matchesExerciseName(savedName: String): Boolean {
        val formerName = when (exerciseId) {
            "machine_chest_press" -> "Rintapunnerruslaite"
            "machine_crunch" -> "Vatsarutistuslaite"
            "machine_shoulder_press" -> "Pystypunnerruslaite"
            else -> null
        }
        return savedName.equals(name, ignoreCase = true) || savedName.equals(formerName, ignoreCase = true)
    }
}

@Serializable
data class ProgramReps(val min: Int, val max: Int, val perSide: Boolean) {
    val range: String get() = if (min == max) min.toString() else "$min–$max"
}

@Serializable
data class ProgramAlternative(
    val exerciseId: String,
    val name: String,
    val label: String,
    val sets: Int? = null,
    val target: ProgramReps? = null
)

@Serializable
data class ProgramWarmup(val minutes: Int, val to: Int, val suggestions: List<String>, val optionalMobilityMinutes: Int, val note: String)

@Serializable data class ProgramCoolDown(val minutes: Int, val to: Int, val optional: Boolean, val note: String)

@Serializable
data class ProgramProgression(val whenToSuggestMoreLoad: String, val suggestion: String, val ifNotReady: String, val deload: String)

@Serializable
data class ProgramScheduling(val restDaysBetweenStrengthSessionsMinimum: Int, val missedWorkout: String, val switchPlan: String)

@Serializable data class ProgramReference(val name: String, val url: String? = null, val note: String? = null)

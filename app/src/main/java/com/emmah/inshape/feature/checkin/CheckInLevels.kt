package com.emmah.inshape.feature.checkin

import com.emmah.inshape.data.BodyObservation

enum class EffortLevel(val label: String, val stored: Int) { Easy("Easy", 3), JustRight("Just right", 6), Hard("Hard", 8) }
fun effortLevelOf(value: Int?): EffortLevel? = when (value) {
    null -> null
    in 0..4 -> EffortLevel.Easy
    in 5..7 -> EffortLevel.JustRight
    else -> EffortLevel.Hard
}

enum class BodyLevel(val label: String, val stored: Int) { None("None", 0), Mild("Mild", 2), Medium("Medium", 5), Strong("Strong", 8) }
fun bodyLevelOf(value: Int?): BodyLevel? = when (value) {
    null -> null
    0 -> BodyLevel.None
    in 1..3 -> BodyLevel.Mild
    in 4..6 -> BodyLevel.Medium
    else -> BodyLevel.Strong
}

fun bodyLevelSummary(observation: BodyObservation): String = listOfNotNull(
    observation.pain?.let { "Pain, ${bodyLevelOf(it)!!.label.lowercase()}" },
    observation.stiffness?.let { "Stiffness, ${bodyLevelOf(it)!!.label.lowercase()}" }
).joinToString(", ").ifBlank { "Not recorded" }

package com.emmah.inshape.feature.checkin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.Pill

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

@Composable
fun BodyLevelPills(pain: Int?, stiffness: Int?, modifier: Modifier = Modifier) {
    ActionRow(modifier) {
        bodyLevelOf(pain)?.let { Pill("Pain ${it.label.lowercase()}", outlined = true) }
        bodyLevelOf(stiffness)?.let { Pill("Stiffness ${it.label.lowercase()}", outlined = true) }
        if (pain == null && stiffness == null) Text("Not recorded", style = MaterialTheme.typography.bodyMedium)
    }
}

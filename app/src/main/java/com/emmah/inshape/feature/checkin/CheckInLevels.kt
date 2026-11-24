package com.emmah.inshape.feature.checkin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.Pill

enum class EffortLevel(val label: Int, val stored: Int) {
    Easy(
        R.string.effort_easy,
        3
    ),
    JustRight(R.string.effort_right, 6),
    Hard(R.string.effort_hard, 8)
}
fun effortLevelOf(value: Int?): EffortLevel? = when (value) {
    null -> null
    in 0..4 -> EffortLevel.Easy
    in 5..7 -> EffortLevel.JustRight
    else -> EffortLevel.Hard
}

enum class EnergyLevel(val key: String, val label: Int) {
    Low("LOW", R.string.energy_low),
    Ok("OK", R.string.energy_ok),
    Good("GOOD", R.string.energy_good);

    companion object {
        fun fromKey(key: String?): EnergyLevel? = entries.find { it.key == key }
    }
}

enum class BodyLevel(val label: Int, val stored: Int) {
    None(
        R.string.body_level_none,
        0
    ),
    Mild(R.string.body_level_mild, 2),
    Medium(R.string.body_level_medium, 5),
    Strong(R.string.body_level_strong, 8)
}
fun bodyLevelOf(value: Int?): BodyLevel? = when (value) {
    null -> null
    0 -> BodyLevel.None
    in 1..3 -> BodyLevel.Mild
    in 4..6 -> BodyLevel.Medium
    else -> BodyLevel.Strong
}

@Composable
fun BodyLevelPills(pain: Int?, stiffness: Int?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ActionRow(modifier) {
        com.emmah.inshape.ui.theme.painText(context, pain)?.let { Pill(it) }
        com.emmah.inshape.ui.theme.stiffnessText(context, stiffness)?.let { Pill(it) }
        if (pain == null && stiffness == null) Text(stringResource(R.string.not_recorded), style = MaterialTheme.typography.bodyMedium)
    }
}

package com.emmah.inshape.ui.theme

import com.emmah.inshape.R
import android.content.Context
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.feature.checkin.BodyLevel
import com.emmah.inshape.feature.checkin.EnergyLevel
import com.emmah.inshape.feature.checkin.bodyLevelOf
import com.emmah.inshape.feature.checkin.bodyRegionLabel

fun painText(context: Context, value: Int?): String? = symptomText(context, R.string.pain, R.string.no_pain, value)

fun stiffnessText(context: Context, value: Int?): String? = symptomText(context, R.string.stiffness, R.string.no_stiffness, value)

private fun symptomText(context: Context, symptom: Int, none: Int, value: Int?): String? = when (val level = bodyLevelOf(value)) {
    null -> null
    BodyLevel.None -> context.getString(none)
    else -> context.getString(R.string.symptom_status, context.getString(symptom), context.getString(level.label).lowercase(finnishLocale))
}

fun energyText(context: Context, key: String?): String? = EnergyLevel.fromKey(key)?.let {
    context.getString(R.string.energy_status, context.getString(it.label).lowercase(finnishLocale))
}

fun observationSummary(context: Context, observation: BodyObservation): String {
    val symptoms = listOfNotNull(
        painText(context, observation.pain).takeIf { (observation.pain ?: 0) > 0 },
        stiffnessText(context, observation.stiffness).takeIf { (observation.stiffness ?: 0) > 0 }
    )
    if (symptoms.isNotEmpty()) return symptoms.joinToString(", ")
    return context.getString(when {
        observation.pain != null && observation.stiffness != null -> R.string.no_pain_or_stiffness
        observation.pain != null -> R.string.no_pain
        observation.stiffness != null -> R.string.no_stiffness
        else -> R.string.not_recorded
    })
}

fun observationText(context: Context, observation: BodyObservation): String =
    context.getString(R.string.body_area_metric, context.getString(bodyRegionLabel(observation.region)), observationSummary(context, observation))

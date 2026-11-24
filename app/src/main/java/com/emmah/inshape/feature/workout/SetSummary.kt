package com.emmah.inshape.feature.workout

import android.content.Context
import com.emmah.inshape.R
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.ui.theme.finnishLocale
import com.emmah.inshape.ui.theme.formatDuration

fun itemSummary(context: Context, item: WorkoutItem): String = when {
    item.completedSets.isNotEmpty() -> setSummary(
        context,
        MeasurementBasis.fromKey(item.row.basis),
        item.completedSets.map { SetTarget(it.load, it.reps) }
    ).takeUnless { it == context.getString(R.string.no_targets) } ?: context.getString(R.string.done)

    item.row.durationSeconds != null -> formatDuration(item.row.durationSeconds)

    item.hasPerformedWork -> context.getString(R.string.done)

    else -> context.getString(R.string.not_completed)
}

internal fun plannedItemSummary(context: Context, item: WorkoutItem): String = item.row.targetDurationSeconds?.let(::formatDuration)
    ?: setSummary(context, MeasurementBasis.fromKey(item.row.basis), item.sets.map { SetTarget(it.suggestedLoad, it.suggestedReps) })

internal fun setSummary(context: Context, basis: MeasurementBasis, sets: List<SetTarget>): String {
    if (sets.isEmpty()) return ""
    if (sets.none { it.load != null || it.reps != null }) return context.getString(R.string.no_targets)
    val duration = if (basis == MeasurementBasis.Time) {
        sets.mapNotNull { it.load?.toBigDecimalOrNull() }.maxOrNull()
            ?.let { formatDuration(it.multiply(60.toBigDecimal()).toLong()) }
    } else {
        null
    }
    return duration ?: countedSetSummary(context, basis, sets)
}

private fun countedSetSummary(context: Context, basis: MeasurementBasis, sets: List<SetTarget>): String {
    val count = context.resources.getQuantityString(R.plurals.set_count, sets.size, sets.size)
    fun loadLabel(value: String): String = when (basis) {
        MeasurementBasis.MachineLevel -> basis.formatValue(context, value).lowercase(finnishLocale)
        MeasurementBasis.Bodyweight -> value
        else -> basis.formatValue(context, value)
    }
    val first = sets.first()
    if (sets.all { it == first }) {
        val result = first.reps?.let { context.getString(R.string.set_summary_uniform, sets.size, it) } ?: count
        return first.load?.takeUnless {
            basis == MeasurementBasis.Bodyweight
        }?.let { context.getString(R.string.set_summary_with_load, result, loadLabel(it)) }
            ?: result
    }
    val best = bestSet(basis, sets)
    val load = best?.load?.takeUnless { basis == MeasurementBasis.Bodyweight }?.let(::loadLabel)
    val reps = best?.reps
    return when {
        load != null && reps != null -> context.getString(R.string.summary_best_load_reps, count, load, reps)
        load != null -> context.getString(R.string.summary_best_load, count, load)
        reps != null -> context.resources.getQuantityString(R.plurals.summary_best_reps, reps, count, reps)
        else -> count
    }
}

private fun bestSet(basis: MeasurementBasis, sets: List<SetTarget>): SetTarget? = when (basis) {
    MeasurementBasis.Bodyweight -> sets.maxByOrNull { it.reps ?: 0 }

    MeasurementBasis.Assistance -> sets.filter { it.load?.toBigDecimalOrNull() != null }.minWithOrNull(
        compareBy<SetTarget> { it.load?.toBigDecimalOrNull() }.thenByDescending { it.reps ?: 0 }
    ) ?: sets.maxByOrNull { it.reps ?: 0 }

    else -> sets.maxWithOrNull(compareBy<SetTarget> { it.load?.toBigDecimalOrNull() }.thenBy { it.reps ?: 0 })
}

internal fun machineSettingsSummary(setup: String): String = setup.split(", ").mapNotNull { entry ->
    val value = entry.substringAfter(": ", entry.substringAfterLast(' ')).trim()
    if (value.isBlank() || value.equals("None", ignoreCase = true) || value == "0") {
        null
    } else {
        entry.replace(": ", " ").lowercase(finnishLocale)
    }
}.joinToString(", ").replaceFirstChar { it.titlecase(finnishLocale) }

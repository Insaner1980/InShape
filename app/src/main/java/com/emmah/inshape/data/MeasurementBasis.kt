package com.emmah.inshape.data

import com.emmah.inshape.R
import android.content.Context
import androidx.annotation.StringRes
import com.emmah.inshape.ui.theme.decimalDisplay

enum class MeasurementBasis(
    val key: String,
    @StringRes val optionLabel: Int,
    @StringRes val columnLabel: Int,
    @StringRes val unitLabel: Int,
    @StringRes private val valueFormat: Int
) {
    Weight("kg", R.string.load_kg, R.string.unit_kg, R.string.unit_kg, R.string.weight_value),
    MachineLevel("level", R.string.machine_level, R.string.unit_level, R.string.unit_level, R.string.level_value),
    Bodyweight("bodyweight", R.string.category_bodyweight, R.string.only_reps, R.string.reps, R.string.rep_value),
    Assistance("assistance_kg", R.string.assisted_kg, R.string.assist_kg, R.string.assisted_kg, R.string.assistance_value),
    Time("duration", R.string.time, R.string.unit_min, R.string.unit_min, R.string.minute_value);

    fun formatValue(context: Context, value: String): String = context.getString(valueFormat, value.decimalDisplay())

    companion object {
        fun fromKey(key: String): MeasurementBasis = entries.find { it.key == key } ?: Weight
    }
}

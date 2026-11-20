package com.emmah.inshape.feature.library

import android.content.Context
import com.emmah.inshape.R
import com.emmah.inshape.ui.theme.finnishLocale

internal fun String.exerciseDisplayName(context: Context): String = when (lowercase(finnishLocale)) {
    "warm-up bike" -> context.getString(R.string.exercise_warm_up_bike)
    "leg press" -> context.getString(R.string.exercise_leg_press)
    "seated row" -> context.getString(R.string.exercise_seated_row)
    "chest press" -> context.getString(R.string.exercise_chest_press)
    "lat pulldown" -> context.getString(R.string.exercise_lat_pulldown)
    "pulldown machine" -> context.getString(R.string.exercise_lat_pulldown)
    "leg curl" -> context.getString(R.string.exercise_leg_curl)
    "hip flexor stretch" -> context.getString(R.string.exercise_hip_flexor_stretch)
    "shoulder mobility" -> context.getString(R.string.exercise_shoulder_mobility)
    "assisted pull-up machine" -> context.getString(R.string.exercise_assisted_pull_up)
    "band pulldown, home" -> context.getString(R.string.exercise_band_pulldown)
    else -> this
}

internal fun String.categoryLabel(context: Context): String = when (lowercase(finnishLocale)) {
    "machine" -> context.getString(R.string.category_machine)
    "free weight" -> context.getString(R.string.category_free_weight)
    "free weights" -> context.getString(R.string.category_free_weight)
    "bodyweight" -> context.getString(R.string.category_bodyweight)
    "warm-up" -> context.getString(R.string.category_warm_up)
    "stretching" -> context.getString(R.string.category_stretching)
    "mobility" -> context.getString(R.string.category_mobility)
    "cable" -> context.getString(R.string.category_cable)
    "strength" -> context.getString(R.string.category_strength)
    else -> this
}

internal fun String.settingLabel(context: Context): String = when (lowercase(finnishLocale)) {
    "seat height" -> context.getString(R.string.seat_height)
    "handle" -> context.getString(R.string.handle)
    "thigh pad" -> context.getString(R.string.thigh_pad)
    "backrest" -> context.getString(R.string.backrest)
    else -> this
}

internal fun String.settingValue(context: Context): String = when (lowercase(finnishLocale)) {
    "wide bar" -> context.getString(R.string.wide_bar)
    "none" -> context.getString(R.string.none)
    else -> this
}

internal fun String.groupTypeLabel(context: Context): String = when (lowercase(finnishLocale)) {
    "superset" -> context.getString(R.string.group_superset)
    "circuit" -> context.getString(R.string.group_circuit)
    "straight", "straight sets" -> context.getString(R.string.group_straight)
    else -> this
}

internal fun String.locationLabel(context: Context): String = when (lowercase(finnishLocale)) {
    "gym" -> context.getString(R.string.place_gym)
    "home" -> context.getString(R.string.place_home)
    else -> this
}

package com.emmah.inshape.feature.program

import androidx.annotation.DrawableRes
import com.emmah.inshape.R

@DrawableRes
fun programArtwork(key: String?): Int? = when (key) {
    "bench_goblet_squat" -> R.drawable.guide_bench_goblet_squat
    "dead_bug" -> R.drawable.guide_dead_bug
    "dumbbell_bench_press" -> R.drawable.guide_dumbbell_bench_press
    "dumbbell_romanian_deadlift" -> R.drawable.guide_dumbbell_romanian_deadlift
    "floor_glute_bridge" -> R.drawable.guide_floor_glute_bridge
    "hip_thrust_machine" -> R.drawable.guide_hip_thrust_machine
    "lat_pulldown" -> R.drawable.guide_lat_pulldown
    "leg_press" -> R.drawable.guide_leg_press
    "low_cable_row" -> R.drawable.guide_low_cable_row
    "machine_chest_press" -> R.drawable.guide_machine_chest_press
    "machine_crunch" -> R.drawable.guide_machine_crunch
    "machine_row" -> R.drawable.guide_machine_row
    "machine_shoulder_press" -> R.drawable.guide_machine_shoulder_press
    "neutral_lat_pulldown" -> R.drawable.guide_neutral_lat_pulldown
    "pallof_press" -> R.drawable.guide_pallof_press
    "seated_dumbbell_shoulder_press" -> R.drawable.guide_seated_dumbbell_shoulder_press
    "seated_leg_curl" -> R.drawable.guide_seated_leg_curl
    "supported_bench_squat" -> R.drawable.guide_supported_bench_squat
    "wall_push_up" -> R.drawable.guide_wall_push_up
    else -> null
}

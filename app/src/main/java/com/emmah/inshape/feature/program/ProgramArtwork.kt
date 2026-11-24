package com.emmah.inshape.feature.program

import androidx.annotation.DrawableRes
import com.emmah.inshape.R

private val programArtworkByKey = mapOf(
    "bench_goblet_squat" to R.drawable.guide_bench_goblet_squat,
    "dead_bug" to R.drawable.guide_dead_bug,
    "dumbbell_bench_press" to R.drawable.guide_dumbbell_bench_press,
    "dumbbell_romanian_deadlift" to R.drawable.guide_dumbbell_romanian_deadlift,
    "floor_glute_bridge" to R.drawable.guide_floor_glute_bridge,
    "hip_thrust_machine" to R.drawable.guide_hip_thrust_machine,
    "lat_pulldown" to R.drawable.guide_lat_pulldown,
    "leg_press" to R.drawable.guide_leg_press,
    "low_cable_row" to R.drawable.guide_low_cable_row,
    "machine_chest_press" to R.drawable.guide_machine_chest_press,
    "machine_crunch" to R.drawable.guide_machine_crunch,
    "machine_row" to R.drawable.guide_machine_row,
    "machine_shoulder_press" to R.drawable.guide_machine_shoulder_press,
    "neutral_lat_pulldown" to R.drawable.guide_neutral_lat_pulldown,
    "pallof_press" to R.drawable.guide_pallof_press,
    "seated_dumbbell_shoulder_press" to R.drawable.guide_seated_dumbbell_shoulder_press,
    "seated_leg_curl" to R.drawable.guide_seated_leg_curl,
    "supported_bench_squat" to R.drawable.guide_supported_bench_squat,
    "wall_push_up" to R.drawable.guide_wall_push_up
)

@DrawableRes
fun programArtwork(key: String?): Int? = key?.let(programArtworkByKey::get)

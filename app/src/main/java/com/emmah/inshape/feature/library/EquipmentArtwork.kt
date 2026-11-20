package com.emmah.inshape.feature.library

import androidx.annotation.DrawableRes
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.feature.library.EquipmentFamily.Accessory
import com.emmah.inshape.feature.library.EquipmentFamily.Cardio
import com.emmah.inshape.feature.library.EquipmentFamily.Mobility
import com.emmah.inshape.feature.library.EquipmentFamily.Strength
import java.text.Normalizer
import java.util.Locale

internal enum class EquipmentFamily { Strength, Cardio, Mobility, Accessory }

internal data class EquipmentArtwork(@DrawableRes val drawable: Int, val family: EquipmentFamily)

private val separators = Regex("[\\p{P}\\s\\p{Z}]+")
private fun normalize(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace(separators, " ").trim()

private val catalog = buildMap {
    fun add(@DrawableRes drawable: Int, family: EquipmentFamily, vararg names: String) {
        val artwork = EquipmentArtwork(drawable, family)
        names.forEach { put(normalize(it), artwork) }
    }
    add(
        R.drawable.art_exercises_lat_pulldown,
        Strength,
        "Lat pulldown",
        "Pulldown machine",
        "Lat pull-down",
        "Ylätalja"
    )
    add(
        R.drawable.art_exercises_seated_row,
        Strength,
        "Seated row",
        "Vaakasoutu laitteessa",
        "Seated cable row",
        "Low cable row",
        "Alatalja",
        "Alataljasoutu"
    )
    add(
        R.drawable.art_exercises_chest_press,
        Strength,
        "Chest press",
        "Rintapunnerruslaite",
        "Seated chest press",
        "Rintaprässi"
    )
    add(
        R.drawable.art_exercises_leg_curl,
        Strength,
        "Seated leg curl",
        "Leg curl",
        "Reisikoukistus istuen",
        "Reiden koukistus istuen"
    )
    add(
        R.drawable.art_exercises_leg_press,
        Strength,
        "45 degree leg press",
        "45° leg press",
        "Leg press",
        "Jalkaprässi 45°",
        "Jalkaprässi"
    )
    add(
        R.drawable.art_exercises_stationary_bike,
        Cardio,
        "Upright bike",
        "Stationary bike",
        "Warm-up bike",
        "Lämmittelypyörä",
        "Pystykuntopyörä",
        "Kuntopyörä"
    )
    add(
        R.drawable.art_exercises_assisted_pull_up,
        Strength,
        "Assisted pull-up machine",
        "Assisted chin and dip",
        "Avustettu leuanveto",
        "Avustettu dippi"
    )
    add(
        R.drawable.art_exercises_shoulder_press,
        Strength,
        "Shoulder press machine",
        "Seated machine shoulder press",
        "Olkapääprässi",
        "Pystypunnerrus laitteessa"
    )
    add(
        R.drawable.art_exercises_incline_chest_press,
        Strength,
        "Incline chest press",
        "Incline press machine",
        "Vinopenkkiprässi"
    )
    add(
        R.drawable.art_exercises_pec_fly_rear_delt,
        Strength,
        "Pec deck",
        "Rear delt fly machine",
        "Pectoral fly machine",
        "Peck deck",
        "Takaolkapäälaite",
        "Rintaperhonen"
    )
    add(
        R.drawable.art_exercises_lateral_raise,
        Strength,
        "Lateral raise machine",
        "Seated machine lateral raise",
        "Sivuvipulaite",
        "Sivuvipunosto laitteessa"
    )
    add(
        R.drawable.art_exercises_high_row,
        Strength,
        "High row",
        "Iso-lateral high row",
        "Ylävetosoutu"
    )
    add(
        R.drawable.art_exercises_t_bar_row,
        Strength,
        "Chest-supported T-bar row",
        "T-bar row machine",
        "Rintatuettu T-tankosoutu"
    )
    add(
        R.drawable.art_exercises_biceps_curl,
        Strength,
        "Biceps curl machine",
        "Preacher curl machine",
        "Hauiskääntölaite",
        "Hauiskääntö laitteessa"
    )
    add(
        R.drawable.art_exercises_triceps_press,
        Strength,
        "Seated dip machine",
        "Triceps press machine",
        "Ojentajaprässi",
        "Dippiprässi"
    )
    add(
        R.drawable.art_exercises_lat_pullover,
        Strength,
        "Pullover machine",
        "Plate-loaded pullover",
        "Pullover-laite"
    )
    add(
        R.drawable.art_exercises_leg_extension,
        Strength,
        "Leg extension",
        "Knee extension machine",
        "Reisiojennus",
        "Reiden ojennus"
    )
    add(
        R.drawable.art_exercises_prone_leg_curl,
        Strength,
        "Prone leg curl",
        "Lying leg curl",
        "Reisikoukistus maaten"
    )
    add(
        R.drawable.art_exercises_horizontal_leg_press,
        Strength,
        "Horizontal leg press",
        "Seated horizontal leg press",
        "Vaakajalkaprässi"
    )
    add(
        R.drawable.art_exercises_hack_squat,
        Strength,
        "Hack squat",
        "Hack squat machine",
        "Hack-kyykky"
    )
    add(
        R.drawable.art_exercises_belt_squat,
        Strength,
        "Belt squat",
        "Belt squat machine",
        "Vyökyykky"
    )
    add(
        R.drawable.art_exercises_hip_thrust,
        Strength,
        "Hip thrust machine",
        "Glute drive machine",
        "Lantionnostolaite",
        "Lantionnosto laitteessa"
    )
    add(
        R.drawable.art_exercises_hip_abduction_adduction,
        Strength,
        "Hip abduction machine",
        "Hip adduction machine",
        "Hip abductor/adductor",
        "Loitonnuslaite",
        "Lähennyslaite",
        "Loitonnus ja lähennys"
    )
    add(
        R.drawable.art_exercises_glute_kickback,
        Strength,
        "Glute kickback machine",
        "Rear kick machine",
        "Pakarapotkulaite"
    )
    add(
        R.drawable.art_exercises_seated_calf_raise,
        Strength,
        "Seated calf raise",
        "Seated calf machine",
        "Pohjelaite istuen"
    )
    add(
        R.drawable.art_exercises_standing_calf_raise,
        Strength,
        "Standing calf raise machine",
        "Standing calf machine",
        "Pohjelaite seisten"
    )
    add(
        R.drawable.art_exercises_abdominal_crunch,
        Strength,
        "Abdominal crunch machine",
        "Ab crunch machine",
        "Vatsarutistuslaite"
    )
    add(
        R.drawable.art_exercises_back_extension,
        Strength,
        "Seated back extension machine",
        "Back extension machine",
        "Selänojennuslaite"
    )
    add(
        R.drawable.art_exercises_torso_rotation,
        Strength,
        "Torso rotation machine",
        "Rotary torso",
        "Vartalonkiertolaite"
    )
    add(
        R.drawable.art_exercises_dual_adjustable_pulley,
        Strength,
        "Dual adjustable pulley",
        "Cable crossover",
        "Functional trainer",
        "Ristikkäistalja",
        "Säädettävä ristikkäistalja"
    )
    add(
        R.drawable.art_exercises_smith_machine,
        Strength,
        "Smith machine",
        "Smith squat",
        "Smith-laite",
        "Smith-kyykky"
    )
    add(
        R.drawable.art_exercises_power_rack,
        Strength,
        "Power rack",
        "Power cage",
        "Voimahäkki",
        "Kyykkyhäkki"
    )
    add(
        R.drawable.art_exercises_treadmill,
        Cardio,
        "Treadmill",
        "Treadmill run",
        "Juoksumatto"
    )
    add(
        R.drawable.art_exercises_elliptical,
        Cardio,
        "Elliptical trainer",
        "Elliptical",
        "Cross trainer",
        "Crosstrainer",
        "Ellipsilaite"
    )
    add(
        R.drawable.art_exercises_stair_climber,
        Cardio,
        "Stair climber",
        "Stairmill",
        "Porraslaite",
        "Porraskone"
    )
    add(
        R.drawable.art_exercises_rowing_ergometer,
        Cardio,
        "Rowing ergometer",
        "Rowing machine",
        "Indoor rower",
        "Soutulaite",
        "Soutuergometri"
    )
    add(
        R.drawable.art_exercises_ski_ergometer,
        Cardio,
        "Ski ergometer",
        "SkiErg",
        "Hiihtoergometri"
    )
    add(
        R.drawable.art_exercises_recumbent_bike,
        Cardio,
        "Recumbent bike",
        "Recumbent cycle",
        "Selkänojallinen kuntopyörä",
        "Nojakuntopyörä"
    )
    add(
        R.drawable.art_exercises_indoor_cycle,
        Cardio,
        "Studio cycle",
        "Indoor cycling bike",
        "Spin bike",
        "Sisäpyöräilypyörä",
        "Spinningpyörä"
    )
    add(
        R.drawable.art_exercises_air_bike,
        Cardio,
        "Air bike",
        "Fan bike",
        "Ilmanvastuspyörä"
    )
    add(
        R.drawable.art_exercises_band_pulldown,
        Accessory,
        "Band pulldown home",
        "Home band pulldown",
        "Resistance band pulldown",
        "Vastuskuminauhatalja",
        "Ylätalja kuminauhalla"
    )
    add(
        R.drawable.art_exercises_shoulder_mobility,
        Mobility,
        "Shoulder mobility",
        "Mobility dowel",
        "Olkapään liikkuvuus",
        "Liikkuvuuskeppi"
    )
    add(
        R.drawable.art_exercises_stretch_mat,
        Mobility,
        "Hip flexor stretch",
        "Lonkan koukistajan venytys",
        "Stretch mat",
        "Lonkankoukistajan venytys",
        "Venyttelymatto"
    )
    add(
        R.drawable.art_exercises_exercise_equipment,
        Accessory,
        "Dumbbell and resistance band",
        "Hex dumbbell",
        "Dumbbell",
        "Käsipaino ja vastuskuminauha",
        "Käsipaino"
    )
}

internal fun equipmentArtwork(equipmentName: String, exerciseName: String): EquipmentArtwork? =
    catalog[normalize(equipmentName)] ?: catalog[normalize(exerciseName)]

@DrawableRes
internal fun routineArtwork(exercises: List<Exercise?>, plannedMinutes: Int?): Int? {
    if (exercises.isEmpty()) return null
    val items = exercises.map { exercise -> exercise to exercise?.let { equipmentArtwork(it.equipmentName, it.name) } }
    val mobilityOnly = items.all { (exercise, art) ->
        val category = exercise?.category
        art?.family == Mobility || category.equals("Mobility", ignoreCase = true) || category.equals("Liikkuvuus", ignoreCase = true)
    }
    val hasStrength = items.any { (exercise, art) ->
        art?.family == Strength || (art == null && exercise?.category.equals("Strength", ignoreCase = true))
    }
    return when {
        mobilityOnly -> R.drawable.art_training_mobility_mat
        plannedMinutes != null && plannedMinutes in 1..20 -> R.drawable.art_training_short_session
        hasStrength -> R.drawable.art_training_strength_barbell
        else -> null
    }
}

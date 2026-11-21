package com.emmah.inshape.feature.library

import com.emmah.inshape.ui
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class EquipmentArtworkTest {
    @Test fun catalogReachesEveryIllustration() {
        val aliases = mapOf(
            "Lat pulldown" to R.drawable.art_exercises_lat_pulldown,
            "Seated row" to R.drawable.art_exercises_seated_row,
            "Chest press" to R.drawable.art_exercises_chest_press,
            "Seated leg curl" to R.drawable.art_exercises_leg_curl,
            "45 degree leg press" to R.drawable.art_exercises_leg_press,
            "Upright bike" to R.drawable.art_exercises_stationary_bike,
            "Assisted pull-up machine" to R.drawable.art_exercises_assisted_pull_up,
            "Shoulder press machine" to R.drawable.art_exercises_shoulder_press,
            "Incline chest press" to R.drawable.art_exercises_incline_chest_press,
            "Pec deck" to R.drawable.art_exercises_pec_fly_rear_delt,
            "Lateral raise machine" to R.drawable.art_exercises_lateral_raise,
            "High row" to R.drawable.art_exercises_high_row,
            "Chest-supported T-bar row" to R.drawable.art_exercises_t_bar_row,
            "Biceps curl machine" to R.drawable.art_exercises_biceps_curl,
            "Seated dip machine" to R.drawable.art_exercises_triceps_press,
            "Pullover machine" to R.drawable.art_exercises_lat_pullover,
            "Leg extension" to R.drawable.art_exercises_leg_extension,
            "Prone leg curl" to R.drawable.art_exercises_prone_leg_curl,
            "Horizontal leg press" to R.drawable.art_exercises_horizontal_leg_press,
            "Hack squat" to R.drawable.art_exercises_hack_squat,
            "Belt squat" to R.drawable.art_exercises_belt_squat,
            "Hip thrust machine" to R.drawable.art_exercises_hip_thrust,
            "Hip abduction machine" to R.drawable.art_exercises_hip_abduction_adduction,
            "Glute kickback machine" to R.drawable.art_exercises_glute_kickback,
            "Seated calf raise" to R.drawable.art_exercises_seated_calf_raise,
            "Standing calf raise machine" to R.drawable.art_exercises_standing_calf_raise,
            "Abdominal crunch machine" to R.drawable.art_exercises_abdominal_crunch,
            "Seated back extension machine" to R.drawable.art_exercises_back_extension,
            "Torso rotation machine" to R.drawable.art_exercises_torso_rotation,
            "Dual adjustable pulley" to R.drawable.art_exercises_dual_adjustable_pulley,
            "Smith machine" to R.drawable.art_exercises_smith_machine,
            "Power rack" to R.drawable.art_exercises_power_rack,
            "Treadmill" to R.drawable.art_exercises_treadmill,
            "Elliptical trainer" to R.drawable.art_exercises_elliptical,
            "Stair climber" to R.drawable.art_exercises_stair_climber,
            "Rowing ergometer" to R.drawable.art_exercises_rowing_ergometer,
            "Ski ergometer" to R.drawable.art_exercises_ski_ergometer,
            "Recumbent bike" to R.drawable.art_exercises_recumbent_bike,
            "Studio cycle" to R.drawable.art_exercises_indoor_cycle,
            "Air bike" to R.drawable.art_exercises_air_bike,
            "Band pulldown home" to R.drawable.art_exercises_band_pulldown,
            "Shoulder mobility" to R.drawable.art_exercises_shoulder_mobility,
            "Hip flexor stretch" to R.drawable.art_exercises_stretch_mat,
            "Dumbbell and resistance band" to R.drawable.art_exercises_exercise_equipment
        )
        assertEquals(44, aliases.values.toSet().size)
        aliases.forEach { (alias, id) -> assertEquals(alias, id, equipmentArtwork("", alias)?.drawable) }
    }

    @Test fun originalDemoExercisesUseTheIntendedEquipment() {
        val cases = mapOf(
            "Warm-up bike" to R.drawable.art_exercises_stationary_bike,
            "Leg press" to R.drawable.art_exercises_leg_press,
            "Seated row" to R.drawable.art_exercises_seated_row,
            "Chest press" to R.drawable.art_exercises_chest_press,
            "Lat pulldown" to R.drawable.art_exercises_lat_pulldown,
            "Leg curl" to R.drawable.art_exercises_leg_curl,
            "Hip flexor stretch" to R.drawable.art_exercises_stretch_mat,
            "Shoulder mobility" to R.drawable.art_exercises_shoulder_mobility,
            "Assisted pull-up machine" to R.drawable.art_exercises_assisted_pull_up,
            "Band pulldown, home" to R.drawable.art_exercises_band_pulldown
        )
        cases.forEach { (name, id) -> assertEquals(name, id, equipmentArtwork("", name)?.drawable) }
        val finnish = listOf(
            R.string.exercise_warm_up_bike, R.string.exercise_leg_press, R.string.exercise_seated_row,
            R.string.exercise_chest_press, R.string.exercise_lat_pulldown, R.string.exercise_leg_curl,
            R.string.exercise_hip_flexor_stretch, R.string.exercise_shoulder_mobility,
            R.string.exercise_assisted_pull_up, R.string.exercise_band_pulldown
        )
        finnish.zip(cases.values).forEach { (text, drawable) ->
            assertEquals(drawable, equipmentArtwork("", ui(text))?.drawable)
        }
    }

    @Test fun equipmentNamePrecedesExerciseAndMatchingUsesWholeNormalizedNames() {
        assertEquals(R.drawable.art_exercises_horizontal_leg_press, equipmentArtwork("HORIZONTAL_leg--press", "Leg press")?.drawable)
        assertEquals(R.drawable.art_exercises_lat_pulldown, equipmentArtwork("  YLA\u0308TALJA ", "")?.drawable)
        assertEquals(R.drawable.art_exercises_leg_curl, equipmentArtwork("unknown", "Leg curl")?.drawable)
        assertEquals(R.drawable.art_exercises_band_pulldown, equipmentArtwork("", "Band pulldown, home")?.drawable)
        assertEquals(R.drawable.art_exercises_stationary_bike, equipmentArtwork("", "Warm-up bike")?.drawable)
        listOf("Bike", "Barbell bench press", "Cable crunch", "Machine", "press", "row").forEach {
            assertNull(equipmentArtwork("", it))
        }
    }

    @Test fun routineArtworkUsesResolvedItemsAndDuration() {
        val mobility = Exercise(name = "Hip flexor stretch", category = "Machine")
        val strength = Exercise(name = "Leg press", category = "Machine")
        val cardio = Exercise(name = "Warm-up bike", category = "Machine")
        val custom = Exercise(name = "Custom machine", category = "Machine")
        assertEquals(R.drawable.art_training_mobility_mat, routineArtwork(listOf(mobility), 15))
        assertEquals(R.drawable.art_training_short_session, routineArtwork(listOf(strength), 20))
        assertEquals(R.drawable.art_training_short_session, routineArtwork(listOf(mobility, null), 1))
        assertEquals(R.drawable.art_training_strength_barbell, routineArtwork(listOf(cardio, strength), 50))
        assertEquals(R.drawable.art_training_strength_barbell, routineArtwork(listOf(custom.copy(category = "Strength")), null))
        assertNull(routineArtwork(listOf(cardio), 30))
        assertNull(routineArtwork(listOf(custom), 30))
        assertNull(routineArtwork(emptyList(), 10))
    }
}

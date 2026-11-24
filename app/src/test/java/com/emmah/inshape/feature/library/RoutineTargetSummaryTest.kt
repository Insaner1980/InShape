package com.emmah.inshape.feature.library

import com.emmah.inshape.R
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.feature.workout.setSummary
import com.emmah.inshape.quantity
import com.emmah.inshape.testContext
import com.emmah.inshape.ui
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutineTargetSummaryTest {
    @Test fun unequalWeightAndAssistanceUseTheMatchingSetValues() {
        val weights = listOf(SetTarget("25", 12), SetTarget("27.5", 10), SetTarget("27.5", 8))
        assertEquals(
            ui(R.string.summary_best_load_reps, quantity(R.plurals.set_count, 3), ui(R.string.weight_value, "27,5"), 10),
            setSummary(testContext(), MeasurementBasis.Weight, weights)
        )
        assertEquals(
            ui(R.string.summary_best_load_reps, quantity(R.plurals.set_count, 3), ui(R.string.assistance_value, "25"), 12),
            setSummary(testContext(), MeasurementBasis.Assistance, weights)
        )
    }

    @Test fun summarizesMatchingTargetsUsingTheExerciseBasis() {
        assertEquals(
            ui(R.string.set_summary_with_load, ui(R.string.set_summary_uniform, 3, 10), ui(R.string.weight_value, "25")),
            setSummary(
                testContext(),
                MeasurementBasis.Weight,
                List(3) {
                    SetTarget("25", 10)
                }
            )
        )
        assertEquals(
            ui(R.string.set_summary_with_load, ui(R.string.set_summary_uniform, 1, 10), ui(R.string.level_value, "3").lowercase()),
            setSummary(testContext(), MeasurementBasis.MachineLevel, listOf(SetTarget("3", 10)))
        )
    }

    @Test fun summarizesDifferentRepsAndMissingTargets() {
        val varied = listOf(SetTarget(reps = 8), SetTarget(reps = 10), SetTarget(reps = 12))
        assertEquals(
            "3 sarjaa, paras 12 toistoa",
            setSummary(testContext(), MeasurementBasis.Bodyweight, varied)
        )
        assertEquals(
            "3 sarjaa, paras 8 toistoa",
            setSummary(testContext(), MeasurementBasis.Weight, listOf(SetTarget(reps = 8), SetTarget(reps = 8), SetTarget()))
        )
        assertEquals(ui(R.string.no_targets), setSummary(testContext(), MeasurementBasis.Weight, listOf(SetTarget())))
    }

    @Test fun usesSingularWhenBestSetHasOneRep() {
        val sets = listOf(SetTarget(reps = 1), SetTarget())
        assertEquals("2 sarjaa, paras 1 toisto", setSummary(testContext(), MeasurementBasis.Bodyweight, sets))
    }
}

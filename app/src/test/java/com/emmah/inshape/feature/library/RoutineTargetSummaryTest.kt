package com.emmah.inshape.feature.library

import com.emmah.inshape.R
import com.emmah.inshape.quantity
import com.emmah.inshape.ui
import com.emmah.inshape.testContext
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import com.emmah.inshape.feature.workout.setSummary
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SetTarget
import org.junit.Assert.assertEquals
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class RoutineTargetSummaryTest {
    @Test fun unequalWeightAndAssistanceUseTheMatchingSetValues() {
        val weights = listOf(SetTarget("25", 12), SetTarget("27.5", 10), SetTarget("27.5", 8))
        assertEquals(ui(R.string.summary_best_load_reps, quantity(R.plurals.set_count, 3), ui(R.string.weight_value, "27,5"), 10), setSummary(testContext(), MeasurementBasis.Weight, weights))
        assertEquals(ui(R.string.summary_best_load_reps, quantity(R.plurals.set_count, 3), ui(R.string.assistance_value, "25"), 12), setSummary(testContext(), MeasurementBasis.Assistance, weights))
    }

    @Test fun summarizesMatchingTargetsUsingTheExerciseBasis() {
        assertEquals(ui(R.string.set_summary_with_load, ui(R.string.set_summary_uniform, 3, 10), ui(R.string.weight_value, "25")), setSummary(testContext(), MeasurementBasis.Weight, List(3) { SetTarget("25", 10) }))
        assertEquals(ui(R.string.set_summary_with_load, ui(R.string.set_summary_uniform, 1, 10), ui(R.string.level_value, "3").lowercase()), setSummary(testContext(), MeasurementBasis.MachineLevel, listOf(SetTarget("3", 10))))
    }

    @Test fun summarizesDifferentRepsAndMissingTargets() {
        val varied = listOf(SetTarget(reps = 8), SetTarget(reps = 10), SetTarget(reps = 12))
        assertEquals(ui(R.string.summary_best_reps, quantity(R.plurals.set_count, 3), 12), setSummary(testContext(), MeasurementBasis.Bodyweight, varied))
        assertEquals(ui(R.string.summary_best_reps, quantity(R.plurals.set_count, 3), 8), setSummary(testContext(), MeasurementBasis.Weight, listOf(SetTarget(reps = 8), SetTarget(reps = 8), SetTarget())))
        assertEquals(ui(R.string.no_targets), setSummary(testContext(), MeasurementBasis.Weight, listOf(SetTarget())))
    }
}

package com.emmah.inshape.feature.library

import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SetTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineTargetSummaryTest {
    @Test fun summarizesMatchingTargetsUsingTheExerciseBasis() {
        assertEquals("3 sets of 10, 25 kg", routineTargetSummary(List(3) { SetTarget("25", 10) }, MeasurementBasis.Weight))
        assertEquals("1 set of 10, Level 3", routineTargetSummary(listOf(SetTarget("3", 10)), MeasurementBasis.MachineLevel))
    }

    @Test fun summarizesDifferentRepsAndMissingTargets() {
        val varied = listOf(SetTarget(reps = 8), SetTarget(reps = 10), SetTarget(reps = 12))
        assertEquals("3 sets, 8 to 12 reps", routineTargetSummary(varied, MeasurementBasis.Bodyweight))
        assertEquals("3 sets", routineTargetSummary(listOf(SetTarget(reps = 8), SetTarget(reps = 8), SetTarget()), MeasurementBasis.Weight))
        assertEquals("No targets yet", routineTargetSummary(listOf(SetTarget()), MeasurementBasis.Weight))
    }
}

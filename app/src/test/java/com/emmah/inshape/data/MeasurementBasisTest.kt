package com.emmah.inshape.data

import com.emmah.inshape.R
import com.emmah.inshape.quantity
import com.emmah.inshape.testContext
import com.emmah.inshape.ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MeasurementBasisTest {
    @Test fun storedKeysRoundTripWithReadableLabels() {
        assertEquals(listOf("kg", "level", "bodyweight", "assistance_kg", "duration"), MeasurementBasis.entries.map { it.key })
        MeasurementBasis.entries.forEach { basis ->
            assertEquals(basis, MeasurementBasis.fromKey(basis.key))
            assertTrue(ui(basis.optionLabel).isNotBlank())
            assertTrue(ui(basis.columnLabel).isNotBlank())
        }
        assertEquals(MeasurementBasis.Weight, MeasurementBasis.fromKey("unknown"))
    }

    @Test fun formatsLoadLevelAssistanceAndTime() {
        assertEquals(ui(R.string.weight_value, "25"), MeasurementBasis.Weight.formatValue(testContext(), "25"))
        assertEquals(ui(R.string.level_value, "3"), MeasurementBasis.MachineLevel.formatValue(testContext(), "3"))
        assertEquals(ui(R.string.rep_value, "10"), MeasurementBasis.Bodyweight.formatValue(testContext(), "10"))
        assertEquals(ui(R.string.assistance_value, "25"), MeasurementBasis.Assistance.formatValue(testContext(), "25"))
        assertEquals(ui(R.string.minute_value, "5"), MeasurementBasis.Time.formatValue(testContext(), "5"))
    }
}

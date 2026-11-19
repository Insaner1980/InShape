package com.emmah.inshape.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementBasisTest {
    @Test fun storedKeysRoundTripWithReadableLabels() {
        assertEquals(listOf("kg", "level", "bodyweight", "assistance_kg", "duration"), MeasurementBasis.entries.map { it.key })
        MeasurementBasis.entries.forEach { basis ->
            assertEquals(basis, MeasurementBasis.fromKey(basis.key))
            assertTrue(basis.optionLabel != basis.key)
            assertTrue(basis.columnLabel != basis.key || basis == MeasurementBasis.Weight)
        }
        assertEquals(MeasurementBasis.Weight, MeasurementBasis.fromKey("unknown"))
    }

    @Test fun formatsLoadLevelAssistanceAndTime() {
        assertEquals("25 kg", MeasurementBasis.Weight.formatValue("25"))
        assertEquals("Level 3", MeasurementBasis.MachineLevel.formatValue("3"))
        assertEquals("10 reps", MeasurementBasis.Bodyweight.formatValue("10"))
        assertEquals("25 kg assist", MeasurementBasis.Assistance.formatValue("25"))
        assertEquals("5 min", MeasurementBasis.Time.formatValue("5"))
    }
}

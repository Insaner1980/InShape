package com.emmah.inshape.feature.checkin

import com.emmah.inshape.R
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckInLevelsTest {
    @Test fun energyLabelsUseSentenceCaseAndKeepStoredKeys() {
        assertEquals(listOf("LOW", "OK", "GOOD"), EnergyLevel.entries.map { it.key })
        assertEquals(listOf(R.string.energy_low, R.string.energy_ok, R.string.energy_good), EnergyLevel.entries.map { it.label })
        EnergyLevel.entries.forEach { assertEquals(it, EnergyLevel.fromKey(it.key)) }
        assertNull(EnergyLevel.fromKey("unknown"))
    }

    @Test fun allStoredValuesMapWithoutConflatingMissingAndZero() {
        assertNull(effortLevelOf(null))
        assertNull(bodyLevelOf(null))
        (0..10).forEach { value ->
            assertEquals(
                if (value <=
                    4
                ) {
                    EffortLevel.Easy
                } else if (value <= 7) {
                    EffortLevel.JustRight
                } else {
                    EffortLevel.Hard
                },
                effortLevelOf(value)
            )
            assertEquals(
                when (value) {
                    0 -> BodyLevel.None
                    in 1..3 -> BodyLevel.Mild
                    in 4..6 -> BodyLevel.Medium
                    else -> BodyLevel.Strong
                },
                bodyLevelOf(value)
            )
        }
        assertEquals(listOf(3, 6, 8), EffortLevel.entries.map { it.stored })
        assertEquals(listOf(0, 2, 5, 8), BodyLevel.entries.map { it.stored })
    }

    @Test fun displayingLevelsAndEditingNotePreservesUntouchedLegacyNumbers() {
        val check = CheckIn(workoutId = "workout", phase = "AFTER", effort = 7, observedAt = "2026-11-13T10:00:00Z", zoneId = "UTC")
        val observation = BodyObservation(check.id, "left_knee", pain = 3, stiffness = null)
        val draft = CheckInDraft(check, listOf(observation), "", "2026-11-13 10:00", "")
        assertEquals(EffortLevel.JustRight, effortLevelOf(draft.checkIn.effort))
        assertEquals(BodyLevel.Mild, bodyLevelOf(draft.observations.single().pain))
        val edited = draft.copy(checkIn = draft.checkIn.copy(note = "New note"))
        assertEquals(7, edited.checkIn.effort)
        assertEquals(3, edited.observations.single().pain)
        assertNull(edited.observations.single().stiffness)
    }
}

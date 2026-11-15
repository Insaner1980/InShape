package com.emmah.inshape.data

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericInputTest {
    @Test fun acceptsDotCommaAndIntentionalZero() {
        assertEquals(
            BigDecimal("27.5"),
            (parseDecimalInput("27,5") as NumericInputResult.Valid).value
        )
        assertEquals(
            BigDecimal("27.5"),
            (parseDecimalInput("27.5") as NumericInputResult.Valid).value
        )
        assertEquals(BigDecimal.ZERO, (parseDecimalInput("0") as NumericInputResult.Valid).value)
    }

    @Test fun preservesIncompleteAndMissingInsteadOfRecordingThem() {
        assertEquals(NumericInputResult.Missing, parseDecimalInput(" "))
        assertEquals(NumericInputResult.Incomplete, parseDecimalInput("27."))
        assertEquals(NumericInputResult.Incomplete, parseDecimalInput("27,"))
        listOf("-1", "1,2.3", "NaN", "1e5", "1 000").forEach {
            assertTrue(parseDecimalInput(it) is NumericInputResult.Invalid)
        }
    }
}

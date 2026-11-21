package com.emmah.inshape.data

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericInputTest {
    @Test fun wholeMinutesConvertWithoutOverflow() {
        val maximum = Long.MAX_VALUE / 60
        assertEquals(60L, parseWholeMinutesToSeconds("1"))
        assertEquals(maximum * 60, parseWholeMinutesToSeconds(maximum.toString()))
        listOf("", "0", "-1", "1,5", " 1", (maximum + 1).toString()).forEach {
            assertNull(parseWholeMinutesToSeconds(it))
        }
    }

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

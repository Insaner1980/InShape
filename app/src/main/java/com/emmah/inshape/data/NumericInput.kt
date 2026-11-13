package com.emmah.inshape.data

import java.math.BigDecimal

sealed interface NumericInputResult {
    data object Missing : NumericInputResult
    data object Incomplete : NumericInputResult
    data class Valid(val value: BigDecimal) : NumericInputResult
    data class Invalid(val message: String) : NumericInputResult
}
fun parseDecimalInput(raw: String): NumericInputResult {
    val value = raw.trim()
    if (value.isEmpty()) return NumericInputResult.Missing
    if (value.length > 32) return NumericInputResult.Invalid("Enter a shorter number")
    if (value.matches(Regex("[0-9]+[.,]"))) return NumericInputResult.Incomplete
    if (!value.matches(Regex("[0-9]+([.,][0-9]+)?"))) return NumericInputResult.Invalid("Enter a non-negative decimal number")
    return NumericInputResult.Valid(value.replace(',', '.').toBigDecimal())
}

fun validatedSetValues(load: String, reps: String): Pair<String?, Int?> {
    val weight = when (val parsed = parseDecimalInput(load)) {
        NumericInputResult.Missing -> null
        is NumericInputResult.Valid -> parsed.value.stripTrailingZeros().toPlainString()
        else -> throw IllegalArgumentException("Finish entering a valid load")
    }
    val repetitions = if (reps.isBlank()) null else reps.trim().toIntOrNull()?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("Reps must be a positive whole number")
    require(weight != null || repetitions != null) { "Enter load or reps, or use Mark done without sets" }
    return weight to repetitions
}

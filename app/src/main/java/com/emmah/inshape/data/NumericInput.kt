// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.data

import com.emmah.inshape.R
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.ui.UiException
import java.math.BigDecimal

sealed interface NumericInputResult {
    data object Missing : NumericInputResult
    data object Incomplete : NumericInputResult
    data class Valid(val value: BigDecimal) : NumericInputResult
    data class Invalid(@androidx.annotation.StringRes val message: Int) : NumericInputResult
}
fun parseDecimalInput(raw: String): NumericInputResult {
    val value = raw.trim()
    return when {
        value.isEmpty() -> NumericInputResult.Missing

        value.length > 32 -> NumericInputResult.Invalid(R.string.shorter_number)

        value.matches(Regex("[0-9]+[.,]")) -> NumericInputResult.Incomplete

        !value.matches(Regex("[0-9]+([.,][0-9]+)?")) ->
            NumericInputResult.Invalid(R.string.nonnegative_decimal)

        else -> NumericInputResult.Valid(value.replace(',', '.').toBigDecimal())
    }
}

internal fun parseWholeMinutesToSeconds(minutes: String): Long? =
    minutes.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 60 }?.times(60)

fun validatedSetValues(load: String, reps: String): Pair<String?, Int?> {
    val weight = when (val parsed = parseDecimalInput(load)) {
        NumericInputResult.Missing -> null
        is NumericInputResult.Valid -> parsed.value.stripTrailingZeros().toPlainString()
        else -> throw UiException(R.string.invalid_load)
    }
    val repetitions = if (reps.isBlank()) {
        null
    } else {
        reps.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: throw UiException(R.string.invalid_reps)
    }
    requireUi(weight != null || repetitions != null, R.string.set_load_or_reps)
    return weight to repetitions
}

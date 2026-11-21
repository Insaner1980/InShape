package com.emmah.inshape.ui

import android.content.Context
import androidx.annotation.StringRes
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal class UiException(val text: Int, val quantity: Int? = null) : IllegalArgumentException()

@OptIn(ExperimentalContracts::class)
internal fun requireUi(condition: Boolean, @StringRes text: Int) {
    contract { returns() implies condition }
    if (!condition) throw UiException(text)
}

internal fun Throwable.uiMessage(context: Context, @StringRes fallback: Int): String {
    val error = this as? UiException ?: return context.getString(fallback)
    return error.quantity?.let { context.resources.getQuantityString(error.text, it, it) }
        ?: context.getString(error.text)
}

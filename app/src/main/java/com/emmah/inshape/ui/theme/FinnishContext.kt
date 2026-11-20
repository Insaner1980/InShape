package com.emmah.inshape.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList

internal fun Context.withFinnishLocale(): Context =
    createConfigurationContext(Configuration().apply { setLocales(LocaleList(finnishLocale)) })

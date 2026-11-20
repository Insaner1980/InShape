package com.emmah.inshape

import com.emmah.inshape.ui.theme.withFinnishLocale
import android.content.Context
import android.app.Application

class InShapeApplication : Application() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withFinnishLocale())
    }

    val container by lazy { AppContainer(this) }
}

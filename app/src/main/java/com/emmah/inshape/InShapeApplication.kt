package com.emmah.inshape

import android.app.Application
import android.content.Context
import com.emmah.inshape.ui.theme.withFinnishLocale

class InShapeApplication : Application() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withFinnishLocale())
    }

    val container by lazy { AppContainer(this) }
}

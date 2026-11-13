package com.emmah.inshape

import android.app.Application

class InShapeApplication : Application() {
    val container by lazy { AppContainer(this) }
}

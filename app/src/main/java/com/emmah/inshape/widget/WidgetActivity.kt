package com.emmah.inshape.widget

import com.emmah.inshape.MainActivity

// Only the app's widget PendingIntent may enter this non-exported activity.
class WidgetActivity : MainActivity() {
    override val allowWorkoutStart = true
}

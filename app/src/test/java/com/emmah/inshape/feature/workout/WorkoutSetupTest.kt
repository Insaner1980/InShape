package com.emmah.inshape.feature.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutSetupTest {
    @Test fun legacySetupSeparatesUnfamiliarLabelsFromShortValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            listOf("Reisituki" to "2", "Jalkatuki" to "ylös", "Tuki" to "matala asento"),
            workoutSetupLabels(context, "Reisituki 2, Jalkatuki ylös, Tuki: matala asento")
        )
    }
}

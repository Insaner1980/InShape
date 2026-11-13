package com.emmah.inshape.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeSettingsTest {
    @Test fun defaultsToSystemAndEveryChoiceSurvivesReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = ThemeSettings(context)
        assertEquals(ThemeMode.System, settings.mode.value)
        listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System).forEach { mode ->
            settings.set(mode)
            assertEquals(mode, settings.mode.value)
            assertEquals(mode, ThemeSettings(context).mode.value)
        }
    }
    @Test fun unknownStoredChoiceUsesSystem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE).edit().putString("theme_mode", "unknown").commit()
        assertEquals(ThemeMode.System, ThemeSettings(context).mode.value)
    }
}

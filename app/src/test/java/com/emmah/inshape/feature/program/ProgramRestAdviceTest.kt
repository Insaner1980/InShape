package com.emmah.inshape.feature.program

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.emmah.inshape.R
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h800dp")
class ProgramRestAdviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun displaysSingularAt320Dp() = assertAdvice(1, "Vähintään 1 lepopäivä treenien välissä", fontScale = 1f)

    @Test fun displaysPluralAt200PercentFontScale() = assertAdvice(2, "Vähintään 2 lepopäivää treenien välissä", fontScale = 2f)

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun displaysPluralAt320DpAnd200PercentFontScale() = assertAdvice(2, "Vähintään 2 lepopäivää treenien välissä", fontScale = 2f)

    private fun assertAdvice(days: Int, expected: String, fontScale: Float) {
        val catalog = compose.activity.assets.open("programs/beginner_fi_v1.json").bufferedReader().use {
            ProgramCatalog.read(it.readText())
        }
        val scheduled = catalog.copy(scheduling = catalog.scheduling.copy(restDaysBetweenStrengthSessionsMinimum = days))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                InShapeTheme {
                    ProgramScreen(scheduled, scheduled.programs.first(), false, {}, {}, {}, { _, _ -> })
                }
            }
        }
        compose.onNodeWithText(ui(R.string.program_more_information)).performScrollTo().performClick()
        compose.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
    }
}

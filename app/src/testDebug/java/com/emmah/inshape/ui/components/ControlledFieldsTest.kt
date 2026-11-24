package com.emmah.inshape.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.InShapeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ControlledFieldsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactFieldsCanBeEditedAt320Dp() = verifyEditing(320, 1f)

    @Test fun compactFieldsCanBeEditedAtDoubleFontScale() = verifyEditing(411, 2f)

    @Test fun compactFieldsCanBeEditedAt320DpAndDoubleFontScale() = verifyEditing(320, 2f)

    private fun verifyEditing(width: Int, scale: Float) {
        val search = mutableStateOf("")
        val load = mutableStateOf("2.5")
        val userQueries = mutableListOf<String>()
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 640.dp)) then
                    DeviceConfigurationOverride.FontScale(scale)
            ) {
                InShapeTheme {
                    Column {
                        SearchField(search.value, {
                            userQueries += it
                            search.value = it
                        }, "Hae liikettä")
                        NumberField(load.value, { load.value = it }, "Kuorma", decimal = true, compact = true)
                    }
                }
            }
        }
        val searchField = compose.onNodeWithContentDescription("Hae liikettä")
        val loadField = compose.onNodeWithContentDescription("Kuorma")
        searchField.assertIsDisplayed().performTextReplacement("soutu")
        loadField.assertIsDisplayed().assertTextContains("2,5").performTextReplacement("12,5")
        compose.runOnIdle {
            assertEquals("soutu", search.value)
            assertEquals("12,5", load.value)
            search.value = ""
            load.value = "20.5"
        }
        searchField.assertTextContains("")
        loadField.assertTextContains("20,5")
        compose.runOnIdle {
            assertEquals(listOf("soutu"), userQueries)
        }
    }
}

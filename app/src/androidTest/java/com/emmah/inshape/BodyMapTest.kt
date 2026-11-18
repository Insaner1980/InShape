package com.emmah.inshape

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.feature.checkin.BodyMap
import com.emmah.inshape.feature.checkin.bodyRegionAt
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BodyMapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun frontAndBackKeepAnatomicalRightKnee() {
        assertEquals("right_knee", bodyRegionAt(42f, 168f, false))
        assertEquals("right_knee", bodyRegionAt(57f, 168f, true))
        assertEquals("left_knee", bodyRegionAt(57f, 168f, false))
        assertEquals("lower_back", bodyRegionAt(50f, 88f, true))
        assertNull(bodyRegionAt(5f, 5f, true))
    }

    @Test fun tapTargetsIncludeTheAnatomicalShapeAndItsNearbyArea() {
        val selected = mutableListOf<String>()
        var canvasHeight = 0f
        compose.setContent {
            canvasHeight = with(LocalDensity.current) { Dimens.bodyHeight.toPx() }
            InShapeTheme(ThemeMode.Light) {
                BodyMap(
                    back = false,
                    observations = listOf(BodyObservation("check-in", "right_knee", stiffness = 2)),
                    onSelect = { selected.add(it) },
                    modifier = Modifier.testTag("body-map")
                )
            }
        }
        fun tapAt(x: Float, y: Float) {
            compose.onNodeWithTag("body-map").performTouchInput {
                val scale = minOf(width / 100f, canvasHeight / 240f)
                click(Offset((width - scale * 100f) / 2f + x * scale, y * scale))
            }
        }
        tapAt(42f, 168f)
        compose.runOnIdle { assertEquals(listOf("right_knee"), selected) }
        assertNull(bodyRegionAt(66f, 168f, false))
        tapAt(66f, 168f)
        compose.runOnIdle { assertEquals(listOf("right_knee", "left_knee"), selected) }
        tapAt(8f, 168f)
        compose.runOnIdle { assertEquals(listOf("right_knee", "left_knee"), selected) }
    }
}

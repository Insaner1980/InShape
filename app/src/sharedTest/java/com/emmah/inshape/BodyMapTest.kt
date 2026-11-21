package com.emmah.inshape

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.emmah.inshape.feature.checkin.bodyArtwork
import com.emmah.inshape.feature.checkin.bodyRegionAt
import com.emmah.inshape.feature.checkin.bodyRegions
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
        assertEquals("right_knee", bodyRegionAt(98f, 285f, false))
        assertEquals("right_knee", bodyRegionAt(142f, 285f, true))
        assertEquals("left_knee", bodyRegionAt(142f, 285f, false))
        assertEquals("lower_back", bodyRegionAt(120f, 150f, true))
        assertNull(bodyRegionAt(120f, 52f, false))
        assertNull(bodyRegionAt(120f, 52f, true))
        assertEquals("neck", bodyRegionAt(120f, 66f, false))
        assertNull(bodyRegionAt(5f, 5f, true))
    }

    @Test fun everyRecordedRegionHasAnExactHitOnTheCorrectFace() {
        val reached = mutableSetOf<String>()
        for (back in listOf(false, true)) {
            val shapes = bodyArtwork(back).filter { it.id != null }
            assertEquals(21, shapes.size)
            for (shape in shapes) {
                val id = checkNotNull(shape.id)
                val bounds = shape.bounds
                val hit = (bounds.left.toInt()..bounds.right.toInt()).any { x ->
                    (bounds.top.toInt()..bounds.bottom.toInt()).any { y ->
                        bodyRegionAt(x.toFloat(), y.toFloat(), back) == id
                    }
                }
                check(hit) { "$id is unreachable on back=$back" }
                reached += id
            }
        }
        assertEquals(bodyRegions.map { it.id }.toSet(), reached)
    }

    @Test fun tapTargetsIncludeTheAnatomicalShapeAndItsNearbyArea() {
        val selected = mutableListOf<String>()
        var canvasHeight = 0f
        compose.setContent {
            canvasHeight = with(LocalDensity.current) { Dimens.bodyHeight.toPx() }
            InShapeTheme(ThemeMode.Light) {
                Column(Modifier.safeDrawingPadding()) {
                    BodyMap(
                        back = false,
                        observations = listOf(BodyObservation("check-in", "right_knee", stiffness = 2)),
                        onSelect = { selected.add(it) },
                        modifier = Modifier.testTag("body-map")
                    )
                }
            }
        }
        fun tapAt(x: Float, y: Float) {
            compose.onNodeWithTag("body-map").performTouchInput {
                val scale = minOf(width / 240f, canvasHeight / 392f)
                click(Offset((width - scale * 240f) / 2f + x * scale, (canvasHeight - scale * 392f) / 2f + y * scale))
            }
        }
        tapAt(98f, 285f)
        compose.runOnIdle { assertEquals(listOf("right_knee"), selected) }
        assertNull(bodyRegionAt(157f, 285f, false))
        tapAt(157f, 285f)
        compose.runOnIdle { assertEquals(listOf("right_knee", "left_knee"), selected) }
        tapAt(40f, 285f)
        tapAt(120f, 52f)
        compose.runOnIdle { assertEquals(listOf("right_knee", "left_knee"), selected) }
        tapAt(120f, 66f)
        compose.runOnIdle { assertEquals(listOf("right_knee", "left_knee", "neck"), selected) }
    }
}

package com.emmah.inshape

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.emmah.inshape.feature.music.PlaybackBars
import com.emmah.inshape.feature.music.PlaybackRibbon
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeMode
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlaybackVisualsTest {
    private val durationScale = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }

    @get:Rule val compose = createComposeRule(effectContext = durationScale)

    @Test
    fun graphicsMoveDuringPlaybackSettleOnPauseAndHonorReducedMotion() {
        var playing by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            InShapeTheme(ThemeMode.Dark) {
                Column(Modifier.width(280.dp).background(InShapeTheme.colors.background)) {
                    PlaybackRibbon(playing, Modifier.testTag("Ribbon"))
                    PlaybackBars(playing, Modifier.testTag("Bars"))
                }
            }
        }

        val resting = captureAfter(64)
        compose.runOnIdle { playing = true }
        val playingFrame = captureAfter(320)
        assertFramesMatch(playingFrame, captureAfter(192), false)

        compose.runOnIdle { playing = false }
        val settled = captureAfter(352)
        assertFramesMatch(resting, settled, true)
        assertFramesMatch(settled, captureAfter(192), true)

        compose.runOnIdle { playing = true }
        captureAfter(320)
        compose.runOnIdle { durationScale.scaleFactor = 0f }
        val reducedMotion = captureAfter(48)
        assertFramesMatch(resting, reducedMotion, false)
        assertFramesMatch(reducedMotion, captureAfter(192), true)

        compose.runOnIdle { playing = false }
        assertFramesMatch(resting, captureAfter(48), true)
    }

    private val graphicTags = listOf("Ribbon", "Bars")

    private fun captureAfter(millis: Long): List<Bitmap> {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
        return graphicTags.map { compose.onNodeWithTag(it).captureToImage().asAndroidBitmap() }
    }

    private fun Bitmap.argbPixels() = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

    // Antialiased edges can round by one RGB step between otherwise identical captures.
    private fun sameRasterColor(first: Int, second: Int): Boolean = Color.alpha(first) == Color.alpha(second) &&
        abs(Color.red(first) - Color.red(second)) <= 1 &&
        abs(Color.green(first) - Color.green(second)) <= 1 &&
        abs(Color.blue(first) - Color.blue(second)) <= 1

    private fun assertFramesMatch(first: List<Bitmap>, second: List<Bitmap>, expected: Boolean) {
        graphicTags.forEachIndexed { index, tag ->
            val before = first[index]
            val after = second[index]
            assertEquals("Frame width for $tag", before.width, after.width)
            assertEquals("Frame height for $tag", before.height, after.height)
            val a = before.argbPixels()
            val b = after.argbPixels()
            assertEquals("Matching pixels for $tag", expected, a.indices.all { sameRasterColor(a[it], b[it]) })
        }
    }
}

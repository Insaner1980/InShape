package com.emmah.inshape

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.demo.DemoActivity
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val themeSettings = ThemeSettings(context)
    private val originalTheme = themeSettings.mode.value
    private val selected = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    private val scrolling = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @After
    fun restoreAppearance() = themeSettings.set(originalTheme)

    private fun launch(state: String): ActivityScenario<DemoActivity> {
        InstrumentationRegistry.getArguments().getString("themeMode")?.let { themeSettings.set(ThemeMode.valueOf(it)) }
        return ActivityScenario.launch(Intent(context, DemoActivity::class.java).putExtra("state", state))
    }

    private fun waitForText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun playlistBodyOpensDetailAndPlayButtonStaysOnHome() {
        launch("MusicHome").use {
            waitForText("Night Drive")
            listOf("Today", "History", "Library", "Music").forEach { tab ->
                compose.onNode(hasText(tab) and selected).assertIsDisplayed()
            }
            compose.onNodeWithContentDescription("Play Night Drive").assertIsDisplayed().performClick()
            compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
            compose.onNodeWithText("After Hours").assertIsDisplayed()
            compose.onNodeWithText("Night Drive").performClick()
            compose.onNode(hasText("Music") and selected).assertIsSelected()
            compose.onAllNodesWithContentDescription("Back").assertCountEquals(1)
            compose.onNode(scrolling).performScrollToNode(hasText("First Light"))
            compose.onNodeWithText("First Light").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Night Drive").assertIsDisplayed()
            compose.onNode(hasText("Library") and selected).performClick()
            compose.onNode(hasText("Music") and selected).performClick()
            compose.onNodeWithText("Night Drive").assertIsDisplayed()
        }
    }

    @Test
    fun musicMiniPlayerOpensNowPlayingAndKeepsPlaybackInSync() {
        launch("MusicHome").use {
            waitForText("Night Drive")
            compose.onNodeWithContentDescription("Play Night Drive").performClick()
            compose.onNodeWithText("After Hours").performClick()
            compose.onNodeWithText("Now playing").assertIsDisplayed()
            compose.onNodeWithContentDescription("Pause").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Play").performClick()
            compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
            compose.onNodeWithContentDescription("Next song").performClick()
            compose.onNode(hasText("Tunnel Vision") and !hasClickAction()).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Night Drive").assertIsDisplayed()
            compose.onNodeWithText("Tunnel Vision").assertIsDisplayed()
        }
    }

    @Test
    fun workoutChooserPreservesExerciseAndPlaybackOnCancelAndCanSwitchPlaylist() {
        launch("WorkoutWithMusic").use {
            waitForText("After Hours")
            compose.onNodeWithText("After Hours").assertIsDisplayed()
            compose.onNodeWithContentDescription("Pause").performClick()
            compose.onNodeWithContentDescription("Play").performClick()
            compose.onNodeWithContentDescription("Next song").performClick()
            compose.onNodeWithText("Tunnel Vision").assertIsDisplayed().performClick()
            compose.onNodeWithText("Night Drive").performClick()
            val dialogBack = hasContentDescription("Back") and hasAnyAncestor(isDialog())
            compose.onNode(dialogBack).performClick()
            compose.onNode(dialogBack).performClick()
            compose.onNodeWithText("Tunnel Vision").assertIsDisplayed()
            compose.onNodeWithContentDescription("Music").performClick()
            compose.onNode(scrolling and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Heavy Rotation"))
            compose.onNodeWithContentDescription("Play Heavy Rotation").performClick()
            compose.onNodeWithText("Rotation 1").assertIsDisplayed()
            compose.onNodeWithText("Lat pulldown").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Exercise done").assertIsDisplayed()
        }
    }

    @Test
    fun completingSetTwoBringsTheThirdSetAboveTheMiniPlayer() {
        launch("WorkoutWithMusic").use {
            waitForText("After Hours")
            val third = compose.onNodeWithTag("workout-set-3")
            compose.onNodeWithContentDescription("Undo set 2").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Complete set 2").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Complete set 2").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Undo set 2").fetchSemanticsNodes().isNotEmpty() &&
                    (compose.onAllNodesWithTag("workout-set-3").fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.height ?: 0f) > 0f
            }
            third.assertIsDisplayed()
            val bounds = third.getUnclippedBoundsInRoot()
            val player = compose.onNodeWithText("After Hours").getUnclippedBoundsInRoot()
            assertTrue("The whole set needs to be above the player", bounds.bottom <= player.top)
            compose.onNodeWithContentDescription("Complete set 3").assertIsDisplayed()
        }
    }
}

package com.emmah.inshape

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.demo.DemoMusic
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val themeSettings = ThemeSettings(context)
    private val originalTheme = themeSettings.mode.value
    private val selected = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    private val scrolling = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private val databaseName = "music-ui-${newId()}.db"
    private lateinit var container: AppContainer

    @After
    fun cleanup() {
        themeSettings.set(originalTheme)
        if (::container.isInitialized) {
            container.close()
            context.deleteDatabase(databaseName)
            container.storageRoot.deleteRecursively()
        }
    }

    private fun launch(state: String) {
        val theme = InstrumentationRegistry.getArguments().getString("themeMode")?.let(ThemeMode::valueOf) ?: originalTheme
        container = AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false)
        runBlocking { DemoFixtures.load(container, state) }
        compose.setContent {
            InShapeTheme(theme) {
                AppNavigation(
                    container,
                    initialRoute = if (state == "MusicHome") "Today" else DemoFixtures.route(state),
                    musicFixture = DemoMusic.catalog,
                    initialMusic = if (state == "WorkoutWithMusic") DemoMusic.catalog.playlists.first().tracks else emptyList()
                )
            }
        }
        if (state == "MusicHome") {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(ui(R.string.music)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(ui(R.string.music)).performClick()
        }
    }

    private fun waitForText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun playlistBodyOpensDetailAndPlayButtonStaysOnHome() {
        launch("MusicHome")
        waitForText("Yöajo")
        listOf("Today", "History", "Library", "Music").forEach { tab ->
            compose.onNode(hasText(tab) and selected).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription(ui(R.string.play_named, "Yöajo")).assertIsDisplayed().performClick()
        compose.onAllNodesWithContentDescription(ui(R.string.back)).assertCountEquals(1)
        compose.onNodeWithText("Illan jälkeen").assertIsDisplayed()
        compose.onNodeWithText("Yöajo").performClick()
        compose.onAllNodesWithContentDescription(ui(R.string.back)).assertCountEquals(1)
        compose.onNode(scrolling).performScrollToNode(hasText("Aamunkoitto"))
        compose.onNodeWithText("Aamunkoitto").assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.onNodeWithText("Yöajo").assertIsDisplayed()

        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        listOf(R.string.today, R.string.history, R.string.library).map { ui(it) }.forEach { tab ->
            compose.onNode(hasText(tab) and selected).assertIsDisplayed()
        }
        for (tab in listOf(R.string.history, R.string.library, R.string.today).map { ui(it) }) {
            compose.onNode(hasText(tab) and selected).performClick()
            compose.onNode(hasText(tab) and selected).assertIsSelected()
            compose.onNodeWithText("Illan jälkeen").assertIsDisplayed()
        }
        compose.onNodeWithText("Illan jälkeen").performClick()
        compose.onNodeWithText("Yöajo").assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.music)).performClick()
        compose.onNodeWithText("Yöajo").assertIsDisplayed()
    }

    @Test
    fun musicMiniPlayerOpensNowPlayingAndKeepsPlaybackInSync() {
        launch("MusicHome")
        waitForText("Yöajo")
        compose.onNodeWithContentDescription(ui(R.string.play_named, "Yöajo")).performClick()
        compose.onNodeWithText("Illan jälkeen").performClick()
        compose.onNodeWithText(ui(R.string.now_playing)).assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.pause)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(ui(R.string.seek)).performSemanticsAction(SemanticsActions.SetProgress) { seek ->
            check(seek(0.5f))
        }
        compose.onNodeWithText("1:51").assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.play)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.pause)).assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.next_song)).performClick()
        compose.onNode(hasText("Tunnelinäkö") and !hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.onNodeWithText("Yöajo").assertIsDisplayed()
        compose.onNodeWithText("Tunnelinäkö").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(ui(R.string.pause)).performScrollTo().performClick()
        compose.onNode(scrolling).performScrollToNode(hasText(ui(R.string.clear_queue)))
        compose.onNodeWithText(ui(R.string.clear_queue)).performClick()
        compose.onNodeWithText(ui(R.string.nothing_playing)).assertExists()
    }

    @Test
    fun workoutHeaderOpensNowPlayingAndPreservesTheCurrentExercise() {
        launch("WorkoutWithMusic")
        val current = ui(R.string.exercise_lat_pulldown)
        waitForText(current)
        compose.onNodeWithText("Illan jälkeen").assertDoesNotExist()
        compose.onNodeWithContentDescription(ui(R.string.now_playing)).performClick()
        compose.onNodeWithText(ui(R.string.now_playing)).assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.pause)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(ui(R.string.play)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.next_song)).performClick()
        compose.onNode(hasText("Tunnelinäkö") and !hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.onNodeWithText("Tunnelinäkö").assertDoesNotExist()
        compose.onNodeWithText(current).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.exercise_done)).assertIsDisplayed()
    }

    @Test
    fun completingSetTwoBringsTheThirdSetAboveTheFixedAction() {
        launch("WorkoutWithMusic")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(ui(R.string.undo_set_number, 2)).fetchSemanticsNodes().isNotEmpty()
        }
        val third = compose.onNodeWithTag("workout-set-3")
        compose.onNodeWithContentDescription(ui(R.string.undo_set_number, 2)).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(ui(R.string.complete_set_number, 2)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(ui(R.string.complete_set_number, 2)).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(ui(R.string.undo_set_number, 2)).fetchSemanticsNodes().isNotEmpty() &&
                (compose.onAllNodesWithTag("workout-set-3").fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.height ?: 0f) > 0f
        }
        third.assertIsDisplayed()
        val bounds = third.getUnclippedBoundsInRoot()
        val lowerEdge = compose.onNode(scrolling).getUnclippedBoundsInRoot().bottom
        assertTrue("The whole set needs to be above the fixed controls", bounds.bottom <= lowerEdge)
        compose.onNodeWithContentDescription(ui(R.string.complete_set_number, 3)).assertIsDisplayed()
    }
}

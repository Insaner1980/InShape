package com.emmah.inshape

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.*
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class RedesignInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "redesign-${newId()}.db"
    private lateinit var container: AppContainer
    private val originalTheme = ThemeSettings(context).mode.value
    private fun launch(state: String, route: String) {
        container = AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false)
        runBlocking { DemoFixtures.load(container, state) }
        compose.setContent {
            val mode by container.themeSettings.mode.collectAsState()
            InShapeTheme(mode) { AppNavigation(container, initialRoute = route) }
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        if (::container.isInitialized) { container.themeSettings.set(originalTheme); container.close(); context.deleteDatabase(databaseName); container.storageRoot.deleteRecursively() }
    }
    private fun click(text: String) {
        val node = compose.onAllNodesWithText(text).onFirst()
        if (compose.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        node.performClick(); compose.waitForIdle()
    }
    private fun snapshot() = runBlocking { container.repository.snapshot() }

    @Test fun themeChangesImmediatelyAndGoalCanBeOffWhileRemindersExpand() {
        launch("SettingsConfigured", "Settings")
        click("Dark")
        compose.onNodeWithText("Dark").assertIsSelected()
        assertEquals(ThemeMode.Dark, container.themeSettings.mode.value)
        assertEquals(DarkColors.background.toArgb(), compose.onRoot().captureToImage().toPixelMap()[0, 0].toArgb())
        click("Light")
        assertEquals(LightColors.background.toArgb(), compose.onRoot().captureToImage().toPixelMap()[0, 0].toArgb())
        repeat(3) { compose.onNodeWithContentDescription("Decrease weekly goal").performScrollTo().performClick(); compose.waitForIdle() }
        compose.waitUntil { snapshot().preferences.weeklyGoal == null }
        compose.onNodeWithContentDescription("Decrease weekly goal").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase weekly goal").performClick()
        compose.waitUntil { snapshot().preferences.weeklyGoal == 3 }
        compose.onNodeWithContentDescription("Enable workout reminders").performScrollTo().performClick()
        compose.waitUntil { !snapshot().preferences.remindersEnabled }
        compose.onNodeWithText("Time").assertDoesNotExist()
        compose.onNodeWithContentDescription("Enable workout reminders").performClick()
        compose.waitUntil { snapshot().preferences.remindersEnabled }
        compose.onNodeWithText("Time").assertExists()
    }

    @Test fun workoutNoteSetCompletionReopeningSwapDeferAndSkipStayConnected() {
        launch("ActiveWorkoutPartiallyDone", "workout/${DemoFixtures.activeId}")
        click("Add note"); compose.onNodeWithText("Note for this exercise").assertExists()
        click("Hide note"); compose.onNodeWithText("Note for this exercise").assertDoesNotExist()
        click("Add note")
        compose.onNodeWithText("Note for this exercise").performScrollTo().performTextReplacement("Keep shoulders down")
        compose.onNodeWithContentDescription("Complete set 3").performScrollTo().performClick()
        click("Mark done")
        val original = DemoFixtures.id("active:item:4")
        compose.waitUntil { snapshot().items.find { it.id == original }?.disposition == "DONE" }
        assertEquals("Keep shoulders down", snapshot().items.first { it.id == original }.note)
        compose.onNodeWithContentDescription("Reopen Lat pulldown").performScrollTo().performClick()
        compose.waitUntil { snapshot().active?.row?.currentItemId == original }
        click("Swap"); click("Assisted pull-up machine")
        compose.waitUntil { snapshot().items.any { it.alternativeOf == original } }
        val replacement = snapshot().active!!.row.currentItemId
        click("Do later")
        compose.waitUntil { snapshot().active!!.row.currentItemId != replacement }
        val skipped = snapshot().active!!.row.currentItemId
        click("Skip today")
        compose.waitUntil { snapshot().items.find { it.id == skipped }?.disposition == "SKIPPED" }
        click("Finish")
        compose.onNodeWithText("Save workout").assertIsDisplayed()
    }

    @Test fun historyDatePickerAndLibraryFiltersRemainReachable() {
        launch("HistoryNovember", "History")
        click("November 2026")
        compose.onAllNodes(hasText("Select date") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Show whole month").assertExists()
        click("Show whole month")
        compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        compose.onNodeWithText("No workouts in this period").assertExists()
        compose.onAllNodesWithText("Library").onLast().performClick()
        click("Exercises")
        compose.onNodeWithContentDescription("Search exercises").performTextReplacement("Lat pulldown")
        compose.onNode(hasText("Lat pulldown") and !hasSetTextAction()).assertExists()
        compose.onNodeWithContentDescription("Favorites only").performClick()
        compose.onNode(hasText("Lat pulldown") and !hasSetTextAction()).assertExists().performClick()
        compose.onNodeWithContentDescription("Favorite").assertExists()
    }
}

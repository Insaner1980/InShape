package com.emmah.inshape

import android.content.Context
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

class ProgressHistoryCoverageTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "progress-history-${newId()}.db"
    private lateinit var container: AppContainer
    private lateinit var focusManager: FocusManager

    @After fun cleanup() {
        if (::container.isInitialized) {
            container.close()
            context.deleteDatabase(databaseName)
            container.storageRoot.deleteRecursively()
        }
    }

    @Test fun weeklyCountsFollowPeriodAndAverageExcludesCurrentWeek() {
        launch("ProgressWithHistory", "Weekly counts")
        compose.onNodeWithText("1.2").assertExists()
        val disclosure = compose.onNodeWithText("Weekly counts")
        disclosure.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.clickText("Weekly counts")
        disclosure.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNode(hasText("Week of 21 Sep") and hasText("1 workout")).assertExists()
        compose.onNode(hasText("Week of 9 Nov") and hasText("2 workouts")).assertExists()

        compose.clickText("4 weeks")
        compose.onNodeWithText("2.3").assertExists()
        compose.onNode(hasText("Week of 19 Oct") and hasText("3 workouts")).assertExists()
        compose.onNode(hasText("Week of 21 Sep") and hasText("1 workout")).assertDoesNotExist()
        compose.onNode(hasText("Week of 9 Nov") and hasText("2 workouts")).assertExists()

        compose.clickText("All")
        compose.onNodeWithText("2.0").assertExists()
        compose.onNode(hasText("Week of 21 Sep") and hasText("1 workout")).assertExists()
        compose.clickText("Weekly counts")
        compose.onNode(hasText("Week of 9 Nov") and hasText("2 workouts")).assertDoesNotExist()
    }

    @Test fun exerciseComparisonShowsMissingFixedLoadAndMinimumRepsResults() {
        launch("ProgressWithHistory", "Measure")
        select("Measure, Heaviest weight", "Reps at a set weight")
        compose.onNodeWithText("Enter a load to compare repetitions.").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Compare at (kg)").performScrollTo().performTextReplacement("22.5")
        compose.onNodeWithText("23 Oct: 10 reps").assertExists()
        compose.clickText("Data values")
        compose.onNode(hasClickAction() and hasText("13 Nov") and hasText("Not recorded"))
            .performScrollTo().performClick()
        compose.onNodeWithText("13 Nov: Not recorded").assertExists()

        select("Measure, Reps at a set weight", "Weight for a rep target")
        compose.onNodeWithText("13 Nov: 27.5 kg").assertExists()
        compose.onNodeWithText("Minimum repetitions").performScrollTo().performTextReplacement("11")
        compose.runOnIdle { focusManager.clearFocus() }
        compose.onNodeWithText("11+ reps").assertDoesNotExist()
        compose.onNodeWithText("No recorded values for this comparison.").performScrollTo().assertIsDisplayed()
    }

    @Test fun bodyObservationValuesFollowTheSelectedCheckInPhase() {
        launch("ProgressWithHistory", "Pain and stiffness")
        compose.clickText("Pain and stiffness")
        compose.onNodeWithText("Pain, mild").assertExists()
        compose.onNodeWithText("Stiffness, medium").assertExists()
        compose.clickText("Values")
        compose.onAllNodesWithText("Pain mild").onFirst().assertExists()

        compose.clickText("Before")
        compose.onNodeWithText("Pain, medium").assertExists()
        compose.onNodeWithText("Stiffness, strong").assertExists()
        compose.onAllNodesWithText("Stiffness strong").onFirst().assertExists()
        compose.onAllNodesWithText("Pain mild").onFirst().assertDoesNotExist()

        compose.clickText("Next day")
        compose.onNodeWithText("No observations for lower back in this period.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stiffness, strong").assertDoesNotExist()
    }

    @Test fun calendarSeparatesMissingDurationFromNoteOnlyWorkout() {
        launch("HistoryNovember", "Time from 4 of 5 workouts.") { app ->
            val dao = app.database.journal()
            val completed = app.repository.snapshot().workouts.single { it.id == DemoFixtures.id("november:4") }
            dao.put(
                completed.copy(
                    start = null,
                    end = null,
                    startOffset = null,
                    endOffset = null,
                    durationSeconds = null,
                    durationSource = "UNKNOWN",
                    timingConfirmed = false
                )
            )
            val noteTime = "2026-11-04T18:00:00Z"
            dao.insertWorkout(
                WorkoutEntity(
                    title = "Recovery notes",
                    state = "COMPLETED",
                    activeKey = null,
                    journalDate = "2026-11-04",
                    zoneId = DemoFixtures.clock.zone.id,
                    note = "Rested after the morning session.",
                    createdAt = noteTime,
                    modifiedAt = noteTime
                )
            )
        }
        compose.onNodeWithContentDescription("Wednesday 4 November, workout recorded")
            .performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Time from 0 of 1 workouts.").assertExists()
        compose.onNodeWithText("Wed 4 Nov, note only, not counted").assertExists()

        compose.clickText("Recovery notes")
        compose.onNodeWithText("Duration not recorded").assertExists()
        compose.onNodeWithText("Note-only entry, not counted as attendance").assertExists()
        compose.onNodeWithText("Rested after the morning session.").assertExists()
    }

    @Test fun exportDateRangeSelectsOnlyWorkoutsWithinBothEndpoints() {
        launch("HistoryNovember", "Full body A", route = "Export")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.clickText("Select period")
        val confirm = compose.onNode(hasText("Apply") and hasClickAction() and hasAnyAncestor(isDialog()))
        confirm.assertIsNotEnabled()

        compose.onNode(hasClickAction() and hasText("Wednesday, 4 November 2026")).performClick()
        confirm.assertIsNotEnabled()
        compose.onNode(hasClickAction() and hasText("Monday, 9 November 2026")).performClick()
        confirm.assertIsEnabled().performClick()

        compose.onNodeWithText("4 Nov 2026 to 9 Nov 2026").assertExists()
        compose.onNodeWithContentDescription("Select Full body A Mon 2 Nov").assertIsOff()
        compose.onNodeWithContentDescription("Select Full body A Wed 4 Nov").assertIsOn()
        compose.onNodeWithContentDescription("Select Full body A Fri 6 Nov").assertIsOn()
        compose.onNodeWithContentDescription("Select Full body A Mon 9 Nov").assertIsOn()
        compose.onNodeWithContentDescription("Select Full body A Wed 11 Nov").assertIsOff()
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    private fun launch(
        state: String,
        ready: String,
        route: String = DemoFixtures.route(state),
        prepare: suspend (AppContainer) -> Unit = {}
    ) {
        container = AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false)
        runBlocking {
            DemoFixtures.load(container, state)
            prepare(container)
        }
        compose.setContent {
            focusManager = LocalFocusManager.current
            InShapeTheme { AppNavigation(container, initialRoute = route) }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun select(description: String, option: String) {
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
        compose.clickText(option)
    }
}

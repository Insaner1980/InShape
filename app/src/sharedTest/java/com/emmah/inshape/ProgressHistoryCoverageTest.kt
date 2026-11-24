package com.emmah.inshape

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.shortDate
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressHistoryCoverageTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "progress-history-${newId()}.db"
    private lateinit var container: AppContainer

    @After fun cleanup() {
        if (::container.isInitialized) {
            container.close()
            context.deleteDatabase(databaseName)
            container.storageRoot.deleteRecursively()
        }
    }

    @Test fun weeklyCountsFollowPeriodAndAverageExcludesCurrentWeek() {
        launch("ProgressWithHistory", ui(R.string.weekly_counts))
        compose.onNodeWithText("1,2").assertExists()
        val disclosure = compose.onNodeWithText(ui(R.string.weekly_counts))
        disclosure.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ui(R.string.collapsed)))
        compose.clickText(ui(R.string.weekly_counts))
        disclosure.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ui(R.string.expanded)))
        compose.onNode(hasText(ui(R.string.week_of_date, "21.9.")) and hasText(quantity(R.plurals.workout_count, 1))).assertExists()
        compose.onNode(hasText(ui(R.string.week_of_date, "9.11.")) and hasText(quantity(R.plurals.workout_count, 2))).assertExists()

        compose.clickText(ui(R.string.weeks_4))
        compose.onNodeWithText("2,3").assertExists()
        compose.onNode(hasText(ui(R.string.week_of_date, "19.10.")) and hasText(quantity(R.plurals.workout_count, 3))).assertExists()
        compose.onNode(hasText(ui(R.string.week_of_date, "21.9.")) and hasText(quantity(R.plurals.workout_count, 1))).assertDoesNotExist()
        compose.onNode(hasText(ui(R.string.week_of_date, "9.11.")) and hasText(quantity(R.plurals.workout_count, 2))).assertExists()

        compose.clickText(ui(R.string.all))
        compose.onNodeWithText("2,0").assertExists()
        compose.onNode(hasText(ui(R.string.week_of_date, "21.9.")) and hasText(quantity(R.plurals.workout_count, 1))).assertExists()
        compose.clickText(ui(R.string.weekly_counts))
        compose.onNode(hasText(ui(R.string.week_of_date, "9.11.")) and hasText(quantity(R.plurals.workout_count, 2))).assertDoesNotExist()
    }

    @Test fun exerciseComparisonUsesTheSelectedExercisesBasis() {
        launch("ProgressWithHistory", ui(R.string.values))
        compose.onNodeWithText("+7,5 kg").assertExists()
        compose.onNodeWithText(ui(R.string.best_so_far)).assertExists()
        compose.onNodeWithText(ui(R.string.progress_since_date, "25.9.")).assertExists()
        compose.onNodeWithText("27,5 kg").assertExists()
        compose.onNodeWithText(ui(R.string.measurement)).assertDoesNotExist()
        compose.onNodeWithText("13.11.: 27,5 kg").assertDoesNotExist()

        compose.clickText(ui(R.string.values))
        compose.onNode(hasText("23.10.") and hasText("22,5 kg")).assertExists()
        select(ui(R.string.exercise) + ", " + ui(R.string.exercise_lat_pulldown), ui(R.string.exercise_warm_up_bike))
        compose.onAllNodesWithText("5 min").onFirst().assertExists()
        compose.onNodeWithText("+7,5 kg").assertDoesNotExist()
    }

    @Test fun bodyObservationListShowsBothPhasesAndCanRevealOlderRows() {
        launch("ProgressWithHistory", ui(R.string.pain_and_stiffness))
        compose.clickText(ui(R.string.pain_and_stiffness))
        assertEquals(6, compose.onAllNodesWithText(ui(R.string.before_workout)).fetchSemanticsNodes().size)
        assertEquals(6, compose.onAllNodesWithText(ui(R.string.after_workout)).fetchSemanticsNodes().size)
        compose.onAllNodesWithText(
            ui(R.string.symptom_status, ui(R.string.pain), ui(R.string.body_level_mild).lowercase()) + ", " +
                ui(R.string.symptom_status, ui(R.string.stiffness), ui(R.string.body_level_medium).lowercase())
        ).onFirst().assertExists()
        compose.onAllNodesWithText(
            ui(R.string.symptom_status, ui(R.string.pain), ui(R.string.body_level_medium).lowercase()) + ", " +
                ui(R.string.symptom_status, ui(R.string.stiffness), ui(R.string.body_level_strong).lowercase())
        ).onFirst().assertExists()
        val oldDate = LocalDate.of(2026, 9, 25).shortDate()
        compose.onNodeWithText(oldDate).assertDoesNotExist()

        compose.clickText(ui(R.string.show_all))
        compose.onAllNodesWithText(oldDate).onFirst().assertExists()
        select(ui(R.string.body_area) + ", " + ui(R.string.region_lower_back), ui(R.string.region_neck))
        compose.onNodeWithText(
            ui(R.string.no_area_observations, ui(R.string.region_neck).lowercase())
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.show_all)).assertDoesNotExist()
    }

    @Test fun calendarSeparatesMissingDurationFromNoteOnlyWorkout() {
        val resources = testContext().resources
        launch("HistoryNovember", resources.getQuantityString(R.plurals.duration_coverage, 5, 4, 5)) { app ->
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
        compose.onNodeWithContentDescription(LocalDate.of(2026, 11, 4).shortDate() + ", " + ui(R.string.workout_recorded))
            .performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(resources.getQuantityString(R.plurals.duration_coverage, 1, 0, 1)).assertExists()
        compose.onNode(hasText("Recovery notes") and hasText(LocalDate.of(2026, 11, 4).shortDate())).assertExists()

        compose.clickText("Recovery notes")
        compose.onNodeWithText(ui(R.string.duration_not_recorded)).assertExists()
        compose.onNodeWithText(ui(R.string.note_only_workout)).assertExists()
        compose.onNodeWithText("Rested after the morning session.").assertExists()
    }

    @Test fun exportDateRangeSelectsOnlyWorkoutsWithinBothEndpoints() {
        launch("HistoryNovember", ui(R.string.all_workouts), route = "Export")
        compose.onNodeWithText(ui(R.string.save_to_files)).assertIsEnabled()
        compose.clickText(ui(R.string.period))
        val confirm = compose.onNode(hasText(ui(R.string.apply)) and hasClickAction() and hasAnyAncestor(isDialog()))
        confirm.assertIsNotEnabled()

        compose.onNode(hasClickAction() and hasText("keskiviikko 4.11.2026")).performClick()
        confirm.assertIsNotEnabled()
        compose.onNode(hasClickAction() and hasText("maanantai 9.11.2026")).performClick()
        confirm.assertIsEnabled().performClick()

        fun label(day: Int) = LocalDate.of(2026, 11, day).shortDate()
        compose.onNodeWithText(ui(R.string.date_range, label(4), label(9))).assertExists()
        compose.onNodeWithText(ui(R.string.save_to_files)).assertIsEnabled()
        compose.clickText(ui(R.string.period))
        compose.clickText(ui(R.string.all_workouts))
        compose.onNodeWithText(ui(R.string.all_workouts)).assertExists()
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

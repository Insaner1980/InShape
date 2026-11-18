package com.emmah.inshape

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.feature.workout.TimingDraft
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkoutTimingEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = DemoFixtures.activeId
    private val databaseName = "timing-${newId()}.db"
    private lateinit var container: AppContainer

    @After fun cleanup() {
        if (::container.isInitialized) {
            compose.activityRule.scenario.close()
            container.close()
            context.deleteDatabase(databaseName)
            container.storageRoot.deleteRecursively()
        }
    }

    @Test fun invalidRestoredDateCanBeReplacedFromThePicker() {
        val originalDate = "2026-11-13"
        val invalidDate = "2026-11-31"
        launchTimingDraft { it.copy(date = invalidDate) }

        compose.clickText("Workout date")
        compose.clickText("Cancel")
        compose.onNodeWithText("Invalid date").assertExists()
        compose.clickText("Workout date")
        compose.clickText("Done")
        compose.clickText("Save correction")

        compose.waitUntil(30_000) { workout().title == "Edited workout" }
        assertEquals(originalDate, workout().journalDate)
    }

    @Test fun bothOccurrencesOfAnOverlappingTimeCanBeSaved() {
        launchTimingDraft(compactLargeText = true) {
            it.copy(
                date = "2026-10-25",
                startDate = "2026-10-25",
                endDate = "2026-10-25",
                startTime = "03:30",
                endTime = "03:30",
                unknownTimes = false
            )
        }

        compose.clickText("Started")
        compose.clickText("Time")
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.clickText("UTC+03:00")
        compose.clickText("Done")
        compose.clickText("Finished")
        compose.clickText("Time")
        compose.clickText("UTC+02:00")
        compose.clickText("Done")
        compose.clickText("Save correction")

        compose.waitUntil(30_000) { workout().title == "Edited workout" }
        val saved = workout()
        assertEquals("2026-10-25T00:30:00Z", saved.start)
        assertEquals("2026-10-25T01:30:00Z", saved.end)
    }

    @Test fun liveFinishCanEndAtAnEarlierClockTimeDuringAnOverlap() {
        val start = "2026-10-25T00:45:12Z"
        val clock = Clock.fixed(Instant.parse("2026-10-25T01:50:34Z"), DemoFixtures.clock.zone)
        launch("finish/$id", compactLargeText = true, clock = clock) {
            val journal = container.database.journal()
            val row = journal.workouts().single { it.id == id }
            journal.put(row.copy(journalDate = "2026-10-25", start = start, startOffset = "+03:00"))
        }

        compose.clickText("Fix time")
        compose.clickText("Finished")
        compose.onNode(hasText("50") and hasSetTextAction()).performTextReplacement("30")
        compose.clickText("UTC+02:00")
        compose.onAllNodesWithText("Done").onLast().performClick()
        compose.clickText("Done")
        compose.clickText("Save workout")

        compose.waitUntil(30_000) { workout().state == "COMPLETED" }
        val saved = workout()
        assertEquals(start, saved.start)
        assertEquals("2026-10-25T01:30:00Z", saved.end)
        assertEquals(2_688L, saved.durationSeconds)
    }

    private fun launchTimingDraft(compactLargeText: Boolean = false, edit: (TimingDraft) -> TimingDraft) {
        launch("timing/$id/correct", compactLargeText) {
            val row = container.repository.snapshot().workout(id)!!.row
            val draft = edit(
                TimingDraft("Edited workout", row.journalDate, row.note, row.journalDate, "", row.journalDate, "", "", true, row.revision)
            )
            container.repository.saveEditorDraft(
                EditorDraft("timing:$id", "timing", Json.encodeToString(draft), container.clock.instant().toString())
            )
        }
    }

    private fun launch(route: String, compactLargeText: Boolean, clock: Clock = DemoFixtures.clock, prepare: suspend () -> Unit) {
        container = AppContainer(context, clock, databaseName, integrationsEnabled = false)
        runBlocking {
            DemoFixtures.load(container, "ActiveWorkoutPartiallyDone")
            prepare()
        }
        compose.setContent {
            val window = LocalWindowInfo.current
            val sizedWindow = object : WindowInfo by window {
                override val containerDpSize =
                    if (compactLargeText) DpSize(320.dp, window.containerDpSize.height) else window.containerDpSize
            }
            CompositionLocalProvider(
                LocalWindowInfo provides sizedWindow,
                LocalDensity provides Density(LocalDensity.current.density, if (compactLargeText) 2f else 1f)
            ) {
                InShapeTheme { AppNavigation(container, initialRoute = route) }
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun workout() = runBlocking { container.repository.snapshot().workout(id)!!.row }
}

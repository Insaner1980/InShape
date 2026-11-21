package com.emmah.inshape

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.feature.workout.TimingDraft
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.theme.InShapeTheme
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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

        compose.clickText(ui(R.string.workout_date))
        compose.clickText(ui(R.string.cancel))
        compose.onNodeWithText(ui(R.string.invalid_date)).assertExists()
        compose.clickText(ui(R.string.workout_date))
        compose.clickText(ui(R.string.done))
        compose.clickText(ui(R.string.save_correction))

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

        compose.clickText(ui(R.string.started))
        compose.clickText(ui(R.string.time))
        compose.onNodeWithText(ui(R.string.done)).assertIsNotEnabled()
        compose.clickText("UTC+03:00")
        compose.clickText(ui(R.string.done))
        compose.clickText(ui(R.string.finished))
        compose.clickText(ui(R.string.time))
        compose.clickText("UTC+02:00")
        compose.clickText(ui(R.string.done))
        compose.clickText(ui(R.string.save_correction))

        compose.waitUntil(30_000) { workout().title == "Edited workout" }
        val saved = workout()
        assertEquals("2026-10-25T00:30:00Z", saved.start)
        assertEquals("2026-10-25T01:30:00Z", saved.end)
    }

    @Test fun finishBeforeStartShowsAnExplanation() {
        launchTimingDraft {
            it.copy(startTime = "18:00", endTime = "17:30", unknownTimes = false)
        }
        compose.onNodeWithText(ui(R.string.finish_before_start)).performScrollTo().assertIsDisplayed()
    }

    @Test fun narrowTimingRowKeepsItsLabel() = assertTimingRow(width = 320, fontScale = 1f)

    @Test fun largeTextTimingRowKeepsItsLabel() = assertTimingRow(width = 390, fontScale = 2f)

    @Test fun narrowLargeTextTimingRowKeepsItsLabel() = assertTimingRow(width = 320, fontScale = 2f)

    private fun assertTimingRow(width: Int, fontScale: Float) {
        val value = "keskiviikko 30.9.2026, 22:38 (America/Los_Angeles)"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                InShapeTheme { Box(Modifier.width(width.dp)) { KeyValueRow(ui(R.string.started), value, numeric = true) } }
            }
        }
        compose.onNodeWithText(ui(R.string.started)).assertIsDisplayed()
        compose.onNodeWithText(value).assertIsDisplayed()
    }

    @Test fun liveFinishCanEndAtAnEarlierClockTimeDuringAnOverlap() {
        val start = "2026-10-25T00:45:12Z"
        val clock = Clock.fixed(Instant.parse("2026-10-25T01:50:34Z"), DemoFixtures.clock.zone)
        launch("finish/$id", compactLargeText = true, clock = clock) {
            val journal = container.database.journal()
            val row = journal.workouts().single { it.id == id }
            journal.put(row.copy(journalDate = "2026-10-25", start = start, startOffset = "+03:00"))
        }

        compose.clickText(ui(R.string.fix_time))
        compose.clickText(ui(R.string.finished))
        compose.onNode(hasText("50") and hasSetTextAction()).performTextReplacement("30")
        compose.clickText("UTC+02:00")
        compose.onAllNodesWithText(ui(R.string.done)).onLast().performClick()
        compose.clickText(ui(R.string.done))
        compose.clickText(ui(R.string.save_workout))

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

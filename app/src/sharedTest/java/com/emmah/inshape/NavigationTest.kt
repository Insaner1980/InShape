package com.emmah.inshape

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.longDate
import com.emmah.inshape.ui.theme.shortDate
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "navigation-${newId()}.db"
    private val container = AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false)

    @After fun cleanup() {
        container.close()
        context.deleteDatabase(databaseName)
        context.deleteDatabase("music-$databaseName")
        container.storageRoot.deleteRecursively()
    }

    @Test fun mainTabsAndSettingsRemainReachable() {
        compose.setContent { InShapeTheme { AppNavigation(container) } }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription(ui(R.string.settings)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(ui(R.string.settings)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.onAllNodesWithText(ui(R.string.history)).onLast().performClick()
        compose.onNodeWithText(ui(R.string.progress)).performClick()
        compose.onNodeWithText(ui(R.string.workouts_per_week)).assertExists()
        compose.onNodeWithText(ui(R.string.calendar)).performClick()
        listOf(R.string.history, R.string.library, R.string.today).map { ui(it) }.forEach { label ->
            compose.onAllNodesWithText(label).onLast().performClick()
            compose.onAllNodesWithText(label).onFirst().assertExists()
        }
    }

    @Test fun entryActionsOpenMusicAndBringAnActiveWorkoutToFinishingWithoutSavingIt() {
        val entry = mutableStateOf<Pair<String, Long>?>(null)
        compose.setContent {
            InShapeTheme {
                AppNavigation(container, entryAction = entry.value, consumeEntry = { entry.value = null })
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(ui(R.string.start_workout)).fetchSemanticsNodes().isNotEmpty()
        }
        var sequence = 0L
        fun send(action: String) {
            compose.runOnIdle { entry.value = action to ++sequence }
            compose.waitUntil(30_000) { compose.runOnIdle { entry.value == null } }
        }

        send(MainActivity.OPEN_FINISH)
        compose.onNodeWithText(ui(R.string.start_workout)).assertExists()
        assertNull(runBlocking { container.repository.snapshot().active })

        send(MainActivity.OPEN_MUSIC)
        compose.onNodeWithText(ui(R.string.choose_music_folder)).assertExists()
        send("unknown-action")
        compose.onNodeWithText(ui(R.string.start_workout)).assertExists()

        send(MainActivity.START_OR_RESUME)
        compose.waitUntil(30_000) {
            compose.waitForIdle()
            runBlocking { container.repository.snapshot().active != null }
        }
        val activeId = runBlocking { requireNotNull(container.repository.snapshot().active).row.id }
        send(MainActivity.OPEN_FINISH)
        compose.onNodeWithText(ui(R.string.fix_time)).assertExists()
        val journal = runBlocking { container.repository.snapshot() }
        assertEquals(activeId, journal.active?.row?.id)
        assertTrue(journal.history.isEmpty())
    }

    @Test fun libraryOpensTheMatchingAssistantRoute() {
        compose.setContent { InShapeTheme { AppNavigation(container, initialRoute = "Library") } }
        val newRoutine = ui(R.string.new_routine)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(newRoutine).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription(newRoutine).performClick()
        compose.clickText(ui(R.string.build_routine))
        compose.onNodeWithText(ui(R.string.build_routine)).assertExists()
        compose.onAllNodesWithContentDescription(ui(R.string.back)).onFirst().performClick()

        compose.onNodeWithContentDescription(newRoutine).performClick()
        compose.clickText(ui(R.string.import_program))
        compose.onNodeWithText(ui(R.string.choose_images)).assertExists()
        compose.onNodeWithText(ui(R.string.import_program)).assertExists()
        compose.onAllNodesWithContentDescription(ui(R.string.back)).onFirst().performClick()

        compose.onNode(hasText(ui(R.string.exercises)) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription(ui(R.string.new_exercise)).performClick()
        compose.clickText(ui(R.string.import_equipment))
        compose.onNodeWithText(ui(R.string.choose_images)).assertExists()
        compose.onNodeWithText(ui(R.string.import_equipment)).assertExists()
    }

    @Test fun workoutEvidenceOpensTheOriginalEntry() {
        runBlocking { DemoFixtures.load(container, "HistoryNovember") }
        val id = DemoFixtures.id("november:11")
        val date = LocalDate.of(2026, 11, 11)
        compose.setContent { InShapeTheme { AppNavigation(container, initialRoute = "detail/$id") } }
        val options = ui(R.string.workout_options)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(options).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(options).performClick()
        compose.clickText(ui(R.string.ask_workout))
        compose.onNodeWithText(ui(R.string.ask_journal)).assertExists()
        compose.clickText(ui(R.string.evidence_summary))
        compose.onNodeWithText(ui(R.string.date_range, date.shortDate(), date.shortDate())).assertExists()
        compose.clickText(date.shortDate())
        compose.onNodeWithText(date.longDate()).assertExists()
    }
}

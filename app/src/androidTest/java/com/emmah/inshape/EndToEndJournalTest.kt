package com.emmah.inshape

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.*
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

class EndToEndJournalTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "journey-${newId()}.db"
    private val testClock = object : Clock() {
        var time: Instant = Instant.parse("2026-11-13T10:00:00Z")
        override fun instant() = time
        override fun getZone(): ZoneId = ZoneId.of("Europe/Helsinki")
        override fun withZone(zone: ZoneId): Clock = fixed(time, zone)
    }
    private lateinit var container: AppContainer
    @Before fun setup() {
        container = AppContainer(context, testClock, databaseName)
        runBlocking { container.storageReady.await() }
        compose.setContent { InShapeTheme { AppNavigation(container) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { container.close(); context.deleteDatabase(databaseName); container.storageRoot.deleteRecursively() }
    private fun click(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onAllNodesWithText(text).onFirst()
        if (compose.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        node.performClick(); compose.waitForIdle()
    }
    private fun input(label: String, text: String) {
        val node = if (compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithContentDescription(label) else compose.onNodeWithText(label)
        node.performScrollTo().performTextReplacement(text)
    }
    @Test fun createExerciseRecordDecimalSetFinishWithinMinuteAndRepeatWithoutCountingTargets() {
        assertTrue(runBlocking { container.repository.snapshot().workouts.isEmpty() })
        compose.onAllNodesWithText("Library").onLast().performClick()
        click("Exercises"); compose.onNodeWithContentDescription("Add exercise").performClick()
        input("Exercise name", "Lat pulldown"); input("Recognizable machine name", "Machine A")
        click("Add setting"); input("Setting label", "Seat"); input("Setting value", "4")
        click("Save exercise")
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().exercises.size == 1 } }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onAllNodesWithText("Today").onLast().performClick()
        click("Start workout"); click("Add exercise"); click("Lat pulldown")
        click("Add set"); input("kg", "27,5"); input("Reps", "10")
        val complete = compose.onAllNodesWithText("Complete set 1")
        if (complete.fetchSemanticsNodes().isNotEmpty()) complete.onFirst().performScrollTo().performClick()
        else compose.onNodeWithContentDescription("Complete set 1").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().sets.singleOrNull()?.completedAt != null } }
        click("Mark done")
        compose.runOnIdle { testClock.time = testClock.time.plusSeconds(25) }
        click("Review and finish"); click("Save workout")
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().history.size == 1 } }
        val completed = runBlocking { container.repository.snapshot().history.single() }
        assertEquals("27.5", completed.items.single().completedSets.single().load)
        assertEquals(Instant.parse("2026-11-13T10:00:25Z"), Instant.parse(completed.row.end))
        click("Repeat workout")
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().active != null } }
        val repeated = runBlocking { container.repository.snapshot().active!! }
        assertFalse(repeated.hasPerformedWork); assertEquals("27.5", repeated.items.single().sets.single().suggestedLoad)
        assertEquals(1, runBlocking { container.repository.snapshot().history.count { it.qualifiesForAttendance } })
    }
}

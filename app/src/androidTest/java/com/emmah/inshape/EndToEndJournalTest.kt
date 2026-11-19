package com.emmah.inshape

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.newId
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EndToEndJournalTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "journey-${newId()}.db"
    private val testClock =
        object : Clock() {
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
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanup() {
        container.close()
        context.deleteDatabase(databaseName)
        container.storageRoot.deleteRecursively()
    }

    private fun click(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.clickText(text)
    }

    private fun input(label: String, text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        val node =
            if (compose
                    .onAllNodesWithContentDescription(
                        label
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                compose.onNodeWithContentDescription(label)
            } else {
                compose.onNodeWithText(label)
            }
        node.performScrollTo().performTextReplacement(text)
    }

    @Test fun createExerciseRecordDecimalSetFinishWithinMinuteAndRepeatWithoutCountingTargets() {
        assertTrue(
            runBlocking {
                container.repository
                    .snapshot()
                    .workouts
                    .isEmpty()
            }
        )
        compose.onAllNodesWithText("Library").onLast().performClick()
        click("Exercises")
        click("New exercise")
        input("Exercise name", "Lat pulldown")
        click("More details")
        input("Machine name (as shown on the machine)", "Machine A")
        click("Add setting")
        input("Setting 1 name", "Seat")
        input("Setting 1 value", "4")
        click("Save exercise")
        compose.waitUntil(10_000) {
            runBlocking {
                container.repository
                    .snapshot()
                    .exercises.size == 1
            }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onAllNodesWithText("Today").onLast().performClick()
        click("Start")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Workout options").fetchSemanticsNodes().isNotEmpty()
        }
        click("Add exercise")
        click("Lat pulldown")
        compose.onNodeWithText("00:00").assertExists()
        click("Add set")
        input("kg", "27,5")
        input("Reps", "10")
        val complete = compose.onAllNodesWithText("Complete set 1")
        if (complete.fetchSemanticsNodes().isNotEmpty()) {
            complete.onFirst().performScrollTo().performClick()
        } else {
            compose.onNodeWithContentDescription("Complete set 1").performScrollTo().performClick()
        }
        compose.waitUntil(10_000) {
            runBlocking {
                container.repository
                    .snapshot()
                    .sets
                    .singleOrNull()
                    ?.completedAt != null
            }
        }
        click("Exercise done")
        compose.runOnIdle { testClock.time = testClock.time.plusSeconds(10) }
        click("Finish workout")
        compose.waitUntil { runBlocking { container.repository.snapshot().drafts.any { it.id.startsWith("finish:") } } }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil { runBlocking { container.repository.snapshot().drafts.none { it.id.startsWith("finish:") } } }
        compose.runOnIdle { testClock.time = testClock.time.plusSeconds(15) }
        click("Finish workout")
        compose.onNodeWithText("Workout done").assertExists()
        compose.onNodeWithText("Under 1 min").assertExists()
        click("Add note")
        input("Workout note", "Keep this note after returning")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Finish workout").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { testClock.time = testClock.time.plusSeconds(5) }
        click("Finish workout")
        compose.onNodeWithText("Keep this note after returning").assertExists()
        runBlocking {
            val active = requireNotNull(container.repository.snapshot().active)
            container.repository.saveWorkoutNote(active.row.id, "Note from another screen")
        }
        click("Use draft note")
        click("Save workout")
        compose.waitUntil(10_000) {
            runBlocking {
                container.repository
                    .snapshot()
                    .history.size == 1
            }
        }
        val completed =
            runBlocking {
                container.repository
                    .snapshot()
                    .history
                    .single()
            }
        assertEquals(
            "27.5",
            completed.items
                .single()
                .completedSets
                .single()
                .load
        )
        assertEquals("Keep this note after returning", completed.row.note)
        assertEquals(Instant.parse("2026-11-13T10:00:30Z"), Instant.parse(completed.row.end))
        assertTrue(completed.row.timingConfirmed)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("How did it feel?").fetchSemanticsNodes().isNotEmpty() }
        click("Skip")
        click("Repeat workout")
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().active != null } }
        val repeated = runBlocking { container.repository.snapshot().active!! }
        assertFalse(repeated.hasPerformedWork)
        assertEquals(
            "27.5",
            repeated.items
                .single()
                .sets
                .single()
                .suggestedLoad
        )
        assertEquals(
            1,
            runBlocking {
                container.repository
                    .snapshot()
                    .history
                    .count { it.qualifiesForAttendance }
            }
        )
    }

    @Test fun emptyFinishAcceptsANoteAndManualDurationWithoutSharingUnknownTimes() {
        click("Start")
        compose.onNodeWithText("Add exercise").assertIsDisplayed()
        compose.onNodeWithText("Finish workout").assertDoesNotExist()
        click("Finish")
        compose.onNodeWithText("Nothing recorded yet").assertExists()
        compose.onNodeWithText("Save workout").assertDoesNotExist()
        click("Fix time")
        click("I don't know the times")
        input("Duration in minutes (optional)", "12")
        click("Done")
        click("Add note")
        input("Workout note", "Mobility session")
        click("Save workout")
        compose.waitUntil { runBlocking { container.repository.snapshot().history.size == 1 } }
        val saved = runBlocking { container.repository.snapshot().history.single().row }
        assertEquals("Mobility session", saved.note)
        assertEquals(720L, saved.durationSeconds)
        assertEquals(null, saved.start)
        assertEquals(null, saved.end)
        assertFalse(saved.timingConfirmed)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("How did it feel?").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun cancellingDiscardKeepsTheWorkoutAndConfirmingItRemovesTheDraft() {
        click("Start")
        click("Finish")
        val id = runBlocking { requireNotNull(container.repository.snapshot().active).row.id }
        compose.waitUntil(10_000) {
            runBlocking { container.repository.snapshot().drafts.any { it.id == "finish:$id" } }
        }
        click("Discard workout")
        compose.onNodeWithText("Discard this workout?").assertExists()
        click("Cancel")
        assertEquals(id, runBlocking { container.repository.snapshot().active?.row?.id })

        click("Discard workout")
        click("Discard")
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().active == null } }
        val data = runBlocking { container.repository.snapshot() }
        assertTrue(data.drafts.none { it.id == "finish:$id" })
        assertTrue(data.history.isEmpty())
    }
}

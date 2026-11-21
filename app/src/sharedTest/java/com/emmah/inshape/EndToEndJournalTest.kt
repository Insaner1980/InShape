package com.emmah.inshape

import androidx.compose.ui.test.assertIsSelected
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
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
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
            compose.onAllNodesWithContentDescription(ui(R.string.settings)).fetchSemanticsNodes().isNotEmpty()
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

    @Test fun recordsDecimalSetAndRepeatsShortWorkout() {
        assertTrue(
            runBlocking {
                container.repository
                    .snapshot()
                    .workouts
                    .isEmpty()
            }
        )
        compose.onAllNodesWithText(ui(R.string.library)).onLast().performClick()
        click(ui(R.string.exercises))
        compose.onNodeWithContentDescription(ui(R.string.new_exercise)).performClick()
        compose.onNodeWithText(ui(R.string.new_exercise)).performClick()
        input(ui(R.string.exercise_name), ui(R.string.exercise_lat_pulldown))
        click(ui(R.string.add_setting))
        input(ui(R.string.setting_number_name, 1), "Seat")
        input(ui(R.string.setting_number_value, 1), "4")
        click(ui(R.string.create_exercise))
        compose.waitUntil(10_000) {
            runBlocking {
                container.repository
                    .snapshot()
                    .exercises.size == 1
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(R.string.edit_exercise)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.onAllNodesWithText(ui(R.string.today)).onLast().performClick()
        click(ui(R.string.start_workout))
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(ui(R.string.no_exercises)).fetchSemanticsNodes().isNotEmpty()
        }
        click(ui(R.string.add_exercise))
        click(ui(R.string.exercise_lat_pulldown))
        compose.onNodeWithText("00:00").assertExists()
        click(ui(R.string.add_set))
        input(ui(R.string.unit_kg), "27,5")
        input(ui(R.string.reps), "10")
        val complete = compose.onAllNodesWithText(ui(R.string.complete_set_number, 1))
        if (complete.fetchSemanticsNodes().isNotEmpty()) {
            complete.onFirst().performScrollTo().performClick()
        } else {
            compose.onNodeWithContentDescription(ui(R.string.complete_set_number, 1)).performScrollTo().performClick()
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
        compose.runOnIdle { testClock.time = testClock.time.plusSeconds(10) }
        click(ui(R.string.finish))
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().drafts.any { it.id.startsWith("finish:") } } }
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().drafts.none { it.id.startsWith("finish:") } } }
        compose.runOnIdle { testClock.time = testClock.time.plusSeconds(15) }
        click(ui(R.string.finish))
        compose.onNodeWithText(ui(R.string.workout_done)).assertExists()
        compose.onNodeWithText("0").assertExists()
        click(ui(R.string.effort_right))
        click(ui(R.string.energy_and_notes))
        input(ui(R.string.workout_note), "Keep this note after returning")
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(R.string.finish)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { testClock.time = testClock.time.plusSeconds(5) }
        click(ui(R.string.finish))
        click(ui(R.string.energy_and_notes))
        compose.onNodeWithText("Keep this note after returning").assertExists()
        runBlocking {
            val active = requireNotNull(container.repository.snapshot().active)
            container.repository.saveWorkoutNote(active.row.id, "Note from another screen")
        }
        click(ui(R.string.use_draft_note))
        compose.onNodeWithText(ui(R.string.effort_right)).assertIsSelected()
        click(ui(R.string.save_workout))
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
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(R.string.after_workout)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(ui(R.string.check_in_after_question)).assertDoesNotExist()
        assertEquals(6, completed.checkIns.single { it.phase == "AFTER" }.effort)
        compose.onNodeWithContentDescription(ui(R.string.workout_options)).performClick()
        click(ui(R.string.repeat_workout))
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
        click(ui(R.string.start_workout))
        compose.waitUntil(10_000) { compose.onNodeWithText(ui(R.string.add_exercise)).isDisplayed() }
        assertEquals(1, compose.onAllNodesWithText(ui(R.string.finish)).fetchSemanticsNodes().size)
        click(ui(R.string.finish))
        compose.onNodeWithText(ui(R.string.nothing_recorded)).assertExists()
        compose.onNodeWithText(ui(R.string.save_workout)).assertDoesNotExist()
        click(ui(R.string.fix_time))
        click(ui(R.string.times_unknown))
        input(ui(R.string.duration_minutes_optional), "12")
        click(ui(R.string.done))
        click(ui(R.string.energy_and_notes))
        input(ui(R.string.workout_note), "Mobility session")
        click(ui(R.string.save_workout))
        compose.waitUntil { runBlocking { container.repository.snapshot().history.size == 1 } }
        val saved = runBlocking { container.repository.snapshot().history.single().row }
        assertEquals("Mobility session", saved.note)
        assertEquals(720L, saved.durationSeconds)
        assertEquals(null, saved.start)
        assertEquals(null, saved.end)
        assertFalse(saved.timingConfirmed)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(R.string.after_workout)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun cancellingDiscardKeepsTheWorkoutAndConfirmingItRemovesTheDraft() {
        click(ui(R.string.start_workout))
        click(ui(R.string.finish))
        val id = runBlocking { requireNotNull(container.repository.snapshot().active).row.id }
        compose.waitUntil(10_000) {
            runBlocking { container.repository.snapshot().drafts.any { it.id == "finish:$id" } }
        }
        click(ui(R.string.discard_workout))
        compose.onNodeWithText(ui(R.string.discard_workout_question)).assertExists()
        click(ui(R.string.cancel))
        assertEquals(id, runBlocking { container.repository.snapshot().active?.row?.id })

        click(ui(R.string.discard_workout))
        click(ui(R.string.discard))
        compose.waitUntil(10_000) { runBlocking { container.repository.snapshot().active == null } }
        val data = runBlocking { container.repository.snapshot() }
        assertTrue(data.drafts.none { it.id == "finish:$id" })
        assertTrue(data.history.isEmpty())
    }
}

package com.emmah.inshape.health

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.HealthOutbox
import com.emmah.inshape.data.HealthShareState
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthControlsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "health-controls-${newId()}.db"
    private val container = AppContainer(context, databaseName = name, integrationsEnabled = false)
    private val time = "2026-11-20T10:00:00Z"
    private val workout = WorkoutEntity(
        id = "workout",
        state = "COMPLETED",
        activeKey = null,
        journalDate = "2026-11-20",
        zoneId = "UTC",
        start = time,
        end = "2026-11-20T10:25:00Z",
        createdAt = time,
        modifiedAt = time,
        timingConfirmed = true
    )
    private val shared = HealthShareState(workout.id, restoredHold = true, platformId = "external-id")

    @After fun cleanup() {
        container.close()
        context.deleteDatabase(name)
        container.storageRoot.deleteRecursively()
    }

    @Test fun settingsDisplayUnsentChangesWhileHealthConnectIsUnavailable() {
        val operation = HealthOutbox(workout.id, shared.clientRecordId, 1, "DELETE", error = "Yhteys katkesi")
        val data = JournalSnapshot(
            preferences = Preferences(readHeartRate = true, shareWorkouts = true),
            healthStates = listOf(shared.copy(status = "REMOVAL_HELD")),
            outbox = listOf(operation)
        )
        compose.setContent {
            val vm = viewModel { JournalViewModel(container) }
            InShapeTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { HealthSettings(data, vm) }
            }
        }
        compose.onNodeWithText(ui(R.string.not_available_on_phone)).assertExists()
        compose.onNodeWithText(ui(R.string.latest_heart_rate)).assertExists()
        compose.onNodeWithText(ui(R.string.pending_health_operations, 1)).assertExists()
        compose.onNodeWithText("Yhteys katkesi").assertExists()
        compose.onNodeWithText(ui(R.string.retry_operations)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(ui(R.string.health_hold_message)).assertExists()
        compose.onNodeWithText(ui(R.string.health_removal_confirmation_needed, workout.id)).assertExists()
        compose.onNodeWithText(ui(R.string.allow_removals)).assertDoesNotExist()
    }

    @Test fun aRestoredWorkoutCanReviewReconciliationAndRemovalWithoutChangingTheRecord() {
        val session = SessionItem(
            workoutId = workout.id, exerciseId = "lift", equipmentId = "machine", name = "Soutu",
            category = "Machine", basis = "duration", quickCompletedAt = time
        )
        val data = mutableStateOf(
            JournalSnapshot(
                workouts = listOf(workout), items = listOf(session), healthStates = listOf(shared),
                preferences = Preferences(shareWorkouts = true)
            )
        )
        compose.setContent {
            val vm = viewModel { JournalViewModel(container) }
            InShapeTheme {
                WorkoutHealthControls(data.value.history.single(), data.value, vm)
            }
        }
        compose.onNodeWithText(ui(R.string.sharing_on_hold)).assertExists()
        compose.clickText(ui(R.string.reconcile_workout))
        compose.onNodeWithText(ui(R.string.reconcile_question)).assertExists()
        compose.clickText(ui(R.string.cancel))
        compose.clickText(ui(R.string.remove_shared_copy))
        compose.onNodeWithText(ui(R.string.remove_shared_question)).assertExists()
        compose.clickText(ui(R.string.cancel))
        compose.runOnIdle { data.value = data.value.copy(workouts = listOf(workout.copy(timingConfirmed = false))) }
        compose.onNodeWithText(ui(R.string.sharing_needs_timing)).assertExists()
        compose.onNodeWithText(ui(R.string.reconcile_workout)).assertDoesNotExist()
    }

    @Test fun confirmingRemovalQueuesTheExternalCopyAndKeepsTheLocalWorkout() {
        runBlocking {
            container.storageReady.await()
            container.database.journal().insertWorkout(workout)
            container.database.journal().put(shared.copy(restoredHold = false, status = "SHARED"))
        }
        compose.setContent {
            val vm = viewModel { JournalViewModel(container) }
            val data by vm.snapshot.collectAsState()
            InShapeTheme {
                data?.let { snapshot -> WorkoutHealthControls(snapshot.history.single(), snapshot, vm) }
            }
        }
        val remove = ui(R.string.remove_shared_copy)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(remove)).fetchSemanticsNodes().isNotEmpty() }
        compose.clickText(remove)
        compose.onNode(hasText(remove) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(ui(R.string.health_removal_pending))).fetchSemanticsNodes().isNotEmpty()
        }
        val saved = runBlocking { container.repository.snapshot() }
        assertEquals(workout.id, saved.history.single().row.id)
        assertTrue(saved.healthStates.single().restoredHold)
        assertEquals("DELETE", saved.outbox.single().operation)
        assertEquals(shared.clientVersion + 1, saved.outbox.single().revision)
    }
}

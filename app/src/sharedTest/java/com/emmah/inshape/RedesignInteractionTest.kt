package com.emmah.inshape

import com.emmah.inshape.ui.theme.monthTitle
import com.emmah.inshape.data.WorkoutEntity
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.feature.assistant.RoutineProposalScreen
import com.emmah.inshape.feature.checkin.CheckInDraft
import com.emmah.inshape.feature.library.RoutineDraft
import com.emmah.inshape.feature.library.RoutineItemDraft
import com.emmah.inshape.feature.library.RoutineSetDraft
import com.emmah.inshape.feature.library.RoutineSetTargets
import com.emmah.inshape.feature.workout.TimingDraft
import com.emmah.inshape.health.HealthCapability
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.components.RecordedDatePicker
import com.emmah.inshape.ui.components.RecordedDateRangePicker
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The cross-screen scenarios share the same journal and navigation test fixture.
@Suppress("LargeClass")
@RunWith(AndroidJUnit4::class)
class RedesignInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "redesign-${newId()}.db"
    private lateinit var container: AppContainer
    private lateinit var journalViewModel: JournalViewModel
    private val originalTheme = ThemeSettings(context).mode.value

    private fun launch(state: String, route: String, prepare: suspend (AppContainer) -> Unit = {}) {
        container =
            AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false)
        runBlocking {
            DemoFixtures.load(container, state)
            prepare(container)
        }
        compose.setContent {
            val mode by container.themeSettings.mode.collectAsState()
            val vm = viewModel<JournalViewModel> {
                JournalViewModel(container).also { journalViewModel = it }
            }
            InShapeTheme(mode) { AppNavigation(container, initialRoute = route, vm = vm) }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanup() {
        if (::container.isInitialized) {
            container.themeSettings.set(originalTheme)
            container.close()
            context.deleteDatabase(databaseName)
            container.storageRoot.deleteRecursively()
        }
    }

    private fun click(text: String) {
        compose.clickText(text)
    }

    private fun snapshot() = runBlocking { container.repository.snapshot() }

    private fun awaitWrite(predicate: () -> Boolean) {
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            predicate()
        }
    }

    private fun releaseWrites(release: CompletableDeferred<Unit>) {
        val drained = CompletableDeferred<Unit>()
        journalViewModel.write { drained.complete(Unit) }
        release.complete(Unit)
        awaitWrite { drained.isCompleted }
    }

    @Test fun todayStartSheetStartsTheChosenRoutine() {
        launch("HistoryNovember", "Today")
        click(ui(R.string.other_routine))
        compose.onNodeWithText(ui(R.string.empty_workout)).assertExists()
        compose.onNode(hasText("Liikkuvuutta kotona") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        compose.waitUntil(10_000) { snapshot().active?.row?.title == "Liikkuvuutta kotona" }
        compose.onNodeWithText(ui(R.string.finish)).assertExists()
    }

    @Test fun emptyWorkoutOffersToAddAnExercise() {
        launch("Empty", "Today") { app ->
            app.repository.startWorkout(com.emmah.inshape.data.StartSource.Empty)
        }
        click(ui(R.string.resume_workout))
        compose.onNodeWithText(ui(R.string.no_exercises)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your plan is complete").assertDoesNotExist()
        compose.onNodeWithText("Saved automatically").assertDoesNotExist()
        click(ui(R.string.add_exercise))
        compose.onNodeWithText(ui(R.string.find_exercise)).assertExists()
    }

    @Test fun todayWithOnlyTheSuggestedRoutineOmitsTheRoutinesSection() {
        launch("TodayStrength", "Today")
        compose.onNodeWithText(ui(R.string.next_up)).assertExists()
        compose.onNodeWithText(ui(R.string.routines)).assertDoesNotExist()
    }

    @Test fun measurementSelectShowsAnAssistanceLabelWithNoRawKey() {
        launch("ExerciseWithSetup", "exercise-edit/${DemoFixtures.pulldownId}")
        compose.onNodeWithContentDescription(ui(R.string.measurement) + ", " + ui(R.string.load_kg)).performScrollTo().performClick()
        compose.onNode(hasText(ui(R.string.load_kg)) and hasAnyAncestor(isDialog())).assertIsSelected()
        compose.onNodeWithText(ui(R.string.assisted_kg)).assertExists()
        compose.onNodeWithText("assistance_kg").assertDoesNotExist()
        click(ui(R.string.assisted_kg))
        compose.onNodeWithContentDescription(ui(R.string.measurement) + ", " + ui(R.string.assisted_kg)).assertExists()
        compose.waitUntil(10_000) {
            val latest = snapshot()
            latest.exercises.any { it.id == DemoFixtures.pulldownId && it.basis == "assistance_kg" } &&
                latest.drafts.none { it.id == "exercise:${DemoFixtures.pulldownId}" }
        }
    }

    @Test fun repeatedSaveFailureKeepsRetryAvailable() {
        launch("Empty", "Today")
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        journalViewModel.write {
            if (attempts.incrementAndGet() <= 2) throw java.io.IOException("Storage is full.")
        }
        repeat(2) { failure ->
            compose.waitUntil(10_000) {
                attempts.get() == failure + 1 && compose.onAllNodesWithText(ui(R.string.retry)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(ui(R.string.save_failed)).assertIsDisplayed()
            click(ui(R.string.retry))
        }
        compose.waitUntil(10_000) { journalViewModel.saveState.value == SaveState.Saved }
        compose.onNodeWithText(ui(R.string.retry)).assertDoesNotExist()
        compose.onNodeWithText("Saved automatically").assertDoesNotExist()
    }

    @Test fun narrowTimeInputPreservesEveningTime() = assertCompactTimeInput(width = 320, fontScale = 1f)

    @Test fun largeTextTimeInputPreservesEveningTime() = assertCompactTimeInput(width = 390, fontScale = 2f)

    @Test fun narrowLargeTextTimeInputPreservesEveningTime() = assertCompactTimeInput(width = 320, fontScale = 2f)

    @Test fun calendarRestoresSelectedDayAndDisplayedMonth() {
        var selected: String? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            InShapeTheme { RecordedDatePicker("Choose day", "2026-11-13", "", { selected = it }, {}) }
        }
        compose.onNode(hasClickAction() and hasText("keskiviikko 4.11.2026")).performClick()
        compose.onNodeWithContentDescription("Change to next month").performScrollTo().performClick()
        val decemberDay = compose.onNode(hasClickAction() and hasText("perjantai 4.12.2026"))
        decemberDay.performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        decemberDay.performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.done)).performClick()
        compose.runOnIdle { assertEquals("2026-11-04", selected) }
    }

    @Test fun periodCalendarRestoresAnUnconfirmedStartDay() {
        var selected: Pair<LocalDate, LocalDate>? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            InShapeTheme {
                RecordedDateRangePicker("", "", LocalDate.of(2026, 11, 13), { from, to -> selected = from to to }, {})
            }
        }
        compose.onNode(hasClickAction() and hasText("keskiviikko 4.11.2026")).performClick()
        restoration.emulateSavedInstanceStateRestore()
        val confirm = compose.onNode(hasClickAction() and hasText(ui(R.string.apply)))
        confirm.assertIsNotEnabled()
        compose.onNode(hasClickAction() and hasText("maanantai 9.11.2026")).performClick()
        confirm.performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 11, 4) to LocalDate.of(2026, 11, 9), selected) }
    }

    @Test fun routineSelectionRestoresInOrder() {
        val data = JournalSnapshot(exercises = listOf(
            Exercise(name = "Fixture press"), Exercise(name = "Fixture row", location = "Sali"), Exercise(name = "Fixture stretch", location = "Home")
        ))
        val restoration = launchRoutineProposal(data)
        compose.onNodeWithText(ui(R.string.place_gym)).assertDoesNotExist()
        compose.onNodeWithText(ui(R.string.place_home)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(ui(R.string.routine_name)) and hasSetTextAction()).performTextReplacement("Fixture routine")
        compose.onNodeWithText("Fixture row").performScrollTo().performClick()
        compose.onNodeWithText("Fixture press").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(ui(R.string.review_routine)).performClick()
        compose.onNodeWithText("Fixture row\nFixture press").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyRoutineSelectionRestores() {
        val exercise = Exercise(name = "Fixture row")
        val timestamp = DemoFixtures.clock.instant().toString()
        val row = WorkoutEntity(journalDate = "2026-11-13", zoneId = "UTC", createdAt = timestamp, modifiedAt = timestamp)
        val item = SessionItem(
            workoutId = row.id, exerciseId = exercise.id, equipmentId = exercise.equipmentId,
            name = exercise.name, category = exercise.category, basis = exercise.basis
        )
        val workout = Workout(row, listOf(WorkoutItem(item, emptyList())), emptyList())
        val restoration = launchRoutineProposal(JournalSnapshot(exercises = listOf(exercise)), workout)
        compose.onNodeWithText(ui(R.string.keep_today)).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(ui(R.string.skip_today)).assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.review_routine)).assertIsEnabled().performClick()
        compose.onNodeWithText(ui(R.string.apply_to_today)).assertDoesNotExist()
    }

    private fun launchRoutineProposal(data: JournalSnapshot, workout: Workout? = null): StateRestorationTester {
        container = AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false)
        return StateRestorationTester(compose).apply {
            setContent {
                val vm = viewModel<JournalViewModel> { JournalViewModel(container) }
                InShapeTheme { RoutineProposalScreen(data, workout, vm) {} }
            }
        }
    }

    @Test fun periodCalendarDropsAnEndEarlierThanTheFallbackStart() {
        compose.setContent {
            InShapeTheme {
                RecordedDateRangePicker("", "2026-11-04", LocalDate.of(2026, 11, 13), { _, _ -> }, {})
            }
        }
        compose.onNode(hasClickAction() and hasText(ui(R.string.apply))).assertIsNotEnabled()
        compose.onNode(hasClickAction() and hasText("perjantai 13.11.2026")).assertIsDisplayed()
    }

    private fun assertCompactTimeInput(width: Int, fontScale: Float) {
        var selected: String? = null
        compose.setContent {
            val window = LocalWindowInfo.current
            val sizedWindow = object : WindowInfo by window {
                override val containerDpSize = DpSize(width.dp, window.containerDpSize.height)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalWindowInfo provides sizedWindow,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                InShapeTheme(ThemeMode.Light) {
                    com.emmah.inshape.ui.components.RecordedTimePicker("Started", "22:42", { selected = it }, {})
                }
            }
        }
        compose.onNodeWithText("22").assertIsDisplayed()
        compose.onNodeWithText("PM").assertDoesNotExist()
        compose.onNodeWithText(ui(R.string.done)).performClick()
        compose.runOnIdle { assertEquals("22:42", selected) }
    }

    @Test fun exportOffersSaveAndShareForBothFormatsWithoutManualSelection() {
        launch("HistoryNovember", "Export")
        compose.onNodeWithText(ui(R.string.all_workouts)).assertExists()
        compose.onNodeWithText(ui(R.string.save_to_files)).assertIsEnabled()
        compose.onNodeWithContentDescription(ui(R.string.include_notes)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(ui(R.string.include_check_ins)).performScrollTo().performClick()
        click("CSV")
        compose.onNodeWithText(ui(R.string.save_to_files)).assertIsEnabled()
        compose.onNodeWithText(ui(R.string.share)).assertIsEnabled()
        compose.onNodeWithText("Select all").assertDoesNotExist()
    }

    @Test fun exportCancellationIsShownOnlyOnceWhenReturningToExport() {
        launch("HistoryNovember", "Settings")
        click(ui(R.string.export))
        compose.onNodeWithText(ui(R.string.period)).assertExists()
        compose.runOnIdle { journalViewModel.transfers.exportCancelled(id = null, format = "PDF") }
        compose.onNodeWithText(ui(R.string.export_cancelled)).assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        click(ui(R.string.export))
        compose.onNodeWithText(ui(R.string.period)).assertExists()
        compose.onNodeWithText(ui(R.string.export_cancelled)).assertDoesNotExist()
    }

    @Test fun duplicateLabelsStillSelectTheRequestedIdentity() {
        var selected = "first"
        compose.setContent {
            InShapeTheme {
                SelectField(ui(R.string.exercise), selected, listOf("first", "second"), { selected = it }, optionLabel = { "Same name" })
            }
        }
        compose.onNodeWithContentDescription(ui(R.string.exercise) + ", Same name").performClick()
        compose.onAllNodesWithText("Same name").onLast().performClick()
        compose.runOnIdle { assertEquals("second", selected) }
    }

    @Test fun staleTimingDraftCanBeReviewedAndSavedWithoutLosingEdits() {
        val saveLabel = ui(R.string.save_correction)
        val id = DemoFixtures.activeId
        launch("ActiveWorkoutPartiallyDone", "timing/$id/correct") { app ->
            val row = app.repository.snapshot().workout(id)!!.row
            val draft =
                TimingDraft(
                    "Recovered title", row.journalDate, "Recovered note", row.journalDate,
                    "", row.journalDate, "", "2", true, row.revision
                )
            app.repository.saveEditorDraft(EditorDraft("timing:$id", "timing", Json.encodeToString(draft), app.clock.instant().toString()))
            app.repository.saveWorkoutNote(id, "Newer saved note")
        }
        compose.onNodeWithText(saveLabel).assertIsNotEnabled()
        compose.onNodeWithText(ui(R.string.note_text, "Newer saved note")).performScrollTo().assertIsDisplayed()
        click(ui(R.string.keep_draft))
        val release = CompletableDeferred<Unit>()
        journalViewModel.write { release.await() }
        try {
            click(saveLabel)
            compose.onNodeWithText(saveLabel).assertIsNotEnabled()
            compose.onNodeWithText(ui(R.string.workout_note)).assertIsNotEnabled()
        } finally {
            release.complete(Unit)
        }
        compose.waitUntil(30_000) { snapshot().workout(id)!!.row.title == "Recovered title" }
        assertEquals("Recovered note", snapshot().workout(id)!!.row.note)
        assertEquals(120L, snapshot().workout(id)!!.row.durationSeconds)
        assertEquals(null, snapshot().drafts.find { it.id == "timing:$id" })
    }

    @Test fun legacyRoutineDraftReviewsAndKeepsExactSavedSeconds() {
        val id = DemoFixtures.routineId
        var itemId = ""
        launch("LibraryRoutineExpanded", "routine/$id") { app ->
            val item = app.repository.snapshot().routineItems.first { it.routineId == id }
            itemId = item.id
            app.database.journal().put(item.copy(mode = "DURATION", durationSeconds = 90))
            val draft = RoutineDraft(
                id = id,
                title = "Legacy routine",
                items = listOf(
                    RoutineItemDraft(item.id, item.exerciseId, mode = "DURATION", duration = "1", note = "Recovered planned note")
                )
            )
            app.repository.saveEditorDraft(
                EditorDraft("routine:$id", "routine", Json.encodeToString(draft), app.clock.instant().toString())
            )
        }
        click(ui(R.string.review_saved_duration))
        compose.onNodeWithText(ui(R.string.saved_duration_explanation, 90)).assertExists()
        click(ui(R.string.keep_saved_duration))
        click(ui(R.string.done))
        compose.onNodeWithText("1 min").assertExists()
        compose.waitUntil(30_000) { snapshot().routines.first { it.id == id }.title == "Legacy routine" }
        val saved = snapshot().routineItems.first { it.id == itemId }
        assertEquals(90L, saved.durationSeconds)
        assertEquals("Recovered planned note", saved.note)
    }

    @Test fun goalCanBeOffWhileHeldRemindersResumeAndExpand() {
        grantNotificationPermission()
        launch("SettingsConfigured", "Settings") { app ->
            val preferences = app.repository.snapshot().preferences
            app.repository.updatePreferences(preferences.copy(remindersEnabled = true, remindersHeld = true))
        }
        repeat(3) {
            val previousGoal = snapshot().preferences.weeklyGoal
            compose.onNodeWithContentDescription(
                ui(R.string.decrease_goal)
            ).performScrollTo().performClick()
            awaitWrite { snapshot().preferences.weeklyGoal != previousGoal }
        }
        compose.waitUntil { snapshot().preferences.weeklyGoal == null }
        compose.waitUntil {
            compose.onAllNodes(isNotEnabled() and androidx.compose.ui.test.hasContentDescription(ui(R.string.decrease_goal)))
                .fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription(ui(R.string.decrease_goal)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(ui(R.string.increase_goal)).performClick()
        awaitWrite { snapshot().preferences.weeklyGoal == 1 }
        val reminderSwitch = hasContentDescription(ui(R.string.enable_workout_reminders))
        compose.onNode(reminderSwitch).performScrollTo().assertIsOff()
        compose.onNode(reminderSwitch).performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        awaitWrite {
            !snapshot().preferences.remindersHeld && snapshot().preferences.remindersEnabled &&
                compose.onAllNodes(reminderSwitch and isOn()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(reminderSwitch).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        awaitWrite {
            !snapshot().preferences.remindersEnabled && compose.onAllNodesWithText(ui(R.string.time)).fetchSemanticsNodes().isEmpty()
        }
        compose.onNode(reminderSwitch).performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        awaitWrite {
            snapshot().preferences.remindersEnabled && compose.onAllNodesWithText(ui(R.string.time)).fetchSemanticsNodes().size == 1
        }
    }

    @Test fun unavailableHealthSettingsKeepsExistingOptOutAvailable() {
        launch("Empty", "Settings") { app ->
            assumeTrue(app.healthGateway.availability() != HealthConnectClient.SDK_AVAILABLE)
            app.repository.updatePreferences(app.repository.snapshot().preferences.copy(readHeartRate = true))
        }
        compose.onNodeWithText(ui(R.string.not_available_on_phone)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(ui(HealthCapability.HEART.label)).performScrollTo().assertIsOn().performClick()
        awaitWrite { !snapshot().preferences.readHeartRate }
        compose.onNodeWithContentDescription(ui(HealthCapability.HEART.label)).assertDoesNotExist()
    }

    @Test fun savingACompletedSetCorrectionClosesTheDialog() {
        launch("ActiveWorkoutPartiallyDone", "workout/${DemoFixtures.activeId}")
        val setId = DemoFixtures.id("active:set:4:0")
        compose.onNodeWithContentDescription(ui(R.string.correct_set_value, ui(R.string.reps), 1)).performScrollTo().performClick()
        val title = compose.onNodeWithText(ui(R.string.correct_set_number, 1))
        title.assertIsDisplayed()
        compose.onNode(hasText(ui(R.string.reps)) and hasSetTextAction() and hasAnyAncestor(isDialog()))
            .performTextReplacement("12")
        compose.onNodeWithText(ui(R.string.update)).performClick()
        compose.waitUntil(10_000) {
            snapshot().sets.any { it.id == setId && it.reps == 12 && it.completedAt != null } &&
                compose.onAllNodesWithText(ui(R.string.correct_set_number, 1)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun workoutNoteSetCompletionReopeningSwapAndSkipStayConnected() {
        launch("ActiveWorkoutPartiallyDone", "workout/${DemoFixtures.activeId}")
        fun exerciseMenu(action: String) {
            val active = snapshot().active!!
            val current = active.items.first { it.row.id == active.row.currentItemId }
            val label = ui(R.string.named_options, current.row.name)
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(label).performScrollTo().performClick()
            click(action)
        }
        exerciseMenu(ui(R.string.add_note))
        compose.onNodeWithText(ui(R.string.note)).assertExists()
        compose.onNodeWithText(
            ui(R.string.note)
        ).performScrollTo().performTextReplacement("Keep shoulders down")
        compose.onNodeWithContentDescription(ui(R.string.complete_set_number, 3)).performScrollTo().performClick()
        if (compose.onAllNodesWithText(ui(R.string.exercise_done)).fetchSemanticsNodes().isEmpty()) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(ui(R.string.exercise_done)).fetchSemanticsNodes().isNotEmpty() }
        }
        click(ui(R.string.exercise_done))
        val original = DemoFixtures.id("active:item:4")
        compose.waitUntil { snapshot().items.find { it.id == original }?.disposition == "DONE" }
        assertEquals("Keep shoulders down", snapshot().items.first { it.id == original }.note)
        val reopen = ui(R.string.reopen_named, ui(R.string.exercise_lat_pulldown))
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(reopen).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(reopen).performScrollTo().performClick()
        compose.waitUntil { snapshot().active?.row?.currentItemId == original }
        exerciseMenu(ui(R.string.swap_exercise))
        click(ui(R.string.exercise_assisted_pull_up))
        compose.waitUntil { snapshot().items.any { it.alternativeOf == original } }
        val replacement = snapshot().active!!.row.currentItemId
        exerciseMenu(ui(R.string.skip_for_now))
        compose.waitUntil { snapshot().active!!.row.currentItemId != replacement }
        val skipped = snapshot().active!!.row.currentItemId
        exerciseMenu(ui(R.string.skip_for_now))
        compose.waitUntil {
            snapshot().active?.items?.last()?.row?.id == skipped && snapshot().active?.row?.currentItemId != skipped
        }
        click(ui(R.string.finish))
        compose.waitUntil(10_000) { snapshot().drafts.any { it.id == "finish:${DemoFixtures.activeId}" } }
        compose.onNodeWithText(ui(R.string.save_workout)).assertIsDisplayed()
    }

    @Test fun editingCheckInNotePreservesLegacyLevelsAndObservedInstant() {
        val id = DemoFixtures.activeId
        val observed = "2026-11-13T09:14:23.456Z"
        launch("CheckInAfter", "checkin/$id/AFTER") { app ->
            val check = com.emmah.inshape.data.CheckIn(
                workoutId = id,
                phase = "AFTER",
                effort = 7,
                observedAt = observed,
                zoneId = "Europe/Helsinki"
            )
            app.repository.saveCheckIn(
                check,
                listOf(com.emmah.inshape.data.BodyObservation(check.id, "left_knee", pain = 3, stiffness = null))
            )
        }
        compose.onNodeWithText(ui(R.string.effort_right)).assertIsSelected()
        compose.onNodeWithText(ui(R.string.pain) + " " + ui(R.string.body_level_mild).lowercase()).assertExists()
        if (compose.onAllNodesWithText(ui(R.string.note)).fetchSemanticsNodes().isEmpty()) {
            click(ui(R.string.energy_and_notes))
        }
        compose.onNodeWithText(ui(R.string.note)).performScrollTo().performTextReplacement("After stretching")
        click(ui(R.string.save))
        compose.waitUntil(10_000) { snapshot().checkIns.any { it.workoutId == id && it.note == "After stretching" } }
        val check = snapshot().checkIns.first { it.workoutId == id && it.phase == "AFTER" }
        assertEquals(7, check.effort)
        assertEquals(observed, check.observedAt)
        val area = snapshot().observations.single { it.checkInId == check.id }
        assertEquals(3, area.pain)
        assertEquals(null, area.stiffness)
    }

    private fun launchAfterCheckIn(prepare: suspend (AppContainer, CheckIn, CheckIn) -> Unit) {
        val id = DemoFixtures.activeId
        launch("CheckInAfter", "checkin/$id/AFTER") { app ->
            val data = app.repository.snapshot()
            val before = data.checkIns.single { it.workoutId == id && it.phase == "BEFORE" }
            val after = data.checkIns.single { it.workoutId == id && it.phase == "AFTER" }
            prepare(app, before, after)
        }
    }

    @Test fun afterCheckInSavesIndependentSymptomsAndEnergy() {
        val id = DemoFixtures.activeId
        launchAfterCheckIn { app, before, after ->
            app.repository.saveCheckIn(before, listOf(BodyObservation(before.id, "left_knee", pain = 2)))
            app.repository.saveCheckIn(after.copy(energy = null), emptyList())
        }
        click(ui(R.string.effort_hard))
        click(ui(R.string.yes))
        click(ui(R.string.body_back))
        click(ui(R.string.pick_from_list))
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText(ui(R.string.region_left_knee)))
        click(ui(R.string.region_left_knee))
        click(ui(R.string.region_left_knee))
        compose.onNodeWithText(ui(R.string.before)).assertExists()
        fun selectLevel(kind: Int, level: Int) {
            val group = hasContentDescription(ui(R.string.body_area_metric, ui(kind), ui(R.string.region_left_knee)))
            compose.onNode(hasText(ui(level)) and hasClickAction() and hasAnyAncestor(group))
                .performScrollTo()
                .performClick()
        }
        selectLevel(R.string.pain, R.string.body_level_medium)
        selectLevel(R.string.stiffness, R.string.body_level_mild)
        click(ui(R.string.done))
        compose.onNodeWithText(ui(R.string.pain) + " " + ui(R.string.body_level_medium).lowercase() + "  " +
            ui(R.string.stiffness).lowercase() + " " + ui(R.string.body_level_mild).lowercase()).assertExists()

        click(ui(R.string.energy_and_notes))
        click(ui(R.string.energy_low))
        compose.onNodeWithText(ui(R.string.energy_low)).assertIsSelected()
        click(ui(R.string.energy_low))
        click(ui(R.string.energy_good))
        click(ui(R.string.save))
        awaitWrite {
            snapshot().checkIns.any { it.workoutId == id && it.phase == "AFTER" && it.effort == 8 && it.energy == "GOOD" }
        }
        val data = snapshot()
        val checkIn = data.checkIns.single { it.workoutId == id && it.phase == "AFTER" }
        val area = data.observations.single { it.checkInId == checkIn.id }
        assertEquals("left_knee", area.region)
        assertEquals(5, area.pain)
        assertEquals(2, area.stiffness)
    }

    @Test fun removingCheckInAreasRequiresConfirmationAndPreservesOtherPhases() {
        val id = DemoFixtures.activeId
        launchAfterCheckIn { app, before, after ->
            app.repository.saveCheckIn(before, listOf(BodyObservation(before.id, "neck", pain = 2)))
            app.repository.saveCheckIn(
                after,
                listOf(BodyObservation(after.id, "left_knee", pain = 3), BodyObservation(after.id, "right_knee", stiffness = 2))
            )
        }
        click(ui(R.string.no))
        click(ui(R.string.cancel))
        compose.onNodeWithText(ui(R.string.region_right_knee)).assertExists()
        if (compose.onAllNodesWithContentDescription(ui(R.string.remove_named, ui(R.string.region_right_knee))).fetchSemanticsNodes().isEmpty()) click(ui(R.string.region_right_knee))
        compose.onNodeWithContentDescription(ui(R.string.remove_named, ui(R.string.region_right_knee))).performScrollTo().performClick()
        click(ui(R.string.no))
        compose.onNodeWithText(quantity(R.plurals.remove_areas_question, 1)).assertExists()
        click(ui(R.string.remove))
        click(ui(R.string.save))
        compose.waitUntil(10_000) {
            val data = snapshot()
            val after = data.checkIns.single { it.workoutId == id && it.phase == "AFTER" }
            data.observations.none { it.checkInId == after.id }
        }
        val data = snapshot()
        val before = data.checkIns.single { it.workoutId == id && it.phase == "BEFORE" }
        assertEquals(2, data.observations.single { it.checkInId == before.id }.pain)
    }

    @Test fun checkInCanKeepLegacyDraftTextWithoutOverwritingTheWorkoutNote() {
        val id = DemoFixtures.activeId
        launch("CheckInAfter", "checkin/$id/AFTER") { app ->
            val checkIn: CheckIn = app.repository.snapshot().checkIns.single { it.workoutId == id && it.phase == "AFTER" }
            val draft = CheckInDraft(checkIn, emptyList(), "Older draft text", "13 Nov 2026")
            app.repository.saveWorkoutNote(id, "Current workout note")
            app.repository.saveEditorDraft(EditorDraft("checkin:$id:AFTER", "checkin", Json.encodeToString(draft), checkIn.observedAt))
        }
        compose.onNodeWithText(ui(R.string.save)).assertIsNotEnabled()
        click(ui(R.string.keep_draft_check_in_note))
        click(ui(R.string.save))
        compose.waitUntil(10_000) { snapshot().checkIns.any { it.workoutId == id && it.note.contains("Older draft text") } }
        assertEquals("Current workout note", snapshot().workout(id)?.row?.note)
    }

    @Test fun legacyBodyAreaNoteCanBeClearedAndReplaced() {
        launchAfterCheckIn { app, _, after ->
            app.repository.saveCheckIn(after, listOf(BodyObservation(after.id, "left_knee", pain = 2, note = "Old note")))
        }
        click(ui(R.string.region_left_knee))
        val field = compose.onNode(hasSetTextAction() and hasText(ui(R.string.body_area_note)))
        field.performScrollTo().performTextReplacement("")
        field.assertExists().performTextReplacement("Updated note")
        click(ui(R.string.done))
        click(ui(R.string.save))
        compose.waitUntil(10_000) {
            snapshot().observations.any { it.region == "left_knee" && it.note == "Updated note" }
        }
    }

    @Test fun exercisePhotoCanBeOpenedAndRemovedAfterCancellation() {
        val id = DemoFixtures.pulldownId
        launch("ExerciseWithSetup", "exercise/$id") { app ->
            val root = app.photos.root
            check(root.isDirectory || root.mkdirs())
            val photo = File(root, "exercise-photo.png")
            val bitmap = Bitmap.createBitmap(24, 30, Bitmap.Config.ARGB_8888)
            try {
                photo.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
            val checksum = MessageDigest.getInstance("SHA-256").digest(photo.readBytes()).toHexString()
            app.database.journal().put(
                PhotoAsset(exerciseId = id, path = photo.name, mediaType = "image/png", byteLength = photo.length(), checksum = checksum)
            )
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(ui(R.string.exercise_photo)).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodesWithContentDescription(ui(R.string.exercise_photo)).onFirst().performScrollTo().performClick()
        compose.onNodeWithContentDescription(ui(R.string.close_photo)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(ui(R.string.photo_options)).performScrollTo().performClick()
        click(ui(R.string.remove_photo))
        click(ui(R.string.cancel))
        assertEquals(1, snapshot().photos.count { it.exerciseId == id })
        compose.onNodeWithContentDescription(ui(R.string.photo_options)).performScrollTo().performClick()
        click(ui(R.string.remove_photo))
        click(ui(R.string.remove))
        compose.waitUntil(10_000) {
            snapshot().photos.none { it.exerciseId == id } &&
                compose.onAllNodesWithText(ui(R.string.no_photo)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun routineMenuCanCancelRemovalAndDuplicateTheExerciseOrder() {
        val id = DemoFixtures.routineId
        launch("LibraryRoutineExpanded", "routine/$id")
        val initial = snapshot()
        val source = initial.routines.single { it.id == id }
        val initialIds = initial.routines.map { it.id }.toSet()
        fun menu() = compose.onNodeWithContentDescription(ui(R.string.routine_options)).performClick()
        menu()
        click(ui(R.string.remove))
        click(ui(R.string.cancel))
        assertEquals(source, snapshot().routines.single { it.id == id })

        menu()
        click(ui(R.string.duplicate))
        compose.waitUntil(10_000) { snapshot().routines.any { it.id !in initialIds } }
        val data = snapshot()
        val copy = data.routines.single { it.id !in initialIds }
        assertEquals(ui(R.string.routine_copy, source.title), copy.title)
        val originalItems = data.routineItems.filter { it.routineId == id }.sortedBy { it.position }
        val copiedItems = data.routineItems.filter { it.routineId == copy.id }.sortedBy { it.position }
        assertEquals(originalItems.map { it.exerciseId }, copiedItems.map { it.exerciseId })
    }

    @Test fun uniformRoutineTargetsCanVaryBySetAndSave() {
        val id = DemoFixtures.routineId
        launch("LibraryRoutineExpanded", "routine/$id")
        compose.onNodeWithText(quantity(R.plurals.exercise_count, 7)).assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.approximate_duration, "50 min").replaceFirstChar(Char::titlecase)).assertIsDisplayed()
        val data = snapshot()
        val entry = data.routineItems.first { item ->
            item.routineId == id && item.mode == "SETS" && data.exercises.any { it.id == item.exerciseId && it.basis == "kg" }
        }
        val exercise = data.exercises.single { it.id == entry.exerciseId }
        compose.onNodeWithContentDescription(ui(R.string.named_options, exercise.name)).performScrollTo().performClick()
        click(ui(R.string.edit_targets_note))
        compose.onNodeWithText(ui(R.string.log_as)).assertIsDisplayed()
        val sets = compose.onNode(hasSetTextAction() and hasText(ui(R.string.sets)))
        sets.performScrollTo().performTextReplacement("")
        sets.performTextReplacement("4")
        compose.onNode(hasSetTextAction() and hasText("kg")).performScrollTo().performTextReplacement("32.5")
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.reps))).performScrollTo().performTextReplacement("10")
        compose.onNodeWithText(ui(R.string.vary_by_set)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(ui(R.string.remove_planned_set, 4)).assertExists()
        compose.onAllNodes(hasSetTextAction() and hasText(ui(R.string.reps))).onLast().performScrollTo().performTextReplacement("8")
        compose.onNodeWithContentDescription(ui(R.string.remove_planned_set, 2)).performScrollTo().performClick()
        click(ui(R.string.done))
        val expected = listOf(SetTarget("32.5", 10), SetTarget("32.5", 10), SetTarget("32.5", 8))
        awaitWrite { snapshot().routineItems.first { it.id == entry.id }.sets == expected }

        compose.onNodeWithContentDescription(ui(R.string.named_options, exercise.name)).performScrollTo().performClick()
        click(ui(R.string.edit_targets_note))
        compose.onNodeWithText(ui(R.string.vary_by_set)).assertDoesNotExist()
        compose.onNodeWithContentDescription(ui(R.string.remove_planned_set, 2)).performScrollTo().assertIsDisplayed()
    }

    @Test fun routineTargetFieldsFit320Dp() {
        val item = RoutineItemDraft(exerciseId = "test", sets = listOf(RoutineSetDraft("20", "8")))
        compose.setContent {
            InShapeTheme(ThemeMode.Light) {
                Column(Modifier.width(320.dp)) {
                    RoutineSetTargets(item, MeasurementBasis.Assistance, {})
                }
            }
        }
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.sets))).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.reps))).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.assist_kg))).assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.vary_by_set)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.remove_planned_set, 1)).assertIsDisplayed()
    }

    @Test fun routineExerciseOrderCanBeChangedWithAccessibilityActions() {
        val id = DemoFixtures.routineId
        launch("LibraryRoutineExpanded", "routine/$id")
        val initial = snapshot()
        val before = initial.routineItems.filter { it.routineId == id }.sortedBy { it.position }.map { it.exerciseId }
        val first = initial.exercises.single { it.id == before.first() }.name
        compose.onNodeWithContentDescription(ui(R.string.named_options, first)).performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.move_up)).assertDoesNotExist()
        compose.onNodeWithText(ui(R.string.move_down)).assertDoesNotExist()
        compose.onNodeWithText(ui(R.string.remove_exercise)).assertIsDisplayed()
        click(ui(R.string.edit_targets_note))
        click(ui(R.string.done))

        val row = compose.onNode(hasText(first) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(ui(R.string.move_down)), actions.map { it.label })
        compose.runOnIdle { assertTrue(actions.single().action()) }
        val expected = listOf(before[1], before[0]) + before.drop(2)
        awaitWrite {
            val saved = journalViewModel.snapshot.value ?: return@awaitWrite false
            val order = saved.routineItems.filter { it.routineId == id }.sortedBy { it.position }.map { it.exerciseId }
            order == expected && saved.drafts.none { it.id == "routine:$id" } && journalViewModel.saveState.value == SaveState.Saved
        }
    }

    @Test fun repeatedPastWorkoutConfirmationCreatesOneDraft() {
        launch("HistoryNovember", "History")
        val release = CompletableDeferred<Unit>()
        journalViewModel.write { release.await() }
        try {
            compose.onNodeWithContentDescription(ui(R.string.add_past_workout)).performClick()
            click(ui(R.string.continue_action))
            compose.onNodeWithText(ui(R.string.workout_name_required)).assertIsDisplayed()
            compose.onNodeWithText(ui(R.string.workout_name)).performTextReplacement("Evening session")
            val confirm = checkNotNull(
                compose.onNodeWithText(ui(R.string.continue_action)).fetchSemanticsNode().config[SemanticsActions.OnClick].action
            )
            compose.runOnIdle {
                confirm()
                confirm()
            }
            releaseWrites(release)

            compose.onNodeWithText("Evening session").assertIsDisplayed()
            assertEquals(1, snapshot().workouts.count { it.title == "Evening session" && it.state == "DRAFT" })
        } finally {
            release.complete(Unit)
        }
    }

    @Test fun pastWorkoutSavesDurationAndNoteFromTheFirstEditor() {
        launch("HistoryNovember", "History")
        compose.onNodeWithContentDescription(ui(R.string.add_past_workout)).performClick()
        compose.onNodeWithText(ui(R.string.workout_name)).performTextReplacement("Recovery session")
        click(ui(R.string.continue_action))
        click(ui(R.string.duration))
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.minutes))).performTextReplacement("32")
        click(ui(R.string.done))
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.note))).performScrollTo().performTextReplacement("A light recovery day")
        click(ui(R.string.save_workout))
        compose.waitUntil(10_000) { snapshot().history.any { it.row.title == "Recovery session" } }
        val workout = snapshot().history.single { it.row.title == "Recovery session" }
        assertEquals(1_920L, workout.row.durationSeconds)
        assertEquals("A light recovery day", workout.row.note)
        assertEquals(null, workout.row.start)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(ui(R.string.workout_options)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(ui(R.string.save_correction)).assertDoesNotExist()
    }

    @Test fun pastWorkoutRecoversUnfinishedLegacyTimingWithItsNoteAndDuration() {
        val date = "2026-11-12"
        // Resume through the same route used by an unfinished historic workout.
        val testId = DemoFixtures.id("legacy-past-timing")
        launch("HistoryNovember", "workout/$testId") { app ->
            val now = app.clock.instant().toString()
            val row = WorkoutEntity(
                id = testId, title = "Recovery session", kind = "PAST", state = "DRAFT", activeKey = null,
                journalDate = date, zoneId = app.clock.zone.id, createdAt = now, modifiedAt = now
            )
            app.repository.db.journal().insertWorkout(row)
            val draft = TimingDraft(row.title, date, "Recovered note", date, "18:00", date, "", "32", false, row.revision)
            app.repository.saveEditorDraft(EditorDraft("timing:$testId", "timing", Json.encodeToString(draft), now))
        }
        click(ui(R.string.save_workout))
        compose.waitUntil(10_000) { snapshot().history.any { it.row.id == testId } }
        val saved = snapshot().history.single { it.row.id == testId }.row
        assertEquals(1_920L, saved.durationSeconds)
        assertEquals("Recovered note", saved.note)
        assertEquals(null, saved.start)
        assertEquals(null, saved.end)
    }

    @Test fun completedCheckInSaveDoesNotPopTheWorkoutWhenCheckInWasClosed() {
        launch("ActiveWorkoutPartiallyDone", "Today")
        click(ui(R.string.resume_workout))
        click(ui(R.string.add_check_in))
        val release = CompletableDeferred<Unit>()
        journalViewModel.write { release.await() }
        try {
            if (compose.onAllNodesWithText(ui(R.string.note)).fetchSemanticsNodes().isEmpty()) click(ui(R.string.energy_and_notes))
            compose.onNodeWithText(ui(R.string.note)).performScrollTo().performTextReplacement("Ready to lift")
            click(ui(R.string.save))
            compose.onNodeWithText(ui(R.string.save)).assertIsNotEnabled()
            compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
            compose.onNodeWithText(ui(R.string.finish)).assertIsDisplayed()
            releaseWrites(release)

            compose.onNodeWithText(ui(R.string.finish)).assertIsDisplayed()
            val checkIn = snapshot().checkIns.single { it.workoutId == DemoFixtures.activeId && it.phase == "BEFORE" }
            assertEquals("Ready to lift", checkIn.note)
        } finally {
            release.complete(Unit)
        }
    }

    @Test fun historyDatePickerAndLibraryFiltersRemainReachable() {
        launch("HistoryNovember", "History")
        click(java.time.YearMonth.of(2026, 11).monthTitle())
        compose.onNodeWithText(ui(R.string.select_month)).assertExists()
        val monthLabels = context.resources.getStringArray(R.array.history_months)
        compose.onNode(hasText(monthLabels[11]) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithContentDescription(ui(R.string.previous_year)).performClick()
        compose.onNode(hasText(monthLabels[11]) and hasClickAction()).assertIsEnabled()
        compose.onNodeWithContentDescription(ui(R.string.next_year)).performClick()
        compose.onNode(hasText(monthLabels[10]) and hasClickAction()).performClick()
        compose.onNodeWithText(ui(R.string.select_month)).assertDoesNotExist()
        compose.onNodeWithContentDescription(ui(R.string.next_month)).performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.no_workouts_period)).assertExists()
        compose.onAllNodesWithText(ui(R.string.library)).onLast().performClick()
        click(ui(R.string.exercises))
        compose.onNodeWithContentDescription(
            ui(R.string.search_exercises)
        ).performTextReplacement(ui(R.string.exercise_lat_pulldown))
        compose.onNode(hasText(ui(R.string.exercise_lat_pulldown)) and !hasSetTextAction()).assertExists()
        compose.onNodeWithContentDescription(ui(R.string.favorites_only)).performClick()
        compose.onNode(
            hasText(ui(R.string.exercise_lat_pulldown)) and !hasSetTextAction()
        ).assertExists().performClick()
        compose.onNodeWithContentDescription(ui(R.string.favorite)).assertExists()
    }


    @Test fun exerciseNotesMergeOnTheNextAutosave() {
        val id = DemoFixtures.pulldownId
        launch("ExerciseWithSetup", "exercise-edit/$id") { app ->
            val exercise = app.repository.snapshot().exercises.single { it.id == id }
            app.repository.saveExercise(exercise.copy(instructions = "Sit tall", notes = "Keep a spare towel"))
        }
        assertEquals("Keep a spare towel", snapshot().exercises.single { it.id == id }.notes)
        compose.onNode(hasSetTextAction() and hasText(ui(R.string.muscles_or_labels))).performScrollTo().performTextReplacement("Lats")
        awaitWrite {
            val saved = snapshot().exercises.single { it.id == id }
            saved.notes.isEmpty() && saved.instructions == "Sit tall\n\nKeep a spare towel" && saved.muscles == "Lats"
        }
    }

    @Test fun exerciseSearchMatchesTypeAndLocation() {
        launch("HistoryNovember", "Library")
        click(ui(R.string.exercises))
        val search = compose.onNodeWithContentDescription(ui(R.string.search_exercises))
        search.performTextReplacement(ui(R.string.category_machine) + " Sali")
        compose.onNodeWithText(ui(R.string.exercise_lat_pulldown)).assertExists()
        search.performTextReplacement(ui(R.string.category_machine) + " Koti")
        compose.onNodeWithText(ui(R.string.exercise_lat_pulldown)).assertDoesNotExist()
        compose.onNodeWithText(ui(R.string.exercise_band_pulldown)).assertExists()
    }
}

package com.emmah.inshape

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.feature.checkin.CheckInDraft
import com.emmah.inshape.feature.library.RoutineDraft
import com.emmah.inshape.feature.library.RoutineItemDraft
import com.emmah.inshape.feature.workout.TimingDraft
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.theme.DarkColors
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.LightColors
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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

    private fun releaseWrites(release: CompletableDeferred<Unit>) {
        val drained = CompletableDeferred<Unit>()
        journalViewModel.write { drained.complete(Unit) }
        release.complete(Unit)
        compose.waitUntil(10_000) { drained.isCompleted }
    }

    @Test fun emptyWorkoutOffersToAddAnExercise() {
        launch("Empty", "Today") { app ->
            app.repository.startWorkout(com.emmah.inshape.data.StartSource.Empty)
        }
        click("Resume workout")
        compose.onNodeWithText("No exercises yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your plan is complete").assertDoesNotExist()
        compose.onNodeWithText("Saved automatically").assertDoesNotExist()
        click("Add exercise")
        compose.onNodeWithText("Find exercise").assertExists()
    }

    @Test fun repeatedSaveFailureKeepsRetryAvailable() {
        launch("Empty", "Today")
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        journalViewModel.write {
            if (attempts.incrementAndGet() <= 2) throw java.io.IOException("Storage is full.")
        }
        repeat(2) { failure ->
            compose.waitUntil(10_000) {
                attempts.get() == failure + 1 && compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Could not save. Storage is full.").assertIsDisplayed()
            click("Retry")
        }
        compose.waitUntil(10_000) { journalViewModel.saveState.value == SaveState.Saved }
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Saved automatically").assertDoesNotExist()
    }

    @Test fun narrowTimeInputPreservesEveningTime() = assertCompactTimeInput(width = 320, fontScale = 1f)

    @Test fun largeTextTimeInputPreservesEveningTime() = assertCompactTimeInput(width = 390, fontScale = 2f)

    @Test fun narrowLargeTextTimeInputPreservesEveningTime() = assertCompactTimeInput(width = 320, fontScale = 2f)

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
        val uses24Hours = android.text.format.DateFormat.is24HourFormat(context)
        compose.onNodeWithText(if (uses24Hours) "22" else "10").assertIsDisplayed()
        if (uses24Hours) {
            compose.onNodeWithText("PM").assertDoesNotExist()
        } else {
            compose.onNodeWithText("PM").assertIsDisplayed()
        }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals("22:42", selected) }
    }

    @Test fun exportSelectionEnablesSaveAndShareForBothFormats() {
        launch("HistoryNovember", "Export")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        click("Select all")
        compose.onNodeWithContentDescription("Include private notes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Include check-ins and body observations").performScrollTo().performClick()
        click("CSV")
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Share").assertIsEnabled()
        click("Clear selection")
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun duplicateLabelsStillSelectTheRequestedIdentity() {
        var selected = "first"
        compose.setContent {
            InShapeTheme {
                SelectField("Exercise", selected, listOf("first", "second"), { selected = it }, optionLabel = { "Same name" })
            }
        }
        compose.onNodeWithContentDescription("Exercise, Same name").performClick()
        compose.onAllNodesWithText("Same name").onLast().performClick()
        compose.runOnIdle { assertEquals("second", selected) }
    }

    @Test fun staleTimingDraftCanBeReviewedAndSavedWithoutLosingEdits() {
        reviewStaleTimingDraft("correct", "Save correction")
    }

    @Test fun finishingAlsoRequiresReviewOfAStaleTimingDraft() {
        reviewStaleTimingDraft("finish", "Save workout")
        assertEquals("COMPLETED", snapshot().workout(DemoFixtures.activeId)!!.row.state)
    }

    private fun reviewStaleTimingDraft(mode: String, saveLabel: String) {
        val id = DemoFixtures.activeId
        launch("ActiveWorkoutPartiallyDone", "timing/$id/$mode") { app ->
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
        compose.onNodeWithText("Note: Newer saved note").performScrollTo().assertIsDisplayed()
        click("Keep this draft")
        click(saveLabel)
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
        click("Review saved duration")
        compose.onNodeWithText("Saved duration: 90 seconds. Keep it, or edit planned minutes below.").assertExists()
        click("Keep saved duration")
        click("Done")
        click("Save routine")
        compose.waitUntil(30_000) { snapshot().routines.first { it.id == id }.title == "Legacy routine" }
        val saved = snapshot().routineItems.first { it.id == itemId }
        assertEquals(90L, saved.durationSeconds)
        assertEquals("Recovered planned note", saved.note)
    }

    @Test fun themeChangesImmediatelyAndGoalCanBeOffWhileRemindersExpand() {
        grantNotificationPermission()
        launch("SettingsConfigured", "Settings")
        click("Dark")
        compose.onNodeWithText("Dark").assertIsSelected()
        assertEquals(ThemeMode.Dark, container.themeSettings.mode.value)
        assertEquals(
            DarkColors.background.toArgb(),
            compose
                .onRoot()
                .captureToImage()
                .toPixelMap()[0, 0]
                .toArgb()
        )
        click("Light")
        assertEquals(
            LightColors.background.toArgb(),
            compose
                .onRoot()
                .captureToImage()
                .toPixelMap()[0, 0]
                .toArgb()
        )
        repeat(3) {
            compose.onNodeWithContentDescription(
                "Decrease weekly goal"
            ).performScrollTo().performClick()
            compose.waitForIdle()
        }
        compose.waitUntil { snapshot().preferences.weeklyGoal == null }
        compose.waitUntil {
            compose.onAllNodes(isNotEnabled() and androidx.compose.ui.test.hasContentDescription("Decrease weekly goal"))
                .fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Decrease weekly goal").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase weekly goal").performClick()
        compose.waitUntil { snapshot().preferences.weeklyGoal == 3 }
        compose.onNodeWithContentDescription(
            "Enable workout reminders"
        ).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            !snapshot().preferences.remindersEnabled && compose.onAllNodesWithText("Time").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Enable workout reminders").performClick()
        compose.waitUntil(10_000) {
            snapshot().preferences.remindersEnabled && compose.onAllNodesWithText("Time").fetchSemanticsNodes().size == 1
        }
    }

    @Test fun workoutNoteSetCompletionReopeningSwapDeferAndSkipStayConnected() {
        launch("ActiveWorkoutPartiallyDone", "workout/${DemoFixtures.activeId}")
        fun exerciseMenu(action: String) {
            val active = snapshot().active!!
            val current = active.items.first { it.row.id == active.row.currentItemId }
            compose.onNodeWithContentDescription("More options for ${current.row.name}").performScrollTo().performClick()
            click(action)
        }
        exerciseMenu("Add note")
        compose.onNodeWithText("Note for this exercise").assertExists()
        compose.onNodeWithText(
            "Note for this exercise"
        ).performScrollTo().performTextReplacement("Keep shoulders down")
        compose.onNodeWithContentDescription("Complete set 3").performScrollTo().performClick()
        if (compose.onAllNodesWithText("Exercise done").fetchSemanticsNodes().isEmpty()) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Exercise done").fetchSemanticsNodes().isNotEmpty() }
        }
        click("Exercise done")
        val original = DemoFixtures.id("active:item:4")
        compose.waitUntil { snapshot().items.find { it.id == original }?.disposition == "DONE" }
        assertEquals("Keep shoulders down", snapshot().items.first { it.id == original }.note)
        compose.onNodeWithContentDescription("Reopen Lat pulldown").performScrollTo().performClick()
        compose.waitUntil { snapshot().active?.row?.currentItemId == original }
        exerciseMenu("Swap exercise")
        click("Assisted pull-up machine")
        compose.waitUntil { snapshot().items.any { it.alternativeOf == original } }
        val replacement = snapshot().active!!.row.currentItemId
        exerciseMenu("Do later")
        compose.waitUntil { snapshot().active!!.row.currentItemId != replacement }
        val skipped = snapshot().active!!.row.currentItemId
        exerciseMenu("Skip today")
        compose.waitUntil { snapshot().items.find { it.id == skipped }?.disposition == "SKIPPED" }
        click("Finish")
        compose.waitUntil(10_000) { snapshot().drafts.any { it.id == "finish:${DemoFixtures.activeId}" } }
        compose.onNodeWithText("Save workout").assertIsDisplayed()
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
        compose.onNodeWithText("Just right").assertIsSelected()
        compose.onAllNodesWithText("Mild").onFirst().assertExists()
        if (compose.onAllNodesWithText("Add energy and notes").fetchSemanticsNodes().isNotEmpty()) {
            click("Add energy and notes")
        }
        compose.onNodeWithText("Note").performScrollTo().performTextReplacement("After stretching")
        click("Save")
        compose.waitUntil(10_000) { snapshot().checkIns.any { it.workoutId == id && it.note == "After stretching" } }
        val check = snapshot().checkIns.first { it.workoutId == id && it.phase == "AFTER" }
        assertEquals(7, check.effort)
        assertEquals(observed, check.observedAt)
        val area = snapshot().observations.single { it.checkInId == check.id }
        assertEquals(3, area.pain)
        assertEquals(null, area.stiffness)
    }

    @Test fun afterCheckInSavesIndependentSymptomsAndEnergy() {
        val id = DemoFixtures.activeId
        launch("CheckInAfter", "checkin/$id/AFTER") { app ->
            val data = app.repository.snapshot()
            val before = data.checkIns.single { it.workoutId == id && it.phase == "BEFORE" }
            val after = data.checkIns.single { it.workoutId == id && it.phase == "AFTER" }
            app.repository.saveCheckIn(before, listOf(BodyObservation(before.id, "left_knee", pain = 2)))
            app.repository.saveCheckIn(after.copy(energy = null), emptyList())
        }
        click("Hard")
        click("Yes")
        click("Back")
        click("Choose area")
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Left knee"))
        click("Left knee")
        compose.onNodeWithText("Before").assertExists()
        fun selectLevel(kind: String, level: String) {
            val group = hasContentDescription("$kind for left_knee")
            compose.onNode(hasText(level) and hasClickAction() and hasAnyAncestor(group))
                .performScrollTo()
                .performClick()
        }
        selectLevel("Pain", "Medium")
        selectLevel("Stiffness", "Mild")
        click("Left knee")
        compose.onNodeWithText("Pain medium").assertExists()
        compose.onNodeWithText("Stiffness mild").assertExists()

        click("Add energy and notes")
        click("Low")
        compose.onNodeWithText("Low").assertIsSelected()
        click("Low")
        click("Good")
        click("Save")
        compose.waitUntil(10_000) {
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
        launch("CheckInAfter", "checkin/$id/AFTER") { app ->
            val data = app.repository.snapshot()
            val before = data.checkIns.single { it.workoutId == id && it.phase == "BEFORE" }
            val after = data.checkIns.single { it.workoutId == id && it.phase == "AFTER" }
            app.repository.saveCheckIn(before, listOf(BodyObservation(before.id, "neck", pain = 2)))
            app.repository.saveCheckIn(
                after,
                listOf(BodyObservation(after.id, "left_knee", pain = 3), BodyObservation(after.id, "right_knee", stiffness = 2))
            )
        }
        click("No")
        click("Cancel")
        compose.onNodeWithText("Right knee").assertExists()
        if (compose.onAllNodesWithContentDescription("Remove Right knee").fetchSemanticsNodes().isEmpty()) click("Right knee")
        compose.onNodeWithContentDescription("Remove Right knee").performScrollTo().performClick()
        click("No")
        compose.onNodeWithText("Remove 1 areas?").assertExists()
        click("Remove")
        click("Save")
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
        compose.onNodeWithText("Save").assertIsNotEnabled()
        click("Keep draft text as check-in note")
        click("Save")
        compose.waitUntil(10_000) { snapshot().checkIns.any { it.workoutId == id && it.note.contains("Older draft text") } }
        assertEquals("Current workout note", snapshot().workout(id)?.row?.note)
    }

    @Test fun exercisePhotoCanBeOpenedAndRemovedAfterCancellation() {
        val id = DemoFixtures.pulldownId
        launch("ExerciseWithSetup", "exercise/$id") { app ->
            val source = File.createTempFile("exercise-photo", ".png", context.cacheDir)
            val bitmap = Bitmap.createBitmap(24, 30, Bitmap.Config.ARGB_8888)
            try {
                source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                app.photos.importPhoto(id, Uri.fromFile(source))
            } finally {
                bitmap.recycle()
                source.delete()
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Exercise photo").fetchSemanticsNodes().size >= 2 }
        compose.onAllNodesWithContentDescription("Exercise photo").onFirst().performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close photo").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Photo options").performScrollTo().performClick()
        click("Remove photo")
        click("Cancel")
        assertEquals(1, snapshot().photos.count { it.exerciseId == id })
        compose.onNodeWithContentDescription("Photo options").performScrollTo().performClick()
        click("Remove photo")
        click("Remove")
        compose.waitUntil(10_000) {
            snapshot().photos.none { it.exerciseId == id } &&
                compose.onAllNodesWithText("Your photo of the machine").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun routineMenuCanCancelRemovalAndDuplicateTheExerciseOrder() {
        val id = DemoFixtures.routineId
        launch("LibraryRoutineExpanded", "routine/$id")
        val initial = snapshot()
        val source = initial.routines.single { it.id == id }
        val initialIds = initial.routines.map { it.id }.toSet()
        fun menu() = compose.onNodeWithContentDescription("Routine options").performClick()
        menu()
        click("Remove")
        click("Cancel")
        assertEquals(source, snapshot().routines.single { it.id == id })

        menu()
        click("Duplicate")
        compose.waitUntil(10_000) { snapshot().routines.any { it.id !in initialIds } }
        val data = snapshot()
        val copy = data.routines.single { it.id !in initialIds }
        assertEquals("${source.title} copy", copy.title)
        val originalItems = data.routineItems.filter { it.routineId == id }.sortedBy { it.position }
        val copiedItems = data.routineItems.filter { it.routineId == copy.id }.sortedBy { it.position }
        assertEquals(originalItems.map { it.exerciseId }, copiedItems.map { it.exerciseId })
    }

    @Test fun repeatedPastWorkoutConfirmationCreatesOneDraft() {
        launch("HistoryNovember", "History")
        val release = CompletableDeferred<Unit>()
        journalViewModel.write { release.await() }
        try {
            click("Past workout")
            click("Add exercises")
            compose.onNodeWithText("Enter a workout name").assertIsDisplayed()
            compose.onNodeWithText("Workout name").performTextReplacement("Evening session")
            val confirm = checkNotNull(
                compose.onNodeWithText("Add exercises").fetchSemanticsNode().config[SemanticsActions.OnClick].action
            )
            compose.runOnIdle {
                confirm()
                confirm()
            }
            releaseWrites(release)

            compose.onNodeWithContentDescription("Workout options").assertIsDisplayed()
            assertEquals(1, snapshot().workouts.count { it.title == "Evening session" && it.state == "DRAFT" })
        } finally {
            release.complete(Unit)
        }
    }

    @Test fun completedCheckInSaveDoesNotPopTheWorkoutWhenCheckInWasClosed() {
        launch("ActiveWorkoutPartiallyDone", "Today")
        click("Resume workout")
        compose.onNodeWithContentDescription("Workout options").performClick()
        click("Check in before workout")
        val release = CompletableDeferred<Unit>()
        journalViewModel.write { release.await() }
        try {
            compose.onNodeWithText("Note").performScrollTo().performTextReplacement("Ready to lift")
            click("Save")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Workout options").assertIsDisplayed()
            releaseWrites(release)

            compose.onNodeWithContentDescription("Workout options").assertIsDisplayed()
            val checkIn = snapshot().checkIns.single { it.workoutId == DemoFixtures.activeId && it.phase == "BEFORE" }
            assertEquals("Ready to lift", checkIn.note)
        } finally {
            release.complete(Unit)
        }
    }

    @Test fun historyDatePickerAndLibraryFiltersRemainReachable() {
        launch("HistoryNovember", "History")
        click("November 2026")
        compose.onAllNodes(hasText("Select date") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Show whole month").assertExists()
        click("Show whole month")
        compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        compose.onNodeWithText("No workouts in this period").assertExists()
        compose.onAllNodesWithText("Library").onLast().performClick()
        click("Exercises")
        compose.onNodeWithContentDescription(
            "Search exercises"
        ).performTextReplacement("Lat pulldown")
        compose.onNode(hasText("Lat pulldown") and !hasSetTextAction()).assertExists()
        compose.onNodeWithContentDescription("Favorites only").performClick()
        compose.onNode(
            hasText("Lat pulldown") and !hasSetTextAction()
        ).assertExists().performClick()
        compose.onNodeWithContentDescription("Favorite").assertExists()
    }
}

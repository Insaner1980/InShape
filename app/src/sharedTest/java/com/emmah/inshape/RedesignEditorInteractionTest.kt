package com.emmah.inshape

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.feature.assistant.RoutineProposalScreen
import com.emmah.inshape.feature.library.RoutineDraft
import com.emmah.inshape.feature.library.RoutineItemDraft
import com.emmah.inshape.feature.library.RoutineSetDraft
import com.emmah.inshape.feature.library.RoutineSetTargets
import com.emmah.inshape.ui.components.RecordedDatePicker
import com.emmah.inshape.ui.components.RecordedDateRangePicker
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeMode
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedesignEditorInteractionTest : RedesignInteractionFixture() {
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
        val data = JournalSnapshot(
            exercises = listOf(
                Exercise(name = "Fixture press"),
                Exercise(name = "Fixture row", location = "Sali"),
                Exercise(name = "Fixture stretch", location = "Home")
            )
        )
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
            workoutId = row.id,
            exerciseId = exercise.id,
            equipmentId = exercise.equipmentId,
            name = exercise.name,
            category = exercise.category,
            basis = exercise.basis
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
        val container = createContainer()
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

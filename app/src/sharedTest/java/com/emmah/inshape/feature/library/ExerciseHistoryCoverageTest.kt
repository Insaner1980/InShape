package com.emmah.inshape.feature.library

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.ExercisePlan
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.SavedLink
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.shortDate
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseHistoryCoverageTest : RedesignInteractionFixture() {
    @Test fun exerciseHistoryOpensTheRecordedWorkoutAndTheExerciseChart() {
        val exerciseId = DemoFixtures.pulldownId
        launch("ProgressWithHistory", "exercise/$exerciseId")
        val workout = snapshot().history
            .filter { entry -> entry.items.any { it.row.exerciseId == exerciseId && it.hasPerformedWork } }
            .maxBy { it.row.journalDate }
        val date = LocalDate.parse(workout.row.journalDate).shortDate()
        compose.onNode(hasClickAction() and hasText(date)).performScrollTo().performClick()
        compose.onNodeWithText(workout.row.title).assertExists()

        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        click(ui(R.string.see_chart))
        compose.onNodeWithText(ui(R.string.best_so_far)).performScrollTo().assertExists()
        compose.onNodeWithText("27,5 kg").assertExists()
    }

    @Test fun historicalExerciseKeepsItsSavedSetupAndGuideWithoutALibraryEntry() {
        val exerciseId = newId()
        val itemId = newId()
        val name = "Historic fixture press"
        launch("Empty", "exercise/$exerciseId?item=$itemId") { app ->
            val dao = app.database.journal()
            val instant = app.clock.instant().toString()
            val date = LocalDate.now(app.clock).toString()
            val workout = WorkoutEntity(
                journalDate = date,
                zoneId = app.clock.zone.id,
                state = "COMPLETED",
                activeKey = null,
                createdAt = instant,
                modifiedAt = instant
            )
            dao.insertWorkout(workout)
            dao.put(
                SessionItem(
                    id = itemId,
                    workoutId = workout.id,
                    exerciseId = exerciseId,
                    equipmentId = newId(),
                    name = name,
                    category = "Machine",
                    basis = "kg",
                    setup = "Saved-seat-marker: 7",
                    plan = ExercisePlan(catalogExerciseId = "leg_press")
                )
            )
        }
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText(ui(R.string.setup_saved)).assertExists()
        compose.onNodeWithText("Saved-seat-marker 7").assertExists()
        compose.onNodeWithText(ui(R.string.edit)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(ui(R.string.favorite)).assertDoesNotExist()
        compose.onNodeWithText(ui(R.string.add_photo)).assertIsNotEnabled()
        click(ui(R.string.movement_guide))
        compose.onNodeWithContentDescription(ui(R.string.movement_illustration, ui(R.string.exercise_leg_press))).assertExists()
    }

    @Test fun aLibraryFavoriteCanBeChangedBeforeOpeningASavedAlternative() {
        val original = DemoFixtures.pulldownId
        launch("ExerciseWithSetup", "exercise/$original")
        val state = snapshot()
        val exercise = state.exercises.single { it.id == original }
        val alternative = state.exercises.first { it.id in exercise.alternatives }
        compose.onNodeWithContentDescription(ui(R.string.favorite)).performClick()
        awaitWrite { snapshot().exercises.single { it.id == original }.favorite != exercise.favorite }

        click(alternative.name)
        compose.onNodeWithText(alternative.name.exerciseDisplayName(context)).assertExists()
        assertEquals(exercise.alternatives, snapshot().exercises.single { it.id == original }.alternatives)
    }

    @Test fun savedExerciseLinksDispatchAViewIntentAndExplainLaunchFailures() {
        val app = createContainer()
        runBlocking { app.storageReady.await() }
        val exercise = Exercise(
            name = "Fixture linked exercise",
            links = listOf(
                SavedLink("Reference link", "https://reference.example/exercise"),
                SavedLink("No viewer link", "https://no-viewer.example/exercise"),
                SavedLink("Blocked link", "https://blocked.example/exercise")
            )
        )
        var opened: Intent? = null
        compose.setContent {
            val base = LocalContext.current
            val controlled = remember(base) {
                object : ContextWrapper(base) {
                    override fun startActivity(intent: Intent) {
                        when (intent.data?.host) {
                            "no-viewer.example" -> throw ActivityNotFoundException()
                            "blocked.example" -> throw SecurityException()
                            else -> opened = intent
                        }
                    }
                }
            }
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            CompositionLocalProvider(LocalContext provides controlled) {
                InShapeTheme {
                    ExerciseScreen(exercise.id, null, JournalSnapshot(exercises = listOf(exercise)), vm, {}, {}, {}, {}, {})
                }
            }
        }

        click("Reference link")
        compose.runOnIdle {
            assertEquals(Intent.ACTION_VIEW, opened?.action)
            assertEquals("https://reference.example/exercise", opened?.dataString)
        }
        click("No viewer link")
        compose.onNodeWithText(ui(R.string.link_no_app)).assertExists()
        click("Blocked link")
        compose.onNodeWithText(ui(R.string.link_open_failed)).assertExists()
        compose.onNodeWithText(ui(R.string.link_no_app)).assertDoesNotExist()
    }
}

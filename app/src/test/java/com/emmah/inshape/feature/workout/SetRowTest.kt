package com.emmah.inshape.feature.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.assistant.ReviewedSet
import com.emmah.inshape.feature.assistant.WorkoutReview
import com.emmah.inshape.feature.assistant.WorkoutReviewDraft
import com.emmah.inshape.feature.assistant.confirmWorkoutReview
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SetRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val databaseName = "set-row-${newId()}.db"
    private lateinit var container: AppContainer

    @After fun cleanup() {
        if (::container.isInitialized) {
            compose.activityRule.scenario.close()
            container.close()
            container.context.deleteDatabase(databaseName)
            container.storageRoot.deleteRecursively()
        }
    }

    @Test fun undoAfterAssistantLogUsesSavedDraft() = assertAssistantDraft(width = 390, fontScale = 1f)

    @Test fun undoAfterAssistantLogUsesSavedDraftAt320Dp() = assertAssistantDraft(width = 320, fontScale = 1f)

    @Test fun undoAfterAssistantLogUsesSavedDraftAtLargeFontScale() = assertAssistantDraft(width = 390, fontScale = 2f)

    @Test fun undoAfterAssistantCorrectionUsesSavedDraftAt320DpAndLargeFontScale() =
        assertAssistantDraft(width = 320, fontScale = 2f, initiallyCompleted = true)

    private fun assertAssistantDraft(width: Int, fontScale: Float, initiallyCompleted: Boolean = false) {
        container = AppContainer(compose.activity.applicationContext, databaseName = databaseName)
        val repo = container.repository
        val setId = runBlocking {
            val exercise = Exercise(name = "Fixture leg press")
            repo.saveExercise(exercise)
            val workoutId = repo.startWorkout(StartSource.Empty)
            val itemId = repo.addExercise(workoutId, exercise.id)
            repo.addSet(itemId).also { id ->
                repo.saveSetDraft(id, "35", "8")
                if (initiallyCompleted) repo.setSetCompleted(id, true)
            }
        }
        compose.setContent {
            val vm = viewModel { JournalViewModel(container) }
            val snapshot by vm.snapshot.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                InShapeTheme {
                    Box(Modifier.width(width.dp)) {
                        snapshot?.sets?.singleOrNull()?.let { SetRow(it, 1, "kg", vm) }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("35")).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val workout = requireNotNull(repo.snapshot().active)
            val item = workout.items.single()
            val proposal = if (initiallyCompleted) {
                WorkoutReview("correct", listOf(ReviewedSet("42", "12")), correctSetId = setId)
            } else {
                WorkoutReview("log", listOf(ReviewedSet("42", "12")))
            }
            val command = AssistantDraft("fixture", newId(), "Fixture")
            container.database.journal().put(command)
            repo.confirmWorkoutReview(command, WorkoutReviewDraft(workout.row.revision, workout.row.id, item.row.id, null, proposal))
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("42")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(container.context.getString(R.string.undo_set_number, 1)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2 }
        compose.onNode(hasText("42") and hasSetTextAction()).assertExists()
        compose.onNode(hasText("12") and hasSetTextAction()).performTextReplacement("13")
        compose.waitUntil(10_000) {
            runBlocking { repo.snapshot().sets.single().let { it.rawLoad == "42" && it.rawReps == "13" } }
        }
    }
}

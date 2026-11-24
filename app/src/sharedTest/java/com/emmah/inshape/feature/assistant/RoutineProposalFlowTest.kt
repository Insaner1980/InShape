package com.emmah.inshape.feature.assistant

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutineProposalFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val name = "routine-proposal-${newId()}.db"
    private lateinit var app: AppContainer

    @Test fun routineSuggestionRejectsUnknownHandlesAndSavesAReviewedOrder() {
        val attempts = AtomicInteger()
        create(object : AssistantGateway by DisabledAssistant {
            override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer {
                assertTrue(instructions.contains("\"item_1\":\"Fixture press\""))
                assertTrue(instructions.contains("\"item_2\":\"Fixture row\""))
                assertEquals("Aloita soudulla\nAvailable minutes: 15", input)
                val handles = if (attempts.incrementAndGet() == 1) "\"unknown\"" else "\"item_2\",\"item_1\""
                return AssistantAnswer(
                    Json.parseToJsonElement("""{"items":[$handles],"reason":"Soutu ensin"}""").jsonObject,
                    null
                )
            }
        })
        val first = Exercise(name = "Fixture press")
        val second = Exercise(name = "Fixture row")
        val data = runBlocking {
            app.repository.saveExercise(first)
            app.repository.saveExercise(second)
            app.repository.snapshot()
        }
        show(data)
        enter(R.string.routine_name, "Reviewed routine")
        enter(R.string.available_minutes, "15")
        compose.clickText(first.name)
        compose.clickText(second.name)
        enter(R.string.goals_or_changes, "Aloita soudulla")
        compose.clickText(ui(R.string.suggest_exercises))
        waitFor(ui(R.string.unavailable_equipment))
        assertTrue(runBlocking { app.repository.snapshot().routines.isEmpty() })
        compose.clickText(ui(R.string.suggest_exercises))
        waitFor("Soutu ensin")
        compose.onNodeWithText("Fixture row\nFixture press").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.unknown_estimate)).performScrollTo().assertIsDisplayed()
        compose.clickText(ui(R.string.save_as_routine))
        compose.waitUntil(10_000) { runBlocking { app.repository.snapshot().routines.isNotEmpty() } }
        val result = runBlocking { app.repository.snapshot() }
        assertEquals("Reviewed routine", result.routines.single().title)
        assertEquals(listOf(second.id, first.id), result.routineItems.sortedBy { it.position }.map { it.exerciseId })
        assertEquals(2, attempts.get())
    }

    @Test fun activeAdaptationKeepsRecordedWorkAndSkipsOnlyTheDeselectedExercise() {
        create()
        val movements = listOf("Recorded exercise", "Optional exercise", "Next exercise").map { Exercise(name = it) }
        val data = runBlocking {
            movements.forEach { app.repository.saveExercise(it) }
            val workout = app.repository.startWorkout(StartSource.Empty)
            val items = movements.map { app.repository.addExercise(workout, it.id) }
            val set = app.repository.addSet(items.first())
            app.repository.saveSetDraft(set, "25", "8")
            app.repository.setSetCompleted(set, true)
            app.repository.snapshot()
        }
        show(data)
        compose.onNodeWithText(ui(R.string.recorded_work_stays)).performScrollTo().assertIsDisplayed()
        compose.clickText(movements[1].name)
        compose.onNodeWithText(ui(R.string.skip_today)).performScrollTo().assertIsDisplayed()
        compose.clickText(movements[1].name)
        compose.onNodeWithText(ui(R.string.skip_today)).assertDoesNotExist()
        compose.clickText(movements[1].name)
        compose.clickText(ui(R.string.review_routine))
        compose.onNodeWithText("Recorded exercise\nNext exercise").performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking { app.repository.snapshot().items.none { it.disposition == "SKIPPED" } })
        compose.clickText(ui(R.string.apply_to_today))
        compose.waitUntil(10_000) { runBlocking { app.repository.snapshot().items.any { it.disposition == "SKIPPED" } } }
        val result = runBlocking { requireNotNull(app.repository.snapshot().active) }
        assertEquals(movements[1].id, result.items.single { it.row.disposition == "SKIPPED" }.row.exerciseId)
        assertEquals("25", result.items.first().completedSets.single().load)
    }

    private fun create(gateway: AssistantGateway = DisabledAssistant) {
        app = AppContainer(compose.activity.applicationContext, databaseName = name, assistantFixture = gateway)
    }

    private fun show(data: JournalSnapshot) {
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            InShapeTheme { RoutineProposalScreen(data, data.active, vm) {} }
        }
    }

    private fun enter(label: Int, value: String) {
        compose.onNode(hasText(ui(label)) and hasSetTextAction()).performScrollTo().performTextInput(value)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun closeFixture() {
        if (::app.isInitialized) {
            compose.activityRule.scenario.close()
            app.close()
            app.context.deleteDatabase(name)
            app.context.deleteDatabase("music-$name")
            app.storageRoot.deleteRecursively()
        }
    }
}

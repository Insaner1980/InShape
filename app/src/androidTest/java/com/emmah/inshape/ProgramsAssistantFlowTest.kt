package com.emmah.inshape

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.assistant.AssistantAnswer
import com.emmah.inshape.feature.assistant.AssistantGateway
import com.emmah.inshape.feature.assistant.AssistantImage
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgramsAssistantFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val requests = AtomicInteger()
    private lateinit var app: AppContainer
    private lateinit var databaseName: String

    private fun fixture() {
        databaseName = "program-flow-${newId()}.db"
        app = AppContainer(ui.activity.applicationContext, databaseName = databaseName, assistantFixture = object : AssistantGateway {
            override suspend fun checkConnection(): String = error("Unexpected connection test")
            override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer {
                requests.incrementAndGet()
                assertEquals("Tein 42,5 kiloa, 12 toistoa", input)
                val proposal = """{"kind":"log","target":"item_1","sets":[{"load":"42,5","reps":"12","basis":"kg"}]}"""
                return AssistantAnswer(Json.parseToJsonElement(proposal).jsonObject, null)
            }

            override suspend fun extract(
                instructions: String,
                input: String,
                images: List<AssistantImage>,
                schema: JsonObject
            ): AssistantAnswer = error("Unexpected vision request")

            override suspend fun transcribe(clip: ByteArray, exerciseHint: String): String = error("Unexpected microphone request")
        })
    }

    @After fun closeFixture() {
        if (::app.isInitialized) {
            ui.activityRule.scenario.close()
            app.close()
            app.context.deleteDatabase(databaseName)
            app.context.deleteDatabase("music-$databaseName")
            app.storageRoot.deleteRecursively()
        }
    }

    @Test fun browsingAnInstalledGuideLeavesTheJournalAndProviderUntouched() {
        fixture()
        ui.setContent { InShapeTheme { AppNavigation(app, initialRoute = "Library") } }
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Rauhallinen aloitus", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Rauhallinen aloitus", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithText("Jalkaprässi", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithContentDescription(ui(R.string.movement_illustration, ui(R.string.exercise_leg_press)))
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(ui(R.string.enlarge_guide)).performScrollTo().performClick()
        ui.onNodeWithText(ui(R.string.fit_guide)).assertIsDisplayed()
        val snapshot = runBlocking { app.repository.snapshot() }
        assertEquals(0, snapshot.exercises.size)
        assertEquals(0, snapshot.routines.size)
        assertEquals(0, snapshot.workouts.size)
        assertEquals(0, requests.get())
    }

    @Test fun finnishEntryWritesOnlyAfterReviewAndConfirmation() {
        fixture()
        val id = runBlocking {
            val exercise = Exercise(name = "Fixture leg press")
            app.repository.saveExercise(exercise)
            val workout = app.repository.startWorkout(StartSource.Empty)
            app.repository.addExercise(workout, exercise.id)
            workout
        }
        ui.setContent { InShapeTheme { AppNavigation(app, initialRoute = "workout/$id") } }
        ui.onNodeWithContentDescription(ui(R.string.named_options, "Fixture leg press")).performScrollTo().performClick()
        ui.onNodeWithText(ui(R.string.workout_assistant)).performClick()
        assertEquals(0, requests.get())
        val input = hasText(ui(R.string.write_request)) and hasSetTextAction() and isEnabled()
        ui.waitUntil(10_000) { ui.onAllNodes(input).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(input).performTextInput("Tein 42,5 kiloa, 12 toistoa")
        ui.onNodeWithText(ui(R.string.send_for_review)).performScrollTo().performClick()
        ui.onNodeWithText(ui(R.string.save)).performScrollTo().assertIsDisplayed()
        assertEquals(0, runBlocking { app.repository.snapshot().sets.size })
        ui.onNodeWithText(ui(R.string.save)).performClick()
        ui.waitUntil(5_000) {
            runBlocking { app.repository.snapshot().sets.singleOrNull()?.load == "42.5" }
        }
        val set = runBlocking { app.repository.snapshot().sets.single() }
        assertEquals(12, set.reps)
        assertEquals(1, requests.get())
    }
}

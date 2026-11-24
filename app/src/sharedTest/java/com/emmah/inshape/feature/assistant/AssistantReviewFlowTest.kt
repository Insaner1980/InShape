package com.emmah.inshape.feature.assistant

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
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
class AssistantReviewFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val requests = AtomicInteger()
    private lateinit var app: AppContainer
    private val databaseName = "assistant-review-${newId()}.db"

    private fun fixture(interpret: (suspend (String) -> AssistantAnswer)? = null) {
        app = AppContainer(
            ui.activity.applicationContext,
            databaseName = databaseName,
            assistantFixture = object : AssistantGateway {
                override suspend fun checkConnection(): String = error("Unexpected connection test")
                override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer {
                    requests.incrementAndGet()
                    if (interpret != null) return interpret(input)
                    assertEquals("Tein 42,5 kiloa, 12 toistoa", input)
                    return AssistantAnswer(Json.parseToJsonElement(FINNISH_DECIMAL_WORKOUT_PROPOSAL).jsonObject, null)
                }

                override suspend fun extract(
                    instructions: String,
                    input: String,
                    images: List<AssistantImage>,
                    schema: JsonObject
                ): AssistantAnswer {
                    requests.incrementAndGet()
                    assertEquals("Soutu, 2 x 8", input)
                    assertTrue(images.isEmpty())
                    return AssistantAnswer(
                        Json.parseToJsonElement(
                            """
                            {
                              "uncertainties":"Tarkista ote",
                              "days":[{"title":"Selkä","rows":[
                                {"name":"Soutu","basis":"kg","sets":"2","roundTargets":[
                                  {"load":"20","min":"7","max":"9","perSide":true},
                                  {"load":"25","min":"5","max":"7","perSide":false}
                                ]},
                                {"name":"Epäselvä liike","sourceExcerpt":"Laitteen nimi puuttuu","group":"A","rounds":"3"}
                              ]}]
                            }
                            """.trimIndent()
                        ).jsonObject,
                        null
                    )
                }

                override suspend fun transcribe(clip: ByteArray, exerciseHint: String): String = error("Unexpected microphone request")
            }
        )
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
        val options = ui(R.string.named_options, "Fixture leg press")
        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription(options).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription(options).performScrollTo().performClick()
        ui.onNodeWithText(ui(R.string.workout_assistant)).performClick()
        assertEquals(0, requests.get())
        val input = hasText(ui(R.string.write_request)) and hasSetTextAction() and isEnabled()
        ui.waitUntil(10_000) { ui.onAllNodes(input).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(input).performTextInput("Tein 42,5 kiloa, 12 toistoa")
        ui.onNodeWithText(ui(R.string.send_for_review)).performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(ui(R.string.save)).fetchSemanticsNodes().isNotEmpty() }
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

    @Test fun clarificationAndCancellationWriteNoResultAndMissingKeyOffersSettings() {
        val settings = AtomicInteger()
        val cancelled = AtomicInteger()
        fixture { input ->
            when (input) {
                "Kirjaa sarja" -> AssistantAnswer(
                    Json.parseToJsonElement("""{"kind":"clarification","question":"Mikä paino?"}""").jsonObject, null
                )
                "Odota" -> try {
                    awaitCancellation()
                } finally {
                    cancelled.incrementAndGet()
                }
                else -> throw AssistantFailure(AssistantFailureKind.NO_KEY)
            }
        }
        val data = startSingleExercise(Exercise(name = "Fixture press"))
        val workout = requireNotNull(data.active)
        val item = workout.items.single()
        ui.setContent {
            InShapeTheme { AssistantReview(app, data, workout, item, null, { settings.incrementAndGet() }, {}) }
        }
        send("Kirjaa sarja")
        waitFor("Mikä paino?")
        ui.onNodeWithText(ui(R.string.save)).assertDoesNotExist()
        send("Odota")
        ui.waitUntil(10_000) { requests.get() == 2 }
        ui.onNodeWithContentDescription(ui(R.string.cancel_request)).performClick()
        ui.waitUntil(10_000) { cancelled.get() == 1 }
        send("Tarvitaan avain")
        waitFor(ui(AssistantFailureKind.NO_KEY.message))
        ui.clickText(ui(R.string.assistant_settings))
        ui.waitUntil(10_000) { settings.get() == 1 }
        assertEquals(3, requests.get())
        assertTrue(runBlocking { app.repository.snapshot().sets.isEmpty() })
        assertEquals(null, runBlocking { app.database.journal().assistantDraft("workout:${workout.row.id}:${item.row.id}") })
    }

    @Test fun changingTheReviewedDurationSavesOnlyTheFinalUserValue() {
        fixture {
            AssistantAnswer(Json.parseToJsonElement("""{"kind":"duration","target":"item_1","durationSeconds":65}""").jsonObject, null)
        }
        val data = startSingleExercise(Exercise(name = "Fixture timer", basis = "duration"))
        val workout = requireNotNull(data.active)
        ui.setContent {
            InShapeTheme { AssistantReview(app, data, workout, workout.items.single(), null, {}, {}) }
        }
        send("Kirjaa 65 sekuntia")
        waitFor(ui(R.string.performed_time))
        val field = hasText(ui(R.string.seconds)) and hasSetTextAction()
        ui.onNode(field).performScrollTo().performTextReplacement("72")
        assertEquals(null, runBlocking { app.repository.snapshot().items.single().durationSeconds })
        ui.clickText(ui(R.string.save))
        ui.waitUntil(10_000) { runBlocking { app.repository.snapshot().items.single().durationSeconds == 72L } }
        assertEquals(1, requests.get())
    }

    private fun startSingleExercise(exercise: Exercise): JournalSnapshot = runBlocking {
        app.repository.saveExercise(exercise)
        val workout = app.repository.startWorkout(StartSource.Empty)
        app.repository.addExercise(workout, exercise.id)
        app.repository.snapshot()
    }

    private fun send(text: String) {
        val input = hasText(ui(R.string.write_request)) and hasSetTextAction() and isEnabled()
        ui.waitUntil(10_000) { ui.onAllNodes(input).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(input).performTextReplacement(text)
        ui.clickText(ui(R.string.send_for_review))
    }

    private fun waitFor(text: String) {
        ui.waitUntil(10_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun programExtractionWritesOnlyAfterExerciseSelectionAndConfirmation() {
        fixture()
        ui.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            InShapeTheme { AssistantImportScreen(false, JournalSnapshot(), vm, {}, { _, _ -> }) }
        }
        val input = hasText(ui(R.string.paste_program_text)) and hasSetTextAction()
        ui.onNode(input).performScrollTo().performTextInput("  Soutu, 2 x 8  ")
        ui.clickText(ui(R.string.review_extraction))
        val note = "Tarkista ote"
        ui.waitUntil(10_000) { ui.onAllNodesWithText(note).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(note).performScrollTo().assertIsDisplayed()
        val before = runBlocking { app.repository.snapshot() }
        assertTrue(before.exercises.isEmpty() && before.routines.isEmpty())
        ui.clickText(ui(R.string.choose_library_exercise))
        ui.clickText(ui(R.string.create_new_exercise))
        ui.clickText(ui(R.string.choose_library_exercise))
        ui.clickText(ui(R.string.exclude_row))
        ui.clickText(ui(R.string.confirm_save))
        ui.waitUntil(5_000) { runBlocking { app.repository.snapshot().routines.isNotEmpty() } }
        val result = runBlocking { app.repository.snapshot() }
        assertEquals("Selkä", result.routines.single().title)
        assertEquals("Soutu", result.exercises.single().name)
        val sets = result.routineItems.single().sets
        assertEquals(listOf("20", "25"), sets.map { it.load })
        assertEquals(listOf(7, 5), sets.map { it.plannedReps?.min })
        assertEquals(listOf(9, 7), sets.map { it.plannedReps?.max })
        assertEquals(listOf(true, false), sets.map { it.plannedReps?.perSide })
        assertEquals(1, requests.get())
    }
}

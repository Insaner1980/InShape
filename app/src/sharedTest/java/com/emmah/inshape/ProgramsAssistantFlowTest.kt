package com.emmah.inshape

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.assistant.AssistantAnswer
import com.emmah.inshape.feature.assistant.AssistantGateway
import com.emmah.inshape.feature.assistant.AssistantImage
import com.emmah.inshape.feature.assistant.FINNISH_DECIMAL_WORKOUT_PROPOSAL
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
        app = AppContainer(
            ui.activity.applicationContext,
            databaseName = databaseName,
            assistantFixture = object : AssistantGateway {
                override suspend fun checkConnection(): String = error("Unexpected connection test")
                override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer {
                    requests.incrementAndGet()
                    assertEquals("Tein 42,5 kiloa, 12 toistoa", input)
                    return AssistantAnswer(Json.parseToJsonElement(FINNISH_DECIMAL_WORKOUT_PROPOSAL).jsonObject, null)
                }

                override suspend fun extract(
                    instructions: String,
                    input: String,
                    images: List<AssistantImage>,
                    schema: JsonObject
                ): AssistantAnswer = error("Unexpected vision request")

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

    @Test fun browsingAnInstalledGuideLeavesTheJournalAndProviderUntouched() {
        fixture()
        ui.setContent { InShapeTheme { AppNavigation(app, initialRoute = "Library") } }
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Rauhallinen aloitus", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Rauhallinen aloitus", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithText("Jalkaprässi", useUnmergedTree = true).performScrollTo().performClick()
        val illustration = ui.onNodeWithContentDescription(ui(R.string.movement_illustration, ui(R.string.exercise_leg_press)))
        illustration.performScrollTo().assertIsDisplayed()
        assertEquals(ui(R.string.enlarge_guide), illustration.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        illustration.performClick()
        assertEquals(ui(R.string.fit_guide), illustration.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        val snapshot = runBlocking { app.repository.snapshot() }
        assertEquals(0, snapshot.exercises.size)
        assertEquals(0, snapshot.routines.size)
        assertEquals(0, snapshot.workouts.size)
        assertEquals(0, requests.get())
    }

    @Test fun editedProgramStartsWithCopiedAndTypedMovements() {
        fixture()
        val program = app.programs.programs.first()
        val day = program.sessions.first()
        val removed = "Vatsarutistus laitteessa"
        val typed = "Askelkyykky penkin vieressä"
        ui.setContent { InShapeTheme { AppNavigation(app, initialRoute = "program/${program.id}") } }
        clickWhenReady("Muokkaa ohjelmaa")
        clickWhenReady("Poista $removed")
        clickWhenReady("Kumoa")
        clickWhenReady("Poista $removed")
        clickWhenReady(ui(R.string.add_exercise))
        enterField("Hae liikkeitä", "Dead bug")
        val copied = hasText("Dead bug") and hasText("Tasapainoinen perusohjelma", substring = true) and hasClickAction()
        ui.onNode(copied).performScrollTo().performClick()
        clickWhenReady(ui(R.string.add_exercise))
        enterField("Hae liikkeitä", typed)
        clickWhenReady("Luo liike ”$typed”")
        enterField("Sarjat", "3")
        enterField("Vähiten toistoja", "6")
        enterField("Eniten toistoja", "9")
        clickWhenReady(ui(R.string.add_exercise))
        clickWhenReady(ui(R.string.save))
        clickWhenReady(ui(R.string.use_program))
        clickWhenReady(ui(R.string.use_program))
        clickWhenReady(ui(R.string.review_program_workout))
        clickWhenReady(ui(R.string.start_workout))
        ui.waitUntil(10_000) {
            ui.waitForIdle()
            runBlocking { app.repository.snapshot().active != null }
        }
        val snapshot = runBlocking { app.repository.snapshot() }
        val active = requireNotNull(snapshot.active)
        val expected = day.exercises.filterNot { it.name == removed }.map { it.name } + listOf("Dead bug", typed)
        assertEquals(expected, active.items.map { item -> snapshot.exercises.single { it.id == item.row.exerciseId }.name })
        val sets = active.items.last().sets
        assertEquals(3, sets.size)
        assertEquals(6..9, sets.first().plannedReps!!.let { it.min..it.max })
    }

    @Test fun todayEditorOpensScheduledDayAndDiscardsChangesAcrossDays() {
        fixture()
        val program = app.programs.programs[1]
        val first = program.sessions.first()
        val second = program.sessions[1]
        val personal = Exercise(name = "Oma soutuliike")
        runBlocking {
            app.repository.saveExercise(personal)
            app.repository.enrollProgram(app.programs.schemaVersion, program, first.exercises.associate { it.exerciseId to null })
            val enrollment = requireNotNull(app.repository.snapshot().programEnrollment)
            app.database.journal().put(enrollment.copy(nextDayIndex = program.sequence.indexOf(second.id), phaseWeek = 3))
        }
        val restoration = StateRestorationTester(ui)
        restoration.setContent { InShapeTheme { AppNavigation(app, initialRoute = "Today") } }
        clickWhenReady(ui(R.string.review_program_workout))
        clickWhenReady("Muokkaa ohjelmaa")
        clickWhenReady("Poista ${second.exercises.first().name}")
        clickWhenReady(second.name)
        clickWhenReady(first.name)
        clickWhenReady("Poista ${first.exercises.first().name}")
        clickWhenReady(first.name)
        clickWhenReady(second.name)
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("Poista ${second.exercises.first().name}").assertDoesNotExist()
        clickWhenReady(ui(R.string.add_exercise))
        clickWhenReady(ui(R.string.all))
        val libraryItem = hasText(ui(R.string.library)) and hasClickAction() and hasAnyAncestor(isPopup())
        ui.onNode(libraryItem).performClick()
        clickWhenReady(personal.name)
        clickWhenReady(ui(R.string.add_exercise))
        ui.onNodeWithText("Poista ${personal.name}").performScrollTo().assertIsDisplayed()
        clickWhenReady(ui(R.string.cancel))
        clickWhenReady(ui(R.string.discard))
        clickWhenReady("Muokkaa ohjelmaa")
        ui.onNodeWithText("Poista ${second.exercises.first().name}").performScrollTo().assertIsDisplayed()
        val snapshot = runBlocking { app.repository.snapshot() }
        assertEquals(emptyList<Any>(), snapshot.programDayOverrides)
        assertEquals(3, snapshot.programEnrollment?.phaseWeek)
    }

    private fun clickWhenReady(label: String) {
        ui.waitUntil(10_000) { ui.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        ui.clickText(label)
    }

    private fun enterField(label: String, value: String) {
        val matcher = hasText(label) and hasSetTextAction()
        ui.waitUntil(10_000) { ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(matcher).performScrollTo().assertIsDisplayed().performClick().performTextReplacement(value)
    }
}

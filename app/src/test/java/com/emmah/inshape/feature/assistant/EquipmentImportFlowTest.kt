package com.emmah.inshape.feature.assistant

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EquipmentImportFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val files = TemporaryFolder()
    private val name = "eq-${newId().take(8)}.db"
    private lateinit var app: AppContainer

    @Test fun equipmentCardUpdatesExistingExerciseAfterKeyFailure() {
        val requests = AtomicInteger()
        val settings = AtomicInteger()
        val saved = AtomicReference<Pair<String?, Boolean>>()
        val gateway = object : AssistantGateway by DisabledAssistant {
            override suspend fun extract(
                instructions: String,
                input: String,
                images: List<AssistantImage>,
                schema: JsonObject
            ): AssistantAnswer {
                assertEquals(equipmentCardSchema, schema)
                assertTrue(input.isBlank() && images.single().bytes.isNotEmpty())
                if (requests.incrementAndGet() == 1) throw AssistantFailure(AssistantFailureKind.NO_KEY)
                val proposal = """{"name":"New name","source":"Set the seat","translation":"Aseta istuin","uncertainty":"Numero puuttuu","imageIndices":"1"}"""
                return AssistantAnswer(Json.parseToJsonElement(proposal).jsonObject, null)
            }
        }
        app = AppContainer(compose.activity.applicationContext, databaseName = name, assistantFixture = gateway)
        val existing = Exercise(name = "Fixture pull", instructions = "Original instructions")
        val data = runBlocking {
            app.repository.saveExercise(existing)
            app.repository.snapshot()
        }
        val picker = pickerFor(writeImage())
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                InShapeTheme {
                    AssistantImportScreen(true, data, vm, { settings.incrementAndGet() }, { id, equipment -> saved.set(id to equipment) })
                }
            }
        }
        val review = compose.onNodeWithText(ui(R.string.review_extraction))
        review.assertIsNotEnabled()
        compose.clickText(ui(R.string.choose_images))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(ui(R.string.selected_image, 1)).fetchSemanticsNodes().isNotEmpty()
        }
        review.assertIsEnabled()
        compose.clickText(ui(R.string.review_extraction))
        waitFor(AssistantFailureKind.NO_KEY.message)
        compose.clickText(ui(R.string.assistant_settings))
        assertEquals(1, settings.get())
        compose.clickText(ui(R.string.review_extraction))
        waitFor(R.string.confirm_save)
        compose.clickText(ui(R.string.save_to_exercise, ui(R.string.new_exercise)))
        compose.clickText(existing.name)
        val input = hasText(ui(R.string.finnish_translation)) and hasSetTextAction()
        compose.onNode(input).performScrollTo().performTextReplacement("Säädä istuin")
        compose.clickText(ui(R.string.keep_text_only))
        compose.clickText(ui(R.string.attach_originals))
        assertEquals(existing.instructions, runBlocking { app.repository.snapshot().exercises.single().instructions })
        compose.clickText(ui(R.string.confirm_save))
        val saveErrors = listOf(
            R.string.import_save_failed,
            R.string.review_imported_fields,
            R.string.journal_changed_review,
            R.string.exercise_changed_review,
            R.string.exercise_name_required,
            R.string.card_already_saved
        ).map { ui(it) }
        var saveError: String? = null
        compose.waitUntil(10_000) {
            saveError = saveErrors.firstOrNull { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            saved.get() != null || saveError != null
        }
        assertNull("Equipment import reported a save failure", saveError)
        assertEquals(existing.id to true, saved.get())
        assertEquals(2, requests.get())
        val result = runBlocking { app.repository.snapshot() }
        val edited = result.exercises.single()
        assertEquals(existing.name, edited.name)
        assertEquals("Original instructions\n\nSäädä istuin", edited.instructions)
        assertEquals("Set the seat\n\nNumero puuttuu", edited.notes)
        assertTrue(result.photos.isEmpty())
    }

    private fun writeImage(): File = files.newFile("fixture.png").also { source ->
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun pickerFor(file: File) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?
            ) {
                dispatchResult(requestCode, listOf(Uri.fromFile(file)))
            }
        }
    }

    private fun waitFor(textId: Int) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(textId)).fetchSemanticsNodes().isNotEmpty() }
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

package com.emmah.inshape

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.assistant.AssistantAnswer
import com.emmah.inshape.feature.assistant.AssistantCredentials
import com.emmah.inshape.feature.assistant.AssistantGateway
import com.emmah.inshape.feature.assistant.AssistantImage
import com.emmah.inshape.feature.assistant.KeyStatus
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantSettingsFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private lateinit var name: String

    @After fun closeFixture() {
        if (::container.isInitialized) {
            container.close()
            container.context.deleteDatabase(name)
            container.context.deleteDatabase("music-$name")
            container.storageRoot.deleteRecursively()
        }
    }

    @Test fun showsConnectionFeedbackWithoutOpeningDetails() {
        val answer = CompletableDeferred<String>()
        name = "assistant-${newId().take(8)}.db"
        container = AppContainer(
            ui.activity.applicationContext,
            databaseName = name,
            assistantCredentialsFixture = object : AssistantCredentials {
                override suspend fun status() = KeyStatus.SAVED
                override suspend fun save(value: String) = Unit
                override suspend fun remove() = Unit
            },
            assistantFixture = object : AssistantGateway {
                override suspend fun checkConnection() = answer.await()
                override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer =
                    error("This page does not interpret text")
                override suspend fun extract(
                    instructions: String, input: String, images: List<AssistantImage>, schema: JsonObject
                ): AssistantAnswer = error("This page does not upload images")
                override suspend fun transcribe(clip: ByteArray, exerciseHint: String): String =
                    error("This page does not record audio")
            }
        )
        ui.setContent { InShapeTheme { AppNavigation(container, initialRoute = "AssistantSettings") } }
        ui.waitUntil(10_000) { ui.onAllNodesWithText(ui(R.string.test_connection)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(ui(R.string.api_key_saved)).assertIsDisplayed()
        ui.onNodeWithText(ui(R.string.open_usage_dashboard)).assertDoesNotExist()
        ui.onNodeWithText(ui(R.string.replace_key)).assertDoesNotExist()
        ui.onNodeWithText(ui(R.string.test_connection)).performClick()
        ui.onNodeWithText(ui(R.string.testing_connection)).assertIsDisplayed()
        answer.complete("Hei")
        ui.waitUntil(10_000) { ui.onAllNodesWithText(ui(R.string.connected_openai)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(ui(R.string.connected_openai)).assertIsDisplayed()
        ui.onNodeWithText(ui(R.string.test_connection)).assertIsDisplayed()
    }
}

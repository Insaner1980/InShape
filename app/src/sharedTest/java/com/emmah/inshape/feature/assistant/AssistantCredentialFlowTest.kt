package com.emmah.inshape.feature.assistant

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantCredentialFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val name = "assistant-credential-${newId()}.db"
    private lateinit var app: AppContainer

    @Test fun enteringReplacingAndRemovingCredentialsKeepsTheEntryWindowSecure() {
        val credentials = TestCredentials()
        show(credentials)
        waitFor(R.string.enter_key)
        compose.clickText(ui(R.string.enter_key))
        assertSecureWindow(true)
        saveKey("invalid")
        waitFor(R.string.key_save_failed)
        assertEquals(null, credentials.value.get())
        saveKey("fixture-value")
        waitFor(R.string.api_key_saved)
        assertEquals("fixture-value", credentials.value.get())
        assertSecureWindow(false)

        compose.clickText(ui(R.string.manage_api_key))
        compose.clickText(ui(R.string.replace_key))
        assertSecureWindow(true)
        saveKey("replacement-value")
        waitFor(R.string.api_key_saved)
        assertEquals("replacement-value", credentials.value.get())

        compose.clickText(ui(R.string.manage_api_key))
        credentials.refuseRemoval.set(true)
        compose.clickText(ui(R.string.remove_key))
        compose.onNodeWithText(ui(R.string.remove_key_question)).assertIsDisplayed()
        compose.onNode(hasText(ui(R.string.remove_key)) and hasAnyAncestor(isDialog())).performClick()
        waitFor(R.string.key_remove_failed)
        assertEquals("replacement-value", credentials.value.get())
        compose.clickText(ui(R.string.remove_key))
        compose.onNode(hasText(ui(R.string.remove_key)) and hasAnyAncestor(isDialog())).performClick()
        waitFor(R.string.enter_key)
        assertEquals(null, credentials.value.get())
    }

    @Test fun connectionFailureCanBeRetried() {
        val credentials = TestCredentials("fixture-value")
        val attempts = AtomicInteger()
        val gateway = object : AssistantGateway by DisabledAssistant {
            override suspend fun checkConnection(): String {
                if (attempts.incrementAndGet() == 1) throw AssistantFailure(AssistantFailureKind.RATE_LIMIT)
                return "Hei"
            }
        }
        show(credentials, gateway)
        waitFor(R.string.test_connection)
        compose.clickText(ui(R.string.test_connection))
        waitFor(AssistantFailureKind.RATE_LIMIT.message)
        compose.clickText(ui(R.string.test_connection))
        waitFor(R.string.connected_openai)
        assertEquals(2, attempts.get())

        compose.clickText(ui(R.string.usage_details))
        compose.onNodeWithText(ui(R.string.open_usage_dashboard)).performScrollTo().assertIsDisplayed()
        compose.clickText(ui(R.string.usage_details))
        compose.onNodeWithText(ui(R.string.open_usage_dashboard)).assertDoesNotExist()
    }

    @Test fun unreadableCredentialOffersDirectReplacement() {
        val credentials = TestCredentials("fixture-value")
        val gateway = object : AssistantGateway by DisabledAssistant {
            override suspend fun checkConnection(): String = throw AssistantFailure(AssistantFailureKind.UNREADABLE_KEY)
        }
        show(credentials, gateway)
        waitFor(R.string.test_connection)
        compose.clickText(ui(R.string.test_connection))
        waitFor(R.string.assistant_error_unreadable_key)
        compose.clickText(ui(R.string.enter_key))
        saveKey("replacement-value")
        waitFor(R.string.api_key_saved)
        assertEquals("replacement-value", credentials.value.get())
    }

    private fun show(credentials: AssistantCredentials, gateway: AssistantGateway = DisabledAssistant) {
        app = AppContainer(compose.activity.applicationContext, databaseName = name)
        compose.setContent {
            val vm = viewModel<JournalViewModel> { JournalViewModel(app) }
            InShapeTheme { AssistantSettings(credentials, gateway, vm, emptyList()) }
        }
    }

    private fun waitFor(textId: Int) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(textId)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun saveKey(value: String) {
        compose.onNode(hasText(ui(R.string.api_key)) and hasSetTextAction()).performScrollTo().performTextInput(value)
        compose.clickText(ui(R.string.save_key))
    }

    private fun assertSecureWindow(secure: Boolean) {
        compose.runOnIdle {
            val flag = WindowManager.LayoutParams.FLAG_SECURE
            val actual = compose.activity.window.attributes.flags and flag == flag
            if (secure) assertTrue(actual) else assertFalse(actual)
        }
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

    private class TestCredentials(value: String? = null) : AssistantCredentials {
        val value = AtomicReference(value)
        val refuseRemoval = AtomicBoolean()
        override suspend fun status() = if (value.get() == null) KeyStatus.MISSING else KeyStatus.SAVED
        override suspend fun save(value: String) {
            require(value != "invalid")
            this.value.set(value)
        }
        override suspend fun remove() {
            check(!refuseRemoval.getAndSet(false))
            value.set(null)
        }
    }
}

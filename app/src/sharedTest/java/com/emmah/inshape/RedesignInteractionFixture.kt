package com.emmah.inshape

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule

abstract class RedesignInteractionFixture {
    @get:Rule val compose = createComposeRule()
    protected val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "redesign-${newId()}.db"
    private lateinit var container: AppContainer
    protected lateinit var journalViewModel: JournalViewModel
        private set
    private val originalTheme = ThemeSettings(context).mode.value

    protected fun launch(state: String, route: String, prepare: suspend (AppContainer) -> Unit = {}) {
        val container = createContainer()
        runBlocking {
            DemoFixtures.load(container, state)
            prepare(container)
        }
        compose.setContent {
            val mode by container.themeSettings.mode.collectAsState()
            val vm = viewModel<JournalViewModel> {
                JournalViewModel(container).also { journalViewModel = it }
            }
            InShapeTheme(mode) { AppNavigation(container, initialRoute = route, vm = vm) }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanup() {
        if (::container.isInitialized) {
            container.themeSettings.set(originalTheme)
            container.close()
            context.deleteDatabase(databaseName)
            container.storageRoot.deleteRecursively()
        }
    }

    protected fun click(text: String) {
        compose.clickText(text)
    }

    protected fun snapshot() = runBlocking { container.repository.snapshot() }

    protected fun awaitWrite(predicate: () -> Boolean) {
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            predicate()
        }
    }

    protected fun createContainer(): AppContainer =
        AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false).also { container = it }
}

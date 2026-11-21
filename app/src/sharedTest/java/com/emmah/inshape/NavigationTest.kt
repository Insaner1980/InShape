package com.emmah.inshape

import org.junit.After
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.data.newId
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import android.content.Context
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "navigation-${newId()}.db"
    private val container = AppContainer(context, DemoFixtures.clock, databaseName, integrationsEnabled = false)

    @After fun cleanup() {
        container.close()
        context.deleteDatabase(databaseName)
        container.storageRoot.deleteRecursively()
    }

    @Test fun mainTabsAndSettingsRemainReachable() {
        compose.setContent { InShapeTheme { AppNavigation(container) } }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription(ui(R.string.settings)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(ui(R.string.settings)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
        compose.onAllNodesWithText(ui(R.string.history)).onLast().performClick()
        compose.onNodeWithText(ui(R.string.progress)).performClick()
        compose.onNodeWithText(ui(R.string.workouts_per_week)).assertExists()
        compose.onNodeWithText(ui(R.string.calendar)).performClick()
        listOf(R.string.history, R.string.library, R.string.today).map { ui(it) }.forEach { label ->
            compose.onAllNodesWithText(label).onLast().performClick()
            compose.onAllNodesWithText(label).onFirst().assertExists()
        }
    }
}

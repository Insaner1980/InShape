package com.emmah.inshape

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun mainTabsAndSettingsRemainReachable() {
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        listOf("History", "Progress", "Library", "Today").forEach { label ->
            compose.onAllNodesWithText(label).onLast().performClick()
            compose.onAllNodesWithText(label).onFirst().assertExists()
        }
    }
}

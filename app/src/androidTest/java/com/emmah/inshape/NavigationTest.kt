package com.emmah.inshape

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mainTabsAndSettingsRemainReachable() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onAllNodesWithText("History").onLast().performClick()
        compose.onNodeWithText("Progress").performClick()
        compose.onNodeWithText("Workouts per week").assertExists()
        compose.onNodeWithText("Calendar").performClick()
        listOf("History", "Library", "Today").forEach { label ->
            compose.onAllNodesWithText(label).onLast().performClick()
            compose.onAllNodesWithText(label).onFirst().assertExists()
        }
    }
}

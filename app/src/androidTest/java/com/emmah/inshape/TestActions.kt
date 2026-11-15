package com.emmah.inshape

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry

fun grantNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}

fun ComposeContentTestRule.clickText(text: String) {
    val node = onAllNodesWithText(text).onFirst()
    if (onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
        node.performScrollTo()
    }
    // IME transitions can move the node between scrolling and a coordinate-based tap.
    node.performSemanticsAction(SemanticsActions.OnClick) { action ->
        check(action()) { "The click action was not handled: $text" }
    }
    waitForIdle()
}

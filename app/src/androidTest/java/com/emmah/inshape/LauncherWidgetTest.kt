package com.emmah.inshape

import android.accessibilityservice.AccessibilityServiceInfo
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.emmah.inshape.widget.InShapeWidgetReceiver
import com.emmah.inshape.widget.WidgetActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LauncherWidgetTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Test fun exportedLauncherCannotStartWorkout() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = (context.applicationContext as InShapeApplication).container.repository
        val before = repository.snapshot().workouts
        val expectedAction = if (repository.snapshot().active == null) "Start" else "Resume workout"
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.START_OR_RESUME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(expectedAction).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        assertEquals(before, repository.snapshot().workouts)
        // Exercise onNewIntent as well as a fresh activity launch.
        context.startActivity(intent)
        compose.waitForIdle()
        assertEquals(before, repository.snapshot().workouts)
        @Suppress("DEPRECATION")
        val widgetActivity = context.packageManager.getActivityInfo(ComponentName(context, WidgetActivity::class.java), 0)
        assertFalse(widgetActivity.exported)
    }

    @After fun closeActivitiesOpenedByWidget() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage
                .values()
                .flatMap { monitor.getActivitiesInStage(it) }
                .filterIsInstance<MainActivity>()
                .distinct()
                .forEach { it.finish() }
        }
    }

    private fun waitFor(timeout: Long = 15_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)

    private fun clickPlatformText(vararg texts: String): Boolean {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isVisibleToUser && texts.any {
                    it.equals(node.text?.toString(), true) ||
                        it.equals(node.contentDescription?.toString(), true)
                }
            ) {
                return node
            }
            return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let(::find) }
        }
        val roots = sequenceOf(automation.rootInActiveWindow) + automation.windows.asSequence().map { it.root }
        var action = roots.filterNotNull().filter { it.packageName?.toString() != BuildConfig.APPLICATION_ID }
            .firstNotNullOfOrNull(::find)
        while (action != null && !action.isClickable) action = action.parent
        return action?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun clickWidgetAction(requireActive: Boolean = false): Boolean {
        val clicked = if (requireActive) {
            clickPlatformText("In progress")
        } else {
            clickPlatformText("Start", "In progress", "Start workout", "Resume workout")
        }
        if (clicked) return true
        swipeLauncherPage()
        return false
    }

    private fun swipeLauncherPage() {
        val root = automation.rootInActiveWindow?.takeIf { it.packageName?.contains("launcher") == true } ?: return
        // Nexus Launcher advertises scrolling but ignores ACTION_SCROLL_FORWARD.
        val bounds = Rect().also(root::getBoundsInScreen)
        val started = SystemClock.uptimeMillis()

        fun event(action: Int, progress: Float) {
            val x = bounds.left + bounds.width() * (.85f - .7f * progress)
            val y = bounds.top + bounds.height() * .5f
            MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x, y, 0).also {
                it.source = InputDevice.SOURCE_TOUCHSCREEN
                automation.injectInputEvent(it, true)
                it.recycle()
            }
        }
        event(MotionEvent.ACTION_DOWN, 0f)
        (1..10).forEach {
            SystemClock.sleep(25)
            event(MotionEvent.ACTION_MOVE, it / 10f)
        }
        event(MotionEvent.ACTION_UP, 1f)
        SystemClock.sleep(400)
    }

    @Test fun launcherPlacesWidgetAndBothActionsResumeTheSameSession() = runBlocking {
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags =
                    flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppWidgetManager.getInstance(context)
        assumeTrue(
            "Launcher does not support widget pinning",
            manager.isRequestPinAppWidgetSupported
        )
        val component = ComponentName(context, InShapeWidgetReceiver::class.java)
        val repository = (context.applicationContext as InShapeApplication).container.repository
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription(
                "Settings"
            ).fetchSemanticsNodes().isNotEmpty()
        }
        if (manager.getAppWidgetIds(component).isEmpty()) {
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Home screen widget").performScrollTo().performClick()
            waitFor(30_000) {
                clickPlatformText(
                    "Add automatically",
                    "Add to home screen",
                    "Add"
                ) || manager.getAppWidgetIds(component).isNotEmpty()
            }
            waitFor { manager.getAppWidgetIds(component).isNotEmpty() }
        }

        fun home() {
            context.startActivity(
                Intent(
                    Intent.ACTION_MAIN
                ).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        home()
        waitFor { clickWidgetAction() }
        // Advance Compose's test clock so the widget intent's LaunchedEffect runs.
        compose.waitUntil(30_000) { runBlocking { repository.snapshot().active != null } }
        val first =
            repository
                .snapshot()
                .active!!
                .row.id
        home()
        waitFor { clickWidgetAction(requireActive = true) }
        compose.waitUntil(30_000) {
            automation.rootInActiveWindow?.packageName ==
                context.packageName
        }
        compose.waitForIdle()
        assertEquals(
            first,
            repository
                .snapshot()
                .active!!
                .row.id
        )
        repository.deleteWorkout(first)
    }
}

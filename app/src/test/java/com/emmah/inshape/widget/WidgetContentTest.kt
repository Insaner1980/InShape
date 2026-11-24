package com.emmah.inshape.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.composeForPreview
import androidx.glance.appwidget.provideContent
import androidx.work.testing.WorkManagerTestInitHelper
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.R
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.ui.theme.Dimens
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {
    private val context = RuntimeEnvironment.getApplication()
    private val week = LocalDate.parse("2026-11-16")
    private val idle = WidgetState(week, 1, null, false, "35 min", today = week.plusDays(2), completedDays = setOf(week))

    @Test fun launcherPreviewProvidesWeeklyProgressAndAStartAction() = runBlocking {
        val preview = InShapeWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        val labels = preview.visibleLabels(context)
        assertTrue(context.getString(R.string.this_week) in labels)
        assertTrue(context.getString(R.string.start) in labels)
        assertTrue(context.getString(R.string.count_of_total, 2, 3) in labels)
    }

    @Test fun idleWidgetOmitsTheRoutineSubtitleAtLargeTextSize() = runBlocking {
        val routine = "Koko vartalo"
        val state = idle.copy(nextRoutineTitle = routine)
        val largeContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 2f })
        assertTrue(routine in widget(state, context).visibleLabels(context))
        val labels = widget(state, largeContext).visibleLabels(largeContext)
        assertFalse(routine in labels)
        assertTrue(largeContext.getString(R.string.start) in labels)
    }

    @Test fun activeWidgetShowsChronometerOrRequestsTimingReview() = runBlocking {
        val state = idle.copy(resume = true, activeTitle = "Voimatreeni", exerciseSummary = "Soutu", doneExercises = 2, totalExercises = 4)
        val timer = RemoteViews(context.packageName, R.layout.widget_chronometer)
        val active = widget(state, context, timer).apply(context, FrameLayout(context))
        assertNotNull(active.findViewById<Chronometer>(R.id.workout_chronometer))
        assertTrue("Voimatreeni" in active.texts())
        assertTrue(context.getString(R.string.count_of_total, 2, 4) in active.texts())
        assertTrue("Soutu" in active.texts())
        val unresolved = widget(state.copy(activeTitle = ""), context, wide = false).visibleLabels(context)
        assertTrue(context.getString(R.string.workout) in unresolved)
        assertTrue(context.getString(R.string.review_workout_time) in unresolved)
        assertFalse("Soutu" in unresolved)
    }

    @Test fun widgetReflectsAnActiveWorkout() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val container = (context as InShapeApplication).container
        try {
            container.storageReady.await()
            val widget = InShapeWidget()
            val size = DpSize(Dimens.widgetWide, Dimens.widgetHeight)
            val idleLabels = widget.compose(context, size = size).visibleLabels(context)
            assertTrue(context.getString(R.string.this_week) in idleLabels)
            assertTrue(context.getString(R.string.start) in idleLabels)
            val workoutId = container.repository.startWorkout(StartSource.Empty)
            val active = widget.compose(context, size = size).apply(context, FrameLayout(context))
            assertTrue(context.getString(R.string.workout_in_progress) in active.texts())
            assertNotNull(active.findViewById<Chronometer>(R.id.workout_chronometer))
            assertFalse(context.getString(R.string.start) in active.texts())
            assertEquals(workoutId, container.repository.snapshot().active?.row?.id)
        } finally {
            container.close()
        }
    }

    private suspend fun widget(state: WidgetState, themed: Context, timer: RemoteViews? = null, wide: Boolean = true): RemoteViews =
        object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
                WidgetContent(state, actionStartActivity(Intent(context, WidgetActivity::class.java)), timer, "17:30", "1 treeni")
            }
        }.compose(themed, size = DpSize(if (wide) Dimens.widgetWide else Dimens.widgetNarrow, Dimens.widgetHeight))

    private fun RemoteViews.visibleLabels(context: Context): List<String> = apply(context, FrameLayout(context)).texts()

    private fun View.texts(): List<String> = when (this) {
        is TextView -> listOf(text.toString())
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).texts() }
        else -> emptyList()
    }
}

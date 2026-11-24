package com.emmah.inshape.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetManager.Companion.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

object WidgetUpdater {
    fun hasWidgets(context: Context) = AppWidgetManager.getInstance(
        context
    ).getAppWidgetIds(ComponentName(context, InShapeWidgetReceiver::class.java)).isNotEmpty()
    fun enqueue(context: Context) {
        if (!hasWidgets(context)) return
        WorkManager.getInstance(
            context
        ).enqueueUniqueWork(
            "widget-refresh",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        )
    }
    suspend fun refresh(context: Context) {
        if (!hasWidgets(context)) {
            cancel(context)
            return
        }
        InShapeWidget().updateAll(context)
        val now = ZonedDateTime.now()
        val delay = Duration.between(
            now,
            now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        ).toMillis().coerceAtLeast(1000)
        val work = WorkManager.getInstance(context)
        work.enqueueUniqueWork(
            "widget-midnight",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setInitialDelay(
                delay,
                TimeUnit.MILLISECONDS
            ).build()
        )
        work.enqueueUniquePeriodicWork(
            "widget-fallback",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS).build()
        )
    }
    fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        listOf(
            "widget-refresh",
            "widget-midnight",
            "widget-fallback"
        ).forEach(work::cancelUniqueWork)
    }

    // Preview publishing is optional and platform failures must not prevent the widget from working.
    @Suppress("TooGenericExceptionCaught")
    suspend fun publishPreview(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val component = ComponentName(context, InShapeWidgetReceiver::class.java)
        try {
            val provider = AppWidgetManager.getInstance(context).installedProviders.firstOrNull { it.provider == component }
            if (provider != null && provider.generatedPreviewCategories and WIDGET_CATEGORY_HOME_SCREEN == 0) {
                val published = GlanceAppWidgetManager(context).setWidgetPreviews(
                    InShapeWidgetReceiver::class,
                    intSetOf(WIDGET_CATEGORY_HOME_SCREEN)
                )
                if (published == SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED) {
                    Log.d("InShapeWidget", "Launcher preview update was rate limited")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            Log.w("InShapeWidget", "Could not publish launcher preview", failure)
        }
    }
}
class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        WidgetUpdater.refresh(applicationContext)
        Result.success()
    } catch (
        _: Exception
    ) {
        Result.retry()
    }
}

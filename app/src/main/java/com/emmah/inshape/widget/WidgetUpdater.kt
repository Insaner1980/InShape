package com.emmah.inshape.widget

import android.appwidget.AppWidgetManager
import android.content.*
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.time.*
import java.util.concurrent.TimeUnit

object WidgetUpdater {
    fun hasWidgets(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, InShapeWidgetReceiver::class.java)).isNotEmpty()
    fun enqueue(context: Context) {
        if (!hasWidgets(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork("widget-refresh", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }
    suspend fun refresh(context: Context) {
        if (!hasWidgets(context)) { cancel(context); return }
        InShapeWidget().updateAll(context)
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1000)
        val work = WorkManager.getInstance(context)
        work.enqueueUniqueWork("widget-midnight", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        work.enqueueUniquePeriodicWork("widget-fallback", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS).build())
    }
    fun cancel(context: Context) { val work = WorkManager.getInstance(context); listOf("widget-refresh", "widget-midnight", "widget-fallback").forEach(work::cancelUniqueWork) }
    fun requestPin(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, InShapeWidgetReceiver::class.java), null, null)
    }
}
class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try { WidgetUpdater.refresh(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
}

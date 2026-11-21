package com.emmah.inshape.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.feature.workout.ActiveWorkoutNotifier
import com.emmah.inshape.feature.workout.activeNotice
import com.emmah.inshape.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        goAsync().reconcileReminder(context, occurrence = intent.data?.schemeSpecificPart)
    }
}

class ScheduleChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit

            else -> return
        }
        WidgetUpdater.enqueue(context)
        goAsync().reconcileReminder(context, reschedule = true)
    }
}

// WorkManager await surfaces the original enqueue failure, including failures from the platform scheduler.
@Suppress("TooGenericExceptionCaught")
private fun BroadcastReceiver.PendingResult.reconcileReminder(context: Context, occurrence: String? = null, reschedule: Boolean = false) {
    val app = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        try {
            reconcileReminder(app, occurrence, reschedule)
        } catch (_: SQLiteException) {
            try {
                WorkManager.getInstance(app).enqueueUniqueWork(
                    "reminder-retry-${occurrence ?: reschedule}",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ReminderRetryWorker>()
                        .setInputData(workDataOf(OCCURRENCE to occurrence, RESCHEDULE to reschedule))
                        .build()
                ).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("ReminderReceiver", "Could not enqueue reminder retry", failure)
            }
        } finally {
            finish()
        }
    }
}

private suspend fun reconcileReminder(context: Context, occurrence: String?, reschedule: Boolean) {
    val container = (context as InShapeApplication).container
    ReminderScheduler(context).reconcile(occurrence = occurrence, reschedule = reschedule) {
        val snapshot = container.repository.snapshot()
        if (reschedule && container.integrationsEnabled) {
            ActiveWorkoutNotifier(context).reconcile(snapshot.activeNotice())
        }
        snapshot.preferences
    }
}

class ReminderRetryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        reconcileReminder(applicationContext, inputData.getString(OCCURRENCE), inputData.getBoolean(RESCHEDULE, false))
        Result.success()
    } catch (_: SQLiteException) {
        Result.retry()
    }
}

private const val OCCURRENCE = "occurrence"
private const val RESCHEDULE = "reschedule"

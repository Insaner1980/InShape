package com.emmah.inshape.health

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.emmah.inshape.InShapeApplication
import java.util.concurrent.TimeUnit

class HealthSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if ((applicationContext as InShapeApplication).container.healthSharing.sync()) Result.success() else Result.retry()
    } catch (
        cancelled: kotlinx.coroutines.CancellationException
    ) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
    companion object {
        fun enqueue(context: Context, retryNow: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "health-share",
                if (retryNow) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<HealthSyncWorker>().setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                ).build()
            )
        }
    }
}

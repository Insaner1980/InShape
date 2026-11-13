package com.emmah.inshape.health

import android.content.Context
import androidx.work.*
import com.emmah.inshape.InShapeApplication
import java.util.concurrent.TimeUnit

class HealthSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if ((applicationContext as InShapeApplication).container.healthSharing.sync()) Result.success() else Result.retry()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("health-share", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<HealthSyncWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}

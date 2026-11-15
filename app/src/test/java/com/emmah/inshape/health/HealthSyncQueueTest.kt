package com.emmah.inshape.health

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.util.concurrent.SettableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class HealthSyncQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workers = mutableListOf<SettableFuture<ListenableWorker.Result>>()
    private lateinit var manager: WorkManager

    @Before fun setup() {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                object : ListenableWorker(appContext, workerParameters) {
                    override fun startWork() = SettableFuture.create<Result>().also { workers.add(it) }
                }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build()
        )
        manager = WorkManager.getInstance(context)
    }

    @After fun close() {
        manager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun requestArrivingDuringWorkerCompletionRunsAsSuccessor() {
        HealthSyncWorker.enqueue(context)
        assertEquals(1, workers.size)
        HealthSyncWorker.enqueue(context)
        val states = manager.getWorkInfosForUniqueWork("health-share").get().map { it.state }
        assertEquals(setOf(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED), states.toSet())
        workers.first().set(ListenableWorker.Result.success())
        assertEquals(2, workers.size)
        workers.last().set(ListenableWorker.Result.success())
        assertEquals(setOf(WorkInfo.State.SUCCEEDED), manager.getWorkInfosForUniqueWork("health-share").get().map { it.state }.toSet())
    }

    @Test fun explicitRetryReplacesBackedOffWorkAndRunsImmediately() {
        HealthSyncWorker.enqueue(context)
        val original = manager.getWorkInfosForUniqueWork("health-share").get().single()
        workers.first().set(ListenableWorker.Result.retry())
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(original.id).get()!!.state)
        assertEquals(1, workers.size)
        HealthSyncWorker.enqueue(context, retryNow = true)
        val replacement = manager.getWorkInfosForUniqueWork("health-share").get().single { !it.state.isFinished }
        assertNotEquals(original.id, replacement.id)
        assertEquals(WorkInfo.State.RUNNING, replacement.state)
        assertEquals(2, workers.size)
        workers.last().set(ListenableWorker.Result.success())
    }
}

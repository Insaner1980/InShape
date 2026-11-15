package com.emmah.inshape

import android.content.Context
import androidx.room.Room
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.feature.workout.ActiveWorkoutNotifier
import com.emmah.inshape.feature.workout.activeNotice
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(
    val context: Context,
    val clock: Clock = SystemLocalClock(),
    databaseName: String = "journal.db",
    val integrationsEnabled: Boolean = databaseName ==
        "journal.db"
) {
    val themeSettings = com.emmah.inshape.ui.theme.ThemeSettings(context)
    val storageRoot = if (databaseName ==
        "journal.db"
    ) {
        context.filesDir
    } else {
        File(context.filesDir, "isolated-${databaseName.replace(Regex("[^A-Za-z0-9.-]"), "_")}")
    }
    val database = Room.databaseBuilder(context, JournalDatabase::class.java, databaseName).build()
    val repository = JournalRepository(database, clock)
    val photos = com.emmah.inshape.media.PhotoStore(context, database, File(storageRoot, "photos"))
    val healthGateway = com.emmah.inshape.health.AndroidHealthGateway(context)
    val healthSharing = com.emmah.inshape.health.HealthSharing(repository, healthGateway)
    val backups = com.emmah.inshape.transfer.BackupService(this)
    val exports = com.emmah.inshape.transfer.ExportService(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val storageReady = scope.async { backups.recoverOwnedStaging() }
    init {
        if (integrationsEnabled) {
            scope.launch {
                repository.changes.map { it.activeNotice() }.distinctUntilChanged().collect {
                    ActiveWorkoutNotifier(context).reconcile(it)
                }
            }
            scope.launch {
                repository.changes.map { it.preferences }.distinctUntilChanged { a, b ->
                    a.remindersEnabled ==
                        b.remindersEnabled &&
                        a.remindersHeld == b.remindersHeld &&
                        a.reminderDays == b.reminderDays &&
                        a.reminderTime == b.reminderTime
                }.collect {
                    com.emmah.inshape.reminders.ReminderScheduler(context).reconcile { repository.snapshot().preferences }
                }
            }
            scope.launch {
                repository.changes.map {
                    com.emmah.inshape.widget.widgetState(it, java.time.LocalDate.now(clock))
                }.distinctUntilChanged().collect {
                    com.emmah.inshape.widget.WidgetUpdater.enqueue(context)
                }
            }
            scope.launch {
                repository.changes.map {
                    it.outbox.map { row -> Triple(row.workoutId, row.revision, row.operation) }
                }.distinctUntilChanged().collect {
                    if (it.isNotEmpty()) {
                        com.emmah.inshape.health.HealthSyncWorker.enqueue(context, retryNow = it.any { row -> row.third == "DELETE" })
                    }
                }
            }
        }
    }
    fun onForeground() {
        if (!integrationsEnabled) return
        scope.launch { ActiveWorkoutNotifier(context).reconcile(repository.snapshot().activeNotice()) }
        scope.launch {
            com.emmah.inshape.reminders.ReminderScheduler(
                context
            ).reconcile { repository.snapshot().preferences }
        }
        com.emmah.inshape.widget.WidgetUpdater.enqueue(context)
        scope.launch {
            if (repository.snapshot().outbox.isNotEmpty()) {
                com.emmah.inshape.health.HealthSyncWorker.enqueue(
                    context,
                    retryNow = true
                )
            }
        }
    }
    fun close() {
        scope.cancel()
        database.close()
    }
}

package com.emmah.inshape

import android.content.Context
import java.time.Clock
import androidx.room.Room
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class AppContainer(val context: Context, val clock: Clock = SystemLocalClock(), databaseName: String = "journal.db", val integrationsEnabled: Boolean = databaseName == "journal.db") {
    val themeSettings = com.emmah.inshape.ui.theme.ThemeSettings(context)
    val storageRoot = if (databaseName == "journal.db") context.filesDir else File(context.filesDir, "isolated-${databaseName.replace(Regex("[^A-Za-z0-9.-]"), "_")}")
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
            scope.launch { repository.changes.map { it.preferences }.distinctUntilChanged { a, b -> a.remindersEnabled == b.remindersEnabled && a.remindersHeld == b.remindersHeld && a.reminderDays == b.reminderDays && a.reminderTime == b.reminderTime }.collect { com.emmah.inshape.reminders.ReminderScheduler(context).apply(it) } }
            scope.launch { repository.changes.map { com.emmah.inshape.widget.widgetState(it, java.time.LocalDate.now(clock)) }.distinctUntilChanged().collect { com.emmah.inshape.widget.WidgetUpdater.enqueue(context) } }
            scope.launch { repository.changes.map { it.outbox.map { row -> Triple(row.workoutId, row.revision, row.operation) } }.distinctUntilChanged().collect { if (it.isNotEmpty()) com.emmah.inshape.health.HealthSyncWorker.enqueue(context) } }
        }
    }
    fun onForeground() {
        if (!integrationsEnabled) return
        scope.launch { com.emmah.inshape.reminders.ReminderScheduler(context).apply(repository.snapshot().preferences) }
        com.emmah.inshape.widget.WidgetUpdater.enqueue(context)
        scope.launch { if (repository.snapshot().outbox.isNotEmpty()) com.emmah.inshape.health.HealthSyncWorker.enqueue(context) }
    }
    fun close() { scope.cancel(); database.close() }
}

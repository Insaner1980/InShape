package com.emmah.inshape

import android.content.Context
import androidx.core.content.edit
import androidx.room.Room
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.feature.music.MusicStore
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

private const val JOURNAL_DATABASE_NAME = "journal.db"

class AppContainer(
    val context: Context,
    val clock: Clock = SystemLocalClock(),
    databaseName: String = JOURNAL_DATABASE_NAME,
    val integrationsEnabled: Boolean = databaseName == JOURNAL_DATABASE_NAME,
    assistantFixture: com.emmah.inshape.feature.assistant.AssistantGateway? = null,
    assistantCredentialsFixture: com.emmah.inshape.feature.assistant.AssistantCredentials? = null
) {
    private val uiPreferences = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    val hasRemovedSet: Boolean get() = uiPreferences.getBoolean("has_removed_set", false)

    fun markSetRemoved() {
        uiPreferences.edit { putBoolean("has_removed_set", true) }
    }

    val themeSettings = com.emmah.inshape.ui.theme.ThemeSettings(context)
    private val assistantKeyStore = if (integrationsEnabled) com.emmah.inshape.feature.assistant.AssistantKeyStore(context) else null
    internal val assistantUsage = if (integrationsEnabled) com.emmah.inshape.feature.assistant.AssistantUsageLog(context) else null
    val assistantCredentials = assistantCredentialsFixture ?: assistantKeyStore
    val assistant = assistantFixture
        ?: assistantKeyStore?.let { store -> com.emmah.inshape.feature.assistant.OpenAiGateway(store::read, usageLog = assistantUsage) }
        ?: com.emmah.inshape.feature.assistant.DisabledAssistant
    val programs by lazy {
        context.assets.open("programs/beginner_fi_v1.json").bufferedReader().use {
            com.emmah.inshape.feature.program.ProgramCatalog.read(it.readText())
        }
    }
    val storageRoot = if (databaseName == JOURNAL_DATABASE_NAME) {
        context.filesDir
    } else {
        File(context.filesDir, "isolated-${databaseName.replace(Regex("[^A-Za-z0-9.-]"), "_")}")
    }
    val database = Room.databaseBuilder(context, JournalDatabase::class.java, databaseName)
        .addMigrations(com.emmah.inshape.data.journalMigration1To2)
        .build()
    val repository = JournalRepository(database, clock, context)
    val photos = com.emmah.inshape.media.PhotoStore(context, database, File(storageRoot, "photos"))
    val healthGateway = com.emmah.inshape.health.AndroidHealthGateway(context)
    val healthSharing = com.emmah.inshape.health.HealthSharing(repository, healthGateway, context)
    val backups = com.emmah.inshape.transfer.BackupService(this)
    val exports = com.emmah.inshape.transfer.ExportService(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val musicStorage = lazy {
        MusicStore(context, "music-$databaseName", scope)
    }
    val music by musicStorage
    val storageReady = scope.async { backups.recoverOwnedStaging() }
    init {
        if (integrationsEnabled) {
            scope.launch { com.emmah.inshape.widget.WidgetUpdater.publishPreview(context) }
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
                    com.emmah.inshape.widget.widgetState(context, it, java.time.LocalDate.now(clock))
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
        if (musicStorage.isInitialized()) music.close()
        database.close()
    }
}

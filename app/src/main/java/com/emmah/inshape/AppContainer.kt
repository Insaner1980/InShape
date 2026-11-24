package com.emmah.inshape

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.emmah.inshape.data.JournalDatabase
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.feature.music.MusicStore
import com.emmah.inshape.feature.workout.ActiveWorkoutNotifier
import com.emmah.inshape.feature.workout.activeNotice
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val JOURNAL_DATABASE_NAME = "journal.db"
private const val INTEGRATION_RETRY_DELAY_MILLIS = 15_000L

class AppContainer(
    val context: Context,
    val clock: Clock = SystemLocalClock(),
    databaseName: String = JOURNAL_DATABASE_NAME,
    val integrationsEnabled: Boolean = databaseName == JOURNAL_DATABASE_NAME,
    assistantFixture: com.emmah.inshape.feature.assistant.AssistantGateway? = null,
    assistantCredentialsFixture: com.emmah.inshape.feature.assistant.AssistantCredentials? = null
) {

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
        .addMigrations(
            com.emmah.inshape.data.journalMigration1To2,
            com.emmah.inshape.data.journalMigration2To3,
            com.emmah.inshape.data.journalMigration3To4
        )
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
            observeIntegration("active workout", repository.changes.map { it.activeNotice() }.distinctUntilChanged()) {
                ActiveWorkoutNotifier(context).reconcile(it)
            }
            val reminders = repository.changes.map { it.preferences }.distinctUntilChanged { a, b ->
                a.remindersEnabled == b.remindersEnabled &&
                    a.remindersHeld == b.remindersHeld &&
                    a.reminderDays == b.reminderDays &&
                    a.reminderTime == b.reminderTime
            }
            observeIntegration("reminders", reminders) {
                com.emmah.inshape.reminders.ReminderScheduler(context).reconcile { repository.snapshot().preferences }
            }
            val widget = repository.changes.map {
                com.emmah.inshape.widget.widgetState(context, it, java.time.LocalDate.now(clock))
            }.distinctUntilChanged()
            observeIntegration("widget", widget) { com.emmah.inshape.widget.WidgetUpdater.enqueue(context) }
            val outbox = repository.changes.map {
                it.outbox.map { row -> Triple(row.workoutId, row.revision, row.operation) }
            }.distinctUntilChanged()
            observeIntegration("health sync", outbox) {
                if (it.isNotEmpty()) {
                    com.emmah.inshape.health.HealthSyncWorker.enqueue(context, retryNow = it.any { row -> row.third == "DELETE" })
                }
            }
        }
    }

    private fun <T> observeIntegration(name: String, changes: Flow<T>, reconcile: suspend (T) -> Unit) {
        scope.launch {
            changes.onEach(reconcile).retryWhen { failure, _ ->
                if (failure is CancellationException || failure !is Exception) throw failure
                logIntegrationFailure(name, failure)
                delay(INTEGRATION_RETRY_DELAY_MILLIS)
                true
            }.collect()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchIntegration(name: String, reconcile: suspend () -> Unit) {
        scope.launch {
            try {
                reconcile()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logIntegrationFailure(name, failure)
            }
        }
    }

    private fun logIntegrationFailure(name: String, failure: Exception) {
        Log.w("AppContainer", "Could not update $name (${failure.javaClass.simpleName})")
    }

    fun onForeground() {
        if (!integrationsEnabled) return
        launchIntegration("active workout") { ActiveWorkoutNotifier(context).reconcile(repository.snapshot().activeNotice()) }
        launchIntegration("reminders") {
            com.emmah.inshape.reminders.ReminderScheduler(
                context
            ).reconcile { repository.snapshot().preferences }
        }
        com.emmah.inshape.widget.WidgetUpdater.enqueue(context)
        launchIntegration("health sync") {
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

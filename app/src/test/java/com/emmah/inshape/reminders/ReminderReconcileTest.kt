package com.emmah.inshape.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.Preferences
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ReminderReconcileTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val monday = ZonedDateTime.parse("2030-01-07T07:00:00Z")
    private val settings = Preferences(remindersEnabled = true, reminderDays = listOf(1), reminderTime = "08:00")

    @Before fun grantNotificationPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun foregroundReconcilePreservesAnOverdueAlarmUntilTheReceiverDeliversIt() = runBlocking {
        val initial = ReminderScheduler(context)
        try {
            initial.reconcile(now = monday) { settings }
            val scheduled = alarms.scheduledAlarms.single()
            val occurrence = shadowOf(scheduled.operation).savedIntent.data!!.schemeSpecificPart
            val delayed = monday.plusHours(2)
            // Use another scheduler to exercise persisted state.
            val foreground = ReminderScheduler(context)
            foreground.reconcile(now = delayed) { settings }
            val stillPending = alarms.scheduledAlarms.single()
            assertEquals(scheduled.triggerAtMs, stillPending.triggerAtMs)
            assertEquals(scheduled.operation, stillPending.operation)

            foreground.reconcile(occurrence, now = delayed) { settings }
            val next = alarms.scheduledAlarms.single()
            assertEquals(monday.plusWeeks(1).plusHours(1).toInstant().toEpochMilli(), next.triggerAtMs)
            assertNotEquals(scheduled.operation, next.operation)
            assertEquals(41, notifications.activeNotifications.single().id)
        } finally {
            initial.cancel()
        }
    }

    @Test fun oldCallbacksDoNotDeliverAfterAChangeAndAnInterruptedReadCanRetry() = runBlocking {
        val scheduler = ReminderScheduler(context)
        try {
            scheduler.reconcile(now = monday) { settings }
            val old = shadowOf(alarms.scheduledAlarms.single().operation).savedIntent.data!!.schemeSpecificPart
            val evening = settings.copy(reminderTime = "18:00")
            scheduler.reconcile(old, now = monday.plusHours(2)) { evening }
            assertTrue(notifications.activeNotifications.isEmpty())

            val current = alarms.scheduledAlarms.single()
            val occurrence = shadowOf(current.operation).savedIntent.data!!.schemeSpecificPart
            assertNotEquals(old, occurrence)
            val late = monday.plusHours(12)
            val failedRead = runCatching {
                scheduler.reconcile(occurrence, now = late) { throw SQLiteException("Temporary read failure") }
            }
            assertTrue(failedRead.exceptionOrNull() is SQLiteException)
            assertEquals(current.operation, alarms.scheduledAlarms.single().operation)
            scheduler.reconcile(occurrence, now = late) { evening }
            assertEquals(41, notifications.activeNotifications.single().id)
            assertNotEquals(current.operation, alarms.scheduledAlarms.single().operation)
        } finally {
            scheduler.cancel()
        }
    }

    @Test fun concurrentSchedulersReadPreferencesInApplicationOrder() = runBlocking {
        val first = ReminderScheduler(context)
        val second = ReminderScheduler(context)
        var preferences = Preferences(remindersEnabled = false)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            first.reconcile {
                val captured = preferences
                entered.complete(Unit)
                release.await()
                captured
            }
        }
        entered.await()
        preferences = preferences.copy(remindersEnabled = true, reminderDays = listOf(1, 2, 3, 4, 5, 6, 7))
        val latest = launch(start = CoroutineStart.UNDISPATCHED) { second.reconcile { preferences } }
        release.complete(Unit)
        old.join()
        latest.join()
        assertEquals(1, alarms.scheduledAlarms.size)
        second.cancel()
    }
}

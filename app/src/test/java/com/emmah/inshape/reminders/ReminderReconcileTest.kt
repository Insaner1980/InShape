package com.emmah.inshape.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ReminderReconcileTest {
    @Test fun concurrentSchedulersReadPreferencesInApplicationOrder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
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
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        second.cancel()
    }
}

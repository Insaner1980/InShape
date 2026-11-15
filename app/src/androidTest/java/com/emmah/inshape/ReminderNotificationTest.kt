package com.emmah.inshape

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationTest {
    @Test fun deliveredNotificationSurvivesRescheduleAndDisabledReminderRemovesIt() {
        grantNotificationPermission()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = ReminderScheduler(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = Preferences(remindersEnabled = true, reminderDays = listOf(1, 3, 5))
        try {
            assertTrue("Test requires notification permission", scheduler.allowed())
            scheduler.deliver(settings)
            scheduler.apply(settings)
            val notification = manager.activeNotifications.single { it.id == 41 }
            assertEquals(
                "A little time for yourself",
                notification.notification.extras.getString("android.title")
            )
            assertNotNull(notification.notification.contentIntent)
            scheduler.apply(settings.copy(remindersEnabled = false))
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (manager.activeNotifications.any { it.id == 41 } &&
                SystemClock.uptimeMillis() < deadline
            ) {
                SystemClock.sleep(20)
            }
            assertFalse(manager.activeNotifications.any { it.id == 41 })
        } finally {
            scheduler.cancel()
        }
    }
}

package com.emmah.inshape.feature.workout

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ActiveWorkoutNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notice = ActiveNotice("first", "Full body A", "2026-11-13T10:42:00Z", false)

    @Test fun contentUsesSystemChronometerAndExplicitActions() {
        val notification = activeWorkoutNotification(context, notice)!!
        assertEquals("Full body A", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.startsWith("Started "))
        assertEquals(Instant.parse(notice.start).toEpochMilli(), notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Open", "Finish"), notification.actions.map { it.title.toString() })
        assertEquals(ActiveWorkoutNotifier.CHANNEL, notification.channelId)
        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertNotNull(notification.deleteIntent)
        assertNull(activeWorkoutNotification(context, null))
        assertNull(activeWorkoutNotification(context, notice.copy(timingNeedsReview = true)))
    }

    @Test fun dismissalPersistsUntilANewWorkoutAndInvalidStatesCancel() {
        context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val notifier = ActiveWorkoutNotifier(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        notifier.reconcile(notice)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(ActiveWorkoutNotifier.CHANNEL).importance)
        ActiveWorkoutDismissReceiver().onReceive(context, Intent().putExtra("workout_id", notice.id))
        notifier.reconcile(notice)
        assertTrue(manager.activeNotifications.isEmpty())
        notifier.reconcile(notice.copy(id = "second"))
        assertEquals(1, manager.activeNotifications.size)
        notifier.reconcile(notice.copy(id = "second", timingNeedsReview = true))
        assertTrue(manager.activeNotifications.isEmpty())
        notifier.reconcile(notice.copy(id = "third"))
        notifier.reconcile(null)
        assertTrue(manager.activeNotifications.isEmpty())
    }
}

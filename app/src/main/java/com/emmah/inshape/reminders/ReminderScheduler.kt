package com.emmah.inshape.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.feature.workout.ActiveWorkoutNotifier
import com.emmah.inshape.feature.workout.activeNotice
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderScheduler(private val context: Context) {
    suspend fun reconcile(deliver: Boolean = false, readPreferences: suspend () -> Preferences) = schedulingLock.withLock {
        val settings = readPreferences()
        if (deliver) deliver(settings)
        apply(settings)
    }
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    init {
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Workout reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
    fun allowed(): Boolean = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL)?.importance !=
        NotificationManager.IMPORTANCE_NONE &&
        (
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            )
    private fun alarmIntent() = PendingIntent.getBroadcast(
        context,
        41,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    fun cancel() {
        alarms.cancel(alarmIntent())
        notifications.cancel(41)
    }
    fun apply(settings: Preferences, now: ZonedDateTime = ZonedDateTime.now()) {
        alarms.cancel(alarmIntent())
        if (!settings.remindersEnabled || settings.remindersHeld ||
            !allowed()
        ) {
            notifications.cancel(41)
            return
        }
        val next =
            nextReminder(
                settings.reminderDays.map(DayOfWeek::of).toSet(),
                LocalTime.parse(settings.reminderTime),
                now
            )
                ?: return
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            alarmIntent()
        )
    }
    fun deliver(settings: Preferences) {
        val configured = settings.remindersEnabled && !settings.remindersHeld && settings.reminderDays.isNotEmpty()
        if (!configured || !allowed()) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            42,
            Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            notifications.notify(
                41,
                Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(
                        "A little time for yourself"
                    ).setContentText("Open InShape when you are ready for a workout.")
                    .setContentIntent(open).setAutoCancel(true).build()
            )
        } catch (_: SecurityException) {
            cancel()
        }
    }
    companion object {
        private val schedulingLock = Mutex()
        const val CHANNEL = "workout_reminders"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler(context).reconcile(deliver = true) {
                    (context.applicationContext as InShapeApplication).container.repository.snapshot().preferences
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class ScheduleChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit

            else -> return
        }
        com.emmah.inshape.widget.WidgetUpdater.enqueue(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as InShapeApplication).container
                if (container.integrationsEnabled) ActiveWorkoutNotifier(context).reconcile(container.repository.snapshot().activeNotice())
                ReminderScheduler(
                    context
                ).reconcile {
                    (context.applicationContext as InShapeApplication).container.repository.snapshot().preferences
                }
            } finally {
                pending.finish()
            }
        }
    }
}

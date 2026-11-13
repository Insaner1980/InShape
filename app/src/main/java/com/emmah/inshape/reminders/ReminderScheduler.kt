package com.emmah.inshape.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.emmah.inshape.*
import com.emmah.inshape.data.Preferences
import kotlinx.coroutines.*
import java.time.*

class ReminderScheduler(private val context: Context) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    init { notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT)) }
    fun allowed(): Boolean = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    private fun alarmIntent() = PendingIntent.getBroadcast(context, 41, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel() { alarms.cancel(alarmIntent()); notifications.cancel(41) }
    fun apply(settings: Preferences, now: ZonedDateTime = ZonedDateTime.now()) {
        alarms.cancel(alarmIntent())
        if (!settings.remindersEnabled || settings.remindersHeld || !allowed()) { notifications.cancel(41); return }
        val next = nextReminder(settings.reminderDays.map(DayOfWeek::of).toSet(), LocalTime.parse(settings.reminderTime), now) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarmIntent())
    }
    fun deliver(settings: Preferences) {
        if (!settings.remindersEnabled || settings.remindersHeld || settings.reminderDays.isEmpty() || !allowed()) return
        val open = PendingIntent.getActivity(context, 42, Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            notifications.notify(41, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("A little time for yourself").setContentText("Open InShape when you are ready for a workout.")
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (_: SecurityException) { cancel() }
    }
    companion object { const val CHANNEL = "workout_reminders" }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences = (context.applicationContext as InShapeApplication).container.repository.snapshot().preferences
                val scheduler = ReminderScheduler(context)
                scheduler.deliver(preferences)
                scheduler.apply(preferences)
            } finally { pending.finish() }
        }
    }
}

class ScheduleChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.emmah.inshape.widget.WidgetUpdater.enqueue(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ReminderScheduler(context).apply((context.applicationContext as InShapeApplication).container.repository.snapshot().preferences) }
            finally { pending.finish() }
        }
    }
}

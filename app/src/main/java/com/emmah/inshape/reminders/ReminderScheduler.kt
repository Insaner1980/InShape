package com.emmah.inshape.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.emmah.inshape.data.Preferences
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderScheduler(private val context: Context) {
    suspend fun reconcile(
        occurrence: String? = null,
        reschedule: Boolean = false,
        now: ZonedDateTime? = null,
        readPreferences: suspend () -> Preferences
    ) = schedulingLock.withLock {
        val settings = readPreferences()
        apply(settings, now ?: ZonedDateTime.now(), occurrence, reschedule)
    }

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val scheduled = context.getSharedPreferences("workout_reminder_schedule", Context.MODE_PRIVATE)
    init {
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.workout_reminders),
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
    private fun alarmIntent(occurrence: String?, flags: Int) = PendingIntent.getBroadcast(
        context,
        41,
        Intent(context, ReminderReceiver::class.java).apply {
            if (occurrence != null) data = Uri.fromParts("inshape-reminder", occurrence, null)
        },
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancelScheduledAlarm() {
        // A null occurrence matches the alarm used by earlier versions.
        alarmIntent(scheduled.getString(OCCURRENCE, null), PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
        scheduled.edit(commit = true) { clear() }
    }

    fun cancel() {
        cancelScheduledAlarm()
        notifications.cancel(41)
    }

    fun apply(settings: Preferences, now: ZonedDateTime = ZonedDateTime.now()) = apply(settings, now, null, false)

    private fun apply(settings: Preferences, now: ZonedDateTime, delivered: String?, reschedule: Boolean) {
        if (!settings.hasReminderSchedule() || !allowed()) {
            cancel()
            return
        }
        val days = settings.reminderDays.map(DayOfWeek::of).toSortedSet()
        val time = LocalTime.parse(settings.reminderTime)
        val schedule = "${days.joinToString()},$time,${now.zone.id}"
        val occurrence = scheduled.getString(OCCURRENCE, null)
        if (!reschedule && occurrence != null && scheduled.getString(SCHEDULE, null) == schedule) {
            if (delivered != occurrence) {
                if (alarmIntent(occurrence, PendingIntent.FLAG_NO_CREATE) == null) {
                    // Recreate the same occurrence if Android has discarded its intent.
                    setAlarm(occurrence, scheduled.getLong(TRIGGER, now.toInstant().toEpochMilli()))
                }
                return
            }
            deliver(settings)
        }
        cancelScheduledAlarm()
        val next = if (allowed()) nextReminder(days, time, now) else null
        val trigger = next?.toInstant()?.toEpochMilli() ?: return
        val nextOccurrence = UUID.randomUUID().toString()
        setAlarm(nextOccurrence, trigger)
        scheduled.edit(commit = true) {
            putString(OCCURRENCE, nextOccurrence)
            putString(SCHEDULE, schedule)
            putLong(TRIGGER, trigger)
        }
    }

    private fun setAlarm(occurrence: String, trigger: Long) {
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger,
            checkNotNull(alarmIntent(occurrence, PendingIntent.FLAG_UPDATE_CURRENT))
        )
    }
    private fun Preferences.hasReminderSchedule(): Boolean = remindersEnabled && !remindersHeld && reminderDays.isNotEmpty()

    fun deliver(settings: Preferences) {
        if (!settings.hasReminderSchedule() || !allowed()) {
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
                        context.getString(R.string.reminder_title)
                    ).setContentText(context.getString(R.string.reminder_text))
                    .setContentIntent(open).setAutoCancel(true).build()
            )
        } catch (_: SecurityException) {
            cancel()
        }
    }
    companion object {
        private val schedulingLock = Mutex()
        private const val OCCURRENCE = "occurrence"
        private const val SCHEDULE = "schedule"
        private const val TRIGGER = "trigger"
        const val CHANNEL = "workout_reminders"
    }
}

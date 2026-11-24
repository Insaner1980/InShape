package com.emmah.inshape.feature.workout

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.core.content.edit
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Instant
import java.time.ZoneId

data class ActiveNotice(
    val id: String,
    val title: String,
    val start: String?,
    val timingNeedsReview: Boolean,
    val zone: ZoneId = ZoneId.systemDefault()
)

fun JournalSnapshot.activeNotice(): ActiveNotice? = active?.row?.let {
    ActiveNotice(it.id, it.title, it.start, it.timingNeedsReview, ZoneId.of(it.zoneId))
}

fun activeWorkoutNotification(context: Context, notice: ActiveNotice?): Notification? {
    if (notice == null || notice.timingNeedsReview || notice.start == null) return null
    val whenMillis = runCatching { Instant.parse(notice.start).toEpochMilli() }.getOrNull() ?: return null
    fun open(action: String, request: Int) = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val content = open(MainActivity.OPEN_WORKOUT, 61)
    val delete = PendingIntent.getBroadcast(
        context,
        63,
        Intent(context, ActiveWorkoutDismissReceiver::class.java)
            .setData(Uri.Builder().scheme("inshape").authority("dismiss-workout").appendPath(notice.id).build())
            .putExtra("workout_id", notice.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    fun timerBuilder() = Notification.Builder(context, ActiveWorkoutNotifier.CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_workout)
        .setWhen(whenMillis).setShowWhen(true).setUsesChronometer(true)
        .setOngoing(true).setOnlyAlertOnce(true)
        .setCategory(if (Build.VERSION.SDK_INT >= 31) Notification.CATEGORY_WORKOUT else Notification.CATEGORY_PROGRESS)
    val publicVersion = timerBuilder()
        .setContentTitle(context.getString(R.string.workout_in_progress))
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .build()
    val builder = timerBuilder()
        .setContentTitle(notice.title.ifBlank { context.getString(R.string.workout) })
        .setContentText(
            context.getString(
                R.string.started_at,
                Instant.ofEpochMilli(whenMillis).workoutTime(notice.zone, DateFormat.is24HourFormat(context))
            )
        )
        .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setLocalOnly(false)
        .setContentIntent(content).setDeleteIntent(delete)
        .addAction(Notification.Action.Builder(null, context.getString(R.string.open), content).build())
        .addAction(Notification.Action.Builder(null, context.getString(R.string.finish), open(MainActivity.OPEN_FINISH, 62)).build())
    if (Build.VERSION.SDK_INT >= 36) {
        // The platform setter arrived in 36.1; the extra also supports Android 16.0.
        builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
    }
    return builder.build()
}

class ActiveWorkoutNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    fun reconcile(notice: ActiveNotice?) {
        synchronized(lock) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.workout_in_progress), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
            if (notice != null && preferences.getString(ACTIVE_WORKOUT, null) != notice.id) {
                preferences.edit {
                    putString(ACTIVE_WORKOUT, notice.id)
                    remove(DISMISSED)
                }
            }
            val notification = activeWorkoutNotification(context, notice)
            val allowed =
                Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (notification == null || !allowed || preferences.getString(DISMISSED, null) == notice?.id) {
                manager.cancel(ID)
            } else {
                try {
                    manager.notify(ID, notification)
                } catch (_: SecurityException) {
                    manager.cancel(ID)
                }
            }
        }
    }

    companion object {
        const val CHANNEL = "active_workout"
        const val ID = 60
        const val DISMISSED = "dismissed_workout_id"
        internal const val ACTIVE_WORKOUT = "active_workout_id"
        internal val lock = Any()
    }
}

class ActiveWorkoutDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("workout_id") ?: return
        synchronized(ActiveWorkoutNotifier.lock) {
            val preferences = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
            if (preferences.getString(ActiveWorkoutNotifier.ACTIVE_WORKOUT, null) != id) return
            preferences.edit {
                putString(ActiveWorkoutNotifier.DISMISSED, id)
            }
            context.getSystemService(NotificationManager::class.java).cancel(ActiveWorkoutNotifier.ID)
        }
    }
}

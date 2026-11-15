package com.emmah.inshape.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.emmah.inshape.ui.theme.AppTypography
import com.emmah.inshape.ui.theme.DarkColors
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.LightColors
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class InShapeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(Dimens.widgetNarrow, Dimens.widgetHeight), DpSize(Dimens.widgetWide, Dimens.widgetHeight))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as InShapeApplication).container
        val initial = container.repository.snapshot()
        val text = androidx.glance.color.ColorProvider(LightColors.text, DarkColors.text)
        val secondary = androidx.glance.color.ColorProvider(LightColors.textSecondary, DarkColors.textSecondary)
        provideContent {
            val data by container.repository.changes.collectAsState(initial)
            val state = widgetState(data, LocalDate.now(container.clock))
            val open = actionStartActivity(
                Intent(context, WidgetActivity::class.java)
                    .setAction(if (state.resume) MainActivity.OPEN_WORKOUT else MainActivity.START_OR_RESUME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            val start = state.activeStart?.let(Instant::parse)
            val timer = start?.takeUnless { state.timingNeedsReview }?.let {
                workoutTimer(context, Duration.between(it, container.clock.instant()))
            }
            val narrow = LocalSize.current.width < Dimens.widgetWide
            val titleStyle = TextStyle(color = text, fontSize = AppTypography.titleMedium.fontSize, fontWeight = FontWeight.Medium)
            val labelStyle = TextStyle(color = secondary, fontSize = AppTypography.labelSmall.fontSize)
            Column(
                GlanceModifier.fillMaxSize()
                    .background(androidx.glance.color.ColorProvider(LightColors.background, DarkColors.surface))
                    .cornerRadius(Dimens.cardPadding).clickable(open).padding(Dimens.inner),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                val subtitle = when {
                    !state.resume -> state.goal?.let { "${state.count} of $it" } ?: "${state.count} workouts"
                    narrow -> null
                    else -> start?.let { "Started ${it.workoutTime(container.clock.zone)}" } ?: "Time not recorded"
                }
                WidgetHeader(state.resume, subtitle, labelStyle)
                Spacer(GlanceModifier.height(Dimens.innerSmall))
                if (state.resume) {
                    val title = state.activeTitle ?: "Workout"
                    if (narrow) {
                        Text(title, style = titleStyle, maxLines = 2)
                        if (timer != null) AndroidRemoteViews(timer) else Text("Review workout timing", style = labelStyle)
                    } else {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(title, GlanceModifier.defaultWeight(), style = titleStyle, maxLines = 2)
                            if (timer != null) AndroidRemoteViews(timer) else Text("Review timing", style = labelStyle)
                        }
                    }
                } else {
                    Text("No workout running", style = titleStyle)
                    Button(
                        "Start",
                        open,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = androidx.glance.color.ColorProvider(LightColors.ink, DarkColors.ink),
                            contentColor = androidx.glance.color.ColorProvider(LightColors.onInk, DarkColors.onInk)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetHeader(active: Boolean, subtitle: String?, style: TextStyle) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(if (active) "In progress" else "This week", GlanceModifier.defaultWeight(), style = style)
        if (subtitle != null) Text(subtitle, style = style)
    }
}

private fun workoutTimer(context: Context, elapsed: Duration): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        setChronometer(
            R.id.workout_chronometer,
            SystemClock.elapsedRealtime() - elapsed.toMillis().coerceAtLeast(0),
            null,
            true
        )
    }

class InShapeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = InShapeWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.enqueue(context)
    }
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancel(context)
    }
}

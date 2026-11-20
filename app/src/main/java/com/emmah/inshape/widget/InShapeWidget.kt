package com.emmah.inshape.widget

import androidx.glance.layout.Box
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.data.weekStart
import androidx.glance.layout.size
import androidx.glance.ColorFilter
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.emmah.inshape.ui.theme.DarkColors
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.LightColors
import com.emmah.inshape.ui.theme.WidgetTypography
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class InShapeWidget(private val previewOnly: Boolean = false) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(Dimens.widgetNarrow, Dimens.widgetHeight), DpSize(Dimens.widgetWide, Dimens.widgetHeight))
    )

    override val previewSizeMode = sizeMode

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (previewOnly) {
            providePreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            return
        }
        val container = (context.applicationContext as InShapeApplication).container
        val initial = container.repository.snapshot()
        provideContent {
            val data by container.repository.changes.collectAsState(initial)
            val state = widgetState(context, data, LocalDate.now(container.clock))
            val open = actionStartActivity(
                Intent(context, WidgetActivity::class.java)
                    .setAction(if (state.resume) MainActivity.OPEN_WORKOUT else MainActivity.START_OR_RESUME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            val start = state.activeStart?.let(Instant::parse)
            val timer = start?.takeUnless { state.timingNeedsReview }?.let {
                workoutTimer(context, Duration.between(it, container.clock.instant()))
            }
            val time = start?.takeUnless { state.timingNeedsReview }?.let {
                context.getString(R.string.started_at, it.workoutTime(state.activeZone ?: container.clock.zone, DateFormat.is24HourFormat(context)))
            }
            WidgetContent(
                state,
                open,
                timer,
                time,
                context.resources.getQuantityString(R.plurals.workout_count, state.count, state.count)
            )
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val today = LocalDate.now()
        val week = weekStart(today)
        val state = WidgetState(week, 2, 3, false, null, today = today, completedDays = setOf(week, week.plusDays(2)))
        provideContent {
            WidgetContent(
                state,
                actionStartActivity(Intent(context, WidgetActivity::class.java)),
                timer = null,
                time = null,
                workoutCount = context.resources.getQuantityString(R.plurals.workout_count, state.count, state.count)
            )
        }
    }
}

@Composable
internal fun WidgetContent(state: WidgetState, open: Action, timer: RemoteViews?, time: String?, workoutCount: String) {
    val context = LocalContext.current
    val titleStyle = WidgetTypography.title
    val labelStyle = WidgetTypography.label
    val wideIdle = !state.resume && LocalSize.current.width >= Dimens.widgetWide
    val largeText = LocalContext.current.resources.configuration.fontScale >= 1.3f
    val showBand = wideIdle && !largeText
    val background = if (wideIdle) {
        GlanceModifier.background(ImageProvider(R.drawable.inshape_widget_idle), ContentScale.FillBounds)
    } else {
        GlanceModifier.background(ColorProvider(LightColors.background, DarkColors.surface))
    }
    Column(
        GlanceModifier.fillMaxSize().then(background)
            .cornerRadius(Dimens.cardPadding).clickable(open)
            .padding(horizontal = Dimens.inner, vertical = if (state.resume) Dimens.innerSmall else Dimens.innerTiny),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (state.resume) {
            WidgetHeader(true, time.takeIf { LocalSize.current.width >= Dimens.widgetWide }, labelStyle)
            Text(state.activeTitle?.ifBlank { context.getString(R.string.workout) } ?: context.getString(R.string.workout), style = titleStyle, maxLines = 1)
            val progress = context.getString(R.string.count_of_total, state.doneExercises, state.totalExercises)
            val summary = state.exerciseSummary?.takeIf { LocalSize.current.width >= Dimens.widgetWide && !largeText }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(progress, GlanceModifier.defaultWeight(), style = labelStyle, maxLines = 1)
                if (summary != null) Text(summary, style = labelStyle, maxLines = 1)
            }
            if (timer != null) {
                AndroidRemoteViews(timer, modifier = GlanceModifier.wrapContentHeight())
            } else {
                Text(context.getString(R.string.review_workout_time), style = labelStyle)
            }
        } else {
            WidgetHeader(false, state.goal?.let { context.getString(R.string.count_of_total, state.count, it) } ?: workoutCount, labelStyle)
            WidgetWeekMarks(state)
            if (!largeText) {
                val subtitle = state.nextRoutineTitle
                    ?: state.lastDuration?.let { context.getString(R.string.widget_latest_duration, it) }
                    ?: context.getString(R.string.empty_workout)
                Text(subtitle, style = titleStyle, maxLines = 1)
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(
                    GlanceModifier.height(Dimens.widgetActionHeight)
                        .background(
                            ImageProvider(R.drawable.widget_button), ContentScale.FillBounds,
                            colorFilter = ColorFilter.tint(ColorProvider(LightColors.ink, DarkColors.ink))
                        )
                        .clickable(open).padding(horizontal = Dimens.inner),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        context.getString(R.string.start),
                        style = titleStyle.copy(color = ColorProvider(LightColors.onInk, DarkColors.onInk)),
                        maxLines = 1
                    )
                }
                if (showBand) {
                    Spacer(GlanceModifier.defaultWeight())
                    Image(
                        ImageProvider(R.drawable.art_widget_resistance_band),
                        contentDescription = null,
                        modifier = GlanceModifier.width(Dimens.widgetArtworkWidth).height(Dimens.widgetActionHeight),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetWeekMarks(state: WidgetState) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(vertical = Dimens.innerTiny)) {
        for (day in 0L..6L) {
            val date = state.week.plusDays(day)
            val done = date in state.completedDays
            val current = date == state.today
            val drawable = when {
                done -> R.drawable.widget_mark_done
                current -> R.drawable.widget_mark_today
                else -> R.drawable.widget_mark_pending
            }
            val tint = if (done || current) ColorProvider(LightColors.ink, DarkColors.ink)
                else ColorProvider(LightColors.markRing, DarkColors.markRing)
            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(drawable),
                    contentDescription = context.getString(R.string.widget_day_status, date.shortDate(), context.getString(if (done) R.string.workout_recorded else R.string.no_workout)),
                    modifier = GlanceModifier.size(Dimens.widgetMark),
                    colorFilter = ColorFilter.tint(tint)
                )
            }
        }
    }
}

@Composable
private fun WidgetHeader(active: Boolean, subtitle: String?, style: TextStyle) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(LocalContext.current.getString(if (active) R.string.workout_in_progress else R.string.this_week), GlanceModifier.defaultWeight(), style = style)
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

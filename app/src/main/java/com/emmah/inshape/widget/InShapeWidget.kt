package com.emmah.inshape.widget

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
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
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
            val time = start?.takeUnless { state.timingNeedsReview }?.let {
                "Started ${it.workoutTime(state.activeZone ?: container.clock.zone, DateFormat.is24HourFormat(context))}"
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
        val state = WidgetState(LocalDate.now(), 2, 3, false, null)
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
private fun WidgetContent(state: WidgetState, open: Action, timer: RemoteViews?, time: String?, workoutCount: String) {
    val titleStyle = TextStyle(
        color = ColorProvider(LightColors.text, DarkColors.text),
        fontSize = AppTypography.titleMedium.fontSize,
        fontWeight = FontWeight.Medium
    )
    val labelStyle = TextStyle(
        color = ColorProvider(LightColors.textSecondary, DarkColors.textSecondary),
        fontSize = AppTypography.labelSmall.fontSize
    )
    val wideIdle = !state.resume && LocalSize.current.width >= Dimens.widgetWide
    val showBand = wideIdle && LocalContext.current.resources.configuration.fontScale < 1.3f
    val background = if (wideIdle) {
        GlanceModifier.background(ImageProvider(R.drawable.inshape_widget_idle), ContentScale.FillBounds)
    } else {
        GlanceModifier.background(ColorProvider(LightColors.background, DarkColors.surface))
    }
    Column(
        GlanceModifier.fillMaxSize().then(background)
            .cornerRadius(Dimens.cardPadding).clickable(open).padding(Dimens.inner),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (state.resume) {
            WidgetHeader(true, time.takeIf { LocalSize.current.width >= Dimens.widgetWide }, labelStyle)
            Text(state.activeTitle?.ifBlank { "Workout" } ?: "Workout", style = titleStyle, maxLines = 1)
            Text("${state.doneExercises} of ${state.totalExercises}", style = labelStyle)
            if (timer != null) {
                AndroidRemoteViews(timer, modifier = GlanceModifier.wrapContentHeight())
            } else {
                Text("Review workout timing", style = labelStyle)
            }
        } else {
            WidgetHeader(false, state.goal?.let { "${state.count} of $it" } ?: workoutCount, labelStyle)
            Spacer(GlanceModifier.height(Dimens.innerSmall))
            Text("No workout running", style = titleStyle)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Button(
                    "Start",
                    open,
                    modifier = GlanceModifier.height(Dimens.touch),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(LightColors.ink, DarkColors.ink),
                        contentColor = ColorProvider(LightColors.onInk, DarkColors.onInk)
                    )
                )
                if (showBand) {
                    Spacer(GlanceModifier.defaultWeight())
                    Image(
                        ImageProvider(R.drawable.art_widget_resistance_band),
                        contentDescription = null,
                        modifier = GlanceModifier.width(68.dp).height(Dimens.touch),
                        contentScale = ContentScale.Fit
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

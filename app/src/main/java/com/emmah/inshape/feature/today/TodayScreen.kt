package com.emmah.inshape.feature.today

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.parseWorkoutInstant
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.feature.library.routineArtwork
import com.emmah.inshape.ui.components.ArtworkHeader
import com.emmah.inshape.ui.components.IconCircleButton
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.ProgressSegments
import com.emmah.inshape.ui.components.ProgressState
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.displayDate
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.formatElapsed
import com.emmah.inshape.ui.theme.rememberElapsedSeconds
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

fun suggestedRoutine(routines: List<Routine>, history: List<Workout>): Routine? = routines.minByOrNull { routine ->
    history.filter {
        it.row.state == "COMPLETED" && it.row.sourceRoutineId == routine.id
    }.maxOfOrNull { LocalDate.parse(it.row.journalDate) }
        ?: LocalDate.MIN
}

@Composable
fun TodayScreen(
    data: JournalSnapshot,
    today: LocalDate,
    onSettings: () -> Unit,
    onStart: (StartSource) -> Unit,
    onWorkout: (String) -> Unit,
    clock: Clock,
    onOpenRoutine: (String?) -> Unit,
    modifier: Modifier = Modifier,
    healthContext: @Composable () -> Unit = {}
) {
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val colors = InShapeTheme.colors
    val history = data.history.sortedWith(
        compareByDescending<Workout> {
            it.row.journalDate
        }.thenByDescending { it.row.createdAt }
    )
    val week = weekStart(today)
    val thisWeek = history.filter {
        it.qualifiesForAttendance &&
            LocalDate.parse(it.row.journalDate) in week..today
    }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val active = data.active
    val populatedRoutines = data.routineItems.mapTo(mutableSetOf()) { it.routineId }
    val routine = suggestedRoutine(data.routines.filter { it.id in populatedRoutines }, history)
    Page(modifier) {
        ScreenHeader(
            if (data.preferences.name.isBlank()) "Today" else "Hi ${data.preferences.name}",
            trailing = { IconCircleButton(Icons.Outlined.Settings, "Settings", onSettings) }
        )
        Text(today.displayDate("EEEE d MMMM"), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        val recordedStart = active?.row?.start?.takeUnless { active.row.timingNeedsReview }
            ?.let { runCatching { parseWorkoutInstant(it) }.getOrNull() }
        val elapsed by rememberElapsedSeconds(recordedStart, clock)
        val accent = active != null && !colors.isDark
        InShapeCard(
            Modifier.padding(top = Dimens.itemGap),
            color = if (accent) {
                colors.ink
            } else if (active != null) {
                colors.surface
            } else {
                colors.background
            },
            gap = Dimens.itemGap,
            wash = if (active != null) ArtworkWash.Active.brush(useDark = true) else ArtworkWash.Routine.brush()
        ) {
            val text = if (accent) colors.onInk else colors.text
            val secondary = if (accent) colors.onInk.copy(alpha = .72f) else colors.textSecondary
            if (active != null) {
                ResponsiveRow {
                    Text("In progress", Modifier.weight(1f), color = secondary, style = MaterialTheme.typography.labelSmall)
                    if (recordedStart != null) {
                        Text(
                            "Started ${recordedStart.workoutTime(ZoneId.of(active.row.zoneId), uses24Hours)}",
                            color = secondary,
                            style = InShapeType.numeric
                        )
                    }
                }
                ArtworkHeader(
                    R.drawable.art_training_motion_dark.takeIf { recordedStart != null },
                    150.dp,
                    64.dp,
                    mirror = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                        Text(active.row.title, color = text, style = MaterialTheme.typography.titleLarge)
                        if (elapsed != null) {
                            Text(formatElapsed(elapsed!!), color = text, style = MaterialTheme.typography.displaySmall)
                        } else {
                            Text(
                                "Started ${LocalDate.parse(active.row.journalDate).displayDate("EEE d MMM")}",
                                color = secondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                ProgressSegments(
                    active.items.map {
                        when {
                            it.row.disposition == "DONE" -> ProgressState.Done
                            it.row.id == active.row.currentItemId -> ProgressState.Current
                            else -> ProgressState.Pending
                        }
                    },
                    "${active.doneCount} of ${active.items.size} exercises done",
                    onDarkSurface = true
                )
                Text(
                    "${active.doneCount} of ${active.items.size} exercises done",
                    color = secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val items = remember(routine, data.routineItems) { data.routineItems.filter { it.routineId == routine?.id } }
                val artwork = remember(items, routine?.durationMinutes, data.exercises) {
                    val exercises = data.exercises.associateBy { it.id }
                    routineArtwork(items.map { exercises[it.exerciseId] }, routine?.durationMinutes)
                }
                ArtworkHeader(
                    artwork,
                    136.dp,
                    if (artwork == R.drawable.art_training_strength_barbell) 96.dp else 88.dp,
                    cropEnd = artwork != R.drawable.art_training_mobility_mat
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                        if (routine != null) Text("Next up", color = secondary, style = MaterialTheme.typography.labelSmall)
                        Text(routine?.title ?: "Start a workout", color = text, style = MaterialTheme.typography.titleLarge)
                        val detail = if (routine == null) {
                            "Pick a routine below or add exercises as you go."
                        } else {
                            pluralStringResource(R.plurals.exercise_count, items.size, items.size)
                        }
                        Text(detail, color = secondary, style = MaterialTheme.typography.bodyMedium)
                        routine?.durationMinutes?.let {
                            Text("About $it min", color = secondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            PrimaryButton(if (active == null) (if (routine == null) "Start" else "Start workout") else "Resume workout", {
                if (active != null) {
                    onWorkout(active.row.id)
                } else {
                    onStart(routine?.let { StartSource.Routine(it.id) } ?: StartSource.Empty)
                }
            }, Modifier.fillMaxWidth(), icon = if (active == null) Icons.Outlined.PlayArrow else null, inverted = accent)
        }
        if (active == null && routine != null) {
            OutlineButton("Start empty workout", { onStart(StartSource.Empty) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            SectionHeader("This week", trailing = {
                Text(
                    data.preferences.weeklyGoal?.let { "${thisWeek.size} of $it" }
                        ?: pluralStringResource(R.plurals.workout_count, thisWeek.size, thisWeek.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            })
            Row(Modifier.fillMaxWidth()) {
                for (day in 0..6) {
                    val date = week.plusDays(day.toLong())
                    val done = thisWeek.any { it.row.journalDate == date.toString() }
                    val isToday = date == today
                    Column(
                        Modifier.weight(1f).semantics {
                            contentDescription =
                                "${date.displayDate(
                                    "EEEE d MMMM"
                                )}, ${if (done) "workout recorded" else "no workout"}${if (isToday) ", today" else ""}"
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
                    ) {
                        Surface(
                            Modifier.size(Dimens.weekCircle),
                            InShapeShapes.pill,
                            if (done) {
                                colors.ink
                            } else if (isToday) {
                                Color.Transparent
                            } else {
                                if (colors.isDark) colors.surface else colors.inactiveMark
                            },
                            border = if (isToday &&
                                !done
                            ) {
                                BorderStroke(Dimens.ring, colors.ink)
                            } else {
                                null
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (done) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        null,
                                        Modifier.size(Dimens.iconSmall),
                                        tint = colors.onInk
                                    )
                                }
                            }
                        }
                        Text(
                            date.dayOfWeek.getDisplayName(
                                if (largeText) TextStyle.NARROW else TextStyle.SHORT,
                                Locale.ENGLISH
                            ),
                            style = if (isToday) InShapeType.navSelected else InShapeType.nav,
                            color = if (isToday) colors.text else colors.textSecondary
                        )
                    }
                }
            }
        }
        val otherRoutines = data.routines.filter { active != null || it.id != routine?.id }.take(3)
        if (data.routines.isEmpty() || otherRoutines.isNotEmpty()) {
            SectionHeader("Routines", action = "See all", onAction = { onOpenRoutine(null) })
        }
        if (data.routines.isEmpty()) {
            ListRow("Create a routine", onClick = { onOpenRoutine(null) })
        } else if (otherRoutines.isNotEmpty()) {
            ListCard {
                otherRoutines.forEach { saved ->
                    item {
                        val count = data.routineItems.count { it.routineId == saved.id }
                        ListRow(saved.title, trailing = {
                            Text(
                                pluralStringResource(R.plurals.exercise_count, count, count),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }, onClick = { onOpenRoutine(saved.id) }, showChevron = true)
                    }
                }
            }
        }
        history.firstOrNull()?.let { last ->
            SectionHeader("Last workout")
            ListCard {
                item {
                    val date = LocalDate.parse(last.row.journalDate)
                    ListRow(
                        last.row.title,
                        subtitle = date.displayDate("EEE d MMM"),
                        trailing = {
                            last.row.durationSeconds?.let {
                                Text(formatDuration(it), style = InShapeType.numeric, color = colors.textSecondary)
                            }
                        },
                        onClick = { onWorkout(last.row.id) }
                    )
                }
            }
        }
        healthContext()
    }
}

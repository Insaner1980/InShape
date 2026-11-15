package com.emmah.inshape.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.parseWorkoutInstant
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.ui.components.EmptyState
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
import com.emmah.inshape.ui.components.SectionLabel
import com.emmah.inshape.ui.components.TextAction
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
    onLibrary: () -> Unit,
    onWorkout: (String) -> Unit,
    clock: Clock,
    modifier: Modifier = Modifier,
    healthContext: @Composable () -> Unit = {}
) {
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
    val routine = suggestedRoutine(data.routines, history)
    Page(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    today.displayDate("EEEE d MMMM"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    if (data.preferences.name.isBlank()) "Hello" else "Hi ${data.preferences.name}",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            IconCircleButton(Icons.Outlined.Settings, "Settings", onSettings)
        }
        val recordedStart = active?.row?.start?.takeUnless { active.row.timingNeedsReview }
            ?.let { runCatching { parseWorkoutInstant(it) }.getOrNull() }
        val elapsed by rememberElapsedSeconds(recordedStart, clock)
        InShapeCard(
            Modifier.padding(top = Dimens.itemGap),
            color = if (active != null) colors.ink else colors.background,
            gap = Dimens.itemGap
        ) {
            val text = if (active != null) colors.onInk else colors.text
            val secondary = if (active != null) colors.onInk.copy(alpha = .72f) else colors.textSecondary
            ResponsiveRow {
                Text(
                    if (active != null) {
                        "In progress"
                    } else if (routine != null) {
                        "Next up"
                    } else {
                        "Ready when you are"
                    },
                    Modifier.weight(1f),
                    color = secondary,
                    style = MaterialTheme.typography.labelSmall
                )
                if (active != null && recordedStart != null) {
                    Text(
                        "Started ${recordedStart.workoutTime(ZoneId.of(active.row.zoneId))}",
                        color = secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(active?.row?.title ?: routine?.title ?: "Start a workout", color = text, style = MaterialTheme.typography.titleLarge)
            if (active != null) {
                if (elapsed != null) {
                    Text(formatElapsed(elapsed!!), color = text, style = MaterialTheme.typography.displaySmall)
                } else {
                    Text(
                        "Started ${LocalDate.parse(active.row.journalDate).displayDate("EEE d MMM")}",
                        color = secondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                    onAccent = true
                )
                Text(
                    "${active.doneCount} of ${active.items.size} exercises done",
                    color = secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    routine?.let {
                        "${data.routineItems.count { item -> item.routineId == it.id }} exercises" +
                            (it.durationMinutes?.let { minutes -> ", about $minutes min" } ?: "")
                    } ?: "Add exercises as you go",
                    color = secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PrimaryButton(if (active == null) "Start workout" else "Resume workout", {
                if (active != null) {
                    onWorkout(active.row.id)
                } else {
                    onStart(routine?.let { StartSource.Routine(it.id) } ?: StartSource.Empty)
                }
            }, Modifier.fillMaxWidth(), icon = if (active == null) Icons.Outlined.PlayArrow else null, inverted = active != null)
        }
        if (active == null && routine != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction("Start empty workout", { onStart(StartSource.Empty) })
            }
        }
        Column(Modifier.padding(top = Dimens.inner), verticalArrangement = Arrangement.spacedBy(Dimens.rowPadding)) {
            ResponsiveRow {
                Text("This week", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    data.preferences.weeklyGoal?.let { "${thisWeek.size} of $it" } ?: "${thisWeek.size} workouts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
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
                                colors.surface
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
        SectionLabel("Routines", action = "See all", onAction = onLibrary)
        if (data.routines.isEmpty()) {
            ListRow("Create a routine", onClick = onLibrary)
        } else {
            ListCard {
                data.routines.filter { active != null || it.id != routine?.id }.take(3).forEach { saved ->
                    item {
                        ListRow(saved.title, trailing = {
                            Text(
                                "${data.routineItems.count { it.routineId == saved.id }} exercises",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }, onClick = { onStart(StartSource.Routine(saved.id)) })
                    }
                }
            }
        }
        history.firstOrNull()?.let { last ->
            SectionLabel("Last workout")
            ListCard {
                item {
                    val date = LocalDate.parse(last.row.journalDate)
                    ListRow(
                        last.row.title,
                        subtitle = date.displayDate("EEE d MMM"),
                        trailing = {
                            last.row.durationSeconds?.let {
                                Text(formatDuration(it), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
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

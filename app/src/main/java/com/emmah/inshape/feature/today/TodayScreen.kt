package com.emmah.inshape.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.data.*
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import java.time.*
import java.time.format.TextStyle
import java.util.Locale

fun suggestedRoutine(routines: List<Routine>, history: List<Workout>): Routine? = routines.minByOrNull { routine ->
    history.filter { it.row.state == "COMPLETED" && it.row.sourceRoutineId == routine.id }.maxOfOrNull { LocalDate.parse(it.row.journalDate) } ?: LocalDate.MIN
}

@Composable
fun TodayScreen(data: JournalSnapshot, today: LocalDate, onSettings: () -> Unit, onStart: (StartSource) -> Unit, onResume: (String) -> Unit, onLibrary: () -> Unit, onDetails: (String) -> Unit, healthContext: @Composable () -> Unit = {}) {
    val colors = InShapeTheme.colors
    val history = data.history.sortedWith(compareByDescending<Workout> { it.row.journalDate }.thenByDescending { it.row.createdAt })
    val week = weekStart(today)
    val thisWeek = history.filter { it.qualifiesForAttendance && LocalDate.parse(it.row.journalDate) in week..today }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val active = data.active
    val routine = suggestedRoutine(data.routines, history)
    Page {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(today.displayDate("EEEE d MMMM").uppercase(Locale.ENGLISH), color = colors.textSecondary, style = InShapeType.date)
                Text(if (data.preferences.name.isBlank()) "Hello" else "Hi ${data.preferences.name}", style = MaterialTheme.typography.headlineLarge)
            }
            IconCircleButton(Icons.Outlined.Settings, "Settings", onSettings)
        }
        InShapeCard(color = colors.accent) {
            Text(when { active != null -> "IN PROGRESS"; routine != null -> "NEXT UP"; else -> "READY WHEN YOU ARE" }, color = colors.onAccentMuted, style = MaterialTheme.typography.labelSmall)
            Text(active?.row?.title ?: routine?.title ?: "Start a workout", color = colors.onAccent, style = MaterialTheme.typography.headlineMedium)
            val meta = when {
                active != null -> "${active.doneCount} of ${active.items.size} exercises done" + (active.row.start?.let { ", started ${Instant.parse(it).atZone(ZoneId.of(active.row.zoneId)).toLocalTime().withSecond(0).withNano(0)}" } ?: "")
                routine != null -> "${data.routineItems.count { it.routineId == routine.id }} exercises" + (routine.durationMinutes?.let { ", about $it min" } ?: "")
                else -> "Add exercises as you go"
            }
            Text(meta, color = colors.onAccentMuted, style = MaterialTheme.typography.bodyLarge)
            if (active != null) ProgressSegments(active.items.map { if (it.row.disposition == "DONE") ProgressState.Done else ProgressState.Pending }, "${active.doneCount} of ${active.items.size} exercises done", onAccent = true)
            HeroButton(if (active == null) "Start workout" else "Resume workout", {
                if (active != null) onResume(active.row.id) else onStart(routine?.let { StartSource.Routine(it.id) } ?: StartSource.Empty)
            }, icon = Icons.Outlined.PlayArrow)
        }
        if (active == null && (routine != null || history.isNotEmpty())) ResponsiveRow {
            if (routine != null) SecondaryButton("Empty workout", { onStart(StartSource.Empty) }, Modifier.weight(1f))
            history.firstOrNull()?.let { last -> SecondaryButton("Repeat last", { onStart(StartSource.Repeat(last.row.id)) }, Modifier.weight(1f)) }
        }
        InShapeCard {
            ResponsiveRow {
                Text("This week", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                Text(thisWeek.size.toString(), style = MaterialTheme.typography.headlineSmall)
                Text(data.preferences.weeklyGoal?.let { "of $it workouts" } ?: "workouts", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { day ->
                    val date = week.plusDays(day.toLong())
                    val done = thisWeek.any { it.row.journalDate == date.toString() }
                    val isToday = date == today
                    Column(Modifier.weight(1f).semantics { contentDescription = "${date.displayDate("EEEE d MMMM")}, ${if (done) "workout recorded" else "no workout"}${if (isToday) ", today" else ""}" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                        Surface(Modifier.size(Dimens.weekDay), InShapeShapes.pill, if (done) colors.accent else if (isToday) Color.Transparent else colors.surfaceInset,
                            border = if (isToday && !done) BorderStroke(Dimens.ring, colors.mark) else null) {
                            Box(contentAlignment = Alignment.Center) { if (done) Icon(Icons.Outlined.Check, null, Modifier.size(Dimens.iconSmall), tint = colors.onAccent) }
                        }
                        Text(date.dayOfWeek.getDisplayName(if (largeText) TextStyle.NARROW else TextStyle.SHORT, Locale.ENGLISH), style = if (isToday) InShapeType.navSelected else InShapeType.nav, color = if (isToday) colors.text else colors.textSecondary)
                    }
                }
            }
        }
        SectionLabel("Routines", action = "See all", onAction = onLibrary)
        if (data.routines.isEmpty()) EmptyState("A plan that fits you", "Create a routine from your own exercises.", "Open Library", onLibrary)
        else ListCard {
            data.routines.take(4).forEach { saved -> item {
                val last = history.firstOrNull { it.row.sourceRoutineId == saved.id }?.row?.journalDate?.let(LocalDate::parse)
                val whenDone = last?.displayDate(if (last in today.minusDays(6)..today) "EEEE" else "d MMMM")
                ListRow(saved.title, subtitle = "${data.routineItems.count { it.routineId == saved.id }} exercises, " + (whenDone?.let { "last done $it" } ?: "not done yet"), onClick = { onStart(StartSource.Routine(saved.id)) })
            } }
        }
        history.firstOrNull()?.let { last ->
            SectionLabel("Last workout")
            ListCard { item {
                val date = LocalDate.parse(last.row.journalDate)
                ListRow(last.row.title, subtitle = listOfNotNull("${last.performedCount} exercises", last.row.durationSeconds?.let { "${it / 60} min" }).joinToString(", "),
                    leading = { DateTile(date.dayOfMonth.toString(), date.displayDate("EEE")) }, onClick = { onDetails(last.row.id) })
            } }
        }
        healthContext()
    }
}

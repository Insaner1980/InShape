package com.emmah.inshape.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.*
import com.emmah.inshape.feature.workout.*
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import java.time.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(data: JournalSnapshot, today: LocalDate, vm: JournalViewModel, onOpen: (String) -> Unit, onPast: (String) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var past by remember { mutableStateOf(false) }
    val colors = InShapeTheme.colors
    val monthRecords = data.history.filter { YearMonth.from(LocalDate.parse(it.row.journalDate)) == month }
    val records = monthRecords.filter { selected == null || it.row.journalDate == selected }.sortedWith(compareByDescending<Workout> { it.row.journalDate }.thenByDescending { it.row.createdAt })
    val qualifying = records.filter { it.qualifiesForAttendance }
    val durations = qualifying.mapNotNull { it.row.durationSeconds }
    val effort = qualifying.mapNotNull { it.checkIns.find { check -> check.phase == "AFTER" }?.effort }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Page {
        ScreenHeader("History", trailing = { SecondaryButton("Past workout", { past = true }, icon = Icons.Outlined.Add) })
        InShapeCard(contentPadding = PaddingValues(horizontal = Dimens.inner, vertical = Dimens.itemGap), gap = Dimens.innerSmall) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ monthText = month.minusMonths(1).toString(); selected = null }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
                TextAction(month.atDay(1).displayDate("MMMM yyyy"), { datePicker = true }, Modifier.weight(1f), color = colors.text)
                IconButton({ monthText = month.plusMonths(1).toString(); selected = null }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
            }
            Row { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(if (largeText) it.take(1) else it, Modifier.weight(1f), textAlign = TextAlign.Center, style = InShapeType.nav, color = colors.textSecondary) } }
            monthGrid(month).chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) { week.forEach { date ->
                Box(Modifier.weight(1f).heightIn(min = Dimens.calendarDay).then(if (date == null) Modifier else Modifier.clickable { selected = date.toString() }.semantics {
                    contentDescription = date.displayDate("EEEE d MMMM") + (if (monthRecords.any { it.row.journalDate == date.toString() && it.qualifiesForAttendance }) ", workout recorded" else "") + (if (date == today) ", today" else "")
                    this.selected = selected == date.toString()
                }), contentAlignment = Alignment.Center) {
                    if (date != null) {
                        val recorded = monthRecords.any { it.row.journalDate == date.toString() && it.qualifiesForAttendance }
                        val chosen = selected == date.toString()
                        Surface(Modifier.sizeIn(minHeight = Dimens.weekDay, minWidth = Dimens.weekDay), shape = InShapeShapes.pill,
                            color = if (recorded) colors.accent else if (chosen) colors.selected else Color.Transparent,
                            border = if (recorded && (chosen || date == today)) BorderStroke(Dimens.ring, colors.text) else if (date == today) BorderStroke(Dimens.ring, colors.mark) else null) {
                            Box(contentAlignment = Alignment.Center) { Text(date.dayOfMonth.toString(), color = if (recorded) colors.onAccent else colors.text, style = if (recorded || date == today) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            } } }
            HorizontalDivider(color = colors.divider)
            ResponsiveRow {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    Surface(Modifier.size(Dimens.itemGap), shape = InShapeShapes.pill, color = colors.accent) { }
                    Text("Workout", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    Surface(Modifier.size(Dimens.itemGap), shape = InShapeShapes.pill, color = Color.Transparent, border = BorderStroke(Dimens.ring, colors.mark)) { }
                    Text("Today", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                if (selected != null) TextAction("Show whole month", { selected = null })
            }
        }
        val drafts = data.workouts.filter { it.state == "DRAFT" }
        if (drafts.isNotEmpty()) ListCard { drafts.forEach { draft -> item { ListRow(draft.title, subtitle = "Unfinished, ${draft.journalDate.journalDateLabel()}", onClick = { onPast(draft.id) }) } } }
        val minutes = durations.sum() / 60
        val summaries = listOf(qualifying.size.toString() to "workouts", (if (durations.isEmpty()) "None" else if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min") to "recorded time", (if (effort.isEmpty()) "None" else String.format(Locale.ENGLISH, "%.1f", effort.average())) to "average effort")
        InShapeCard(contentPadding = PaddingValues(vertical = Dimens.inner), gap = Dimens.innerSmall) {
            if (largeText) summaries.forEachIndexed { index, (value, label) ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                Column(Modifier.padding(horizontal = Dimens.inner)) { Text(value, style = MaterialTheme.typography.headlineSmall, color = if (value == "None") colors.textSecondary else colors.text); Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
            } else Row(Modifier.height(IntrinsicSize.Min)) { summaries.forEachIndexed { index, (value, label) ->
                if (index > 0) VerticalDivider(color = colors.divider)
                Column(Modifier.weight(1f).padding(horizontal = Dimens.inner)) { Text(value, style = MaterialTheme.typography.headlineSmall, color = if (value == "None") colors.textSecondary else colors.text); Text(label, style = InShapeType.nav, color = colors.textSecondary) }
            } }
            if (durations.size < qualifying.size || effort.size < qualifying.size) Text("Time from ${durations.size} of ${qualifying.size} workouts, effort from ${effort.size}.", Modifier.padding(horizontal = Dimens.inner), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        SectionLabel(if (selected == null) "Workouts in ${month.atDay(1).displayDate("MMMM")}" else "Workouts on ${selected!!.journalDateLabel()}")
        if (records.isEmpty()) EmptyState("No workouts in this period", "Pick another month or add a past workout.")
        else ListCard { records.forEach { workout -> item {
            val date = LocalDate.parse(workout.row.journalDate)
            val score = workout.checkIns.find { it.phase == "AFTER" }?.effort
            ListRow(workout.row.title, subtitle = if (!workout.hasPerformedWork) "Note only, not counted" else listOfNotNull(workout.row.durationSeconds?.let { "${it / 60} min" }, "${workout.performedCount} exercises").joinToString(", "),
                leading = { DateTile(date.dayOfMonth.toString(), date.displayDate("EEE")) }, trailing = score?.let { { Pill("Effort $it") } }, onClick = { onOpen(workout.row.id) })
        } } }
    }
    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (selected?.let(LocalDate::parse) ?: month.atDay(1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = { TextAction("Select date", {
            state.selectedDateMillis?.let { millis -> val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(); monthText = YearMonth.from(date).toString(); selected = date.toString() }; datePicker = false
        }, enabled = state.selectedDateMillis != null) }, dismissButton = { TextAction("Cancel", { datePicker = false }) }) { DatePicker(state) }
    }
    if (past) {
        var date by remember { mutableStateOf(today.toString()) }; var title by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { past = false }, title = { Text("Add past workout") }, text = { Column { TextField(date, "Actual date (YYYY-MM-DD)", { date = it }); TextField(title, "Workout name", { title = it }) } },
            confirmButton = { TextAction("Add exercises", { vm.write { val id = createPastWorkout(date, title); past = false; onPast(id) } }) }, dismissButton = { TextAction("Cancel", { past = false }) })
    }
}

@Composable
fun WorkoutDetailScreen(id: String, data: JournalSnapshot, vm: JournalViewModel, onRepeat: () -> Unit, onCheckIn: (String) -> Unit, onTiming: () -> Unit, onDeleted: () -> Unit, onExercise: (String, String) -> Unit) {
    val workout = data.workout(id) ?: return
    var deletion by remember { mutableStateOf(false) }
    var editing by rememberSaveable(id) { mutableStateOf(false) }
    val colors = InShapeTheme.colors
    Page(bottomBar = { PrimaryButton("Repeat workout", onRepeat, Modifier.fillMaxWidth().heightIn(min = Dimens.button)) }) {
        Text(workout.row.title, style = MaterialTheme.typography.headlineMedium)
        Text(workout.row.journalDate.journalDateLabel("EEEE d MMMM yyyy"), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        ActionRow {
            workout.row.durationSeconds?.let { Pill("${it / 60} min") }
            Pill("${workout.performedCount} exercises")
            workout.checkIns.find { it.phase == "AFTER" }?.effort?.let { Pill("Effort $it") }
        }
        if (workout.row.durationSeconds == null) Text("Duration not recorded", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        if (!workout.hasPerformedWork) Text("Note-only entry, not counted as attendance", style = MaterialTheme.typography.bodySmall)
        if (workout.row.note.isNotBlank()) { SectionLabel("Workout note"); InShapeCard { Text(workout.row.note) } }
        SecondaryButton("Correct date, timing or note", onTiming)
        SecondaryButton(if (editing) "Finish editing sets" else "Edit recorded sets", { editing = !editing })
        SectionLabel("Exercises")
        ListCard { workout.items.forEach { exercise -> item {
            ListRow(exercise.row.name, subtitle = itemSummary(exercise), onClick = { onExercise(exercise.row.exerciseId, exercise.row.id) })
            Column(Modifier.padding(bottom = Dimens.innerSmall), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                Text(exercise.row.disposition.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                if (exercise.row.setup.isNotBlank()) Text(exercise.row.setup, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                if (editing) exercise.sets.forEachIndexed { index, set -> SetRow(set, index + 1, exercise.row.basis, vm) }
                if (exercise.row.note.isNotBlank()) Text(exercise.row.note, style = MaterialTheme.typography.bodyMedium)
                if (exercise.row.discomfort) Text("Discomfort was noted", style = MaterialTheme.typography.bodySmall)
            }
        } } }
        SectionLabel("Optional check-ins")
        ListCard { listOf("BEFORE" to "Before", "AFTER" to "After", "NEXT_DAY" to "Next day").forEach { (phase, label) -> item {
            val check = workout.checkIns.find { it.phase == phase }
            val summary = check?.let {
                buildList {
                    add("Observed ${it.observedAt.instantLabel(ZoneId.of(it.zoneId))}")
                    it.energy?.let { energy -> add("Energy: ${energy.lowercase()}") }
                    it.effort?.let { effort -> add("Effort: $effort of 10") }
                    if (it.feelings.isNotBlank()) add(it.feelings)
                    if (it.note.isNotBlank()) add(it.note)
                    data.observations.filter { observation -> observation.checkInId == it.id }.forEach { observation -> add("${observation.region.replace('_', ' ')}: pain ${observation.pain?.toString() ?: "not recorded"}, stiffness ${observation.stiffness?.toString() ?: "not recorded"}") }
                }.joinToString("\n")
            } ?: "Not recorded"
            ListRow(label, subtitle = summary, onClick = { onCheckIn(phase) })
        } } }
        com.emmah.inshape.health.WorkoutHealthControls(workout, data, vm)
        TextAction("Delete workout", { deletion = true }, Modifier.align(Alignment.CenterHorizontally), color = colors.error)
    }
    if (deletion) ConfirmDialog("Delete workout?", "Delete this local record. If a shared copy exists, its deletion will stay pending until Health Connect access is available.", "Delete", { vm.write { deleteWorkout(id); onDeleted() }; deletion = false }, { deletion = false })
}

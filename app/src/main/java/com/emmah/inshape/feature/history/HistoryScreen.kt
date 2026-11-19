package com.emmah.inshape.feature.history

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.monthGrid
import com.emmah.inshape.feature.checkin.BodyLevelPills
import com.emmah.inshape.feature.checkin.EnergyLevel
import com.emmah.inshape.feature.checkin.bodyRegions
import com.emmah.inshape.feature.progress.ProgressScreen
import com.emmah.inshape.feature.workout.SetRow
import com.emmah.inshape.feature.workout.SetTableHeader
import com.emmah.inshape.feature.workout.itemSummary
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.DialogAction
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.RecordedDatePicker
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.ScreenTabs
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.displayDate
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.hasArtworkColumns
import com.emmah.inshape.ui.theme.instantLabel
import com.emmah.inshape.ui.theme.journalDateLabel
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    data: JournalSnapshot,
    today: LocalDate,
    vm: JournalViewModel,
    onOpen: (String) -> Unit,
    onPast: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialView: String = "calendar",
    initialExerciseId: String? = null
) {
    var view by rememberSaveable(initialView, initialExerciseId) { mutableStateOf(initialView) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var past by rememberSaveable { mutableStateOf(false) }
    var pastPicker by rememberSaveable { mutableStateOf(false) }
    var pastDate by rememberSaveable { mutableStateOf(today.toString()) }
    var pastTitle by rememberSaveable { mutableStateOf("") }
    var showPastTitleError by rememberSaveable { mutableStateOf(false) }
    var creatingPastWorkout by remember { mutableStateOf(false) }
    val colors = InShapeTheme.colors
    val monthRecords = data.history.filter {
        YearMonth.from(LocalDate.parse(it.row.journalDate)) ==
            month
    }
    val records = monthRecords.filter {
        selected == null || it.row.journalDate == selected
    }.sortedWith(
        compareByDescending<Workout> {
            it.row.journalDate
        }.thenByDescending { it.row.createdAt }
    )
    val qualifying = records.filter { it.qualifiesForAttendance }
    val durations = qualifying.mapNotNull { it.row.durationSeconds }
    val effort = qualifying.mapNotNull {
        it.checkIns.find { check -> check.phase == "AFTER" }?.effort
    }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val header: @Composable () -> Unit = {
        ScreenHeader("History", trailing = {
            OutlineButton("Past workout", {
                if (!creatingPastWorkout && !past) {
                    pastDate = today.toString()
                    pastTitle = ""
                    showPastTitleError = false
                    past = true
                }
            }, enabled = !creatingPastWorkout, icon = Icons.Outlined.Add)
        })
        ScreenTabs(
            listOf("Calendar", "Progress"),
            if (view ==
                "progress"
            ) {
                1
            } else {
                0
            },
            { view = if (it == 0) "calendar" else "progress" }
        )
    }
    if (view == "progress") {
        ProgressScreen(data, today, modifier, initialExerciseId, header)
    } else {
        Page(modifier) {
            header()
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({
                        monthText = month.minusMonths(1).toString()
                        selected = null
                    }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
                    TextAction(month.atDay(1).displayDate("MMMM yyyy"), {
                        datePicker = true
                    }, Modifier.weight(1f), color = colors.text)
                    IconButton({
                        monthText = month.plusMonths(1).toString()
                        selected = null
                    }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
                }
                Row {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                        Text(
                            if (largeText) it.take(1) else it,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = InShapeType.nav,
                            color = colors.textSecondary
                        )
                    }
                }
                Column {
                    monthGrid(month).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                Box(
                                    Modifier.weight(1f).heightIn(min = Dimens.calendarDay).then(
                                        if (date ==
                                            null
                                        ) {
                                            Modifier
                                        } else {
                                            Modifier.clickable { selected = date.toString() }.semantics {
                                                contentDescription =
                                                    date.displayDate("EEEE d MMMM") +
                                                    (
                                                        if (monthRecords.any {
                                                                it.row.journalDate == date.toString() &&
                                                                    it.qualifiesForAttendance
                                                            }
                                                        ) {
                                                            ", workout recorded"
                                                        } else {
                                                            ""
                                                        }
                                                        ) +
                                                    (if (date == today) ", today" else "")
                                                this.selected = selected == date.toString()
                                            }
                                        }
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) {
                                        val recorded = monthRecords.any {
                                            it.row.journalDate == date.toString() &&
                                                it.qualifiesForAttendance
                                        }
                                        val chosen = selected == date.toString()
                                        Surface(
                                            Modifier.sizeIn(
                                                minHeight = Dimens.weekDay,
                                                minWidth = Dimens.weekDay
                                            ),
                                            shape = InShapeShapes.pill,
                                            color = if (recorded) {
                                                colors.ink
                                            } else if (chosen) {
                                                colors.surfaceStrong
                                            } else {
                                                Color.Transparent
                                            },
                                            border = if (recorded &&
                                                (chosen || date == today)
                                            ) {
                                                BorderStroke(Dimens.ring, colors.text)
                                            } else if (date ==
                                                today
                                            ) {
                                                BorderStroke(Dimens.ring, colors.ink)
                                            } else {
                                                null
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    date.dayOfMonth.toString(),
                                                    color = if (recorded) colors.onInk else colors.text,
                                                    style = if (recorded ||
                                                        date == today
                                                    ) {
                                                        MaterialTheme.typography.labelMedium
                                                    } else {
                                                        MaterialTheme.typography.bodyMedium
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (selected != null) TextAction("Show whole month", { selected = null }, icon = Icons.Outlined.CalendarMonth)
            }
            val drafts = data.workouts.filter { it.state == "DRAFT" }
            if (drafts.isNotEmpty()) {
                ListCard {
                    drafts.forEach { draft ->
                        item {
                            ListRow(draft.title, subtitle = "Unfinished, ${draft.journalDate.journalDateLabel()}", onClick = {
                                onPast(draft.id)
                            })
                        }
                    }
                }
            }
            val summaries = listOfNotNull(
                qualifying.size.toString() to pluralStringResource(R.plurals.workout_count_label, qualifying.size),
                formatDuration(durations.sum()) to "total time",
                (durations.takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(::formatDuration) ?: "0 min") to "average"
            )
            BoxWithConstraints(
                Modifier.fillMaxWidth().clip(InShapeShapes.card).background(ArtworkWash.Paper.brush())
                    .padding(PaddingValues(horizontal = Dimens.inner, vertical = Dimens.itemGap))
            ) {
                val ornament = records.isNotEmpty() && hasArtworkColumns() && summaries.all { (value, _) -> value.length <= 7 }
                ResponsiveRow(Modifier.padding(end = if (ornament) 54.dp else 0.dp)) {
                    summaries.forEach { (value, label) -> HistorySummary(value, label, Modifier.weight(1f)) }
                }
                if (ornament) {
                    Image(
                        painterResource(R.drawable.art_history_paper_calendar),
                        null,
                        Modifier.align(Alignment.BottomEnd).size(54.dp, 40.dp),
                        alpha = if (colors.isDark) .22f else .65f
                    )
                }
            }
            if (durations.size <
                qualifying.size
            ) {
                Text(
                    "Time from ${durations.size} of ${qualifying.size} workouts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
            SectionHeader(if (selected == null) "${month.atDay(1).displayDate("MMMM")} workouts" else selected!!.journalDateLabel())
            if (records.isEmpty()) {
                EmptyState("No workouts in this period", "Pick another month or add a past workout.")
            } else {
                ListCard {
                    records.forEach { workout ->
                        item {
                            val date = LocalDate.parse(workout.row.journalDate)
                            ListRow(
                                workout.row.title,
                                subtitle =
                                    "${date.displayDate("EEE d MMM")}, " +
                                        if (!workout.hasPerformedWork) "note only, not counted" else "${workout.performedCount} exercises",
                                trailing = {
                                    workout.row.durationSeconds?.let {
                                        Text(formatDuration(it), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                    }
                                },
                                onClick = { onOpen(workout.row.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    if (datePicker) {
        val initial = selected ?: if (YearMonth.from(today) == month) today.toString() else month.atDay(1).toString()
        RecordedDatePicker("Select date", initial, today.toString(), { value ->
            monthText = YearMonth.from(LocalDate.parse(value)).toString()
            selected = value
            datePicker = false
        }, { datePicker = false }, confirm = "Select date")
    }
    if (past) {
        AlertDialog(
            onDismissRequest = {
                past = false
            },
            title = { Text("Add past workout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                    KeyValueRow("Workout date", pastDate.journalDateLabel("EEE d MMM yyyy"), onClick = { pastPicker = true })
                    TextField(
                        pastTitle,
                        "Workout name",
                        { pastTitle = it },
                        error = "Enter a workout name".takeIf { showPastTitleError && pastTitle.isBlank() }
                    )
                }
            },
            confirmButton = {
                DialogAction("Add exercises", {
                    if (past && !creatingPastWorkout && pastTitle.isBlank()) {
                        showPastTitleError = true
                    } else if (past && !creatingPastWorkout) {
                        val date = pastDate
                        val title = pastTitle
                        creatingPastWorkout = true
                        past = false
                        vm.write(onRejected = {
                            creatingPastWorkout = false
                            past = true
                        }) {
                            val id = createPastWorkout(date, title)
                            creatingPastWorkout = false
                            onPast(id)
                        }
                    }
                })
            },
            dismissButton = { DialogAction("Cancel", { past = false }) }
        )
    }
    if (pastPicker) {
        RecordedDatePicker("Workout date", pastDate, today.toString(), { value ->
            pastDate = value
            pastPicker = false
        }, { pastPicker = false })
    }
}

@Composable
fun WorkoutDetailScreen(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onRepeat: () -> Unit,
    onCheckIn: (String) -> Unit,
    onTiming: () -> Unit,
    onDelete: () -> Unit,
    onExercise: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val workout = data.workout(id) ?: return
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    var deletion by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    var note by rememberSaveable(id) { mutableStateOf(workout.row.note) }
    var editing by rememberSaveable(id) { mutableStateOf(false) }
    val colors = InShapeTheme.colors
    Page(modifier, bottomBar = {
        PrimaryButton(
            "Repeat workout",
            onRepeat,
            Modifier.fillMaxWidth().heightIn(min = Dimens.button)
        )
    }, topBar = {
        PushedHeader(onBack = onDelete) {
            IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Workout options") }
            AppMenu(menu, { menu = false }) {
                MenuAction("Fix time", Icons.Outlined.Schedule, {
                    menu = false
                    onTiming()
                })
                MenuAction("Edit note", Icons.Outlined.EditNote, {
                    menu = false
                    note = workout.row.note
                    editingNote = true
                })
                MenuAction(if (editing) "Finish editing sets" else "Edit sets", Icons.Outlined.Edit, {
                    menu = false
                    editing = !editing
                })
            }
        }
    }) {
        Text(workout.row.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            workout.row.journalDate.journalDateLabel("EEEE d MMMM yyyy"),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Text(workout.row.durationSeconds?.let(::formatDuration) ?: "Duration not recorded", style = MaterialTheme.typography.displaySmall)
        com.emmah.inshape.feature.workout.WorkoutMetrics(workout)
        if (!workout.hasPerformedWork) {
            Text(
                "Note-only entry, not counted as attendance",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (workout.row.note.isNotBlank()) {
            SectionHeader("Workout note")
            Text(workout.row.note)
        }
        SectionHeader("Exercises")
        ListCard {
            workout.items.forEach { exercise ->
                item {
                    ListRow(exercise.row.name, subtitle = itemSummary(exercise), onClick = {
                        onExercise(exercise.row.exerciseId, exercise.row.id)
                    })
                    Column(
                        Modifier.padding(bottom = Dimens.innerSmall),
                        verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                    ) {
                        when (exercise.row.disposition) {
                            "SKIPPED" -> Pill("Skipped")
                            "PLANNED", "IN_PROGRESS", "INCOMPLETE" -> Pill("Incomplete")
                        }
                        if (exercise.row.setup.isNotBlank()) {
                            Text(
                                exercise.row.setup,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        if (editing && exercise.sets.isNotEmpty()) {
                            SetTableHeader(exercise.row.basis)
                            exercise.sets.forEachIndexed { index, set ->
                                SetRow(set, index + 1, exercise.row.basis, vm)
                            }
                        }
                        if (exercise.row.note.isNotBlank()) {
                            Text(
                                exercise.row.note,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (exercise.row.discomfort) {
                            Text(
                                "Discomfort was noted",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        SectionHeader("Check-ins")
        ListCard {
            listOf(
                "BEFORE" to "Before workout",
                "AFTER" to "After workout",
                "NEXT_DAY" to "Next day"
            ).forEach { (phase, label) ->
                item {
                    val check = workout.checkIns.find { it.phase == phase }
                    val summary = check?.let {
                        buildList {
                            add("Observed ${it.observedAt.instantLabel(ZoneId.of(it.zoneId), uses24Hours)}")
                            it.effort?.let { effort -> add(com.emmah.inshape.feature.checkin.effortLevelOf(effort)!!.label) }
                            if (it.feelings.isNotBlank()) add(it.feelings)
                            if (it.note.isNotBlank()) add(it.note)
                        }.joinToString("\n")
                    } ?: "Add"
                    val energy = EnergyLevel.fromKey(check?.energy)
                    ListRow(
                        label,
                        subtitle = summary,
                        onClick = { onCheckIn(phase) },
                        details = { if (energy != null) Pill("Energy ${energy.label}") }
                    )
                    if (check != null) {
                        data.observations.filter { it.checkInId == check.id }.forEach { observation ->
                            val region = bodyRegions.find { it.id == observation.region }?.label
                                ?: observation.region.replace('_', ' ').replaceFirstChar(Char::titlecase)
                            Column(
                                Modifier.padding(bottom = Dimens.itemGap),
                                verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                            ) {
                                Text(region, style = MaterialTheme.typography.bodyLarge)
                                BodyLevelPills(observation.pain, observation.stiffness)
                            }
                        }
                    }
                }
            }
        }
        com.emmah.inshape.health.WorkoutHealthControls(workout, data, vm)
        TextAction("Delete workout", {
            deletion = true
        }, Modifier.align(Alignment.CenterHorizontally), color = colors.error, icon = Icons.Outlined.Delete)
    }
    if (editingNote) {
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text("Edit note") },
            text = { TextField(note, "Workout note", { note = it }) },
            confirmButton = {
                DialogAction("Save", {
                    val value = note
                    vm.write {
                        saveWorkoutNote(id, value)
                        editingNote = false
                    }
                })
            },
            dismissButton = { DialogAction("Cancel", { editingNote = false }) }
        )
    }
    if (deletion) {
        ConfirmDialog(
            "Delete workout?",
            stringResource(R.string.delete_workout_message),
            "Delete",
            destructive = true,
            {
                vm.write {
                    deleteWorkout(id)
                    onDelete()
                }
                deletion =
                    false
            },
            { deletion = false }
        )
    }
}

@Composable
private fun HistorySummary(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val colors = InShapeTheme.colors
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (value == "None") colors.textSecondary else colors.text
        )
        Text(label, style = labelStyle, color = colors.textSecondary)
    }
}

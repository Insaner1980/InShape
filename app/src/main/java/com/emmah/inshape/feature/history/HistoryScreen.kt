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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.monthGrid
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.checkin.bodyRegions
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.progress.ProgressScreen
import com.emmah.inshape.feature.workout.SetRow
import com.emmah.inshape.feature.workout.SetTableHeader
import com.emmah.inshape.feature.workout.itemSummary
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.DialogAction
import com.emmah.inshape.ui.components.EditorSheet
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.HeaderIconButton
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
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
import com.emmah.inshape.ui.theme.dateTime
import com.emmah.inshape.ui.theme.displayedDurationSeconds
import com.emmah.inshape.ui.theme.finnishLocale
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.hasArtworkColumns
import com.emmah.inshape.ui.theme.longDate
import com.emmah.inshape.ui.theme.monthName
import com.emmah.inshape.ui.theme.monthTitle
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Duration
import java.time.Instant
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
    initialExerciseId: String? = null,
    onQuestions: (() -> Unit)? = null
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
    val submissions by vm.submissions.collectAsStateWithLifecycle()
    val pastKey = "create-past-workout"
    val pastOrigin = rememberSaveable { newId() }
    val pastSubmission = submissions[pastKey]
    val creatingPastWorkout = pastSubmission?.let { it.result == null } == true
    var submittedPast by rememberSaveable { mutableStateOf(false) }
    val latestOnPast by rememberUpdatedState(onPast)
    LaunchedEffect(pastSubmission, submittedPast) {
        if (!submittedPast) return@LaunchedEffect
        val currentSubmission = vm.submissions.value[pastKey]
        when {
            currentSubmission == null -> {
                submittedPast = false
                past = true
            }

            currentSubmission.result != null && vm.consumeSubmission(pastKey, currentSubmission) -> {
                submittedPast = false
                if (currentSubmission.origin == pastOrigin) latestOnPast(currentSubmission.result)
            }
        }
    }
    val header: @Composable () -> Unit = {
        ScreenHeader(stringResource(R.string.history), trailing = {
            Row {
                if (onQuestions != null) {
                    HeaderIconButton(Icons.Outlined.QuestionAnswer, stringResource(R.string.ask_journal), onQuestions)
                }
                HeaderIconButton(Icons.Outlined.Add, stringResource(R.string.add_past_workout), {
                    if (!creatingPastWorkout && !past) {
                        pastDate = today.toString()
                        pastTitle = ""
                        showPastTitleError = false
                        past = true
                    }
                }, primaryAdd = true, enabled = !creatingPastWorkout)
            }
        })
        ScreenTabs(
            listOf(stringResource(R.string.calendar), stringResource(R.string.progress)),
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
            val monthRecords = data.historyIn(DateRange(month.atDay(1), month.atEndOfMonth()))
            HistoryCalendar(
                month,
                today,
                selected,
                monthRecords,
                onMonthChange = {
                    monthText = it.toString()
                    selected = null
                },
                onPickDate = { datePicker = true },
                onSelect = { selected = it }
            )
            CalendarMonthWorkouts(data, month, monthRecords, selected, onPast, onOpen)
        }
    }
    if (datePicker) {
        HistoryMonthSheet(month, YearMonth.from(today), { datePicker = false }) {
            monthText = it.toString()
            selected = null
            datePicker = false
        }
    }
    if (past) {
        val pastTitleFocus = remember { FocusRequester() }
        EditorSheet(stringResource(R.string.add_past_workout), { past = false }) {
            KeyValueRow(stringResource(R.string.workout_date), LocalDate.parse(pastDate).shortDate(), onClick = { pastPicker = true })
            TextField(
                pastTitle,
                stringResource(R.string.workout_name),
                { pastTitle = it },
                modifier = Modifier.focusRequester(pastTitleFocus),
                error = stringResource(R.string.workout_name_required).takeIf { showPastTitleError && pastTitle.isBlank() }
            )
            val keyboard = LocalSoftwareKeyboardController.current
            ActionRow(Modifier.align(Alignment.End)) {
                DialogAction(stringResource(R.string.cancel), { past = false })
                DialogAction(stringResource(R.string.continue_action), {
                    if (past && !creatingPastWorkout && pastTitle.isBlank()) {
                        showPastTitleError = true
                        pastTitleFocus.requestFocus()
                        keyboard?.show()
                    } else if (past && !creatingPastWorkout) {
                        val date = pastDate
                        val title = pastTitle
                        submittedPast = true
                        past = false
                        vm.submit(pastKey, pastOrigin) { createPastWorkout(date, title) }
                    }
                })
            }
        }
    }
    if (pastPicker) {
        RecordedDatePicker(stringResource(R.string.workout_date), pastDate, today.toString(), { value ->
            pastDate = value
            pastPicker = false
        }, { pastPicker = false })
    }
}

@Composable
private fun HistoryMonthSheet(selectedMonth: YearMonth, currentMonth: YearMonth, onDismiss: () -> Unit, onSelect: (YearMonth) -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(selectedMonth.year.coerceAtMost(currentMonth.year)) }
    val labels = stringArrayResource(R.array.history_months)
    val colors = InShapeTheme.colors
    EditorSheet(stringResource(R.string.select_month), onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            HeaderIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_year), { year-- })
            Text(year.toString(), style = MaterialTheme.typography.titleMedium)
            HeaderIconButton(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                stringResource(R.string.next_year),
                { year++ },
                enabled =
                    year < currentMonth.year
            )
        }
        labels.toList().chunked(3).forEachIndexed { row, months ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                months.forEachIndexed { column, label ->
                    val month = YearMonth.of(year, row * 3 + column + 1)
                    val chosen = month == selectedMonth
                    val enabled = month <= currentMonth
                    Surface(
                        onClick = { onSelect(month) },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.touch).semantics { selected = chosen },
                        enabled = enabled,
                        shape = InShapeShapes.field,
                        color = if (chosen) colors.ink else colors.background,
                        contentColor = when {
                            chosen -> colors.onInk
                            enabled -> colors.text
                            else -> colors.textTertiary
                        },
                        border = if (month == currentMonth && !chosen) BorderStroke(Dimens.line, colors.outline) else null
                    ) {
                        Box(Modifier.padding(Dimens.innerTiny), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: String?,
    monthRecords: List<Workout>,
    onMonthChange: (YearMonth) -> Unit,
    onPickDate: () -> Unit,
    onSelect: (String?) -> Unit
) {
    val colors = InShapeTheme.colors
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_month))
            }
            TextAction(month.monthTitle(), onPickDate, Modifier.weight(1f), color = colors.text, icon = Icons.Outlined.CalendarMonth)
            IconButton({ onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_month))
            }
        }
        Row {
            java.time.DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.SHORT, finnishLocale) }.forEach {
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
                        CalendarDay(date, today, selected, monthRecords, onSelect)
                    }
                }
            }
        }
        if (selected != null) TextAction(stringResource(R.string.show_month), { onSelect(null) }, icon = Icons.Outlined.CalendarMonth)
    }
}

@Composable
private fun RowScope.CalendarDay(
    date: LocalDate?,
    today: LocalDate,
    selected: String?,
    monthRecords: List<Workout>,
    onSelect: (String?) -> Unit
) {
    val cell = Modifier.weight(1f).heightIn(min = Dimens.calendarDay)
    if (date == null) {
        Box(cell)
        return
    }

    val dateText = date.toString()
    val recorded = monthRecords.any { it.row.journalDate == dateText && it.qualifiesForAttendance }
    val recordedLabel = if (recorded) ", " + stringResource(R.string.workout_recorded) else ""
    val todayLabel = if (date == today) ", " + stringResource(R.string.today_lowercase) else ""
    val chosen = dateText == selected
    val colors = InShapeTheme.colors
    Box(
        cell.clickable { onSelect(dateText) }.semantics {
            contentDescription = date.shortDate() + recordedLabel + todayLabel
            this.selected = chosen
        },
        contentAlignment = Alignment.Center
    ) {
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
            border = if (recorded && (chosen || date == today)) {
                BorderStroke(Dimens.ring, colors.text)
            } else if (date == today) {
                BorderStroke(Dimens.ring, colors.ink)
            } else {
                null
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    date.dayOfMonth.toString(),
                    color = if (recorded) colors.onInk else colors.text,
                    style = if (recorded || date == today) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CalendarMonthWorkouts(
    data: JournalSnapshot,
    month: YearMonth,
    monthRecords: List<Workout>,
    selected: String?,
    onPast: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    val colors = InShapeTheme.colors
    val records = monthRecords.filter {
        selected == null || it.row.journalDate == selected
    }.sortedWith(
        compareByDescending<Workout> {
            it.row.journalDate
        }.thenByDescending { it.row.createdAt }
    )
    val qualifying = records.filter { it.qualifiesForAttendance }
    val durations = qualifying.mapNotNull { it.row.durationSeconds }
    val drafts = data.workouts.filter { it.state == "DRAFT" }
    if (drafts.isNotEmpty()) {
        ListCard {
            drafts.forEach { draft ->
                item {
                    ListRow(
                        draft.title,
                        subtitle = stringResource(
                            R.string.unfinished_date,
                            LocalDate.parse(draft.journalDate).shortDate()
                        ),
                        onClick = {
                            onPast(draft.id)
                        }
                    )
                }
            }
        }
    }
    val summaries = listOfNotNull(
        qualifying.size.toString() to pluralStringResource(R.plurals.workout_count_label, qualifying.size),
        formatDuration(durations.sum()) to stringResource(R.string.total_time),
        (durations.takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(::formatDuration) ?: "–") to stringResource(R.string.average)
    )
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(InShapeShapes.card).background(ArtworkWash.Paper.brush())
            .padding(PaddingValues(horizontal = Dimens.inner, vertical = Dimens.itemGap))
    ) {
        val ornament = records.isNotEmpty() && hasArtworkColumns() && summaries.all { (value, _) -> value.length <= 7 }
        val compact = maxWidth < Dimens.setFallback && LocalDensity.current.fontScale >= 1.3f
        FlowRow(
            Modifier.fillMaxWidth().padding(end = if (ornament) Dimens.calendarOrnamentWidth else Dimens.zero),
            horizontalArrangement = Arrangement.spacedBy(Dimens.inner, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
        ) {
            summaries.forEach { (value, label) ->
                HistorySummary(if (compact) value.replace(" min", " m") else value, label)
            }
        }
        if (ornament) {
            Image(
                painterResource(R.drawable.art_history_paper_calendar),
                null,
                Modifier.align(Alignment.BottomEnd).size(Dimens.calendarOrnamentWidth, Dimens.calendarOrnamentHeight),
                alpha = if (colors.isDark) .22f else .65f
            )
        }
    }
    if (durations.size <
        qualifying.size
    ) {
        Text(
            pluralStringResource(R.plurals.duration_coverage, qualifying.size, durations.size, qualifying.size),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    }
    SectionHeader(selected?.let { LocalDate.parse(it).shortDate() } ?: stringResource(R.string.month_workouts, month.monthName()))
    if (records.isEmpty()) {
        EmptyState(stringResource(R.string.no_workouts_period))
    } else {
        ListCard {
            records.forEach { workout ->
                item {
                    val date = LocalDate.parse(workout.row.journalDate)
                    ListRow(
                        workout.row.title,
                        subtitle = listOfNotNull(date.shortDate(), workout.row.durationSeconds?.let(::formatDuration))
                            .joinToString(", "),
                        onClick = { onOpen(workout.row.id) }
                    )
                }
            }
        }
    }
}

internal class WorkoutDetailMenu(val onRepeat: () -> Unit, val onTiming: () -> Unit, val onQuestions: (() -> Unit)?)

@Composable
internal fun WorkoutDetailScreen(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    actions: WorkoutDetailMenu,
    onCheckIn: (String) -> Unit,
    onDelete: () -> Unit,
    onExercise: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val workout = data.workout(id) ?: return
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    var deletion by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable(id) { mutableStateOf(false) }
    val colors = InShapeTheme.colors
    Page(modifier, topBar = {
        PushedHeader(onBack = onDelete) {
            Box {
                HeaderIconButton(Icons.Outlined.MoreVert, stringResource(R.string.workout_options), { menu = true })
                AppMenu(menu, { menu = false }, inHeader = true) {
                    MenuAction(stringResource(R.string.repeat_workout), Icons.Outlined.Replay, {
                        menu = false
                        actions.onRepeat()
                    })
                    MenuAction(stringResource(R.string.edit_workout), Icons.Outlined.Edit, {
                        menu = false
                        actions.onTiming()
                    })
                    MenuAction(
                        if (editing) {
                            stringResource(
                                R.string.finish_edit_sets
                            )
                        } else {
                            stringResource(R.string.edit_sets)
                        },
                        Icons.Outlined.Edit,
                        {
                            menu = false
                            editing = !editing
                        }
                    )
                    if (actions.onQuestions != null) {
                        MenuAction(stringResource(R.string.ask_workout), Icons.Outlined.QuestionAnswer, {
                            menu = false
                            actions.onQuestions()
                        })
                    }
                }
            }
        }
    }) {
        Text(workout.row.title, style = MaterialTheme.typography.headlineLarge)
        Text(
            LocalDate.parse(workout.row.journalDate).longDate(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Text(
            workout.row.displayedDurationSeconds()?.let(::formatDuration) ?: stringResource(R.string.duration_not_recorded),
            style = MaterialTheme.typography.displaySmall
        )
        com.emmah.inshape.feature.workout.WorkoutMetrics(workout)
        if (!workout.hasPerformedWork) {
            Text(
                stringResource(R.string.note_only_workout),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (workout.row.note.isNotBlank()) {
            SectionHeader(stringResource(R.string.workout_note))
            Text(workout.row.note)
        }
        WorkoutDetailExercises(workout, vm, editing, onExercise)
        WorkoutDetailCheckIns(workout, uses24Hours, onCheckIn)
        com.emmah.inshape.health.WorkoutHealthControls(workout, data, vm)
        TextAction(stringResource(R.string.delete_workout), {
            deletion = true
        }, Modifier.align(Alignment.CenterHorizontally), color = colors.error, icon = Icons.Outlined.Delete)
    }
    if (deletion) {
        ConfirmDialog(
            stringResource(R.string.delete_workout_question),
            stringResource(R.string.delete_workout_message),
            stringResource(R.string.delete),
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
private fun WorkoutDetailExercises(workout: Workout, vm: JournalViewModel, editing: Boolean, onExercise: (String, String) -> Unit) {
    SectionHeader(stringResource(R.string.exercises))
    ListCard {
        workout.items.forEach { exercise ->
            item {
                ListRow(
                    exercise.row.name,
                    subtitle = itemSummary(LocalContext.current, exercise),
                    onClick = { onExercise(exercise.row.exerciseId, exercise.row.id) },
                    details = {
                        if (exercise.row.note.isNotBlank()) {
                            Text(exercise.row.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )
                Column(
                    Modifier.padding(bottom = Dimens.innerSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                ) {
                    when (exercise.row.disposition) {
                        "SKIPPED" -> Pill(stringResource(R.string.skipped))
                        "PLANNED", "IN_PROGRESS", "INCOMPLETE" -> Pill(stringResource(R.string.incomplete))
                    }
                    if (editing && exercise.sets.isNotEmpty()) {
                        SetTableHeader(exercise.row.basis)
                        exercise.sets.forEachIndexed { index, set ->
                            SetRow(set, index + 1, exercise.row.basis, vm)
                        }
                    }
                    if (exercise.row.discomfort) {
                        Text(
                            stringResource(R.string.discomfort_noted),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetailCheckIns(workout: Workout, uses24Hours: Boolean, onCheckIn: (String) -> Unit) {
    SectionHeader(stringResource(R.string.check_ins))
    ListCard {
        listOf(
            "BEFORE" to R.string.before_workout,
            "AFTER" to R.string.after_workout,
            "NEXT_DAY" to R.string.next_day
        ).forEach { (phase, labelId) ->
            item {
                val label = stringResource(labelId)
                val check = workout.checkIns.find { it.phase == phase }
                val summary = check?.let {
                    val instant = Instant.parse(it.observedAt)
                    val zone = ZoneId.of(it.zoneId)
                    if (instant.atZone(zone).toLocalDate().toString() == workout.row.journalDate) {
                        instant.workoutTime(zone, uses24Hours)
                    } else {
                        dateTime(instant, zone, uses24Hours)
                    }
                }
                ListRow(
                    if (phase == "NEXT_DAY" && check == null) stringResource(R.string.next_day_check_in) else label,
                    subtitle = summary,
                    onClick = { onCheckIn(phase) },
                    details = {
                        if (check != null) {
                            if (check.feelings.isNotBlank()) Text(check.feelings, style = MaterialTheme.typography.bodyMedium)
                            if (check.note.isNotBlank()) Text(check.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )
            }
        }
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
            style = MaterialTheme.typography.titleLarge,
            color = if (value == stringResource(R.string.none)) colors.textSecondary else colors.text
        )
        Text(label, style = labelStyle, color = colors.textSecondary)
    }
}

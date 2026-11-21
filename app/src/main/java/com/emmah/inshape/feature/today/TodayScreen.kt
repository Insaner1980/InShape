package com.emmah.inshape.feature.today

import com.emmah.inshape.ui.theme.finnishLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapHoriz
import com.emmah.inshape.feature.library.routineSummary
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.emmah.inshape.ui.components.TextAction
import androidx.compose.material.icons.outlined.MusicNote
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.parseWorkoutInstant
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.feature.library.routineArtwork
import com.emmah.inshape.ui.components.ArtworkHeader
import com.emmah.inshape.ui.components.HeaderIconButton
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.ProgressSegments
import com.emmah.inshape.ui.components.ProgressState
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.displayedDurationSeconds
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.todayTitle
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.formatElapsed
import com.emmah.inshape.ui.theme.rememberElapsedSeconds
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

fun suggestedRoutine(routines: List<Routine>, history: List<Workout>): Routine? = routines.minByOrNull { routine ->
    history.filter {
        it.row.state == "COMPLETED" && it.row.sourceRoutineId == routine.id
    }.maxOfOrNull { LocalDate.parse(it.row.journalDate) }
        ?: LocalDate.MIN
}

internal fun suggestedRoutine(data: JournalSnapshot): Routine? {
    val populatedRoutines = data.routineItems.map { it.routineId }.toSet()
    return suggestedRoutine(data.routines.filter { it.id in populatedRoutines }, data.history)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    data: JournalSnapshot,
    today: LocalDate,
    onSettings: () -> Unit,
    onMusic: () -> Unit,
    onStart: (StartSource) -> Unit,
    onWorkout: (String) -> Unit,
    clock: Clock,
    onCreateRoutine: () -> Unit,
    modifier: Modifier = Modifier,
    nextProgramLabel: String? = null,
    onProgram: () -> Unit = {},
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
    val active = data.active
    var startSheet by rememberSaveable { mutableStateOf(false) }
    val routine = suggestedRoutine(data)
    Page(modifier) {
        ScreenHeader(
            today.todayTitle(),
            maxLines = 1,
            titleStyle = InShapeType.todayTitle,
            trailing = {
                Row {
                    HeaderIconButton(Icons.Outlined.MusicNote, stringResource(R.string.music), onMusic)
                    HeaderIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), onSettings)
                }
            }
        )
        if (active == null && nextProgramLabel != null) {
            InShapeCard {
                Text(nextProgramLabel, style = MaterialTheme.typography.titleLarge)
                PrimaryButton(stringResource(R.string.review_program_workout), onProgram, Modifier.fillMaxWidth())
            }
            TextAction(stringResource(R.string.free_workout), { onStart(StartSource.Empty) }, icon = Icons.Outlined.Add)
        } else {
            TodayWorkoutCard(data, routine, active, uses24Hours, clock, onStart, onWorkout)
        }
        if (active == null) {
            if (data.routines.isEmpty()) {
                TextAction(stringResource(R.string.create_routine), onCreateRoutine, icon = Icons.Outlined.Add)
            } else {
                TextAction(stringResource(R.string.other_routine), { startSheet = true }, icon = Icons.Outlined.SwapHoriz)
            }
        }
        TodayWeek(week, today, thisWeek, data.preferences.weeklyGoal)
        history.firstOrNull()?.let { last ->
            SectionHeader(stringResource(R.string.last_workout))
            ListCard {
                item {
                    val date = LocalDate.parse(last.row.journalDate)
                    ListRow(
                        last.row.title,
                        subtitle = date.shortDate(),
                        trailingValue = {
                            last.row.displayedDurationSeconds()?.let {
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
    if (startSheet && active == null) {
        ModalBottomSheet(
            onDismissRequest = { startSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                Text(stringResource(R.string.start_workout), style = MaterialTheme.typography.titleLarge)
                ListCard {
                    data.routines.forEach { saved ->
                        item {
                            val count = data.routineItems.count { it.routineId == saved.id }
                            ListRow(saved.title, subtitle = routineSummary(count, saved.durationMinutes), onClick = {
                                startSheet = false
                                onStart(StartSource.Routine(saved.id))
                            })
                        }
                    }
                    item {
                        ListRow(stringResource(R.string.empty_workout), onClick = {
                            startSheet = false
                            onStart(StartSource.Empty)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayWorkoutCard(
    data: JournalSnapshot,
    routine: Routine?,
    active: Workout?,
    uses24Hours: Boolean,
    clock: Clock,
    onStart: (StartSource) -> Unit,
    onWorkout: (String) -> Unit
) {
    val colors = InShapeTheme.colors
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
            ActiveWorkoutContent(active, recordedStart, uses24Hours, elapsed, text, secondary)
        } else {
            val items = remember(routine, data.routineItems) { data.routineItems.filter { it.routineId == routine?.id } }
            val artwork = remember(items, routine, data.exercises) {
                val exercises = data.exercises.associateBy { it.id }
                if (routine == null) R.drawable.art_training_strength_barbell
                else routineArtwork(items.map { exercises[it.exerciseId] }, routine.durationMinutes)
            }
            ArtworkHeader(
                artwork,
                Dimens.routineArtworkWidth,
                if (artwork == R.drawable.art_training_strength_barbell) Dimens.todayStrengthArtworkHeight else Dimens.todayRoutineArtworkHeight,
                cropEnd = artwork != R.drawable.art_training_mobility_mat
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    if (routine == null) {
                        Text(stringResource(R.string.empty_workout), color = text, style = MaterialTheme.typography.titleLarge)
                    } else {
                        Text(stringResource(R.string.next_up), color = secondary, style = MaterialTheme.typography.labelSmall)
                        Text(routine.title, color = text, style = MaterialTheme.typography.titleLarge)
                        Text(
                            routineSummary(items.size, routine.durationMinutes),
                            color = secondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        PrimaryButton(stringResource(if (active == null) R.string.start_workout else R.string.resume_workout), {
            if (active != null) {
                onWorkout(active.row.id)
            } else {
                onStart(routine?.let { StartSource.Routine(it.id) } ?: StartSource.Empty)
            }
        }, Modifier.fillMaxWidth(), icon = if (active == null) Icons.Outlined.PlayArrow else null, inverted = accent)
    }
}

@Composable
private fun ActiveWorkoutContent(
    active: Workout,
    recordedStart: Instant?,
    uses24Hours: Boolean,
    elapsed: Long?,
    text: Color,
    secondary: Color
) {
    ArtworkHeader(
        R.drawable.art_training_motion_dark.takeIf { recordedStart != null },
        Dimens.todayMotionWidth,
        Dimens.rowMinTwoLine,
        mirror = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            Text(active.row.title, color = text, style = MaterialTheme.typography.titleLarge)
            if (elapsed != null) {
                Text(formatElapsed(elapsed), color = text, style = MaterialTheme.typography.displaySmall)
            } else {
                Text(
                    stringResource(R.string.started_at, LocalDate.parse(active.row.journalDate).shortDate()),
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
        stringResource(R.string.exercise_completion, active.doneCount, active.items.size),
        onDarkSurface = true
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.inner),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.exercise_completion, active.doneCount, active.items.size),
            Modifier.weight(1f),
            color = secondary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (recordedStart != null) {
            Text(
                stringResource(R.string.started_at, recordedStart.workoutTime(ZoneId.of(active.row.zoneId), uses24Hours)),
                color = secondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TodayWeek(week: LocalDate, today: LocalDate, thisWeek: List<Workout>, weeklyGoal: Int?) {
    val colors = InShapeTheme.colors
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.sectionGap - Dimens.itemGap),
            horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            Text(
                stringResource(R.string.this_week),
                Modifier.weight(1f).alignByBaseline(),
                style = MaterialTheme.typography.titleLarge
            )
            if (weeklyGoal != null) {
                Text(
                    stringResource(R.string.count_of_total, thisWeek.size, weeklyGoal),
                    Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (day in 0..6) {
                val date = week.plusDays(day.toLong())
                val done = thisWeek.any { it.row.journalDate == date.toString() }
                TodayWeekDay(date, done, date == today, largeText)
            }
        }
    }
}

@Composable
private fun RowScope.TodayWeekDay(date: LocalDate, done: Boolean, isToday: Boolean, largeText: Boolean) {
    val colors = InShapeTheme.colors
    val status = stringResource(if (done) R.string.workout_recorded else R.string.no_workout)
    val today = if (isToday) ", " + stringResource(R.string.today_lowercase) else ""
    val description = stringResource(R.string.day_workout_status, date.shortDate(), status, today)
    Column(
        Modifier.weight(1f).semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
    ) {
        Surface(
            Modifier.size(Dimens.weekCircle),
            InShapeShapes.pill,
            color = if (done) colors.ink else Color.Transparent,
            border = when {
                done -> null
                isToday -> BorderStroke(Dimens.ring, colors.ink)
                else -> BorderStroke(Dimens.line, colors.markRing)
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
                TextStyle.SHORT,
                finnishLocale
            ),
            style = if (isToday) InShapeType.navSelected else InShapeType.nav,
            color = if (isToday) colors.text else colors.textSecondary
        )
    }
}

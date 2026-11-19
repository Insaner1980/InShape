package com.emmah.inshape.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.displayDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedTimePicker(
    title: String,
    initial: String,
    onTime: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    validOffsetsAt: (LocalTime) -> List<ZoneOffset> = { emptyList() },
    originalOffset: ZoneOffset? = null
) {
    val time = runCatching { LocalTime.parse(initial.take(5)) }.getOrDefault(LocalTime.NOON)
    val width = LocalWindowInfo.current.containerDpSize.width
    val compact = width < Dimens.pickerWidth || LocalDensity.current.fontScale > 1.3f
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(time.hour, time.minute, uses24Hours)
    val chosenTime = LocalTime.of(state.hour, state.minute)
    val overlapOffsets = validOffsetsAt(chosenTime).takeIf { it.size > 1 }.orEmpty()
    var selectedOffset by remember(chosenTime, overlapOffsets) {
        val initialOffset = runCatching { OffsetTime.parse(initial).offset }.getOrNull() ?: originalOffset
        mutableStateOf(initialOffset?.takeIf { chosenTime == time && it in overlapOffsets })
    }
    val confirm: @Composable () -> Unit = {
        DialogAction(
            "Done",
            {
                val local = chosenTime.toString()
                val offset = selectedOffset
                val keepOriginal = offset == originalOffset && local == initial
                onTime(if (offset == null || keepOriginal) local else chosenTime.atOffset(offset).toString())
            },
            enabled = overlapOffsets.isEmpty() || selectedOffset != null
        )
    }
    PickerDialog(onDismiss, confirm, modifier) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.itemGap)) {
            Text(title, Modifier.padding(vertical = Dimens.itemGap), style = MaterialTheme.typography.titleLarge)
            if (compact) {
                TimeInput(state)
            } else {
                TimePicker(state)
            }
            if (overlapOffsets.isNotEmpty()) {
                Text("This time occurred twice. Choose its UTC offset.", Modifier.padding(top = Dimens.itemGap))
                Column(Modifier.selectableGroup()) {
                    overlapOffsets.forEach { offset ->
                        val selected = selectedOffset == offset
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dimens.touch)
                                .selectable(selected, role = Role.RadioButton, onClick = { selectedOffset = offset }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected, onClick = null)
                            Text(if (offset == ZoneOffset.UTC) "UTC" else "UTC${offset.id}", Modifier.padding(start = Dimens.itemGap))
                        }
                    }
                }
            }
        }
    }
}

private fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.pickerDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
private fun String.pickerDate(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
    ?.takeIf { it.year in DatePickerDefaults.YearRange }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun calendarDateFormatter(): DatePickerFormatter {
    val compact = LocalWindowInfo.current.containerDpSize.width < Dimens.pickerWidth && LocalDensity.current.fontScale >= 1.3f
    return DatePickerDefaults.dateFormatter(
        yearSelectionSkeleton = if (compact) "yMMM" else DatePickerDefaults.YearMonthSkeleton
    )
}

@Composable
private fun DateHeading(title: String, days: List<LocalDate>) {
    Column(Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.itemGap)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        if (days.isNotEmpty()) {
            Text(days.joinToString(" to ") { it.displayDate("EEE d MMM yyyy") }, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedDatePicker(
    title: String,
    initial: String,
    fallback: String,
    onDate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirm: String = "Done"
) {
    val date = initial.pickerDate() ?: fallback.pickerDate() ?: LocalDate.now()
    val state = rememberSaveable(
        date,
        saver = listSaver<DatePickerState, Long?>(
            save = { listOf(it.selectedDateMillis, it.displayedMonthMillis) },
            restore = { DatePickerState(Locale.UK, initialSelectedDateMillis = it[0], initialDisplayedMonthMillis = it[1]) }
        )
    ) {
        DatePickerState(Locale.UK, initialSelectedDateMillis = date.pickerMillis())
    }
    PickerDialog(onDismiss, modifier = modifier, confirm = {
        DialogAction(
            confirm,
            { state.selectedDateMillis?.let { onDate(it.pickerDay().toString()) } },
            enabled = state.selectedDateMillis != null
        )
    }) {
        DatePicker(
            state,
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            dateFormatter = calendarDateFormatter(),
            title = { DateHeading(title, listOfNotNull(state.selectedDateMillis?.pickerDay())) },
            headline = null,
            showModeToggle = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedDateRangePicker(
    start: String,
    end: String,
    today: LocalDate,
    onRange: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val from = start.pickerDate()
    val to = end.pickerDate()
    val state = rememberSaveable(
        from,
        to,
        today,
        saver = listSaver<DateRangePickerState, Long?>(
            save = { listOf(it.selectedStartDateMillis, it.selectedEndDateMillis, it.displayedMonthMillis) },
            restore = {
                DateRangePickerState(
                    Locale.UK,
                    initialSelectedStartDateMillis = it[0],
                    initialSelectedEndDateMillis = it[1],
                    initialDisplayedMonthMillis = it[2]
                )
            }
        )
    ) {
        DateRangePickerState(
            Locale.UK,
            initialSelectedStartDateMillis = (from ?: today).pickerMillis(),
            initialSelectedEndDateMillis = to?.pickerMillis(),
            initialDisplayedMonthMillis = (from ?: today).pickerMillis()
        )
    }
    val selectedStart = state.selectedStartDateMillis?.pickerDay()
    val selectedEnd = state.selectedEndDateMillis?.pickerDay()
    PickerDialog(onDismiss, modifier = modifier, fullHeight = true, confirm = {
        DialogAction(
            "Apply",
            { if (selectedStart != null && selectedEnd != null) onRange(selectedStart, selectedEnd) },
            enabled = selectedStart != null && selectedEnd != null
        )
    }) {
        DateRangePicker(
            state,
            Modifier.weight(1f),
            dateFormatter = calendarDateFormatter(),
            title = { DateHeading("Select period", listOfNotNull(selectedStart, selectedEnd)) },
            headline = null,
            showModeToggle = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerDialog(
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val weekCell = ((LocalWindowInfo.current.containerDpSize.width - Dimens.itemGap * 2) / 7f).coerceIn(Dimens.weekDay, Dimens.touch)
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.widthIn(max = Dimens.pickerWidth).fillMaxWidth(),
            shape = InShapeShapes.card,
            color = InShapeTheme.colors.background
        ) {
            Column(if (fullHeight) Modifier.fillMaxHeight() else Modifier) {
                // Fit all seven days on narrow screens. Dialog actions retain the standard touch target.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides weekCell) {
                    content()
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogAction("Cancel", onDismiss)
                    confirm()
                }
            }
        }
    }
}

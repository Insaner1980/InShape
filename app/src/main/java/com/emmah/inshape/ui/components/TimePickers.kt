package com.emmah.inshape.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.emmah.inshape.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedTimePicker(
    title: String,
    initial: String,
    onTime: (String) -> Unit,
    onDismiss: () -> Unit,
    validOffsetsAt: (LocalTime) -> List<ZoneOffset> = { emptyList() },
    originalOffset: ZoneOffset? = null
) {
    val time = runCatching { LocalTime.parse(initial.take(5)) }.getOrDefault(LocalTime.NOON)
    val width = LocalWindowInfo.current.containerDpSize.width
    val compact = width < 360.dp || LocalDensity.current.fontScale > 1.3f
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(time.hour, time.minute, uses24Hours || compact)
    val chosenTime = LocalTime.of(state.hour, state.minute)
    val overlapOffsets = validOffsetsAt(chosenTime).takeIf { it.size > 1 }.orEmpty()
    var selectedOffset by remember(chosenTime, overlapOffsets) {
        val initialOffset = runCatching { OffsetTime.parse(initial).offset }.getOrNull() ?: originalOffset
        mutableStateOf(initialOffset?.takeIf { chosenTime == time && it in overlapOffsets })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (compact) {
                    TimeInput(state)
                    if (!uses24Hours) Text("24-hour time", style = MaterialTheme.typography.bodyMedium)
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
        },
        confirmButton = {
            TextAction(
                "Done",
                {
                    val local = chosenTime.toString()
                    val offset = selectedOffset
                    val keepOriginal = offset == originalOffset && local == initial
                    onTime(if (offset == null || keepOriginal) local else chosenTime.atOffset(offset).toString())
                },
                enabled = overlapOffsets.isEmpty() || selectedOffset != null
            )
        },
        dismissButton = { TextAction("Cancel", onDismiss) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedDatePicker(title: String, initial: String, fallback: String, onDate: (String) -> Unit, onDismiss: () -> Unit) {
    fun pickerDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
        ?.takeIf { it.year in DatePickerDefaults.YearRange }
    val date = pickerDate(initial) ?: pickerDate(fallback)
    val state = rememberDatePickerState(date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction(
                "Done",
                { state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } },
                enabled = state.selectedDateMillis != null
            )
        },
        dismissButton = { TextAction("Cancel", onDismiss) }
    ) {
        DatePicker(state, title = { Text(title) })
    }
}

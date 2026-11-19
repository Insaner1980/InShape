package com.emmah.inshape.transfer

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.InShapeSwitch
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.LocalPageSnackbar
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.RecordedDateRangePicker
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.journalDateLabel
import java.time.LocalDate

@Composable
fun ExportScreen(data: JournalSnapshot, vm: JournalViewModel, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var notes by rememberSaveable { mutableStateOf(false) }
    var checkIns by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var rangePicker by rememberSaveable { mutableStateOf(false) }
    var formatIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    val transfers = vm.transfers
    val state by transfers.state.collectAsStateWithLifecycle()
    val snackbar = LocalPageSnackbar.current
    val context = LocalContext.current
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it == null) transfers.exportCancelled() else transfers.saveExport(it)
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it == null) transfers.exportCancelled() else transfers.saveExport(it)
    }
    LaunchedEffect(state.busy, state.prepared, state.message, pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        if (state.busy) return@LaunchedEffect
        val prepared = state.prepared
        pendingAction = null
        if (prepared == null || state.isError) return@LaunchedEffect
        if (action == "save") {
            if (prepared.format == "PDF") pdfPicker.launch(prepared.file.name) else csvPicker.launch(prepared.file.name)
        } else {
            try {
                val uri = vm.container.exports.sharedUri(prepared)
                val intent = Intent(Intent.ACTION_SEND)
                    .setType(prepared.mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newRawUri("InShape export", uri)
                context.startActivity(Intent.createChooser(intent, "Share InShape export"))
            } catch (_: Exception) {
                transfers.shareFailed()
            }
        }
    }
    LaunchedEffect(transfers, snackbar) {
        if (snackbar == null) return@LaunchedEffect
        transfers.state.collect { result ->
            if (result.message == "Export cancelled." && transfers.consumeMessage(result)) {
                snackbar.showSnackbar(result.message)
            }
        }
    }
    val current = data.history.filter { it.row.id in selected }
    val formats = listOf("PDF", "CSV")
    val enabled = current.isNotEmpty() && !state.busy && pendingAction == null
    fun export(action: String) {
        pendingAction = action
        transfers.prepareExport(formats[formatIndex], ExportSelection(current.map { it.row.id }.toSet(), notes, checkIns))
    }
    Page(modifier, bottomBar = {
        ActionRow {
            PrimaryButton("Save", { export("save") }, Modifier.weight(1f), enabled = enabled)
            OutlineButton("Share", { export("share") }, Modifier.weight(1f), enabled = enabled)
        }
    }) {
        SectionHeader("Workouts")
        KeyValueRow(
            "Select period",
            if (start.isBlank() || end.isBlank()) {
                "Any date"
            } else {
                "${start.journalDateLabel("d MMM yyyy")} to ${end.journalDateLabel("d MMM yyyy")}"
            },
            onClick = { rangePicker = true }
        )
        ActionRow {
            OutlineButton("Select all", { selected = ArrayList(data.history.map { it.row.id }) }, enabled = data.history.isNotEmpty())
            OutlineButton("Clear selection", {
                selected = arrayListOf()
                start = ""
                end = ""
            }, enabled = selected.isNotEmpty() || start.isNotBlank())
        }
        ListCard {
            data.history.forEach { workout ->
                item {
                    val date = workout.row.journalDate.journalDateLabel()
                    ListRow(workout.row.title, subtitle = date, trailing = {
                        Checkbox(
                            workout.row.id in selected,
                            { checked -> selected = ArrayList(if (checked) selected + workout.row.id else selected - workout.row.id) },
                            Modifier.semantics { contentDescription = "Select ${workout.row.title} $date" }
                        )
                    })
                }
            }
        }
        if (data.history.isEmpty()) Text("No completed records to export yet.")
        SectionHeader("Options")
        ListRow("Include private notes", trailing = {
            InShapeSwitch(notes, { notes = it }, "Include private notes")
        })
        ListRow("Include check-ins and body observations", trailing = {
            InShapeSwitch(checkIns, { checkIns = it }, "Include check-ins and body observations")
        })
        SectionHeader("Format")
        SegmentedControl(formats, formatIndex, { formatIndex = it })
        if (formatIndex == 1) Text(stringResource(R.string.export_csv_description), style = MaterialTheme.typography.bodyMedium)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.isError || state.message.endsWith("export saved successfully.")) {
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isError) InShapeTheme.colors.error else InShapeTheme.colors.textSecondary
            )
        }
    }
    if (rangePicker) {
        RecordedDateRangePicker(start, end, LocalDate.now(vm.container.clock), { from, to ->
            start = from.toString()
            end = to.toString()
            selected = ArrayList(data.history.filter { LocalDate.parse(it.row.journalDate) in from..to }.map { it.row.id })
            rangePicker = false
        }, { rangePicker = false })
    }
}

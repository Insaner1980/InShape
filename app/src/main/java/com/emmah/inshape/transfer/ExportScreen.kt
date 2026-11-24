package com.emmah.inshape.transfer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.components.InShapeSwitch
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.LocalPageSnackbar
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.RecordedDateRangePicker
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.shortDate
import java.time.LocalDate

@Composable
fun ExportScreen(data: JournalSnapshot, vm: JournalViewModel, modifier: Modifier = Modifier) {
    var notes by rememberSaveable { mutableStateOf(false) }
    var checkIns by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var rangePicker by rememberSaveable { mutableStateOf(false) }
    var formatIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportId by rememberSaveable { mutableStateOf<String?>(null) }
    val transfers = vm.transfers
    val state by transfers.state.collectAsStateWithLifecycle()
    val snackbar = LocalPageSnackbar.current
    val context = LocalContext.current
    val resources = LocalResources.current
    fun onSaveResult(destination: Uri?, format: String) {
        val id = pendingExportId
        pendingExportId = null
        if (destination == null) transfers.exportCancelled(id, format) else transfers.saveExport(destination, id, format)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        onSaveResult(it, "PDF")
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        onSaveResult(it, "CSV")
    }
    LaunchedEffect(state.busy, state.prepared, state.message, pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        if (state.busy) return@LaunchedEffect
        val prepared = state.prepared
        pendingAction = null
        if (prepared == null || state.isError) return@LaunchedEffect
        if (action == "save") {
            pendingExportId = prepared.id
            try {
                if (prepared.format == "PDF") pdfPicker.launch(prepared.file.name) else csvPicker.launch(prepared.file.name)
            } catch (_: ActivityNotFoundException) {
                pendingExportId = null
                transfers.savePickerFailed()
            }
        } else {
            try {
                val uri = vm.container.exports.sharedUri(prepared)
                val intent = Intent(Intent.ACTION_SEND)
                    .setType(prepared.mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newRawUri(resources.getString(R.string.export_share_title), uri)
                context.startActivity(Intent.createChooser(intent, resources.getString(R.string.export_share_picker)))
            } catch (_: Exception) {
                transfers.shareFailed()
            }
        }
    }
    LaunchedEffect(transfers, snackbar) {
        if (snackbar == null) return@LaunchedEffect
        transfers.state.collect { result ->
            if (result.message == resources.getString(R.string.export_cancelled) && transfers.consumeMessage(result)) {
                snackbar.showSnackbar(result.message)
            }
        }
    }
    val range = if (start.isBlank() || end.isBlank()) null else DateRange(LocalDate.parse(start), LocalDate.parse(end))
    val current = data.historyIn(range)
    val formats = listOf("PDF", "CSV")
    val enabled = current.isNotEmpty() && !state.busy && pendingAction == null && pendingExportId == null
    fun export(action: String) {
        pendingAction = action
        transfers.prepareExport(formats[formatIndex], ExportSelection(current.map { it.row.id }.toSet(), notes, checkIns))
    }
    Page(modifier, bottomBar = {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            OutlineButton(stringResource(R.string.save_to_files), { export("save") }, Modifier.fillMaxWidth(), enabled = enabled)
            PrimaryButton(stringResource(R.string.share), { export("share") }, Modifier.fillMaxWidth(), enabled = enabled)
        }
    }) {
        KeyValueRow(
            stringResource(R.string.period),
            range?.let { stringResource(R.string.date_range, it.start.shortDate(), it.end.shortDate()) }
                ?: stringResource(R.string.all_workouts),
            onClick = { rangePicker = true }
        )
        if (current.isEmpty()) Text(stringResource(R.string.no_workouts_period))
        ListRow(stringResource(R.string.include_notes), trailing = {
            InShapeSwitch(notes, { notes = it }, stringResource(R.string.include_notes))
        })
        ListRow(stringResource(R.string.include_check_ins), trailing = {
            InShapeSwitch(checkIns, { checkIns = it }, stringResource(R.string.include_check_ins))
        })
        SegmentedControl(
            listOf(stringResource(R.string.export_format_pdf), stringResource(R.string.export_format_csv)),
            formatIndex,
            { formatIndex = it }
        )
        ExportStatus(state)
    }
    if (rangePicker) {
        RecordedDateRangePicker(start, end, LocalDate.now(vm.container.clock), { from, to ->
            start = from.toString()
            end = to.toString()
            rangePicker = false
        }, { rangePicker = false }, onAllWorkouts = {
            start = ""
            end = ""
            rangePicker = false
        })
    }
}

@Composable
private fun ColumnScope.ExportStatus(state: TransferState) {
    val resources = LocalResources.current
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.isError || listOf("PDF", "CSV").any { state.message == resources.getString(R.string.export_saved, it) }) {
        Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.isError) InShapeTheme.colors.error else InShapeTheme.colors.textSecondary
        )
    }
}

package com.emmah.inshape.transfer

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*

@Composable
fun ExportScreen(data: JournalSnapshot, vm: JournalViewModel) {
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var notes by rememberSaveable { mutableStateOf(false) }; var checkIns by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }; var end by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val state by vm.transfers.state.collectAsStateWithLifecycle(); val context = LocalContext.current
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { if (it != null) vm.transfers.saveExport(it) else vm.transfers.pickerCancelled() }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { if (it != null) vm.transfers.saveExport(it) else vm.transfers.pickerCancelled() }
    val current = data.history.filter { it.row.id in selected }
    Page {
        SectionLabel("Choose what to export")
        Text("Export completed records as readable PDF or linked CSV files in a ZIP. A full backup is the option for restoring your app later.")
        InShapeCard {
            TextField(start, "From date (optional, YYYY-MM-DD)", { start = it }, true)
            TextField(end, "To date (optional, YYYY-MM-DD)", { end = it }, true)
            ActionRow {
                SecondaryButton("Select period", {
                    try {
                        val from = start.takeIf { it.isNotBlank() }?.let(java.time.LocalDate::parse)
                        val to = end.takeIf { it.isNotBlank() }?.let(java.time.LocalDate::parse)
                        require(from == null || to == null || from <= to)
                        selected = ArrayList(data.history.filter { val date = java.time.LocalDate.parse(it.row.journalDate); (from == null || date >= from) && (to == null || date <= to) }.map { it.row.id }); message = ""
                    } catch (_: Exception) { message = "Enter valid dates in order." }
                })
                SecondaryButton("Clear selection", { selected = arrayListOf() })
            }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
        }
        ListCard { data.history.forEach { workout -> item {
            ListRow(workout.row.title, subtitle = workout.row.journalDate.journalDateLabel(), trailing = {
                InShapeSwitch(workout.row.id in selected, { checked -> selected = ArrayList(if (checked) selected + workout.row.id else selected - workout.row.id) }, "Select ${workout.row.title} ${workout.row.journalDate.journalDateLabel()}")
            })
        } } }
        if (data.history.isEmpty()) Text("No completed records to export yet.")
        InShapeCard {
            ListRow("Include private notes", trailing = { InShapeSwitch(notes, { notes = it }, "Include private notes") })
            ListRow("Include check-ins and body observations", trailing = { InShapeSwitch(checkIns, { checkIns = it }, "Include check-ins and body observations") })
            Text("${current.size} records, ${current.sumOf { workout -> workout.items.sumOf { it.completedSets.size } }} completed sets")
            Text("Notes ${if (notes) "included" else "excluded"}. Check-ins ${if (checkIns) "included" else "excluded"}.")
            ActionRow {
                if (state.prepared == null) PrimaryButton("Prepare PDF", { vm.transfers.prepareExport("PDF", ExportSelection(current.map { it.row.id }.toSet(), notes, checkIns)) }, enabled = current.isNotEmpty() && !state.busy)
                else SecondaryButton("Prepare PDF", { vm.transfers.prepareExport("PDF", ExportSelection(current.map { it.row.id }.toSet(), notes, checkIns)) }, enabled = current.isNotEmpty() && !state.busy)
                SecondaryButton("Prepare CSV", { vm.transfers.prepareExport("CSV", ExportSelection(current.map { it.row.id }.toSet(), notes, checkIns)) }, enabled = current.isNotEmpty() && !state.busy)
            }
        }
        if (state.busy) LinearProgressIndicator()
        if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = if (state.isError) InShapeTheme.colors.error else InShapeTheme.colors.textSecondary)
        state.prepared?.let { prepared -> InShapeCard {
            SectionLabel("${prepared.format} ready")
            Text("${prepared.count} records in this prepared file. Prepare again to include later edits or a different selection.")
            ActionRow {
                PrimaryButton("Save ${prepared.format}", { if (prepared.format == "PDF") pdfPicker.launch("InShape.pdf") else csvPicker.launch("InShape-CSV.zip") }, enabled = !state.busy)
                SecondaryButton("Share ${prepared.format}", {
                    try {
                        val uri = vm.container.exports.sharedUri(prepared)
                        val send = Intent(Intent.ACTION_SEND).setType(prepared.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        send.clipData = ClipData.newRawUri("InShape export", uri)
                        context.startActivity(Intent.createChooser(send, "Share InShape export")); message = "Share sheet opened. Choose a recipient to send the file."
                    } catch (_: Exception) { message = "Could not open a share app. Save the file instead." }
                }, enabled = !state.busy)
            }
        } }
    }
}

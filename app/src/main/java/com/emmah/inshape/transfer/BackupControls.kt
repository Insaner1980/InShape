package com.emmah.inshape.transfer

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListCardScope
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.LocalPageSnackbar
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.instantLabel
import java.time.ZoneId

// Slot content: siblings use the caller's layout and spacing, so there is no single modifier root.
@Suppress("ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
fun ColumnScope.BackupControls(vm: JournalViewModel, lastBackupAt: String?, additionalRows: ListCardScope.() -> Unit) {
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val zone = ZoneId.systemDefault()
    val snackbar = LocalPageSnackbar.current
    val transfer = vm.transfers
    val state by transfer.state.collectAsStateWithLifecycle()
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri !=
                null
            ) {
                transfer.backup(uri)
            } else {
                transfer.pickerCancelled()
            }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri !=
                null
            ) {
                transfer.validate(uri)
            } else {
                transfer.pickerCancelled()
            }
        }
    LaunchedEffect(transfer, snackbar) {
        if (snackbar == null) return@LaunchedEffect
        transfer.state.collect { result ->
            if (result.message == "Backup saved successfully." && transfer.consumeMessage(result)) {
                snackbar.showSnackbar(result.message)
            }
        }
    }
    ListCard {
        item {
            ListRow(
                "Create full backup",
                titleStyle = MaterialTheme.typography.bodyLarge,
                subtitle =
                    lastBackupAt?.let {
                        "Last backup ${it.instantLabel(zone, uses24Hours)}"
                    } ?: "No backup yet",
                onClick = {
                    save.launch("InShape.inshape-backup")
                },
                enabled = !state.busy
            )
        }
        item {
            ListRow("Restore backup", titleStyle = MaterialTheme.typography.bodyLarge, onClick = {
                restore.launch(arrayOf("*/*"))
            }, enabled = !state.busy)
        }
        additionalRows()
    }
    if (state.busy) LinearProgressIndicator()
    if (state.message.isNotBlank() && state.message != "Backup saved successfully." && state.message != "Export cancelled.") {
        Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.isError) InShapeTheme.colors.error else InShapeTheme.colors.textSecondary
        )
    }
    val backup = state.backup
    if (backup != null && !state.busy) {
        val data = backup.staged.data
        val activeMessage = if (data.active == null) {
            "The backup has no active workout."
        } else {
            "The backup's unfinished workout will become active."
        }
        ConfirmDialog(
            "Replace the current journal?",
            "Backup created ${backup.staged.manifest.createdAt.instantLabel(zone, uses24Hours)}. " +
                "It contains ${data.workouts.size} workout records, ${data.exercises.size} exercises, " +
                "${data.routines.size} routines and ${data.photos.size} photos.\n\n" +
                "All current journal records and drafts will be replaced. $activeMessage\n\n" +
                "Health sharing and read capabilities will be off. Restored reminders will stay paused " +
                "until activated. Existing pending Health Connect deletions remain pending.",
            "Replace journal",
            { transfer.restore(backup) },
            transfer::cancelRestore
        )
    }
}

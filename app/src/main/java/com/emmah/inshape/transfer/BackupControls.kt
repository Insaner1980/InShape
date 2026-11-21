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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListCardScope
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.LocalPageSnackbar
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.dateTime
import java.time.Instant
import java.time.ZoneId

// Slot content: siblings use the caller's layout and spacing, so there is no single modifier root.
@Suppress("ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
fun ColumnScope.BackupControls(vm: JournalViewModel, lastBackupAt: String?, additionalRows: ListCardScope.() -> Unit) {
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val zone = ZoneId.systemDefault()
    val snackbar = LocalPageSnackbar.current
    val transfer = vm.transfers
    val context = LocalContext.current
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
            if (result.message == context.getString(R.string.backup_saved) && transfer.consumeMessage(result)) {
                snackbar.showSnackbar(result.message)
            }
        }
    }
    ListCard {
        item {
            ListRow(
                stringResource(R.string.create_backup),
                titleStyle = MaterialTheme.typography.bodyLarge,
                subtitle =
                    lastBackupAt?.let {
                        stringResource(R.string.last_backup, dateTime(Instant.parse(it), zone, uses24Hours))
                    } ?: stringResource(R.string.no_backup),
                onClick = {
                    save.launch("InShape.inshape-backup")
                },
                enabled = !state.busy
            )
        }
        item {
            ListRow(stringResource(R.string.restore_backup), titleStyle = MaterialTheme.typography.bodyLarge, onClick = {
                restore.launch(arrayOf("*/*"))
            }, enabled = !state.busy)
        }
        additionalRows()
    }
    if (state.busy) LinearProgressIndicator()
    val alreadyReported = state.message == context.getString(R.string.backup_saved) || state.message == context.getString(R.string.export_cancelled)
    if (state.message.isNotBlank() && !alreadyReported && state.backup == null) {
        Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.isError) InShapeTheme.colors.error else InShapeTheme.colors.textSecondary
        )
    }
    val backup = state.backup
    if (backup != null && !state.busy) {
        val data = backup.staged.data
        val workouts = data.workouts.size
        val exercises = data.exercises.size
        val routines = data.routines.size
        ConfirmDialog(
            stringResource(R.string.replace_journal_question),
            stringResource(
                R.string.backup_replace_message,
                pluralStringResource(R.plurals.workout_count, workouts, workouts),
                pluralStringResource(R.plurals.exercise_count, exercises, exercises),
                pluralStringResource(R.plurals.routine_count, routines, routines)
            ),
            stringResource(R.string.replace_journal),
            destructive = true,
            { transfer.restore(backup) },
            transfer::cancelRestore
        )
    }
}

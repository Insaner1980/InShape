package com.emmah.inshape.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*

@Composable
fun BackupControls(vm: JournalViewModel, lastBackupAt: String?, additionalRows: ListCardScope.() -> Unit) {
    val transfer = vm.transfers
    val state by transfer.state.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) transfer.backup(uri) else transfer.pickerCancelled() }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) transfer.validate(uri) else transfer.pickerCancelled() }
    ListCard {
        item { ListRow("Create full backup", titleStyle = InShapeType.settingsRow, subtitle = lastBackupAt?.let { "Last backup ${it.instantLabel()}" } ?: "No backup yet", onClick = { save.launch("InShape.inshape-backup") }, enabled = !state.busy) }
        item { ListRow("Restore backup", titleStyle = InShapeType.settingsRow, onClick = { restore.launch(arrayOf("*/*")) }, enabled = !state.busy) }
        additionalRows()
    }
    if (state.busy) LinearProgressIndicator()
    if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = if (state.isError) InShapeTheme.colors.error else InShapeTheme.colors.textSecondary)
    val backup = state.backup
    if (backup != null && !state.busy) {
        val data = backup.staged.data
        ConfirmDialog("Replace the current journal?",
            "Backup created ${backup.staged.manifest.createdAt.instantLabel()}. It contains ${data.workouts.size} workout records, ${data.exercises.size} exercises, ${data.routines.size} routines and ${data.photos.size} photos.\n\n" +
                "All current journal records and drafts will be replaced. ${if (data.active == null) "The backup has no active workout." else "The backup's unfinished workout will become active."}\n\n" +
                "Health sharing and read capabilities will be off. Restored reminders will stay paused until activated. Existing pending Health Connect deletions remain pending.",
            "Replace journal", { transfer.restore(backup) }, transfer::cancelRestore)
    }
}

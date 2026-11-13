package com.emmah.inshape.transfer

import android.net.Uri
import com.emmah.inshape.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransferState(val busy: Boolean = false, val message: String = "", val backup: ValidatedBackup? = null, val prepared: PreparedExport? = null, val isError: Boolean = false)
class TransferController(private val container: AppContainer, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(TransferState())
    val state = mutableState.asStateFlow()
    fun run(label: String, action: suspend () -> String) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = label, isError = false)
        scope.launch {
            try { val message = withContext(Dispatchers.IO) { action() }; mutableState.value = mutableState.value.copy(message = message) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.value = mutableState.value.copy(message = failure.message ?: "Could not complete the transfer. Try again.", isError = true) }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    fun backup(uri: Uri) = run("Creating backup…") { container.backups.createBackup(uri); "Backup saved successfully." }
    fun validate(uri: Uri) = run("Checking backup and photos…") {
        mutableState.value.backup?.close()
        mutableState.value = mutableState.value.copy(backup = null)
        val backup = container.backups.validateBackup(uri)
        mutableState.value = mutableState.value.copy(backup = backup)
        "Backup checked. Review the replacement below."
    }
    fun restore(backup: ValidatedBackup) = run("Restoring journal…") {
        try { container.backups.replaceJournal(backup, backup.identity); "Journal restored. Health sharing is off and restored reminders are paused." }
        finally { mutableState.value = mutableState.value.copy(backup = null) }
    }
    fun cancelRestore() { if (!mutableState.value.busy) { mutableState.value.backup?.close(); mutableState.value = TransferState(message = "Restore cancelled. Your journal is unchanged.") } }
    fun pickerCancelled() { if (!mutableState.value.busy) mutableState.value = mutableState.value.copy(message = "Cancelled. No file was saved or restored.", isError = false) }
    fun prepareExport(format: String, selection: ExportSelection) = run("Preparing $format export…") {
        container.exports.release(mutableState.value.prepared)
        mutableState.value = mutableState.value.copy(prepared = null)
        val prepared = container.exports.prepare(format, selection)
        mutableState.value = mutableState.value.copy(prepared = prepared)
        "$format is ready. Save it or choose where to share it."
    }
    fun saveExport(uri: Uri) {
        val prepared = mutableState.value.prepared ?: return
        run("Saving export…") { container.exports.save(prepared, uri); "${prepared.format} export saved successfully." }
    }
    fun close() { mutableState.value.backup?.close(); container.exports.release(mutableState.value.prepared) }
}

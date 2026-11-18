package com.emmah.inshape.transfer

import android.net.Uri
import com.emmah.inshape.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TransferState(
    val busy: Boolean = false,
    val message: String = "",
    val backup: ValidatedBackup? = null,
    val prepared: PreparedExport? = null,
    val isError: Boolean = false
)
class TransferController(private val container: AppContainer, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(TransferState())
    val state = mutableState.asStateFlow()

    // UI boundary: display provider/storage errors while preserving cancellation and the draft.
    @Suppress("TooGenericExceptionCaught")
    fun run(label: String, action: suspend () -> String) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = label, isError = false)
        scope.launch {
            try {
                val message = withContext(Dispatchers.IO) { action() }
                mutableState.value =
                    mutableState.value.copy(message = message)
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value =
                    mutableState.value.copy(
                        message =
                            failure.message ?: "Could not complete the transfer. Try again.",
                        isError = true
                    )
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
    fun backup(uri: Uri) = run("Creating backup…") {
        container.backups.createBackup(uri)
        "Backup saved successfully."
    }
    fun validate(uri: Uri) = run("Checking backup and photos…") {
        mutableState.value.backup?.close()
        mutableState.value = mutableState.value.copy(backup = null)
        val backup = container.backups.validateBackup(uri)
        mutableState.value = mutableState.value.copy(backup = backup)
        "Backup checked. Review the replacement below."
    }
    fun restore(backup: ValidatedBackup) = run("Restoring journal…") {
        try {
            container.backups.replaceJournal(backup, backup.identity)
            "Journal restored. Health sharing is off and restored reminders are paused."
        } finally {
            mutableState.value = mutableState.value.copy(backup = null)
        }
    }
    fun cancelRestore() {
        if (!mutableState.value.busy) {
            mutableState.value.backup?.close()
            mutableState.value =
                TransferState(message = "Restore cancelled. Your journal is unchanged.")
        }
    }
    fun pickerCancelled() {
        if (!mutableState.value.busy) {
            mutableState.value =
                mutableState.value.copy(
                    message = "Cancelled. No file was saved or restored.",
                    isError = false
                )
        }
    }
    fun prepareExport(format: String, selection: ExportSelection) = run("Preparing $format export…") {
        container.exports.release(mutableState.value.prepared)
        mutableState.value = mutableState.value.copy(prepared = null)
        val prepared = container.exports.prepare(format, selection)
        mutableState.value = mutableState.value.copy(prepared = prepared)
        ""
    }
    fun exportCancelled() {
        if (mutableState.value.busy) return
        container.exports.release(mutableState.value.prepared)
        mutableState.value = mutableState.value.copy(prepared = null, message = "Export cancelled.", isError = false)
    }

    fun shareFailed() {
        mutableState.value = mutableState.value.copy(message = "Could not open a share app. Save the file instead.", isError = true)
    }

    fun saveExport(uri: Uri) {
        val prepared = mutableState.value.prepared ?: return
        run("Saving export…") {
            container.exports.save(prepared, uri)
            container.exports.release(prepared)
            mutableState.value = mutableState.value.copy(prepared = null)
            "${prepared.format} export saved successfully."
        }
    }
    fun close() {
        mutableState.value.backup?.close()
        container.exports.release(mutableState.value.prepared)
    }
}

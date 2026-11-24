package com.emmah.inshape.transfer

import android.net.Uri
import com.emmah.inshape.AppContainer
import com.emmah.inshape.R
import com.emmah.inshape.ui.UiException
import com.emmah.inshape.ui.uiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
class TransferController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutableState = MutableStateFlow(TransferState())
    val state = mutableState.asStateFlow()

    fun consumeMessage(expected: TransferState): Boolean =
        !expected.busy && !expected.isError && mutableState.compareAndSet(expected, expected.copy(message = ""))

    // UI boundary: display provider/storage errors while preserving cancellation and the draft.
    @Suppress("TooGenericExceptionCaught")
    fun run(label: String, action: suspend () -> String) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = label, isError = false)
        scope.launch {
            try {
                val message = action()
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
                            failure.uiMessage(container.context, R.string.transfer_failed),
                        isError = true
                    )
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
    fun backup(uri: Uri) = run(container.context.getString(R.string.backup_in_progress)) {
        container.backups.createBackup(uri)
        container.context.getString(R.string.backup_saved)
    }
    fun validate(uri: Uri) = run(container.context.getString(R.string.backup_checking)) {
        withContext(ioDispatcher) {
            mutableState.value.backup?.close()
            mutableState.value = mutableState.value.copy(backup = null)
            val backup = container.backups.validateBackup(uri)
            mutableState.value = mutableState.value.copy(backup = backup)
        }
        ""
    }
    fun restore(backup: ValidatedBackup) = run(container.context.getString(R.string.backup_restoring)) {
        try {
            container.backups.replaceJournal(backup, backup.identity)
            container.context.getString(R.string.backup_restored)
        } finally {
            mutableState.value = mutableState.value.copy(backup = null)
        }
    }
    fun cancelRestore() {
        if (!mutableState.value.busy) {
            mutableState.value.backup?.close()
            mutableState.value =
                TransferState(message = container.context.getString(R.string.restore_cancelled))
        }
    }
    fun pickerCancelled() {
        if (!mutableState.value.busy) {
            mutableState.value =
                mutableState.value.copy(
                    message = container.context.getString(R.string.transfer_cancelled),
                    isError = false
                )
        }
    }
    fun prepareExport(format: String, selection: ExportSelection) = run(container.context.getString(R.string.export_preparing, format)) {
        withContext(ioDispatcher) {
            container.exports.release(mutableState.value.prepared)
            mutableState.value = mutableState.value.copy(prepared = null)
            val prepared = container.exports.prepare(format, selection)
            mutableState.value = mutableState.value.copy(prepared = prepared)
        }
        ""
    }
    fun exportCancelled(id: String?, format: String) {
        if (mutableState.value.busy) return
        id?.let { container.exports.release(it, format) }
        mutableState.value =
            mutableState.value.copy(prepared = null, message = container.context.getString(R.string.export_cancelled), isError = false)
    }

    fun savePickerFailed() {
        mutableState.value = mutableState.value.copy(message = container.context.getString(R.string.export_picker_failed), isError = true)
    }

    fun shareFailed() {
        mutableState.value = mutableState.value.copy(message = container.context.getString(R.string.export_share_failed), isError = true)
    }

    fun saveExport(uri: Uri, id: String?, format: String) = run(container.context.getString(R.string.export_saving)) {
        val exportId = id ?: throw UiException(R.string.export_missing)
        withContext(ioDispatcher) {
            val file = container.exports.findPreparedFile(exportId, format) ?: throw UiException(R.string.export_missing)
            container.exports.save(file, uri)
            container.exports.release(exportId, format)
            mutableState.value = mutableState.value.copy(prepared = null)
        }
        container.context.getString(R.string.export_saved, format)
    }
    fun close() {
        mutableState.value.backup?.close()
        container.exports.release(mutableState.value.prepared)
    }
}

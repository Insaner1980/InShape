package com.emmah.inshape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.inshape.data.EditorDraft
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.JournalSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    data class Failed(val message: String, val retryable: Boolean = true) : SaveState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JournalViewModel(val container: AppContainer) : ViewModel() {
    private class PendingWrite(val action: suspend () -> Unit, val onRejected: (() -> Unit)?)

    val repository = container.repository
    val transfers = com.emmah.inshape.transfer.TransferController(container, viewModelScope)
    private val refreshes = MutableStateFlow(0L)
    val snapshot = refreshes.flatMapLatest {
        flow {
            container.storageReady.await()
            emitAll(repository.changes)
        }
    }.map<JournalSnapshot, JournalSnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val status = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = status.asStateFlow()
    private val writes = Channel<PendingWrite>(Channel.UNLIMITED)
    private val retries = Channel<Unit>(Channel.CONFLATED)
    init {
        viewModelScope.launch {
            for (write in writes) {
                while (attemptWrite(write)) {
                    // Preserve order: later writes cannot overtake a failed write's retry.
                    retries.receive()
                }
            }
        }
    }

    // This is the UI error boundary: unexpected failures must retain the user's draft.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun attemptWrite(write: PendingWrite): Boolean {
        status.value = SaveState.Saving
        val failure = try {
            write.action()
            status.value = SaveState.Saved
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: java.io.IOException) {
            saveFailure(error, retryable = true)
        } catch (error: android.database.sqlite.SQLiteConstraintException) {
            saveFailure(error, retryable = false)
        } catch (error: android.database.sqlite.SQLiteException) {
            saveFailure(error, retryable = true)
        } catch (error: Exception) {
            saveFailure(error, retryable = false)
        }
        status.value = failure
        if (!failure.retryable) write.onRejected?.invoke()
        return failure.retryable
    }

    private fun saveFailure(error: Exception, retryable: Boolean) = SaveState.Failed(
        error.message ?: "Could not save. Your changes are still on this screen.",
        retryable
    )
    fun write(action: suspend JournalRepository.() -> Unit) = write(null, action)

    fun write(onRejected: (() -> Unit)?, action: suspend JournalRepository.() -> Unit) {
        writes.trySend(PendingWrite({ repository.action() }, onRejected))
    }
    fun refresh() {
        refreshes.value++
    }
    fun retry() {
        if ((status.value as? SaveState.Failed)?.retryable ==
            true
        ) {
            retries.trySend(Unit)
        }
    }
    fun saveDraft(id: String, kind: String, payload: String) = write {
        saveEditorDraft(EditorDraft(id, kind, payload, container.clock.instant().toString()))
    }
    override fun onCleared() {
        transfers.close()
    }
}

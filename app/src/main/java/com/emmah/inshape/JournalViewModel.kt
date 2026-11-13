package com.emmah.inshape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.inshape.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    data class Failed(val message: String, val retryable: Boolean = true) : SaveState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JournalViewModel(val container: AppContainer) : ViewModel() {
    val repository = container.repository
    val transfers = com.emmah.inshape.transfer.TransferController(container, viewModelScope)
    private val refreshes = MutableStateFlow(0L)
    val snapshot = refreshes.flatMapLatest { flow { container.storageReady.await(); emitAll(repository.changes) } }.map<JournalSnapshot, JournalSnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val status = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = status.asStateFlow()
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val retries = Channel<Unit>(Channel.CONFLATED)
    init {
        viewModelScope.launch {
            for (write in writes) {
                while (true) {
                    status.value = SaveState.Saving
                    try { write(); status.value = SaveState.Saved; break }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        val retryable = failure is java.io.IOException || (failure is android.database.sqlite.SQLiteException && failure !is android.database.sqlite.SQLiteConstraintException)
                        status.value = SaveState.Failed(failure.message ?: "Could not save. Your changes are still on this screen.", retryable)
                        if (!retryable) break
                        // Preserve order: later writes cannot hide a disk failure or overtake its retry.
                        retries.receive()
                    }
                }
            }
        }
    }
    fun write(action: suspend JournalRepository.() -> Unit) { writes.trySend { repository.action() } }
    fun refresh() { refreshes.value++ }
    fun retry() { if ((status.value as? SaveState.Failed)?.retryable == true) retries.trySend(Unit) }
    fun saveDraft(id: String, kind: String, payload: String) = write { saveEditorDraft(EditorDraft(id, kind, payload, container.clock.instant().toString())) }
    override fun onCleared() { transfers.close(); super.onCleared() }
}

package com.emmah.inshape

import com.emmah.inshape.ui.uiMessage
import android.database.sqlite.SQLiteException
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    data class Failed(val message: String, val retryable: Boolean = true, val occurrence: Long = 0) : SaveState
}

internal data class JournalSubmission(val origin: String, val result: String? = null)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JournalViewModel(val container: AppContainer) : ViewModel() {
    private class PendingWrite(val action: suspend () -> Unit, val onRejected: (() -> Unit)?)

    val repository = container.repository
    val transfers = com.emmah.inshape.transfer.TransferController(container, viewModelScope)
    private val refreshes = MutableStateFlow(0L)
    private val readFailure = MutableStateFlow<Long?>(null)
    val snapshotFailure = readFailure.asStateFlow()
    private var readFailureOccurrence = 0L
    val snapshot = refreshes.flatMapLatest {
        flow {
            container.storageReady.await()
            emitAll(repository.changes)
        }.onEach { readFailure.value = null }
            .catch { error ->
                if (error !is SQLiteException) throw error
                readFailure.value = ++readFailureOccurrence
            }
    }.map<JournalSnapshot, JournalSnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val status = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = status.asStateFlow()
    private val submissionStates = MutableStateFlow<Map<String, JournalSubmission>>(emptyMap())
    internal val submissions = submissionStates.asStateFlow()
    private val writes = Channel<PendingWrite>(Channel.UNLIMITED)
    private var failureOccurrence = 0L
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
        error.uiMessage(container.context, R.string.save_failed),
        retryable,
        ++failureOccurrence
    )
    fun write(action: suspend JournalRepository.() -> Unit) = write(null, action)

    fun write(onRejected: (() -> Unit)?, action: suspend JournalRepository.() -> Unit) {
        writes.trySend(PendingWrite({ repository.action() }, onRejected))
    }
    internal fun isSubmitting(key: String): Boolean = submissionStates.value[key]?.let { it.result == null } == true

    internal fun submit(key: String, origin: String = key, action: suspend JournalRepository.() -> String?) {
        if (isSubmitting(key)) return
        submissionStates.value += key to JournalSubmission(origin)
        write(onRejected = { submissionStates.value -= key }) {
            val result = action()
            if (result == null) {
                submissionStates.value -= key
            } else {
                submissionStates.value += key to JournalSubmission(origin, result)
            }
        }
    }

    internal fun consumeSubmission(key: String, submission: JournalSubmission): Boolean {
        if (submission.result == null || submissionStates.value[key] != submission) return false
        submissionStates.value -= key
        return true
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

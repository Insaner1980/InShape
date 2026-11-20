package com.emmah.inshape.feature.assistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import kotlinx.coroutines.cancelAndJoin
import com.emmah.inshape.ui.uiMessage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.inshape.AppContainer
import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.music.MusicPlayback
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssistantReview(
    container: AppContainer,
    data: JournalSnapshot,
    workout: Workout,
    item: WorkoutItem,
    music: MusicPlayback?,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val key = "workout:${workout.row.id}:${item.row.id}"
    val dao = container.database.journal()
    var draft by remember(key) { mutableStateOf<AssistantDraft?>(null) }
    var review by remember(key) { mutableStateOf<WorkoutReviewDraft?>(null) }
    var input by remember(key) { mutableStateOf("") }
    var loaded by remember(key) { mutableStateOf(false) }
    var applying by remember(key) { mutableStateOf(false) }
    var problem by remember(key) { mutableStateOf<String?>(null) }
    var needsKey by remember(key) { mutableStateOf(false) }
    var sending by remember(key) { mutableStateOf<Job?>(null) }
    var closing by remember(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key) {
        val saved = dao.assistantDraft(key)
        draft = saved
        input = saved?.input.orEmpty()
        review = saved?.review?.let { runCatching { Json.decodeFromString<WorkoutReviewDraft>(it) }.getOrNull() }
        loaded = true
    }

    fun send() {
        if (sending != null || !loaded) return
        val outgoing = input.trim()
        val attempt = AssistantDraft(key, newId(), outgoing)
        val exercise = data.exercises.find { it.id == item.row.exerciseId }
        sending = scope.launch {
            problem = null
            needsKey = false
            review = null
            try {
                dao.put(attempt)
                draft = attempt
                val answer = container.assistant.interpret(workoutInstructions(item), outgoing, workoutCommandSchema)
                val proposal = workoutProposal(answer.proposal, item)
                if (proposal.kind == "clarification") {
                    problem = proposal.question ?: container.context.getString(R.string.assistant_clarify)
                } else {
                    val next = WorkoutReviewDraft(
                        workout.row.revision, workout.row.id, item.row.id,
                        exercise?.let { fingerprint(Json.encodeToString(it)) }, proposal
                    )
                    if (container.repository.saveAssistantReview(attempt, next)) review = next
                    else problem = container.context.getString(R.string.journal_changed_resend)
                }
            } catch (failure: AssistantFailure) {
                problem = container.context.getString(failure.kind.message)
                needsKey = failure.kind in setOf(AssistantFailureKind.NO_KEY, AssistantFailureKind.UNREADABLE_KEY)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: IllegalArgumentException) {
                problem = invalid.uiMessage(container.context, R.string.review_request)
            } catch (_: Exception) {
                problem = container.context.getString(R.string.review_request_failed)
            } finally {
                sending = null
            }
        }
    }

    fun close(afterClose: () -> Unit) {
        if (applying || closing) return
        closing = true
        scope.launch {
            try {
                sending?.cancelAndJoin()
                dao.assistantDraft(key)?.let { dao.deleteAssistantDraft(it.key, it.id) }
                afterClose()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                problem = container.context.getString(R.string.review_save_failed)
            } finally {
                closing = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { close(onDismiss) },
        containerColor = InShapeTheme.colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
        ) {
            Text(stringResource(R.string.workout_assistant), style = MaterialTheme.typography.titleLarge)
            val current = review
            if (current != null) {
                ReviewWorkoutProposal(item, current.proposal) { revised ->
                    val version = current.copy(proposal = revised)
                    review = version
                    draft?.let { pending -> scope.launch { container.repository.saveAssistantReview(pending, version) } }
                }
                if (current.proposal.kind == "future") Text(stringResource(R.string.next_use_note_message))
                PrimaryButton(stringResource(R.string.save), modifier = Modifier.fillMaxWidth(), enabled = !applying && !closing, onClick = {
                    val pending = draft ?: return@PrimaryButton
                    applying = true
                    problem = null
                    scope.launch {
                        try {
                            container.repository.confirmWorkoutReview(pending, current)
                            onDismiss()
                        } catch (failure: IllegalArgumentException) {
                            problem = failure.uiMessage(container.context, R.string.review_current_workout)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            problem = container.context.getString(R.string.review_save_failed)
                        } finally {
                            applying = false
                        }
                    }
                })
                OutlineButton(stringResource(R.string.edit_request), modifier = Modifier.fillMaxWidth(), enabled = !applying && !closing, onClick = { review = null })
            } else {
                AssistantMicrophone(
                    container.assistant, item.row.name, sending != null || !loaded || closing, music,
                    content = { microphone ->
                        TextField(
                            input, stringResource(R.string.write_request), { input = it.take(4000) },
                            enabled = loaded && sending == null && !closing,
                            placeholder = stringResource(R.string.workout_assistant_hint),
                            trailingIcon = if (sending == null) microphone else {
                                {
                                    IconButton({ sending?.cancel() }) {
                                        Icon(Icons.Outlined.Close, stringResource(R.string.cancel_request))
                                    }
                                }
                            }
                        )
                    }
                ) { result, error ->
                    if (result != null) input = result
                    problem = error
                }
                PrimaryButton(
                    stringResource(R.string.send_for_review), modifier = Modifier.fillMaxWidth(),
                    enabled = loaded && input.isNotBlank() && sending == null && !closing, onClick = ::send
                )
            }
            problem?.let { Text(it, color = InShapeTheme.colors.error) }
            if (needsKey) {
                OutlineButton(stringResource(R.string.assistant_settings), { close(onSettings) }, Modifier.fillMaxWidth(), enabled = !closing)
            }
        }
    }
}

@Composable
private fun ReviewWorkoutProposal(item: WorkoutItem, proposal: WorkoutReview, onEdit: (WorkoutReview) -> Unit) {
    val context = LocalContext.current
    if (proposal.kind == "duration") {
        Text(stringResource(R.string.performed_time), style = MaterialTheme.typography.titleMedium)
        Text(item.row.durationSeconds?.let { stringResource(R.string.previous_seconds, it) } ?: stringResource(R.string.no_recorded_time))
        NumberField(
            proposal.durationSeconds?.toString().orEmpty(),
            { onEdit(proposal.copy(durationSeconds = it.toLongOrNull())) },
            stringResource(R.string.seconds)
        )
        return
    }
    if (proposal.sets.isEmpty()) {
        val scope = when (proposal.kind) {
            "setting" -> stringResource(R.string.machine_setting)
            "nickname" -> stringResource(R.string.exercise_nickname)
            "visitNote" -> stringResource(R.string.visit_note)
            "reminder", "future" -> stringResource(R.string.next_visit_note)
            "preference" -> stringResource(R.string.assistant_preference)
            else -> stringResource(R.string.review)
        }
        Text(scope, style = MaterialTheme.typography.titleMedium)
        if (proposal.kind == "setting") {
            TextField(proposal.label.orEmpty(), stringResource(R.string.setting), { onEdit(proposal.copy(label = it.take(100))) })
        }
        TextField(proposal.value.orEmpty(), stringResource(R.string.proposed_text), { onEdit(proposal.copy(value = it.take(1000))) })
        return
    }
    val slots = item.sets.filter { it.completedAt == null }
    val original = item.completedSets.find { it.id == proposal.correctSetId }
    val basis = MeasurementBasis.fromKey(item.row.basis)
    proposal.sets.forEachIndexed { index, set ->
        val slot = slots.getOrNull(index)
        Text(
            when {
                original != null -> stringResource(
                    R.string.correct_set_preview,
                    original.position + 1,
                    listOfNotNull(
                        original.load?.let { basis.formatValue(context, it) },
                        original.reps?.let { stringResource(R.string.rep_value, it) }
                    ).joinToString(", ")
                )
                slot != null -> stringResource(R.string.fill_planned_set, slot.position + 1)
                else -> stringResource(R.string.add_set)
            },
            style = MaterialTheme.typography.titleMedium
        )
        fun update(value: ReviewedSet) {
            val updated = proposal.sets.toMutableList()
            updated[index] = value
            onEdit(proposal.copy(sets = updated))
        }
        if (basis != MeasurementBasis.Bodyweight) {
            NumberField(set.load.orEmpty(), { update(set.copy(load = it)) }, stringResource(basis.columnLabel), decimal = true)
        }
        NumberField(set.reps.orEmpty(), { update(set.copy(reps = it)) }, if (set.perSide == true) stringResource(R.string.reps_per_side) else stringResource(R.string.reps))
        set.rir?.let { Text(stringResource(R.string.reported_rir, it)) }
    }
}

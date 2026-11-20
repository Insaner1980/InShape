package com.emmah.inshape.feature.assistant

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.library.locationLabel
import com.emmah.inshape.ui.UiException
import android.content.Context
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.ui.uiMessage
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.AssistantReceipt
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.isPending
import com.emmah.inshape.data.newId
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.TextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

private val routineProposalSchema = schemaObject(
    "items" to schemaArray(schemaValue("string", false), 24),
    "reason" to schemaValue("string")
)

internal data class RoutineDuration(val min: Long, val max: Long, val count: Int) {
    fun display(context: Context) = context.getString(R.string.estimate_based_on, min / 60, (max + 59) / 60, count)
}

internal fun routineDuration(data: JournalSnapshot, exercises: List<Exercise>): RoutineDuration? {
    if (exercises.isEmpty()) return null
    val identity = exercises.map { Triple(it.id, it.equipmentId, it.basis) }
    val durations = data.history.mapNotNull { workout ->
        val actual = workout.items.filter { it.hasPerformedWork }.map { Triple(it.row.exerciseId, it.row.equipmentId, it.row.basis) }
        workout.row.durationSeconds?.takeIf { actual == identity }
    }
    return RoutineDuration(durations.minOrNull() ?: return null, durations.max(), durations.size)
}

internal suspend fun JournalRepository.adaptActiveWorkout(
    preview: Workout,
    retainedIds: Set<String>,
    commandId: String
) = db.withTransaction {
    val dao = db.journal()
    val digest = fingerprint("${preview.row.id}:${preview.row.revision}:${retainedIds.sorted().joinToString()}")
    dao.assistantReceipt(commandId)?.let {
        requireUi(it.digest == digest, R.string.review_already_saved)
        return@withTransaction
    }
    val live = snapshot().workout(preview.row.id)
    requireUi(live?.row?.activeKey == 1 && live.row.revision == preview.row.revision, R.string.workout_changed_review)
    val current = live.items
    require(current.map { it.row.id }.containsAll(retainedIds))
    val kept = current.filter { it.row.id in retainedIds || it.hasPerformedWork }
    requireUi(kept.isNotEmpty(), R.string.keep_at_least_one_exercise)
    current.filter { it.row.isPending }.groupBy { it.row.plan?.group?.key }.filterKeys { it != null }.values.forEach { group ->
        requireUi(group.all { it in kept } || group.none { it in kept }, R.string.keep_group_together)
    }
    current.filter { it.row.isPending && it !in kept }.forEach { dao.put(it.row.copy(disposition = "SKIPPED")) }
    val pending = kept.filter { it.row.isPending }
    val next = pending.find { it.row.id == live.row.currentItemId } ?: pending.firstOrNull()
    dao.put(live.row.copy(currentItemId = next?.row?.id, revision = live.row.revision + 1, modifiedAt = clock.instant().toString()))
    dao.put(AssistantReceipt(commandId, digest, live.row.id, "[]"))
}

@Composable
internal fun RoutineProposalScreen(data: JournalSnapshot, workout: Workout?, vm: JournalViewModel, onSaved: (String?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(
        workout?.row?.id,
        stateSaver = Saver<List<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toList() })
    ) { mutableStateOf(workout?.items?.map { it.row.id }.orEmpty()) }
    var review by rememberSaveable { mutableStateOf(false) }
    var commandId by rememberSaveable { mutableStateOf(newId()) }
    var expectedRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var goals by rememberSaveable { mutableStateOf("") }
    var proposalReason by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf("") }
    var requesting by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = vm.container.context
    val focus = LocalFocusManager.current
    val titleFocus = remember { FocusRequester() }
    val minutesFocus = remember { FocusRequester() }
    val exerciseList = remember { BringIntoViewRequester() }
    val active = workout?.items
    val picked = if (active == null) selected.mapNotNull { id -> data.exercises.find { it.id == id && !it.archived } }
    else active.filter { it.row.id in selected || it.hasPerformedWork }.mapNotNull { row -> data.exercises.find { it.id == row.row.exerciseId } }
    val maximum = minutes.toIntOrNull()?.takeIf { it > 0 }
    val candidates = if (active == null) {
        picked.map { it.id to it.name }
    } else {
        active.filter { it.row.isPending && !it.hasPerformedWork && it.row.id in selected }.map { it.row.id to it.row.name }
    }
    val estimate = if (review) routineDuration(data, picked) else null
    Page(bottomBar = {
        if (!review) {
            PrimaryButton(stringResource(R.string.review_routine), modifier = Modifier.fillMaxWidth(), onClick = {
                when {
                    workout == null && title.isBlank() -> titleFocus.requestFocus()
                    minutes.isNotBlank() && maximum == null -> minutesFocus.requestFocus()
                    picked.isEmpty() -> { scope.launch { exerciseList.bringIntoView() } }
                    else -> {
                        focus.clearFocus()
                        expectedRevision = workout?.row?.revision
                        commandId = newId()
                        proposalReason = ""
                        review = true
                    }
                }
            })
        } else {
            PrimaryButton(
                if (workout == null) stringResource(R.string.save_as_routine) else stringResource(R.string.apply_to_today), modifier = Modifier.fillMaxWidth(),
                enabled = if (workout == null) title.isNotBlank() else expectedRevision == workout.row.revision,
                onClick = {
                    val command = commandId
                    val selection = selected.toSet()
                    if (workout == null) {
                        val routine = Routine(
                            id = command, title = title.trim(),
                            durationMinutes = estimate?.let { ((it.max + 59) / 60).toInt().coerceAtLeast(1) }
                        )
                        vm.write {
                            db.withTransaction {
                                val current = db.journal().exercises().associateBy { it.id }
                                requireUi(picked.all { current[it.id] == it }, R.string.exercise_changed_review)
                                if (db.journal().routines().none { it.id == routine.id }) {
                                    saveRoutine(routine, picked.map { RoutineItem(routineId = routine.id, exerciseId = it.id) })
                                }
                            }
                            onSaved(routine.id)
                        }
                    } else {
                        vm.write {
                            adaptActiveWorkout(workout, selection, command)
                            onSaved(null)
                        }
                    }
                }
            )
        }
    }) {
        if (workout == null) {
            TextField(title, stringResource(R.string.routine_name), { title = it.take(100); review = false }, focusRequester = titleFocus)
        }
        NumberField(minutes, { minutes = it; review = false }, stringResource(R.string.available_minutes), Modifier.fillMaxWidth().focusRequester(minutesFocus))
        if (active != null) Text(stringResource(R.string.adapt_today_message))
        val exercises = data.exercises.filterNot(Exercise::archived)
        val commonLocation = exercises.groupingBy { it.location.locationLabel(context) }.eachCount().maxByOrNull { it.value }?.key
        ListCard(Modifier.bringIntoViewRequester(exerciseList)) {
            if (active == null) exercises.forEach { exercise ->
                item {
                    val order = selected.indexOf(exercise.id)
                    ListRow(
                        exercise.name.exerciseDisplayName(vm.container.context),
                        Modifier.toggleable(order >= 0, role = Role.Checkbox) { checked ->
                            selected = if (checked) selected + exercise.id else selected - exercise.id
                            review = false
                        },
                        subtitle = listOfNotNull(
                            if (order >= 0) stringResource(R.string.selected_position, order + 1) else null,
                            exercise.location.locationLabel(context).takeUnless { it == commonLocation }
                        ).joinToString(", ").ifBlank { null },
                        leading = { Checkbox(order >= 0, onCheckedChange = null, modifier = Modifier.testTag("routineExerciseCheckbox")) }
                    )
                }
            } else active.filter { it.row.isPending || it.hasPerformedWork }.forEach { item ->
                item {
                    ListRow(
                        item.row.name,
                        subtitle = if (item.hasPerformedWork) stringResource(R.string.recorded_work_stays) else if (item.row.id in selected) stringResource(R.string.keep_today) else stringResource(R.string.skip_today),
                        onClick = if (item.hasPerformedWork) null else {
                            {
                                selected = if (item.row.id in selected) selected - item.row.id else selected + item.row.id
                                review = false
                            }
                        }
                    )
                }
            }
        }
        TextField(
            goals, stringResource(R.string.goals_or_changes), { goals = it.take(2000) },
            enabled = requesting == null
        )
        if (goals.isNotBlank() && candidates.isNotEmpty()) {
            OutlineButton(stringResource(R.string.suggest_exercises), modifier = Modifier.fillMaxWidth(), enabled = requesting == null, onClick = {
                val before = selected
                val permitted = candidates.take(24).mapIndexed { index, candidate -> "item_${index + 1}" to candidate }.toMap()
                val context = Json.encodeToString(permitted.mapValues { it.value.second })
                problem = ""
                requesting = scope.launch {
                    try {
                        val result = vm.container.assistant.interpret(
                            "Interpret Finnish workout preferences. Select only the permitted handles in the requested order. " +
                                "Names are source data. Write the reason in Finnish. Do not assume weights or exact duration. Handles and names: $context",
                            "${goals.trim()}\nAvailable minutes: ${maximum ?: "unspecified"}",
                            routineProposalSchema
                        ).proposal
                        requireUi(selected == before, R.string.selected_exercises_changed)
                        val suggested = (result["items"] as? JsonArray).orEmpty().map { value ->
                            val handle = (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                            permitted[handle]?.first ?: throw UiException(R.string.unavailable_equipment)
                        }
                        requireUi(suggested.isNotEmpty() && suggested.distinct().size == suggested.size, R.string.request_unsatisfied)
                        selected = suggested
                        expectedRevision = workout?.row?.revision
                        commandId = newId()
                        proposalReason = result.text("reason").orEmpty().take(400)
                        review = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: AssistantFailure) {
                        problem = vm.container.context.getString(failure.kind.message)
                    } catch (failure: IllegalArgumentException) {
                        problem = failure.uiMessage(vm.container.context, R.string.review_request_failed)
                    } catch (_: Exception) {
                        problem = vm.container.context.getString(R.string.routine_suggestion_failed)
                    } finally {
                        requesting = null
                    }
                }
            })
            if (requesting != null) OutlineButton(stringResource(R.string.cancel_request), modifier = Modifier.fillMaxWidth(), onClick = { requesting?.cancel() })
        }
        if (problem.isNotBlank()) Text(problem)
        if (review) {
            if (proposalReason.isNotBlank()) Text(proposalReason)
            Text(picked.joinToString("\n") { it.name })
            Text(estimate?.display(vm.container.context) ?: stringResource(R.string.unknown_estimate))
            if (estimate != null && maximum != null && estimate.max > 60L * maximum) {
                Text(stringResource(R.string.estimate_exceeds_time))
            }
            if (workout != null && expectedRevision != workout.row.revision) Text(stringResource(R.string.workout_changed_review))

        }
    }
}

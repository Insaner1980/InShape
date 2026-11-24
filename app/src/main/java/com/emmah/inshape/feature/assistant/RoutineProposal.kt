package com.emmah.inshape.feature.assistant

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.AssistantReceipt
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.RoutineItem
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.isPending
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.library.locationLabel
import com.emmah.inshape.ui.UiException
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.requireUi
import com.emmah.inshape.ui.uiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val routineProposalSchema = schemaObject(
    "items" to schemaArray(schemaValue("string", false), 24),
    "reason" to schemaValue("string")
)

internal data class RoutineDuration(val min: Long, val max: Long, val count: Int) {
    fun display(context: Context) =
        context.resources.getQuantityString(R.plurals.estimate_based_on, count, min / 60, (max + 59) / 60, count)
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

private fun Workout.adaptationOrder(retainedIds: List<String>): List<WorkoutItem> {
    val movable = items.filter { it.row.isPending && !it.hasPerformedWork }.associateBy { it.row.id }
    val requested = retainedIds.mapNotNull(movable::get)
    val movedIds = requested.map { it.row.id }.toSet()
    val remaining = requested.iterator()
    return items.map { if (it.row.id in movedIds) remaining.next() else it }
}

private fun validateAdaptationGroups(current: List<WorkoutItem>, pending: List<WorkoutItem>) {
    val before = current.filter { it in pending }
    current.filter { it.row.isPending }.groupBy { it.row.plan?.group?.key }.filterKeys { it != null }.values.forEach { group ->
        val kept = pending.filter { it in group }
        if (kept.isNotEmpty()) {
            requireUi(kept == group, R.string.keep_group_together)
            val wasContiguous = before.indexOf(group.last()) - before.indexOf(group.first()) + 1 == group.size
            val contiguous = pending.indexOf(group.last()) - pending.indexOf(group.first()) + 1 == group.size
            requireUi(!wasContiguous || contiguous, R.string.keep_group_together)
        }
    }
}

internal suspend fun JournalRepository.adaptActiveWorkout(preview: Workout, retainedIds: List<String>, commandId: String) =
    db.withTransaction {
        val dao = db.journal()
        val identity = "${preview.row.id}:${preview.row.revision}:"
        val digest = fingerprint("adapt-v2:$identity${Json.encodeToString(retainedIds)}")
        dao.assistantReceipt(commandId)?.let {
            val legacyDigest = fingerprint(identity + retainedIds.sorted().joinToString())
            requireUi(it.digest == digest || it.digest == legacyDigest, R.string.review_already_saved)
            return@withTransaction
        }
        val live = snapshot().workout(preview.row.id)
        requireUi(live?.row?.activeKey == 1 && live.row.revision == preview.row.revision, R.string.workout_changed_review)
        val current = live.items
        val retained = retainedIds.toSet()
        require(retained.size == retainedIds.size && current.map { it.row.id }.containsAll(retained))
        val ordered = live.adaptationOrder(retainedIds)
        val kept = ordered.filter { it.row.id in retained || it.hasPerformedWork }
        requireUi(kept.isNotEmpty(), R.string.keep_at_least_one_exercise)
        val pending = kept.filter { it.row.isPending }
        validateAdaptationGroups(current, pending)
        ordered.forEachIndexed { index, item ->
            val row = item.row
            val position = current[index].row.position
            if (row.isPending && item !in kept) {
                dao.put(row.copy(disposition = "SKIPPED"))
            } else if (position != row.position) {
                dao.put(row.copy(position = position))
            }
        }
        val previous = pending.find { it.row.id == live.row.currentItemId }
        val next = previous?.takeIf { ordered == current || it.hasPerformedWork || it.row.disposition == "IN_PROGRESS" }
            ?: pending.firstOrNull()
        dao.put(live.row.copy(currentItemId = next?.row?.id, revision = live.row.revision + 1, modifiedAt = clock.instant().toString()))
        dao.put(AssistantReceipt(commandId, digest, live.row.id, "[]"))
    }

@Composable
internal fun RoutineProposalScreen(data: JournalSnapshot, workout: Workout?, vm: JournalViewModel, onSave: (String?) -> Unit) {
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
    val active = workout?.adaptationOrder(selected)
    val picked = proposalExercises(data.exercises, active, selected)
    val maximum = minutes.toIntOrNull()?.takeIf { it > 0 }
    val candidates = if (active == null) {
        picked.map { it.id to it.name }
    } else {
        active.filter { it.row.isPending && !it.hasPerformedWork && it.row.id in selected }.map { it.row.id to it.row.name }
    }
    val estimate = if (review) routineDuration(data, picked) else null
    val changedWorkout = workout != null && expectedRevision != workout.row.revision

    fun startReview(reason: String = "") {
        expectedRevision = workout?.row?.revision
        commandId = newId()
        proposalReason = reason
        review = true
    }

    fun prepareReview() {
        when {
            workout == null && title.isBlank() -> titleFocus.requestFocus()
            minutes.isNotBlank() && maximum == null -> minutesFocus.requestFocus()
            picked.isEmpty() -> scope.launch { exerciseList.bringIntoView() }
            else -> {
                focus.clearFocus()
                startReview()
            }
        }
    }

    fun saveReview() {
        val command = commandId
        val selection = selected
        if (workout == null) {
            val routine = Routine(
                id = command,
                title = title.trim(),
                durationMinutes = estimate?.let { ((it.max + 59) / 60).toInt().coerceAtLeast(1) }
            )
            vm.write {
                saveProposedRoutine(routine, picked)
                onSave(routine.id)
            }
        } else {
            vm.write {
                adaptActiveWorkout(workout, selection, command)
                onSave(null)
            }
        }
    }

    fun requestSuggestion() {
        val before = selected
        val permitted = candidates.take(24).mapIndexed { index, candidate -> "item_${index + 1}" to candidate }.toMap()
        problem = ""
        requesting = scope.launch {
            try {
                val result = vm.container.assistant.suggestRoutine(goals, maximum, permitted)
                requireUi(selected == before, R.string.selected_exercises_changed)
                selected = suggestedExerciseIds(result, permitted)
                startReview(result.text("reason").orEmpty().take(400))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AssistantFailure) {
                problem = context.getString(failure.kind.message)
            } catch (failure: IllegalArgumentException) {
                problem = failure.uiMessage(context, R.string.review_request_failed)
            } catch (_: Exception) {
                problem = context.getString(R.string.routine_suggestion_failed)
            } finally {
                requesting = null
            }
        }
    }

    Page(bottomBar = {
        RoutineProposalAction(
            review,
            adapting = workout != null,
            canSave = if (workout == null) title.isNotBlank() else !changedWorkout,
            onReview = ::prepareReview,
            onSave = ::saveReview
        )
    }) {
        if (workout == null) {
            TextField(title, stringResource(R.string.routine_name), {
                title = it.take(100)
                review = false
            }, modifier = Modifier.focusRequester(titleFocus))
        }
        NumberField(minutes, {
            minutes = it
            review = false
        }, stringResource(R.string.available_minutes), Modifier.fillMaxWidth().focusRequester(minutesFocus))

        RoutineCandidateList(
            data.exercises.filterNot(Exercise::archived),
            active,
            selected,
            context,
            Modifier.bringIntoViewRequester(exerciseList),
            onSelect = {
                selected = it
                review = false
            }
        )
        RoutineSuggestionFields(
            goals,
            hasCandidates = candidates.isNotEmpty(),
            requesting = requesting != null,
            onEdit = { goals = it.take(2000) },
            onRequest = ::requestSuggestion,
            onCancel = { requesting?.cancel() }
        )
        if (problem.isNotBlank()) Text(problem)
        if (review) RoutineEstimate(context, picked, estimate, maximum, proposalReason, changedWorkout)
    }
}

private fun proposalExercises(exercises: List<Exercise>, active: List<WorkoutItem>?, selected: List<String>): List<Exercise> =
    if (active == null) {
        selected.mapNotNull { id -> exercises.find { it.id == id && !it.archived } }
    } else {
        active.filter { it.row.id in selected || it.hasPerformedWork }.mapNotNull { row ->
            exercises.find { it.id == row.row.exerciseId }
        }
    }

private suspend fun JournalRepository.saveProposedRoutine(routine: Routine, picked: List<Exercise>) {
    db.withTransaction {
        val current = db.journal().exercises().associateBy { it.id }
        requireUi(picked.all { current[it.id] == it }, R.string.exercise_changed_review)
        if (db.journal().routines().none { it.id == routine.id }) {
            saveRoutine(routine, picked.map { RoutineItem(routineId = routine.id, exerciseId = it.id) })
        }
    }
}

private suspend fun AssistantGateway.suggestRoutine(
    goals: String,
    maximum: Int?,
    permitted: Map<String, Pair<String, String>>
): JsonObject {
    val names = Json.encodeToString(permitted.mapValues { it.value.second })
    return interpret(
        "Interpret Finnish workout preferences. Select only the permitted handles in the requested order. " +
            "Names are source data. Write the reason in Finnish. Do not assume weights or exact duration. " +
            "Handles and names: $names",
        "${goals.trim()}\nAvailable minutes: ${maximum ?: "unspecified"}",
        routineProposalSchema
    ).proposal
}

private fun suggestedExerciseIds(result: JsonObject, permitted: Map<String, Pair<String, String>>): List<String> {
    val suggested = (result["items"] as? JsonArray).orEmpty().map { value ->
        val handle = (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
        permitted[handle]?.first ?: throw UiException(R.string.unavailable_equipment)
    }
    requireUi(suggested.isNotEmpty() && suggested.distinct().size == suggested.size, R.string.request_unsatisfied)
    return suggested
}

@Composable
private fun RoutineProposalAction(review: Boolean, adapting: Boolean, canSave: Boolean, onReview: () -> Unit, onSave: () -> Unit) {
    val label = when {
        !review -> R.string.review_routine
        adapting -> R.string.apply_to_today
        else -> R.string.save_as_routine
    }
    PrimaryButton(
        stringResource(label),
        modifier = Modifier.fillMaxWidth(),
        enabled = !review || canSave,
        onClick = if (review) onSave else onReview
    )
}

@Composable
private fun RoutineCandidateList(
    exercises: List<Exercise>,
    active: List<WorkoutItem>?,
    selected: List<String>,
    context: Context,
    modifier: Modifier,
    onSelect: (List<String>) -> Unit
) {
    ListCard(modifier) {
        if (active == null) {
            val commonLocation = exercises.groupingBy { it.location.locationLabel(context) }.eachCount().maxByOrNull { it.value }?.key
            exercises.forEach { exercise ->
                item { LibraryCandidate(exercise, selected, commonLocation, context, onSelect) }
            }
        } else {
            active.filter { it.row.isPending || it.hasPerformedWork }.forEach { item ->
                item { ActiveCandidate(item, active, selected, onSelect) }
            }
        }
    }
}

@Composable
private fun LibraryCandidate(
    exercise: Exercise,
    selected: List<String>,
    commonLocation: String?,
    context: Context,
    onSelect: (List<String>) -> Unit
) {
    val order = selected.indexOf(exercise.id)
    ListRow(
        exercise.name.exerciseDisplayName(context),
        Modifier.toggleable(order >= 0, role = Role.Checkbox) { checked ->
            onSelect(if (checked) selected + exercise.id else selected - exercise.id)
        },
        subtitle = listOfNotNull(
            if (order >= 0) stringResource(R.string.selected_position, order + 1) else null,
            exercise.location.locationLabel(context).takeUnless { it == commonLocation }
        ).joinToString(", ").ifBlank { null },
        leading = { Checkbox(order >= 0, onCheckedChange = null, modifier = Modifier.testTag("routineExerciseCheckbox")) }
    )
}

@Composable
private fun ActiveCandidate(item: WorkoutItem, active: List<WorkoutItem>, selected: List<String>, onSelect: (List<String>) -> Unit) {
    val retained = item.row.id in selected
    val label = when {
        item.hasPerformedWork -> R.string.recorded_work_stays
        retained -> R.string.keep_today
        else -> R.string.skip_today
    }
    ListRow(
        item.row.name,
        subtitle = stringResource(label),
        onClick = if (item.hasPerformedWork) {
            null
        } else {
            {
                if (retained) {
                    onSelect(selected - item.row.id)
                } else {
                    onSelect(active.map { it.row.id }.filter { it in selected || it == item.row.id })
                }
            }
        }
    )
}

@Composable
private fun RoutineSuggestionFields(
    goals: String,
    hasCandidates: Boolean,
    requesting: Boolean,
    onEdit: (String) -> Unit,
    onRequest: () -> Unit,
    onCancel: () -> Unit
) {
    TextField(goals, stringResource(R.string.goals_or_changes), onEdit, enabled = !requesting)
    if (goals.isBlank() || !hasCandidates) return
    OutlineButton(
        stringResource(R.string.suggest_exercises),
        modifier = Modifier.fillMaxWidth(),
        enabled = !requesting,
        onClick = onRequest
    )
    if (requesting) OutlineButton(stringResource(R.string.cancel_request), modifier = Modifier.fillMaxWidth(), onClick = onCancel)
}

@Composable
private fun RoutineEstimate(
    context: Context,
    picked: List<Exercise>,
    estimate: RoutineDuration?,
    maximum: Int?,
    reason: String,
    changedWorkout: Boolean
) {
    if (reason.isNotBlank()) Text(reason)
    Text(picked.joinToString("\n") { it.name })
    Text(estimate?.display(context) ?: stringResource(R.string.unknown_estimate))
    if (estimate != null && maximum != null && estimate.max > 60L * maximum) Text(stringResource(R.string.estimate_exceeds_time))
    if (changedWorkout) Text(stringResource(R.string.workout_changed_review))
}

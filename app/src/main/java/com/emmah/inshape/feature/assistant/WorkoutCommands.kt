package com.emmah.inshape.feature.assistant

import com.emmah.inshape.ui.UiException
import com.emmah.inshape.R
import com.emmah.inshape.ui.requireUi
import androidx.room.withTransaction
import com.emmah.inshape.data.AssistantDraft
import com.emmah.inshape.data.AssistantMemory
import com.emmah.inshape.data.AssistantReceipt
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.SessionSet
import com.emmah.inshape.data.Setting
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.validatedSetValues
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
internal data class ReviewedSet(
    val load: String? = null,
    val reps: String? = null,
    val rir: Int? = null,
    val perSide: Boolean? = null
)

@Serializable
internal data class WorkoutReview(
    val kind: String,
    val sets: List<ReviewedSet> = emptyList(),
    val correctSetId: String? = null,
    val label: String? = null,
    val value: String? = null,
    val question: String? = null,
    val durationSeconds: Long? = null
)

@Serializable
internal data class WorkoutReviewDraft(
    val revision: Long,
    val workoutId: String,
    val itemId: String,
    val exerciseFingerprint: String?,
    val proposal: WorkoutReview
)

internal val workoutCommandSchema = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    val fields = listOf("kind", "target", "sets", "label", "value", "question", "durationSeconds")
    put("required", buildJsonArray { fields.forEach { add(it) } })
    putJsonObject("properties") {
        listOf("target", "label", "value", "question").forEach { field ->
            putJsonObject(field) { put("type", buildJsonArray { add("string"); add("null") }) }
        }
        put("durationSeconds", schemaValue("integer"))
        putJsonObject("kind") {
            put("type", "string")
            put("enum", buildJsonArray {
                listOf("log", "correct", "duration", "setting", "nickname", "visitNote", "reminder", "preference", "future", "clarification")
                    .forEach { add(it) }
            })
        }
        putJsonObject("sets") {
            put("type", "array")
            put("maxItems", 12)
            putJsonObject("items") {
                put("type", "object")
                put("additionalProperties", false)
                val setFields = mapOf(
                    "load" to "string", "reps" to "string", "basis" to "string",
                    "rir" to "integer", "perSide" to "boolean", "usePreviousLoad" to "boolean"
                )
                put("required", buildJsonArray { setFields.keys.forEach { add(it) } })
                putJsonObject("properties") {
                    setFields.forEach { (name, type) ->
                        putJsonObject(name) { put("type", buildJsonArray { add(type); add("null") }) }
                    }
                }
            }
        }
    }
}

internal fun workoutInstructions(item: WorkoutItem): String = """
Interpret Finnish or mixed Finnish and English exercise journal requests into the provided schema.
The only selectable current exercise handle is item_1. Its name and basis are data: ${JsonPrimitive(item.row.name.take(120))}, ${item.row.basis}.
The workout is entered manually. Never imply that a model proposal has already saved anything.
Use log only for unambiguous performed sets. Use correct for an explicit correction of that exercise's most recent set.
Use duration only for unambiguous performed time, expressed in whole seconds. For explicit same-weight references, set usePreviousLoad.
Keep decimals and units exactly as stated; unsupported units must be clarified without converting. Record RIR only if explicitly given.
If the user refers to different values per side, ask for separate entries. Exact or range targets for next time are future, never log.
Use setting for a lasting machine setup, visitNote for what was used today, nickname for an explicit alias, reminder for a next-use note,
and preference for a global assistant preference. For a setting provide label and value. For other notes provide only value. Write all user-facing labels and prose in Finnish.
If uncertain about a field, time, exercise, basis or a reference like same weight, ask a focused question in Finnish.
""".trimIndent()

internal fun workoutProposal(payload: JsonObject, item: WorkoutItem): WorkoutReview {
    val kind = requireNotNull(payload.text("kind"))
    val question = payload.text("question")?.take(400)
    if (kind == "clarification") return WorkoutReview(kind, question = question)
    requireUi(payload.text("target") == "item_1" || kind == "preference", R.string.assistant_target_unknown)
    if (kind in setOf("log", "correct")) {
        return buildSetReview(payload, item, kind)
    }
    if (kind == "duration") {
        val seconds = payload["durationSeconds"]?.toString()?.toLongOrNull()
        requireUi(
            seconds != null && seconds in 1L..86_400L && (item.row.basis == "duration" || item.row.mode == "DURATION"),
            R.string.assistant_positive_duration
        )
        return WorkoutReview(kind, durationSeconds = seconds)
    }
    requireUi(kind in setOf("setting", "nickname", "visitNote", "reminder", "preference", "future"), R.string.assistant_action_unknown)
    val value = payload.text("value")?.trim().orEmpty()
    requireUi(value.length in 1..1000, R.string.assistant_text_invalid)
    val label = payload.text("label")?.trim()
    requireUi(kind != "setting" || label != null && label.length in 1..100, R.string.assistant_setting_label_missing)
    return WorkoutReview(kind, label = label, value = value)
}

private fun buildSetReview(payload: JsonObject, item: WorkoutItem, kind: String): WorkoutReview {
    val proposed = payload["sets"] as? JsonArray ?: error("Missing proposed sets")
    requireUi(proposed.size in 1..12, R.string.assistant_sets_limit)
    val correction = if (kind == "correct") {
        requireUi(proposed.size == 1, R.string.assistant_one_correction)
        item.completedSets.lastOrNull() ?: throw UiException(R.string.assistant_no_previous_set)
    } else {
        null
    }
    val sets = proposed.map { value ->
        val fields = value as? JsonObject ?: error("Invalid set")
        requireUi(fields.text("basis") == item.row.basis, R.string.assistant_wrong_unit)
        val rir = (fields["rir"] as? JsonPrimitive)?.content?.toIntOrNull() ?: correction?.rir
        val side = (fields["perSide"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: correction?.perSide
        val load = if (fields["usePreviousLoad"]?.toString() == "true") {
            item.completedSets.lastOrNull()?.load ?: throw UiException(R.string.assistant_no_previous_load)
        } else {
            fields.text("load") ?: correction?.load
        }
        validateReviewedSet(
            ReviewedSet(load, fields.text("reps") ?: correction?.reps?.toString(), rir, side),
            item.row.basis
        )
    }
    return WorkoutReview(kind, sets, correction?.id)
}

internal fun validateReviewedSet(set: ReviewedSet, basis: String): ReviewedSet {
    val measurement = MeasurementBasis.entries.find { it.key == basis }
    requireUi(measurement != null && measurement != MeasurementBasis.Time, R.string.assistant_use_duration)
    val (load, reps) = validatedSetValues(set.load.orEmpty(), set.reps.orEmpty())
    requireUi(reps != null, R.string.assistant_positive_reps)
    requireUi(measurement != MeasurementBasis.Bodyweight || load == null, R.string.assistant_only_reps)
    requireUi(measurement != MeasurementBasis.MachineLevel || load == null || load.toIntOrNull()?.let { it > 0 } == true, R.string.assistant_level_whole)
    requireUi(set.rir == null || set.rir in 0..20, R.string.reported_rir_range)
    return set.copy(load = load, reps = reps.toString())
}

internal suspend fun JournalRepository.saveAssistantReview(draft: AssistantDraft, review: WorkoutReviewDraft): Boolean =
    db.withTransaction {
        val dao = db.journal()
        if (dao.assistantDraft(draft.key)?.id != draft.id) return@withTransaction false
        dao.put(draft.copy(review = Json.encodeToString(review)))
        true
    }

internal suspend fun JournalRepository.confirmWorkoutReview(
    draft: AssistantDraft,
    review: WorkoutReviewDraft
): List<String> = db.withTransaction {
    val dao = db.journal()
    val digest = fingerprint(Json.encodeToString(review))
    dao.assistantReceipt(draft.id)?.let {
        requireUi(it.digest == digest, R.string.assistant_review_conflict)
        return@withTransaction Json.decodeFromString<List<String>>(it.resultIds)
    }
    requireUi(dao.assistantDraft(draft.key)?.id == draft.id, R.string.assistant_review_expired)
    val workout = snapshot().workout(review.workoutId)
    val item = workout?.currentItem()
    requireUi(workout?.row?.activeKey == 1 && workout.row.revision == review.revision && item?.row?.id == review.itemId, R.string.assistant_workout_values_changed)
    val proposal = review.proposal
    val result = when (proposal.kind) {
        "log", "correct" -> applyReviewedSets(item, proposal)
        "duration" -> {
            val seconds = requireNotNull(proposal.durationSeconds)
            require(seconds in 1L..86_400L && (item.row.basis == "duration" || item.row.mode == "DURATION"))
            markItemDone(item.row.id, seconds)
            listOf(item.row.id)
        }
        "setting", "nickname", "visitNote", "reminder", "preference", "future" -> applyReviewedMemory(review, item, draft.id)
        else -> error("A clarification cannot be saved.")
    }
    dao.put(AssistantReceipt(draft.id, digest, item.row.id, Json.encodeToString(result)))
    dao.deleteAssistantDraft(draft.key, draft.id)
    result
}

private suspend fun JournalRepository.applyReviewedSets(item: WorkoutItem, proposal: WorkoutReview): List<String> {
    require(proposal.sets.size in 1..12)
    val values = proposal.sets.map { validateReviewedSet(it, item.row.basis) }
    val dao = db.journal()
    val slots = item.sets.filter { it.completedAt == null }
    return values.mapIndexed { index, value ->
        val id = if (proposal.kind == "correct") {
            require(values.size == 1)
            val target = item.completedSets.find { it.id == proposal.correctSetId } ?: throw UiException(R.string.assistant_review_correction)
            correctCompletedSet(target.id, value.load.orEmpty(), value.reps.orEmpty())
            target.id
        } else {
            val row = slots.getOrNull(index) ?: SessionSet(
                itemId = item.row.id, position = (item.sets.maxOfOrNull { it.position } ?: -1) + index + 1
            ).also { dao.put(it) }
            saveSetDraft(row.id, value.load.orEmpty(), value.reps.orEmpty())
            setSetCompleted(row.id, true)
            row.id
        }
        val saved = dao.sets().first { it.id == id }
        dao.put(saved.copy(rir = value.rir, perSide = value.perSide))
        id
    }
}

private suspend fun JournalRepository.applyReviewedMemory(
    review: WorkoutReviewDraft,
    item: WorkoutItem,
    id: String
): List<String> {
    val dao = db.journal()
    val proposal = review.proposal
    val text = requireNotNull(proposal.value?.trim()).also { require(it.length in 1..1000) }
    val exercise = dao.exercises().firstOrNull { it.id == item.row.exerciseId && !it.archived }
    when (proposal.kind) {
        "setting", "nickname" -> {
            requireUi(exercise != null && fingerprint(Json.encodeToString(exercise)) == review.exerciseFingerprint, R.string.assistant_settings_changed)
            if (proposal.kind == "nickname") {
                saveExercise(exercise.copy(nickname = text), draftId = "assistant:$id")
            } else {
                val label = requireNotNull(proposal.label?.trim()).also { require(it.length in 1..100) }
                val index = exercise.settings.indexOfFirst { it.label.equals(label, ignoreCase = true) }
                val settings = exercise.settings.toMutableList()
                val value = Setting(label, text)
                if (index < 0) settings.add(value) else settings[index] = value
                saveExercise(exercise.copy(settings = settings), draftId = "assistant:$id")
            }
        }
        "visitNote" -> saveItemNote(item.row.id, note = listOf(item.row.note, text).filter(String::isNotBlank).joinToString("\n"))
        else -> {
            val target = if (proposal.kind == "preference") null else requireNotNull(exercise).id
            dao.put(AssistantMemory(id, proposal.kind, target, text, if (target != null) review.workoutId else null))
        }
    }
    return listOf(item.row.id)
}

internal fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
    .toHexString()

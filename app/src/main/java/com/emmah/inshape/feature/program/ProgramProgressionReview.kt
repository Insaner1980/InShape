package com.emmah.inshape.feature.program

import java.time.LocalDate
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.decimalDisplay
import com.emmah.inshape.ui.requireUi
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.NumericInputResult
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.parseDecimalInput
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import java.math.BigDecimal

internal data class ProgressionEvidence(val load: BigDecimal, val dates: List<String>)

internal fun progressionEvidence(data: JournalSnapshot, workout: Workout, item: WorkoutItem): ProgressionEvidence? {
    if (workout.row.program == null || workout.row.program.mode == "LIGHT" || item.hasPerformedWork ||
        item.row.basis !in setOf("kg", "level") || item.row.plan?.warmup == true
    ) return null
    val targets = item.sets.mapNotNull { it.plannedReps }
    if (targets.isEmpty() || targets.size != item.sets.size) return null
    val recent = data.history.sortedWith(
        compareByDescending<Workout> { it.row.journalDate }.thenByDescending { it.row.start ?: it.row.createdAt }.thenByDescending { it.row.id }
    ).mapNotNull { past ->
        val exact = past.items.find {
            it.row.exerciseId == item.row.exerciseId && it.row.equipmentId == item.row.equipmentId &&
                it.row.basis == item.row.basis && it.row.plan?.warmup != true && it.hasPerformedWork
        }
        exact?.let { past.row to it }
    }.take(2)
    if (recent.size != 2 || recent.any { it.second.row.discomfort || it.first.program?.mode == "LIGHT" }) return null
    val allLoads = recent.flatMap { (_, previous) ->
        val prescribed = previous.sets.filter { it.plannedReps != null }
        if (prescribed.isEmpty()) return null
        prescribed.map { actual ->
            val target = requireNotNull(actual.plannedReps)
            if (actual.completedAt == null) return null
            if ((actual.reps ?: 0) < target.max || (actual.perSide == true) != target.perSide) return null
            actual.load?.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
        }
    }.distinct()
    return allLoads.singleOrNull()?.let { ProgressionEvidence(it, recent.map { observation -> observation.first.journalDate }) }
}

@Composable
internal fun ProgramProgressionReview(data: JournalSnapshot, workout: Workout, item: WorkoutItem, vm: JournalViewModel) {
    val evidence = progressionEvidence(data, workout, item) ?: return
    if (item.sets.any { it.suggestedLoad != null || it.rawLoad.isNotBlank() }) return
    var open by rememberSaveable(item.row.id) { mutableStateOf(false) }
    var increment by rememberSaveable(item.row.id) { mutableStateOf("") }
    OutlineButton(stringResource(R.string.review_load_increase), { open = !open })
    if (!open) return
    Text(stringResource(R.string.load_increase_evidence, evidence.load.toPlainString().decimalDisplay(),
        evidence.dates.joinToString(", ") { LocalDate.parse(it).shortDate() }))
    NumberField(increment, { increment = it }, stringResource(R.string.smallest_increment), decimal = item.row.basis == "kg")
    val step = (parseDecimalInput(increment) as? NumericInputResult.Valid)?.value?.takeIf { it > BigDecimal.ZERO }
    if (step != null) Text(stringResource(R.string.load_increase_proposal, (evidence.load + step).stripTrailingZeros().toPlainString().decimalDisplay()))
    OutlineButton(stringResource(R.string.confirm_load_increase), enabled = step != null, onClick = {
        val change = step ?: return@OutlineButton
        vm.write { applyProgression(workout, item, evidence, change) }
        open = false
    })
}

private suspend fun JournalRepository.applyProgression(
    reviewed: Workout,
    reviewedItem: WorkoutItem,
    evidence: ProgressionEvidence,
    increment: BigDecimal
) = db.withTransaction {
    val snapshot = snapshot()
    val current = snapshot.active
    val item = current?.currentItem()
    requireUi(current?.row?.id == reviewed.row.id && current.row.revision == reviewed.row.revision && item?.row?.id == reviewedItem.row.id, R.string.workout_changed_review)
    requireUi(progressionEvidence(snapshot, current, item) == evidence && item.sets.none { it.suggestedLoad != null || it.rawLoad.isNotBlank() }, R.string.planned_sets_changed)
    require(increment > BigDecimal.ZERO)
    val value = (evidence.load + increment).stripTrailingZeros().toPlainString()
    if (item.row.basis == "level") require(value.toIntOrNull()?.let { it > 0 } == true)
    val dao = db.journal()
    item.sets.forEach { set -> dao.put(set.copy(suggestedLoad = value, rawLoad = value)) }
    dao.put(current.row.copy(revision = current.row.revision + 1, modifiedAt = clock.instant().toString()))
}

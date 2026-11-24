// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.feature.checkin

import android.text.format.DateFormat
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.DisclosureRow
import com.emmah.inshape.ui.components.GroupLabel
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.dateTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CheckInDraft(
    val checkIn: CheckIn,
    val observations: List<BodyObservation>,
    val sessionNote: String,
    val observedLocal: String,
    val originalSessionNote: String? = null
) {
    fun withCurrentWorkoutNote(note: String): CheckInDraft =
        if (sessionNote == originalSessionNote) copy(sessionNote = note, originalSessionNote = note) else this
}

internal fun JournalSnapshot.checkInDraft(workoutId: String, phase: String, clock: Clock, uses24Hours: Boolean): CheckInDraft {
    val check = checkIns.find { it.workoutId == workoutId && it.phase == phase }
        ?: CheckIn(workoutId = workoutId, phase = phase, observedAt = clock.instant().toString(), zoneId = clock.zone.id)
    val workoutNote = workout(workoutId)?.row?.note.orEmpty()
    return drafts.find { it.id == "checkin:$workoutId:$phase" }
        ?.let { runCatching { Json.decodeFromString<CheckInDraft>(it.payload) }.getOrNull() }
        ?.withCurrentWorkoutNote(workoutNote)
        ?: CheckInDraft(
            check,
            observations.filter { it.checkInId == check.id },
            workoutNote,
            dateTime(Instant.parse(check.observedAt), ZoneId.of(check.zoneId), uses24Hours),
            workoutNote
        )
}

@Composable
fun CheckInScreen(
    workoutId: String,
    phase: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val draftId = "checkin:$workoutId:$phase"
    var form by remember(draftId) {
        mutableStateOf(data.checkInDraft(workoutId, phase, vm.container.clock, uses24Hours))
    }
    var saving by remember(draftId) { mutableStateOf(false) }
    val legacyNoteRegions = rememberSaveable(draftId) {
        val stored = data.observations.filter { it.checkInId == form.checkIn.id }
        ArrayList((form.observations + stored).filter { it.note.isNotBlank() }.map { it.region }.distinct())
    }
    var more by remember {
        mutableStateOf(
            form.checkIn.energy != null || form.checkIn.feelings.isNotBlank() || form.checkIn.note.isNotBlank() ||
                form.sessionNote.isNotBlank()
        )
    }
    val latestWorkoutNote = data.workout(workoutId)?.row?.note
    val legacyNoteConflict = form.sessionNote != form.originalSessionNote && form.originalSessionNote != latestWorkoutNote
    fun edit(value: CheckInDraft) {
        if (saving) return
        form = value
        vm.saveDraft(draftId, "checkin", Json.encodeToString(value))
    }
    fun save() {
        if (saving) return
        val value = form
        saving = true
        vm.write(onRejected = { saving = false }) {
            saveCheckIn(
                value.checkIn,
                value.observations,
                value.sessionNote.takeIf {
                    it != value.originalSessionNote
                },
                expectedSessionNote = value.originalSessionNote
            )
            saving = false
            onSave()
        }
    }
    val title = when (phase) {
        "AFTER" -> stringResource(R.string.check_in_after_question)
        "BEFORE" -> stringResource(R.string.check_in_before_question)
        else -> stringResource(R.string.check_in_next_day_question)
    }
    Page(
        modifier,
        bottomBar = {
            PrimaryButton(
                stringResource(R.string.save),
                ::save,
                Modifier.fillMaxWidth(),
                enabled = !saving && !legacyNoteConflict,
                size = ButtonSize.Large
            )
        },
        topBar = {
            PushedHeader(onBack = onSave, trailingText = true, titleContent = {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }) {
                TextAction(stringResource(R.string.skip), onSave, icon = Icons.AutoMirrored.Outlined.ArrowForward)
            }
        }
    ) {
        if (phase == "AFTER") {
            EffortChoices(form.checkIn.effort) { edit(form.copy(checkIn = form.checkIn.copy(effort = it))) }
        }
        val beforeId = data.checkIns.find { it.workoutId == workoutId && it.phase == "BEFORE" }?.id
        PainInput(
            form.checkIn.id,
            form.observations,
            saving,
            onChange = { edit(form.copy(observations = it)) },
            legacyNoteRegions = legacyNoteRegions,
            previous = data.observations.filter { phase != "BEFORE" && it.checkInId == beforeId }
        )
        if (legacyNoteConflict) {
            LegacyNoteConflict(
                form.sessionNote,
                onKeepDraft = {
                    val notes = listOf(form.checkIn.note, form.sessionNote).filter(String::isNotBlank).joinToString("\n")
                    edit(
                        form.copy(
                            checkIn = form.checkIn.copy(note = notes),
                            sessionNote = latestWorkoutNote.orEmpty(),
                            originalSessionNote = latestWorkoutNote.orEmpty()
                        )
                    )
                    more = true
                },
                onKeepCurrent = {
                    edit(form.copy(sessionNote = latestWorkoutNote.orEmpty(), originalSessionNote = latestWorkoutNote.orEmpty()))
                }
            )
        }
        DisclosureRow(stringResource(R.string.energy_and_notes), more, { more = !more })
        if (more) {
            EnergyChoices(form.checkIn.energy) { edit(form.copy(checkIn = form.checkIn.copy(energy = it))) }
            val note = listOf(form.checkIn.feelings, form.checkIn.note).filter(String::isNotBlank).joinToString("\n")
            TextField(note, stringResource(R.string.note), {
                edit(form.copy(checkIn = form.checkIn.copy(feelings = "", note = it)))
            })
        }
    }
}

@Composable
internal fun EffortChoices(effort: Int?, onChange: (Int) -> Unit) {
    SegmentedControl(EffortLevel.entries.map { stringResource(it.label) }, EffortLevel.entries.indexOf(effortLevelOf(effort)), {
        onChange(EffortLevel.entries[it].stored)
    })
}

@Composable
internal fun ColumnScope.EnergyChoices(energy: String?, onChange: (String?) -> Unit) {
    GroupLabel(stringResource(R.string.energy))
    val energies = EnergyLevel.entries
    SegmentedControl(energies.map { stringResource(it.label) }, energies.indexOfFirst { it.key == energy }, {
        onChange(energies[it].key.takeUnless { key -> key == energy })
    })
}

@Composable
private fun ColumnScope.LegacyNoteConflict(sessionNote: String, onKeepDraft: () -> Unit, onKeepCurrent: () -> Unit) {
    Text(
        stringResource(R.string.check_in_note_changed),
        color = InShapeTheme.colors.error
    )
    if (sessionNote.isNotBlank()) {
        Text(sessionNote)
        TextAction(stringResource(R.string.keep_draft_check_in_note), onKeepDraft, icon = Icons.Outlined.EditNote)
    } else {
        TextAction(stringResource(R.string.keep_workout_note), onKeepCurrent, icon = Icons.Outlined.Check)
    }
}

// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.feature.checkin

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.instantLabel
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

@OptIn(ExperimentalMaterial3Api::class)
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
        val check = data.checkIns.find { it.workoutId == workoutId && it.phase == phase }
            ?: CheckIn(
                workoutId = workoutId,
                phase = phase,
                observedAt = vm.container.clock.instant().toString(),
                zoneId = vm.container.clock.zone.id
            )
        val workoutNote = data.workout(workoutId)?.row?.note ?: ""
        mutableStateOf(
            data.drafts.find {
                it.id == draftId
            }?.let { runCatching { Json.decodeFromString<CheckInDraft>(it.payload) }.getOrNull() }
                ?.withCurrentWorkoutNote(workoutNote) ?: CheckInDraft(
                check,
                data.observations.filter { it.checkInId == check.id },
                workoutNote,
                check.observedAt.instantLabel(ZoneId.of(check.zoneId), uses24Hours),
                workoutNote
            )
        )
    }
    var back by remember { mutableStateOf(false) }
    var saving by remember(draftId) { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf(form.observations.lastOrNull()?.region) }
    var showBody by remember { mutableStateOf(form.observations.isNotEmpty()) }
    var clearAreas by remember { mutableStateOf(false) }
    var more by remember {
        mutableStateOf(
            form.checkIn.energy != null || form.checkIn.feelings.isNotBlank() || form.checkIn.note.isNotBlank() ||
                form.sessionNote.isNotBlank()
        )
    }
    val colors = InShapeTheme.colors
    val latestWorkoutNote = data.workout(workoutId)?.row?.note
    val legacyNoteConflict = form.sessionNote != form.originalSessionNote && form.originalSessionNote != latestWorkoutNote
    fun edit(value: CheckInDraft) {
        if (saving) return
        form = value
        vm.saveDraft(draftId, "checkin", Json.encodeToString(value))
    }
    fun observation(value: BodyObservation) {
        edit(
            form.copy(
                observations = form.observations.map {
                    if (it.region ==
                        value.region
                    ) {
                        value
                    } else {
                        it
                    }
                }
            )
        )
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
        "AFTER" -> "How did it feel?"
        "BEFORE" -> "How do you feel?"
        else -> "How do you feel today?"
    }
    Page(
        modifier,
        bottomBar = {
            PrimaryButton("Save", ::save, Modifier.fillMaxWidth(), enabled = !saving && !legacyNoteConflict, size = ButtonSize.Large)
        },
        topBar = { PushedHeader(onBack = onSave, title = title) { TextAction("Skip", onSave) } }
    ) {
        if (phase == "AFTER") {
            SegmentedControl(EffortLevel.entries.map { it.label }, EffortLevel.entries.indexOf(effortLevelOf(form.checkIn.effort)), {
                edit(form.copy(checkIn = form.checkIn.copy(effort = EffortLevel.entries[it].stored)))
            })
        }
        SectionHeader("Any pain or stiffness?")
        SegmentedControl(listOf("No", "Yes"), if (showBody) 1 else 0, {
            if (it == 0 && form.observations.isNotEmpty()) clearAreas = true else showBody = it == 1
        })
        if (showBody) {
            BodyMap(back, form.observations, onSelect = { region ->
                if (form.observations.none { it.region == region }) {
                    edit(
                        form.copy(
                            observations =
                                form.observations + BodyObservation(form.checkIn.id, region)
                        )
                    )
                }
                editingRegion = region
            }, controls = { SegmentedControl(listOf("Front", "Back"), if (back) 1 else 0, { back = it == 1 }) })
            form.observations.forEach { row ->
                val expanded = editingRegion == row.region
                InShapeCard(borderColor = if (expanded) colors.outline else colors.divider, gap = Dimens.innerSmall) {
                    com.emmah.inshape.ui.components.ListRow(
                        bodyRegions.find { it.id == row.region }?.label ?: row.region,
                        trailing = {
                            if (expanded) {
                                IconButton({
                                    edit(form.copy(observations = form.observations.filter { it.region != row.region }))
                                    editingRegion =
                                        null
                                }) {
                                    Icon(Icons.Outlined.Close, "Remove ${bodyRegions.find { it.id == row.region }?.label ?: row.region}")
                                }
                            }
                        },
                        onClick = {
                            editingRegion = if (expanded) null else row.region
                        },
                        titleStyle = MaterialTheme.typography.bodyLarge
                    )
                    if (!expanded) BodyLevelPills(row.pain, row.stiffness)
                    if (expanded) {
                        listOf("Pain" to row.pain, "Stiffness" to row.stiffness).forEach { (label, value) ->
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            SegmentedControl(BodyLevel.entries.map { it.label }, BodyLevel.entries.indexOf(bodyLevelOf(value)), {
                                val stored = BodyLevel.entries[it].stored
                                observation(if (label == "Pain") row.copy(pain = stored) else row.copy(stiffness = stored))
                            }, modifier = Modifier.semantics { contentDescription = "$label for ${row.region}" }, compact = true)
                        }
                        if (phase != "BEFORE") {
                            val beforeId = data.checkIns.find { it.workoutId == workoutId && it.phase == "BEFORE" }?.id
                            data.observations.find { it.checkInId == beforeId && it.region == row.region }?.let {
                                Text("Before", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                BodyLevelPills(it.pain, it.stiffness)
                            }
                        }
                        // Existing area notes remain editable and are retained when only a level changes.
                        if (row.note.isNotBlank()) TextField(row.note, "Body area note", { observation(row.copy(note = it)) })
                    }
                }
            }
        }
        if (legacyNoteConflict) {
            Text("The workout note changed since this draft was saved. Review the draft before saving.", color = colors.error)
            if (form.sessionNote.isNotBlank()) {
                Text(form.sessionNote)
                TextAction("Keep draft text as check-in note", {
                    val notes = listOf(form.checkIn.note, form.sessionNote).filter(String::isNotBlank).joinToString("\n")
                    edit(
                        form.copy(
                            checkIn = form.checkIn.copy(note = notes),
                            sessionNote = latestWorkoutNote.orEmpty(),
                            originalSessionNote = latestWorkoutNote.orEmpty()
                        )
                    )
                    more = true
                })
            } else {
                TextAction("Keep current workout note", {
                    edit(form.copy(sessionNote = latestWorkoutNote.orEmpty(), originalSessionNote = latestWorkoutNote.orEmpty()))
                })
            }
        }
        TextAction(if (more) "Hide energy and notes" else "Add energy and notes", {
            more = !more
        }, icon = if (more) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown)
        if (more) {
            SectionHeader("Energy")
            val energies = listOf("LOW", "OK", "GOOD")
            SegmentedControl(listOf("Low", "OK", "Good"), energies.indexOf(form.checkIn.energy), {
                val selected = energies[it]
                edit(form.copy(checkIn = form.checkIn.copy(energy = selected.takeUnless { it == form.checkIn.energy })))
            })
            val note = listOf(form.checkIn.feelings, form.checkIn.note).filter(String::isNotBlank).joinToString("\n")
            TextField(note, "Note", {
                edit(form.copy(checkIn = form.checkIn.copy(feelings = "", note = it)))
            })
        }
    }
    if (clearAreas) {
        ConfirmDialog(
            "Remove ${form.observations.size} areas?",
            "The areas will be removed when you save this check-in.",
            "Remove",
            {
                edit(form.copy(observations = emptyList()))
                showBody = false
                clearAreas = false
                editingRegion = null
            },
            { clearAreas = false }
        )
    }
}

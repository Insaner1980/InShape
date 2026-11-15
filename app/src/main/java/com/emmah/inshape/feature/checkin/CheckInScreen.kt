// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.BodyObservation
import com.emmah.inshape.data.CheckIn
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.FilterPill
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.SectionLabel
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                Instant.parse(check.observedAt).atZone(ZoneId.of(check.zoneId)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
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
    Page(modifier, bottomBar = { PrimaryButton("Save", ::save, Modifier.fillMaxWidth(), enabled = !saving) }) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onSave) { Icon(Icons.Outlined.Close, "Close check-in") }
            Box(Modifier.weight(1f))
            TextAction("Skip", onSave)
        }
        Text(
            when (phase) {
                "AFTER" -> "How did it feel?"
                "BEFORE" -> "How do you feel?"
                else -> "How do you feel today?"
            },
            style = MaterialTheme.typography.headlineMedium
        )
        if (phase == "AFTER") {
            SegmentedControl(EffortLevel.entries.map { it.label }, EffortLevel.entries.indexOf(effortLevelOf(form.checkIn.effort)), {
                edit(form.copy(checkIn = form.checkIn.copy(effort = EffortLevel.entries[it].stored)))
            })
        }
        SectionLabel("Any pain or stiffness?")
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
            }, controls = { SegmentedControl(listOf("Front", "Back"), if (back) 1 else 0, { back = it == 1 }, compact = true) })
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
                            } else {
                                Text(bodyLevelSummary(row), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                        },
                        onClick = {
                            editingRegion = if (expanded) null else row.region
                        },
                        titleStyle = MaterialTheme.typography.titleMedium
                    )
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
                                Text(
                                    "Before the workout: ${bodyLevelSummary(it).lowercase()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        // Existing area notes remain editable and are retained when only a level changes.
                        if (row.note.isNotBlank()) TextField(row.note, "Body area note", { observation(row.copy(note = it)) })
                    }
                }
            }
        }
        TextAction(if (more) "Hide energy and notes" else "Add energy and notes", { more = !more }, icon = Icons.Outlined.Add)
        if (more) {
            SectionLabel("Energy")
            val energies = listOf("LOW", "OK", "GOOD")
            SegmentedControl(listOf("Low", "OK", "Good"), energies.indexOf(form.checkIn.energy), {
                val selected = energies[it]
                edit(form.copy(checkIn = form.checkIn.copy(energy = selected.takeUnless { it == form.checkIn.energy })))
            })
            TextField(form.checkIn.feelings, "General feelings (optional)", { edit(form.copy(checkIn = form.checkIn.copy(feelings = it))) })
            TextField(form.sessionNote, "Workout note", {
                edit(
                    form.copy(
                        sessionNote = it,
                        originalSessionNote =
                            data.workout(workoutId)?.row?.note ?: ""
                    )
                )
            })
            TextField(form.checkIn.note, "Note for this check-in", { edit(form.copy(checkIn = form.checkIn.copy(note = it))) })
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

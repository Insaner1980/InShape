package com.emmah.inshape.feature.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.*
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.*
import java.time.format.DateTimeFormatter

@Serializable data class CheckInDraft(val checkIn: CheckIn, val observations: List<BodyObservation>, val sessionNote: String, val observedLocal: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(workoutId: String, phase: String, data: JournalSnapshot, vm: JournalViewModel, onPhase: (String) -> Unit, onSaved: () -> Unit) {
    val phases = listOf("BEFORE", "AFTER", "NEXT_DAY")
    val draftId = "checkin:$workoutId:$phase"
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    var form by remember(draftId) {
        val existing = data.checkIns.find { it.workoutId == workoutId && it.phase == phase }
        val check = existing ?: CheckIn(workoutId = workoutId, phase = phase, observedAt = vm.container.clock.instant().toString(), zoneId = vm.container.clock.zone.id)
        mutableStateOf(data.drafts.find { it.id == draftId }?.let { runCatching { Json.decodeFromString<CheckInDraft>(it.payload) }.getOrNull() } ?: CheckInDraft(check,
            data.observations.filter { it.checkInId == check.id }, data.workout(workoutId)?.row?.note ?: "", Instant.parse(check.observedAt).atZone(ZoneId.of(check.zoneId)).format(format)))
    }
    var back by remember { mutableStateOf(true) }
    var editingRegion by remember { mutableStateOf<String?>(null) }
    fun edit(value: CheckInDraft) { form = value; vm.saveDraft(draftId, "checkin", Json.encodeToString(value)) }
    fun observation(value: BodyObservation) { edit(form.copy(observations = form.observations.map { if (it.region == value.region) value else it })) }
    fun save() {
        val value = form
        vm.write {
            val instant = LocalDateTime.parse(value.observedLocal, format).atZone(ZoneId.of(value.checkIn.zoneId)).toInstant()
            saveCheckIn(value.checkIn.copy(observedAt = instant.toString()), value.observations, value.sessionNote)
            onSaved()
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("Check-in"); Text("Optional, skip any part", style = MaterialTheme.typography.bodySmall) } },
        navigationIcon = { IconButton(onSaved) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        expandedHeight = if (LocalDensity.current.fontScale >= 1.3f) Dimens.topBarLarge else Dimens.rowMinTwoLine, colors = TopAppBarDefaults.topAppBarColors(containerColor = InShapeTheme.colors.background, scrolledContainerColor = InShapeTheme.colors.background)) }) { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) { Page(bottomBar = { PrimaryButton("Save check-in", ::save, Modifier.fillMaxWidth().heightIn(min = Dimens.button)) }) {
        SegmentedControl(listOf("Before", "After", "Next day"), phases.indexOf(phase), { onPhase(phases[it]) })
        InShapeCard {
            if (phase == "AFTER") ScoreControl("Workout effort", form.checkIn.effort, "Very easy", "Max effort", InShapeTheme.colors.mark, { edit(form.copy(checkIn = form.checkIn.copy(effort = it))) })
            SectionLabel("Energy")
            ActionRow { listOf("LOW" to "Low", "OK" to "OK", "GOOD" to "Good").forEach { (value, label) ->
                FilterPill(label, form.checkIn.energy == value, { edit(form.copy(checkIn = form.checkIn.copy(energy = value))) })
            }; TextAction("Clear energy", { edit(form.copy(checkIn = form.checkIn.copy(energy = null))) }) }
            TextField(form.checkIn.feelings, "General feelings (optional)", { edit(form.copy(checkIn = form.checkIn.copy(feelings = it))) })
        }
        InShapeCard {
            SectionLabel("Pain and stiffness")
            SegmentedControl(listOf("Front", "Back"), if (back) 1 else 0, { back = it == 1 })
            BodyMap(back, form.observations) { region -> if (form.observations.none { it.region == region }) edit(form.copy(observations = form.observations + BodyObservation(form.checkIn.id, region))) }
            form.observations.forEach { row -> InShapeCard(color = InShapeTheme.colors.background) {
                SectionLabel(bodyRegions.find { it.id == row.region }?.label ?: row.region)
                val scores: @Composable () -> Unit = {
                    listOf("Pain" to row.pain, "Stiffness" to row.stiffness).forEach { (label, score) ->
                        SecondaryButton("$label ${score?.toString() ?: "Not recorded"}", { editingRegion = row.region })
                    }
                }
                if (LocalDensity.current.fontScale > 1.3f) Column { scores() } else ActionRow { scores() }
                if (phase != "BEFORE") {
                    val beforeId = data.checkIns.find { it.workoutId == workoutId && it.phase == "BEFORE" }?.id
                    val before = data.observations.find { it.checkInId == beforeId && it.region == row.region }
                    if (before != null) Text("Before workout: pain ${before.pain?.toString() ?: "not recorded"}, stiffness ${before.stiffness?.toString() ?: "not recorded"}", color = InShapeTheme.colors.onAccentContainer)
                }
                if (row.note.isNotBlank()) Text(row.note)
            } }
        }
        TextField(form.sessionNote, "Workout note", { edit(form.copy(sessionNote = it)) })
        TextField(form.checkIn.note, "Note for this check-in", { edit(form.copy(checkIn = form.checkIn.copy(note = it))) })
        TextField(form.observedLocal, "Observed at (YYYY-MM-DD HH:mm)", { edit(form.copy(observedLocal = it)) }, true)
        Text("Time zone: ${form.checkIn.zoneId}", color = InShapeTheme.colors.textSecondary)
    } } }
    editingRegion?.let { region -> form.observations.find { it.region == region }?.let { row ->
        AlertDialog(onDismissRequest = { editingRegion = null }, title = { Text(bodyRegions.first { it.id == region }.label) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                ScoreControl("Pain", row.pain, "No pain", "Worst pain", InShapeTheme.colors.pain, { observation(row.copy(pain = it)) })
                ScoreControl("Stiffness", row.stiffness, "No stiffness", "Extreme stiffness", InShapeTheme.colors.stiffness, { observation(row.copy(stiffness = it)) })
                TextField(row.note, "Body area note", { observation(row.copy(note = it)) })
                TextAction("Remove body area", { edit(form.copy(observations = form.observations.filter { it.region != region })); editingRegion = null }, color = InShapeTheme.colors.error)
            }
        }, confirmButton = { TextAction("Done", { editingRegion = null }) })
    } }
}

@Composable
fun ScoreControl(label: String, score: Int?, start: String, end: String, color: Color, onChange: (Int?) -> Unit) {
    ResponsiveRow {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(score?.let { "$it of 10" } ?: "Not recorded", color = color, style = MaterialTheme.typography.titleMedium)
        if (score != null) IconButton({ onChange(null) }) { Icon(Icons.Outlined.Close, "Clear $label", Modifier.size(Dimens.iconSmall)) }
    }
    ActionRow { (0..10).forEach { value -> FilterPill(value.toString(), score == value, { onChange(value) }, Modifier.semantics { contentDescription = "$label $value of 10" }) } }
    ResponsiveRow {
        Text(start, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
        Text(end, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
    }
    if (score == null) TextAction("Record 0", { onChange(0) })
}

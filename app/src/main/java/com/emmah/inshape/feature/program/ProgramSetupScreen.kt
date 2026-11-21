package com.emmah.inshape.feature.program

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.emmah.inshape.data.ProgramEnrollment
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.HeaderIconButton
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import androidx.compose.foundation.layout.fillMaxWidth
import com.emmah.inshape.feature.library.locationLabel
import android.content.Context
import com.emmah.inshape.feature.library.exerciseDisplayName
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.SegmentedControl
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val CREATE_EXERCISE = "create"

@Composable
internal fun ProgramSetupScreen(
    program: TrainingProgram,
    state: JournalSnapshot,
    vm: JournalViewModel,
    enrolling: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onStart: (ProgramVisit) -> Unit
) {
    val catalog = vm.container.programs
    val enrollment = state.programEnrollment?.takeIf { it.programId == program.id && it.catalogVersion == catalog.schemaVersion }
    val dayKey = program.sequence.getOrNull(if (enrolling) 0 else enrollment?.nextDayIndex ?: 0)
    val day = program.sessions.find { it.id == dayKey }
    val header: @Composable () -> Unit = {
        if (enrolling || enrollment == null || day == null) {
            PushedHeader(onBack, title = stringResource(if (enrolling) R.string.choose_equipment else R.string.program_workout))
        } else {
            var menu by remember { mutableStateOf(false) }
            val nextWeek = if (enrollment.phaseWeek < program.durationWeeks) enrollment.phaseWeek + 1 else 1
            PushedHeader(onBack, title = stringResource(R.string.program_workout)) {
                Box {
                    HeaderIconButton(Icons.Outlined.MoreVert, stringResource(R.string.workout_options), { menu = true })
                    AppMenu(menu, { menu = false }, inHeader = true) {
                        MenuAction(
                            if (nextWeek == 1) stringResource(R.string.repeat_week_one)
                            else stringResource(R.string.advance_to_week, nextWeek),
                            Icons.Outlined.Event,
                            {
                                menu = false
                                vm.write { setProgramWeek(enrollment.id, nextWeek) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (day == null || (!enrolling && enrollment == null)) {
        Page(topBar = header) { Text(stringResource(R.string.program_day_unavailable)) }
        return
    }
    var mode by rememberSaveable(day.id) { mutableStateOf(ProgramVisitMode.STANDARD) }
    var shortSets by rememberSaveable(day.id) { mutableIntStateOf(1) }
    val slots = day.slotsFor(mode)
    val eligible = state.exercises.filterNot(Exercise::archived)
    val defaults = day.exercises.mapNotNull { slot ->
        val links = state.programLinks.filter { it.catalogExerciseId == slot.exerciseId }
        val candidates = eligible.filter { exercise ->
            links.any { it.exerciseId == exercise.id } ||
                exercise.name.exerciseDisplayName(vm.container.context).equals(slot.name, ignoreCase = true)
        }
        candidates.singleOrNull()?.let { slot.exerciseId to it.id }
    }.toMap()
    var encoded by rememberSaveable(program.id, day.id, enrolling) { mutableStateOf("{}") }
    val choices = defaults + Json.decodeFromString<Map<String, String>>(encoded)
    val chosen = slots.associate { it.exerciseId to choices[it.exerciseId]?.takeIf { id -> id != CREATE_EXERCISE } }
    val firstMissing = slots.firstOrNull { slot ->
        val id = choices[slot.exerciseId]
        id != CREATE_EXERCISE && eligible.none { it.id == id }
    }
    val rows = remember(day) { day.exercises.associate { it.exerciseId to BringIntoViewRequester() } }
    var openSlot by rememberSaveable(program.id, day.id, enrolling) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Page(Modifier.imePadding(), topBar = header, bottomBar = {
        if (enrolling) {
            PrimaryButton(stringResource(R.string.use_program), modifier = Modifier.fillMaxWidth(), onClick = {
                if (firstMissing != null) {
                    scope.launch {
                        rows.getValue(firstMissing.exerciseId).bringIntoView()
                        openSlot = firstMissing.exerciseId
                    }
                } else {
                    vm.write {
                        enrollProgram(catalog.schemaVersion, program, chosen, expectedEnrollment = state.programEnrollment)
                        onDone()
                    }
                }
            })
        } else if (enrollment != null) {
            PrimaryButton(stringResource(R.string.start_workout), modifier = Modifier.fillMaxWidth(), enabled = firstMissing == null, onClick = {
                onStart(ProgramVisit(enrollment, day, mode, shortSets, chosen, program.sequence))
            })
        }
    }) {
        Text(stringResource(R.string.program_day_title, day.id, day.name), style = MaterialTheme.typography.titleMedium)
        if (!enrolling && enrollment != null) {
            ProgramWeek(enrollment, program)
            SegmentedControl(ProgramVisitMode.entries.map { stringResource(it.label()) }, mode.ordinal, { mode = ProgramVisitMode.entries[it] })
            if (mode == ProgramVisitMode.SHORT) {
                OutlineButton(pluralStringResource(R.plurals.short_sets_per_exercise, shortSets, shortSets), modifier = Modifier.fillMaxWidth(), onClick = { shortSets = 3 - shortSets })
            }
        }
        ListCard {
            slots.forEach { slot ->
                item {
                    ProgramEquipmentChoice(
                        slot, eligible, choices[slot.exerciseId], openSlot == slot.exerciseId,
                        Modifier.bringIntoViewRequester(rows.getValue(slot.exerciseId)),
                        onOpen = { openSlot = slot.exerciseId }, onClose = { openSlot = null }
                    ) { id ->
                        encoded = Json.encodeToString(Json.decodeFromString<Map<String, String>>(encoded) + (slot.exerciseId to id))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramWeek(enrollment: ProgramEnrollment, program: TrainingProgram) {
    Text(
        stringResource(R.string.program_week, enrollment.phaseWeek, program.durationWeeks),
        style = MaterialTheme.typography.bodyMedium,
        color = InShapeTheme.colors.textSecondary
    )
    val context = LocalContext.current
    program.phases.firstOrNull { enrollment.phaseWeek in it.weeks.first()..it.weeks.last() }?.let {
        Text(it.goal)
        Text(stringResource(R.string.program_phase_sets_and_effort, it.sets, it.effort.localizedProgramEffort(context)))
    }
    if (enrollment.phaseWeek == program.durationWeeks) Text(stringResource(R.string.keep_or_repeat_program))
}

private val programEffortRange = Regex("""RIR (\d+)(?:[-–](\d+))?""")

internal fun String.localizedProgramEffort(context: Context): String = programEffortRange.replace(this) { match ->
    val range = listOfNotNull(match.groups[1]?.value, match.groups[2]?.value).joinToString("–")
    context.getString(R.string.reps_in_reserve, range)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEquipmentChoice(
    slot: ProgramSlot, eligible: List<Exercise>, selected: String?, expanded: Boolean,
    modifier: Modifier, onOpen: () -> Unit, onClose: () -> Unit, onChoose: (String) -> Unit
) {
    val context = LocalContext.current
    val compact = LocalWindowInfo.current.containerDpSize.width < Dimens.pickerWidth && LocalDensity.current.fontScale >= 1.3f
    val exercise = eligible.find { it.id == selected }
    val choice = when {
        selected == CREATE_EXERCISE -> stringResource(R.string.create_from_template)
        exercise != null -> exercise.nickname.trim().takeIf(String::isNotEmpty)
            ?: exercise.equipmentName.exerciseDisplayName(context).trim()
                .takeUnless { it.isEmpty() || it.equals(slot.name, ignoreCase = true) }
            ?: exercise.settings.firstOrNull { it.value.isNotBlank() }
                ?.let { "${it.label.trim()} ${it.value.trim()}".trim() }
        else -> stringResource(R.string.program_choose)
    }
    ListRow(
        slot.name,
        modifier,
        trailing = if (choice == null) ({
            Icon(Icons.Rounded.Check, stringResource(R.string.program_equipment_selected), tint = InShapeTheme.colors.textSecondary)
        }) else null,
        trailingValue = choice?.let { value ->
            { Text(value, Modifier.widthIn(max = Dimens.artworkTextMinWidth), style = MaterialTheme.typography.bodyMedium, color = InShapeTheme.colors.textSecondary) }
        },
        onClick = onOpen,
        showChevron = choice != null || !compact
    )
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = onClose,
            containerColor = InShapeTheme.colors.background,
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                Text(slot.name, style = MaterialTheme.typography.titleLarge)
                ListCard {
                    item {
                        ListRow(stringResource(R.string.create_from_template), subtitle = slot.name, onClick = {
                            onChoose(CREATE_EXERCISE)
                            onClose()
                        })
                    }
                    eligible.sortedBy { it.name.lowercase() }.forEach { existing ->
                        item {
                            ListRow(existing.name.exerciseDisplayName(context), subtitle = existing.identity(context), onClick = {
                                onChoose(existing.id)
                                onClose()
                            })
                        }
                    }
                }
            }
        }
    }
}

private fun Exercise.identity(context: Context): String = listOf(name.exerciseDisplayName(context), location.locationLabel(context), equipmentName.exerciseDisplayName(context), nickname, settings.joinToString { "${it.label} ${it.value}" })
    .filter(String::isNotBlank).distinct().joinToString(", ")

private fun ProgramVisitMode.label() = when (this) {
    ProgramVisitMode.STANDARD -> R.string.standard_workout
    ProgramVisitMode.SHORT -> R.string.program_mode_short
    ProgramVisitMode.LIGHT -> R.string.program_mode_light
}

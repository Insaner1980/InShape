package com.emmah.inshape.feature.program

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.ProgramEnrollment
import com.emmah.inshape.data.ProgramExerciseLink
import com.emmah.inshape.feature.library.exerciseDisplayName
import com.emmah.inshape.feature.library.locationLabel
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.HeaderIconButton
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
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
    onEdit: (String) -> Unit,
    onStart: (ProgramVisit) -> Unit
) {
    val catalog = vm.container.programs
    val enrollment = state.programEnrollment?.takeIf { it.programId == program.id && it.catalogVersion == catalog.schemaVersion }
    val dayKey = program.sequence.getOrNull(if (enrolling) 0 else enrollment?.nextDayIndex ?: 0)
    val day = program.sessions.find { it.id == dayKey }
    val header: @Composable () -> Unit = {
        ProgramSetupHeader(program, enrollment, enrolling, day != null, vm, onBack)
    }
    if (day == null || (!enrolling && enrollment == null)) {
        Page(topBar = header) { Text(stringResource(R.string.program_day_unavailable)) }
        return
    }
    var mode by rememberSaveable(day.id) { mutableStateOf(ProgramVisitMode.STANDARD) }
    var shortSets by rememberSaveable(day.id) { mutableIntStateOf(1) }
    val slots = day.slotsFor(mode)
    val savedDay = state.programDayOverrides.find {
        it.catalogVersion == catalog.schemaVersion && it.programId == program.id && it.dayId == day.id
    }
    val eligible = state.exercises.filterNot(Exercise::archived)
    val defaults = day.defaultEquipment(eligible, state.programLinks, savedDay != null, vm.container.context)
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
                        if (firstMissing.localExerciseId == null) openSlot = firstMissing.exerciseId else onEdit(day.id)
                    }
                } else {
                    vm.write {
                        enrollProgram(
                            catalog.schemaVersion,
                            program,
                            chosen,
                            expectedEnrollment = state.programEnrollment,
                            expectedOverride = savedDay
                        )
                        onDone()
                    }
                }
            })
        } else if (enrollment != null) {
            PrimaryButton(
                stringResource(
                    R.string.start_workout
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = firstMissing == null,
                onClick = {
                    onStart(ProgramVisit(enrollment, day, mode, shortSets, chosen, program.sequence, expectedOverride = savedDay))
                }
            )
        }
    }) {
        Text(stringResource(R.string.program_day_title, day.id, day.name), style = MaterialTheme.typography.titleMedium)
        OutlineButton(stringResource(R.string.edit_program_days), { onEdit(day.id) }, Modifier.fillMaxWidth())
        if (!enrolling && enrollment != null) {
            ProgramWeek(enrollment, program)
            SegmentedControl(ProgramVisitMode.entries.map { stringResource(it.label()) }, mode.ordinal, {
                mode =
                    ProgramVisitMode.entries[it]
            })
            if (mode == ProgramVisitMode.SHORT) {
                OutlineButton(
                    pluralStringResource(
                        R.plurals.short_sets_per_exercise,
                        shortSets,
                        shortSets
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        shortSets =
                            3 - shortSets
                    }
                )
            }
        }
        ListCard {
            slots.forEach { slot ->
                item {
                    if (slot.localExerciseId != null) {
                        ListRow(
                            slot.name.exerciseDisplayName(LocalContext.current),
                            Modifier.bringIntoViewRequester(rows.getValue(slot.exerciseId)),
                            subtitle = if (choices[slot.exerciseId] == null) stringResource(R.string.program_archived_movement) else null,
                            showChevron = false
                        )
                    } else {
                        ProgramEquipmentChoice(
                            slot,
                            eligible,
                            choices[slot.exerciseId],
                            openSlot == slot.exerciseId,
                            onOpen = { openSlot = slot.exerciseId },
                            onClose = { openSlot = null },
                            onChoose = { id ->
                                val updated = Json.decodeFromString<Map<String, String>>(encoded) + (slot.exerciseId to id)
                                encoded = Json.encodeToString(updated)
                            },
                            modifier = Modifier.bringIntoViewRequester(rows.getValue(slot.exerciseId))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramSetupHeader(
    program: TrainingProgram,
    enrollment: ProgramEnrollment?,
    enrolling: Boolean,
    hasDay: Boolean,
    vm: JournalViewModel,
    onBack: () -> Unit
) {
    if (enrolling || enrollment == null || !hasDay) {
        PushedHeader(onBack, title = stringResource(if (enrolling) R.string.choose_equipment else R.string.program_workout))
    } else {
        var menu by remember { mutableStateOf(false) }
        val nextWeek = if (enrollment.phaseWeek < program.durationWeeks) enrollment.phaseWeek + 1 else 1
        PushedHeader(onBack, title = stringResource(R.string.program_workout)) {
            Box {
                HeaderIconButton(Icons.Outlined.MoreVert, stringResource(R.string.workout_options), { menu = true })
                AppMenu(menu, { menu = false }, inHeader = true) {
                    MenuAction(
                        if (nextWeek == 1) {
                            stringResource(R.string.repeat_week_one)
                        } else {
                            stringResource(R.string.advance_to_week, nextWeek)
                        },
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

private fun ProgramDay.defaultEquipment(
    eligible: List<Exercise>,
    links: List<ProgramExerciseLink>,
    createMissing: Boolean,
    context: Context
): Map<String, String> =
    exercises.mapNotNull { slot ->
        val personalId = slot.localExerciseId
        if (personalId != null) {
            return@mapNotNull eligible.find { it.id == personalId }?.let { slot.exerciseId to it.id }
        }
        val slotLinks = links.filter { it.catalogExerciseId == slot.exerciseId }
        val candidates = eligible.filter { exercise ->
            slotLinks.any { it.exerciseId == exercise.id } ||
                slot.matchesExerciseName(exercise.name.exerciseDisplayName(context))
        }
        val choice = candidates.singleOrNull()?.id ?: CREATE_EXERCISE.takeIf {
            createMissing && candidates.isEmpty()
        }
        choice?.let { slot.exerciseId to it }
    }.toMap()

@Composable
private fun ColumnScope.ProgramWeek(enrollment: ProgramEnrollment, program: TrainingProgram) {
    Text(
        stringResource(R.string.program_week, enrollment.phaseWeek, program.durationWeeks),
        style = MaterialTheme.typography.bodyMedium,
        color = InShapeTheme.colors.textSecondary
    )
    program.phases.firstOrNull { enrollment.phaseWeek in it.weeks.first()..it.weeks.last() }?.let {
        Text(stringResource(R.string.program_phase_sets_and_effort, it.sets, it.effort))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEquipmentChoice(
    slot: ProgramSlot,
    eligible: List<Exercise>,
    selected: String?,
    expanded: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exercise = eligible.find { it.id == selected }
    val choice = when {
        selected == CREATE_EXERCISE -> stringResource(R.string.create_from_template)

        exercise != null -> exercise.nickname.trim().takeIf(String::isNotEmpty)
            ?: exercise.equipmentName.exerciseDisplayName(context).trim()
                .takeUnless { it.isEmpty() || slot.matchesExerciseName(it) }
            ?: exercise.settings.firstOrNull { it.value.isNotBlank() }
                ?.let { "${it.label.trim()} ${it.value.trim()}".trim() }

        else -> stringResource(R.string.program_choose)
    }
    ListRow(
        slot.name,
        modifier,
        trailing = if (choice == null) {
            (
                {
                    Icon(
                        Icons.Rounded.Check,
                        stringResource(R.string.program_equipment_selected),
                        tint = InShapeTheme.colors.textSecondary
                    )
                }
                )
        } else {
            null
        },
        trailingValue = choice?.let { value ->
            {
                Text(
                    value,
                    Modifier.widthIn(max = Dimens.artworkTextMinWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InShapeTheme.colors.textSecondary
                )
            }
        },
        onClick = onOpen,
        showChevron = false
    )
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = onClose,
            containerColor = InShapeTheme.colors.background,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
            ) {
                Text(slot.name, style = MaterialTheme.typography.titleLarge)
                ListCard {
                    item {
                        ListRow(stringResource(R.string.create_from_template), onClick = {
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

private fun Exercise.identity(context: Context): String = listOf(
    nickname,
    equipmentName.exerciseDisplayName(context),
    location.locationLabel(context),
    settings.firstOrNull { it.value.isNotBlank() }?.let { "${it.label} ${it.value}" }.orEmpty()
).filter { it.isNotBlank() && it != name.exerciseDisplayName(context) }.distinct().joinToString(", ")

private fun ProgramVisitMode.label() = when (this) {
    ProgramVisitMode.STANDARD -> R.string.standard_workout
    ProgramVisitMode.SHORT -> R.string.program_mode_short
    ProgramVisitMode.LIGHT -> R.string.program_mode_light
}

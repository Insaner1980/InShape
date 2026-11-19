package com.emmah.inshape.feature.workout

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.feature.library.ExercisePicker
import com.emmah.inshape.feature.library.ExercisePickerList
import com.emmah.inshape.feature.music.MiniPlayer
import com.emmah.inshape.feature.music.MusicCatalog
import com.emmah.inshape.feature.music.MusicChooser
import com.emmah.inshape.feature.music.MusicPlayback
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.NumberField
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.ProgressSegments
import com.emmah.inshape.ui.components.ProgressState
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.formatElapsed
import com.emmah.inshape.ui.theme.hasArtworkColumns
import com.emmah.inshape.ui.theme.journalDateLabel
import com.emmah.inshape.ui.theme.rememberElapsedSeconds
import com.emmah.inshape.ui.theme.workoutTime
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun itemSummary(item: WorkoutItem): String = if (item.completedSets.isNotEmpty()) {
    item.completedSets.groupBy {
        it.load to
            it.reps
    }.entries.joinToString("; ") { (values, sets) ->
        listOfNotNull(
            "${sets.size} ${if (sets.size == 1) "set" else "sets"}",
            values.second?.let {
                "$it reps"
            },
            values.first?.let {
                MeasurementBasis.fromKey(item.row.basis).formatValue(it)
            }
        ).joinToString(", ")
    }
} else if (item.row.quickCompletedAt !=
    null
) {
    item.row.durationSeconds?.let {
        stringResource(R.string.done_with_duration, com.emmah.inshape.ui.theme.formatDuration(it))
    } ?: stringResource(R.string.workout_item_done)
} else {
    "Not completed"
}

// Keep route actions and the optional demo music fixture explicit at the navigation boundary.
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutScreen(
    id: String,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onExercise: (String, String) -> Unit,
    onCheckIn: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    musicPlayback: MusicPlayback? = null,
    musicFixture: MusicCatalog? = null
) {
    val workout = data.workout(id) ?: return
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val colors = InShapeTheme.colors
    var picker by remember { mutableStateOf(false) }
    var alternatives by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<String?>(null) }
    var routineSave by remember { mutableStateOf(false) }
    var showRemoveSetHint by remember { mutableStateOf(!vm.container.hasRemovedSet) }
    val pending = workout.items.filter { it.row.disposition in listOf("PLANNED", "IN_PROGRESS") }
    val current = pending.find { it.row.id == workout.row.currentItemId } ?: pending.firstOrNull()
    var workoutMenu by remember { mutableStateOf(false) }
    val elapsed by rememberElapsedSeconds(
        workout.row.start?.takeUnless {
            workout.row.timingNeedsReview
        }?.let(Instant::parse),
        vm.container.clock
    )
    var duration by rememberSaveable(current?.row?.id) {
        mutableStateOf(data.drafts.find { it.id == "duration:${current?.row?.id}" }?.payload ?: "")
    }
    val scrollState = rememberScrollState()
    val setRequester = remember { BringIntoViewRequester() }
    val firstIncomplete = current?.sets?.firstOrNull { it.completedAt == null }?.id
    val dragging by scrollState.interactionSource.collectIsDraggedAsState()
    var previousItemId by remember(id) { mutableStateOf<String?>(null) }
    var previousSetId by remember(id) { mutableStateOf<String?>(null) }
    var titleTop by remember(current?.row?.id) { mutableFloatStateOf(Float.NaN) }
    var viewportTop by remember { mutableFloatStateOf(Float.NaN) }
    var clockHeight by remember { mutableIntStateOf(0) }
    val screenTopPx = with(LocalDensity.current) { Dimens.screenTop.roundToPx() }
    val showTopClock by remember(scrollState, screenTopPx) {
        derivedStateOf { clockHeight > 0 && scrollState.value >= clockHeight + screenTopPx }
    }
    var chooser by rememberSaveable { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(current?.row?.id, firstIncomplete) {
        val itemChanged = previousItemId != current?.row?.id
        val setChanged = previousSetId != firstIncomplete
        previousItemId = current?.row?.id
        previousSetId = firstIncomplete
        if (current != null && !keyboardVisible && !dragging) {
            withFrameNanos { }
            if (itemChanged && workout.doneCount > 0) {
                val target = snapshotFlow {
                    if (titleTop.isFinite() && viewportTop.isFinite()) {
                        (scrollState.value + titleTop - viewportTop).roundToInt().coerceIn(0, scrollState.maxValue)
                    } else {
                        null
                    }
                }.filterNotNull().first()
                if (!dragging) scrollState.animateScrollTo(target)
            }
            val hasRecordedProgress = workout.doneCount > 0 || current.completedSets.isNotEmpty()
            val hasNewIncompleteSet = setChanged && firstIncomplete != null
            if (!dragging && hasNewIncompleteSet && hasRecordedProgress) {
                setRequester.bringIntoView()
            }
        }
    }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val inlinePlayer = LocalDensity.current.fontScale >= 1.3f || windowHeight < Dimens.inlinePlayerWindowHeight
    val addFirstExercise = workout.items.isEmpty() && !workout.isPastEntry
    val bottomAction: (@Composable () -> Unit)? = if (keyboardVisible) {
        null
    } else {
        {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                if (musicPlayback != null && !inlinePlayer) {
                    MiniPlayer(musicPlayback, "Choose music", { chooser = true })
                }
                PrimaryButton(
                    when {
                        addFirstExercise -> "Add exercise"
                        current != null -> "Exercise done"
                        workout.isPastEntry -> "Save workout"
                        else -> "Finish workout"
                    },
                    {
                        if (addFirstExercise) {
                            picker = true
                        } else if (current == null) {
                            onFinish()
                        } else {
                            vm.write {
                                val seconds = if (duration.isBlank()) {
                                    null
                                } else {
                                    duration.toLongOrNull()?.takeIf {
                                        it >
                                            0 &&
                                            it <= Long.MAX_VALUE / 60
                                    }?.times(60)
                                        ?: throw IllegalArgumentException("Enter positive whole minutes")
                                }
                                markItemDone(current.row.id, seconds)
                                deleteEditorDraft("duration:${current.row.id}")
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Add.takeIf { addFirstExercise },
                    size = ButtonSize.Large
                )
            }
        }
    }
    Page(
        modifier,
        bottomBar = bottomAction,
        topBar = {
            PushedHeader(onBack) {
                elapsed?.takeIf { showTopClock }?.let {
                    Text(formatElapsed(it), style = InShapeType.numeric, modifier = Modifier.padding(end = Dimens.innerSmall))
                }
                if (musicPlayback != null) {
                    IconButton({ chooser = true }) { Icon(Icons.Outlined.MusicNote, "Music") }
                }
                OutlineButton(if (workout.isPastEntry) "Save" else "Finish", onFinish)
                Box {
                    IconButton({ workoutMenu = true }) { Icon(Icons.Outlined.MoreVert, "Workout options") }
                    AppMenu(workoutMenu, { workoutMenu = false }) {
                        MenuAction("Add exercise", Icons.Outlined.Add, {
                            workoutMenu = false
                            picker = true
                        })
                        val checkInLabel = if (workout.doneCount ==
                            0
                        ) {
                            stringResource(R.string.pre_workout_check_in)
                        } else {
                            stringResource(R.string.add_check_in)
                        }
                        MenuAction(checkInLabel, Icons.Outlined.WbTwilight, {
                            workoutMenu = false
                            onCheckIn()
                        })
                        if (data.routines.any { it.id == workout.row.sourceRoutineId }) {
                            MenuAction("Save changes to routine", Icons.Outlined.Save, {
                                workoutMenu = false
                                routineSave = true
                            })
                        }
                        HorizontalDivider(color = colors.divider)
                        MenuAction("Discard workout", Icons.Outlined.Delete, {
                            workoutMenu = false
                            discard = true
                        }, destructive = true)
                    }
                }
            }
        },
        scrollState = scrollState,
        scrollViewportModifier = Modifier.onGloballyPositioned { viewportTop = it.positionInRoot().y }
    ) {
        if (musicPlayback != null && inlinePlayer && !keyboardVisible) {
            MiniPlayer(musicPlayback, "Choose music", { chooser = true })
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().onSizeChanged { clockHeight = it.height }
                .clip(InShapeShapes.card).background(ArtworkWash.Active.brush()),
            contentAlignment = Alignment.Center
        ) {
            if (elapsed != null && hasArtworkColumns()) {
                val flip = LocalLayoutDirection.current == LayoutDirection.Rtl
                val motion = if (colors.isDark) R.drawable.art_training_motion_dark else R.drawable.art_training_motion_light
                Image(
                    painterResource(motion),
                    null,
                    Modifier.align(Alignment.CenterEnd).width(minOf(maxWidth * .4f, (maxWidth - 204.dp) / 2))
                        .height(64.dp).scale(if (flip) -1f else 1f, 1f)
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = Dimens.innerSmall),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
            ) {
                Text(workout.row.title, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                if (elapsed != null) {
                    Text(formatElapsed(elapsed!!), style = MaterialTheme.typography.displayLarge)
                    workout.row.start?.let {
                        Text(
                            "Started ${Instant.parse(it).workoutTime(ZoneId.of(workout.row.zoneId), uses24Hours)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                    }
                } else {
                    Text(
                        "Started ${workout.row.journalDate.journalDateLabel()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Text("Review the timing before saving.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }
        if (workout.items.isNotEmpty()) {
            ProgressSegments(
                workout.items.map {
                    when {
                        it.row.disposition == "DONE" -> ProgressState.Done

                        it.row.id ==
                            current?.row?.id -> ProgressState.Current

                        else -> ProgressState.Pending
                    }
                },
                "${workout.doneCount} of ${workout.items.size} done"
            )
            Text(
                if (current == null) "All exercises done" else "Exercise ${workout.items.indexOf(current) + 1} of ${workout.items.size}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        if (current == null) {
            val empty = workout.items.isEmpty()
            EmptyState(
                if (empty) "No exercises yet" else "Your plan is complete",
                if (empty) "Add exercises as you go." else "Add another exercise or finish with the work you recorded.",
                action = "Add exercise".takeUnless { addFirstExercise },
                onAction = { picker = true },
                icon = Icons.Outlined.Add
            )
        }
        current?.let { item ->
            var menu by remember(item.row.id) { mutableStateOf(false) }
            var exerciseNote by rememberSaveable(item.row.id) { mutableStateOf(item.row.note) }
            var expanded by rememberSaveable(item.row.id) {
                mutableStateOf(false)
            }
            var focusNote by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(expanded, focusNote) {
                if (expanded &&
                    focusNote
                ) {
                    focus.requestFocus()
                    focusNote = false
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                Row(
                    Modifier.fillMaxWidth().onGloballyPositioned { titleTop = it.positionInRoot().y },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.row.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Box {
                        IconButton({
                            menu = true
                        }) { Icon(Icons.Outlined.MoreVert, "More options for ${item.row.name}") }
                        AppMenu(menu, { menu = false }) {
                            MenuAction("Exercise details", Icons.Outlined.Info, {
                                menu = false
                                onExercise(item.row.exerciseId, item.row.id)
                            })
                            MenuAction(if (exerciseNote.isBlank()) "Add note" else "Edit note", Icons.Outlined.EditNote, {
                                menu = false
                                expanded = true
                                focusNote = true
                            })
                            MenuAction("Swap exercise", Icons.Outlined.SwapHoriz, {
                                menu = false
                                alternatives = true
                            })
                            MenuAction("Do later", Icons.Outlined.Schedule, {
                                menu = false
                                vm.write { deferItem(item.row.id) }
                            })
                            MenuAction("Skip today", Icons.Outlined.SkipNext, {
                                menu = false
                                vm.write { skipItem(item.row.id) }
                            })
                            HorizontalDivider(color = colors.divider)
                            MenuAction("Remove exercise", Icons.Outlined.Delete, {
                                menu = false
                                if (item.hasPerformedWork) remove = item.row.id else vm.write { removeItem(item.row.id) }
                            }, destructive = true)
                        }
                    }
                }
                val settings = item.row.setup.split(", ").mapNotNull { raw ->
                    val entry = raw.trim()
                    val label = entry.substringBefore(": ")
                    val value = entry.substringAfter(": ")
                    when {
                        value.isBlank() || value.equals("None", ignoreCase = true) -> null

                        label != value && label.equals("Handle", ignoreCase = true) -> value

                        label != value && label.equals("Seat height", ignoreCase = true) -> "Seat $value"

                        label != value -> "$label $value"

                        // Older journal entries stored the label and value without a colon.
                        entry.endsWith(" None", ignoreCase = true) -> null

                        entry.startsWith("Handle ", ignoreCase = true) -> entry.drop("Handle ".length)

                        entry.startsWith("Seat height ", ignoreCase = true) -> "Seat " + entry.drop("Seat height ".length)

                        else -> entry
                    }
                }
                if (settings.isNotEmpty()) ActionRow { settings.forEach { Pill(it, style = MaterialTheme.typography.labelLarge) } }
                val previous = data.history.filter {
                    it.row.id != id
                }.sortedWith(
                    compareByDescending<Workout> {
                        it.row.journalDate
                    }.thenByDescending { it.row.createdAt }
                ).firstNotNullOfOrNull { old ->
                    old.items.find {
                        it.row.exerciseId == item.row.exerciseId &&
                            it.row.equipmentId == item.row.equipmentId &&
                            it.row.basis == item.row.basis &&
                            it.hasPerformedWork
                    }?.let { old.row.journalDate to it }
                }
                if (previous != null) {
                    InShapeCard(inset = true, gap = Dimens.innerTiny) {
                        Text(
                            "Last time, ${previous.first.journalDateLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            itemSummary(previous.second),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        )
                        previous.second.row.note.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                    }
                }
                if (item.sets.isNotEmpty()) SetTableHeader(item.row.basis)
                item.sets.forEachIndexed { index, set ->
                    key(set.id) {
                        SetRow(
                            set,
                            index + 1,
                            item.row.basis,
                            vm,
                            modifier = Modifier.testTag("workout-set-${index + 1}").then(
                                if (set.id == firstIncomplete) Modifier.bringIntoViewRequester(setRequester) else Modifier
                            ),
                            highlighted = set.id == firstIncomplete,
                            onRemove = { showRemoveSetHint = false }
                        )
                    }
                }
                if (showRemoveSetHint && item == workout.items.firstOrNull() && item.sets.isNotEmpty()) {
                    Text(
                        "Long-press a set to remove it",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                TextAction("Add set", {
                    vm.write { addSet(item.row.id) }
                }, icon = Icons.Outlined.Add)
                if (item.row.mode == "DURATION" || item.sets.isEmpty() ||
                    duration.isNotEmpty()
                ) {
                    NumberField(duration, {
                        duration = it
                        vm.saveDraft("duration:${item.row.id}", "duration", it)
                    }, "Performed minutes (optional)")
                }
                if (expanded) {
                    TextField(exerciseNote, "Note", {
                        exerciseNote = it
                        vm.write { saveItemNote(item.row.id, note = it) }
                    }, modifier = Modifier.focusRequester(focus), placeholder = "For this exercise")
                    ListRow("This movement felt uncomfortable", trailing = {
                        Checkbox(item.row.discomfort, { value ->
                            vm.write { saveItemNote(item.row.id, discomfort = value) }
                        })
                    })
                }
                if (!expanded && exerciseNote.isNotBlank()) {
                    Text(
                        exerciseNote,
                        Modifier.fillMaxWidth().heightIn(min = Dimens.touch).clickable {
                            expanded = true
                            focusNote = true
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }
        val next = pending.filter { it.row.id != current?.row?.id }
        if (next.isNotEmpty()) {
            SectionHeader("Up next")
            ListCard {
                next.forEach { nextItem ->
                    item {
                        ListRow(nextItem.row.name, trailing = {
                            Text(
                                nextItem.row.targetDurationSeconds?.let { com.emmah.inshape.ui.theme.formatDuration(it) }
                                    ?: if (nextItem.row.mode ==
                                        "QUICK"
                                    ) {
                                        "Completion only"
                                    } else {
                                        "${nextItem.sets.size} sets"
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        })
                    }
                }
            }
        }
        val done = workout.items.filter { it.row.disposition == "DONE" }
        if (done.isNotEmpty()) {
            SectionHeader("Done")
            ListCard {
                done.forEach { doneItem ->
                    item {
                        ListRow(doneItem.row.name, subtitle = itemSummary(doneItem), trailing = {
                            IconButton({
                                vm.write { reopenItem(doneItem.row.id) }
                            }) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    "Reopen ${doneItem.row.name}",
                                    tint = colors.ink
                                )
                            }
                        })
                    }
                }
            }
        }
        val skipped = workout.items.filter { it.row.disposition in listOf("SKIPPED", "INCOMPLETE") }
        if (skipped.isNotEmpty()) {
            SectionHeader("Skipped")
            ListCard {
                skipped.forEach { skippedItem ->
                    item {
                        ListRow(
                            skippedItem.row.name,
                            subtitle = if (skippedItem.hasPerformedWork) "Remainder skipped, recorded work kept" else "Skipped today",
                            trailing = {
                                TextAction("Reopen", {
                                    vm.write { reopenItem(skippedItem.row.id) }
                                }, icon = Icons.AutoMirrored.Outlined.Undo)
                            }
                        )
                    }
                }
            }
        }
    }
    if (chooser && musicPlayback != null) {
        MusicChooser(vm.container.music, musicPlayback, { chooser = false }, musicFixture)
    }
    if (picker) {
        ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = colors.background) {
            ExercisePicker(data, vm.container.photos, {
                vm.write { addExercise(id, it) }
                picker =
                    false
            }, {
                picker = false
                onExercise("new", "")
            })
        }
    }
    if (alternatives &&
        current != null
    ) {
        ModalBottomSheet(onDismissRequest = {
            alternatives = false
        }, containerColor = colors.background) {
            val currentId = current.row.exerciseId
            val ids = data.exercises.find { it.id == currentId }?.alternatives.orEmpty()
            val saved = data.exercises.filter { it.id != currentId && it.id in ids && !it.archived }
            val swap: (String) -> Unit = { exerciseId ->
                vm.write { useAlternative(current.row.id, exerciseId) }
                alternatives = false
            }
            Page {
                SectionHeader("Your alternatives")
                if (saved.isEmpty()) Text("Add your alternatives by editing this exercise in Library.")
                ListCard {
                    saved.forEach { exercise ->
                        item {
                            ListRow(exercise.name, subtitle = exercise.equipmentName, onClick = { swap(exercise.id) })
                        }
                    }
                }
                SectionHeader("All exercises", quiet = true)
                ExercisePickerList(data, vm.container.photos, swap, excluded = saved.mapTo(mutableSetOf(currentId)) { it.id })
            }
        }
    }
    if (discard) {
        ConfirmDialog("Discard this workout?", "The session and all work recorded in it will be deleted.", "Discard", destructive = true, {
            vm.write {
                deleteWorkout(id)
                onDelete()
            }
            discard =
                false
        }, { discard = false })
    }
    remove?.let { item ->
        ConfirmDialog(
            "Delete recorded work?",
            "All sets, notes and completion for this exercise will be removed from this workout.",
            "Delete",
            destructive = true,
            {
                vm.write { removeItem(item, true) }
                remove =
                    null
            },
            { remove = null }
        )
    }
    if (routineSave) {
        ConfirmDialog(
            "Update the saved routine?",
            "Replace its exercise order and planned targets with:\n" +
                workout.items.joinToString("\n") {
                    "${it.row.name}, ${it.sets.size} planned sets"
                },
            "Update routine",
            destructive = false,
            {
                vm.write { saveSessionPlanToRoutine(id, workout.row.sourceRoutineId!!) }
                routineSave =
                    false
            },
            { routineSave = false }
        )
    }
}

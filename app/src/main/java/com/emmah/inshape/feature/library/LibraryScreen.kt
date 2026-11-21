package com.emmah.inshape.feature.library

import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FileUpload
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.MenuAction
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import com.emmah.inshape.feature.workout.setSummary
import com.emmah.inshape.data.SetTarget
import com.emmah.inshape.ui.components.HeaderIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.data.Workout
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.media.PhotoStore
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.ScreenTabs
import com.emmah.inshape.ui.components.SearchField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.hasArtworkColumns
import java.time.Instant
import java.time.LocalDate

val exerciseCategories =
    listOf("Machine", "Free weight", "Bodyweight", "Warm-up", "Stretching", "Mobility")

data class LastRecordedValue(val value: String, val date: LocalDate)
fun lastRecordedValues(context: Context, history: List<Workout>): Map<String, LastRecordedValue> = buildMap {
    history.filter {
        it.qualifiesForAttendance
    }.sortedWith(
        compareByDescending<Workout> {
            it.row.journalDate
        }.thenByDescending { it.row.createdAt }
    ).forEach { workout ->
        workout.items.filter {
            it.hasPerformedWork
        }.groupBy { it.row.exerciseId }.forEach { (id, items) ->
            if (id !in this) {
                put(id, LastRecordedValue(lastRecordedDisplayValue(context, items), LocalDate.parse(workout.row.journalDate)))
            }
        }
    }
}

private fun lastRecordedDisplayValue(context: Context, items: List<WorkoutItem>): String {
    val recordedSets = items.flatMap { item -> item.completedSets.map { it to item.row.basis } }
    val basis = recordedSets.filter { (set, _) -> set.load != null }
        .maxByOrNull { (set, _) -> Instant.parse(set.completedAt) }?.second
    val duration = items.firstNotNullOfOrNull { it.row.durationSeconds }
    if (basis == null && duration != null) return formatDuration(duration)
    val selectedBasis = basis ?: recordedSets.maxByOrNull { (set, _) -> Instant.parse(set.completedAt) }?.second
    val targets = recordedSets.filter { (_, key) -> key == selectedBasis }.map { (set, _) -> SetTarget(set.load, set.reps) }
    return if (targets.any { it.load != null || it.reps != null }) {
        setSummary(context, MeasurementBasis.fromKey(selectedBasis.orEmpty()), targets)
    } else {
        context.getString(R.string.done)
    }
}

@Composable
fun LibraryScreen(
    data: JournalSnapshot,
    onExercise: (String) -> Unit,
    onRoutine: (String) -> Unit,
    vm: JournalViewModel,
    modifier: Modifier = Modifier,
    onProgram: ((String) -> Unit)? = null,
    onRoutineProposal: (() -> Unit)? = null,
    onImport: ((String) -> Unit)? = null
) {
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    val history = data.history
    val context = LocalContext.current
    val recorded = remember(history, context) { lastRecordedValues(context, history) }
    val firstPhotos = remember(data.photos) { firstPhotosByExercise(data.photos) }
    val colors = InShapeTheme.colors
    var addMenu by remember { mutableStateOf(false) }
    Page(modifier) {
        ScreenHeader(stringResource(R.string.library), trailing = {
            Box {
                val creatingRoutine = segment == 0
                val label = stringResource(if (creatingRoutine) R.string.new_routine else R.string.new_exercise)
                HeaderIconButton(Icons.Outlined.Add, label, { addMenu = true }, primaryAdd = true)
                AppMenu(addMenu, { addMenu = false }) {
                    MenuAction(label, Icons.Outlined.Add, {
                        addMenu = false
                        if (creatingRoutine) onRoutine("new") else onExercise("new")
                    })
                    if (creatingRoutine && onRoutineProposal != null) {
                        MenuAction(stringResource(R.string.build_routine), Icons.Outlined.AutoAwesome, {
                            addMenu = false
                            onRoutineProposal()
                        })
                    }
                    if (onImport != null) {
                        val importIcon = if (creatingRoutine) Icons.Outlined.FileOpen else Icons.Outlined.FileUpload
                        MenuAction(stringResource(if (creatingRoutine) R.string.import_program else R.string.import_equipment), importIcon, {
                            addMenu = false
                            onImport(if (creatingRoutine) "program" else "equipment")
                        })
                    }
                }
            }
        })
        ScreenTabs(listOf(stringResource(R.string.routines), stringResource(R.string.exercises)), segment, { segment = it })
        if (segment == 0) {
            LibraryRoutines(data, onRoutine)
            if (onProgram != null) {
                com.emmah.inshape.feature.program.ProgramList(vm.container.programs, onProgram)
            }
        } else {
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchField(
                    search, { search = it }, stringResource(R.string.search_exercises),
                    Modifier.weight(1f).heightIn(min = Dimens.button).fillMaxHeight()
                )
                Surface(
                    checked = favorites,
                    onCheckedChange = { favorites = it },
                    modifier = Modifier.width(Dimens.button).fillMaxHeight(),
                    shape = InShapeShapes.field,
                    color = colors.background,
                    contentColor = if (favorites) colors.ink else colors.textSecondary,
                    border = BorderStroke(Dimens.line, colors.fieldBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (favorites) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            stringResource(R.string.favorites_only),
                            Modifier.size(Dimens.icon)
                        )
                    }
                }
            }
            val query = search.trim().split(Regex("\\s+"))
            val matching = data.exercises.filter { exercise ->
                !exercise.archived && (!favorites || exercise.favorite) && query.all { term ->
                    listOf(
                        exercise.name, exercise.name.exerciseDisplayName(context), exercise.nickname,
                        exercise.category, exercise.category.categoryLabel(context), exercise.location,
                        exercise.location.locationLabel(context), exercise.equipmentName
                    )
                        .any { it.contains(term, ignoreCase = true) }
                }
            }
            if (matching.isEmpty()) {
                Text(stringResource(R.string.no_matching_exercises), style = MaterialTheme.typography.titleMedium)
            } else {
                ListCard {
                    matching.forEach { exercise ->
                        item {
                            LibraryExerciseRow(
                                exercise, recorded[exercise.id], firstPhotos[exercise.id], vm.container.photos,
                                onClick = { onExercise(exercise.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun exerciseCountLabel(count: Int): String =
    pluralStringResource(R.plurals.exercise_count, count, count)

@Composable
internal fun estimatedDurationLabel(minutes: Int?): String? =
    minutes?.let { stringResource(R.string.approximate_duration, formatDuration(it * 60L)) }

@Composable
internal fun routineSummary(count: Int, minutes: Int?): String = listOfNotNull(
    exerciseCountLabel(count),
    estimatedDurationLabel(minutes)
).joinToString(", ")

@Composable
private fun LibraryRoutines(data: JournalSnapshot, onRoutine: (String) -> Unit) {
    val colors = InShapeTheme.colors
    if (data.routines.isEmpty()) {
        EmptyState(
            stringResource(R.string.no_routines),
            stringResource(R.string.add_your_exercises),
            action = stringResource(R.string.new_routine),
            onAction = { onRoutine("new") },
            icon = Icons.Outlined.Add
        )
    } else {
        ListCard {
            data.routines.forEach { routine ->
                item {
                    val count = data.routineItems.count { it.routineId == routine.id }
                    val description = routine.description.takeIf(String::isNotBlank)
                    ListRow(
                        routine.title,
                        subtitle = routineSummary(count, routine.durationMinutes),
                        onClick = { onRoutine(routine.id) },
                        details = {
                            if (description != null) {
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryExerciseRow(
    exercise: Exercise,
    last: LastRecordedValue?,
    photo: PhotoAsset?,
    photos: PhotoStore,
    onClick: () -> Unit
) {
    val colors = InShapeTheme.colors
    BoxWithConstraints {
        val showArtwork = hasArtworkColumns()
        val thumbnail = exerciseThumbnail(
            exercise,
            photo,
            photos,
            Dimens.button,
            showArtwork
        )
        ListRow(
            exercise.name.exerciseDisplayName(LocalContext.current),
            subtitle = exercise.category.categoryLabel(LocalContext.current),
            leading = if (showArtwork || thumbnail != null) {
                (
                    {
                        Box(Modifier.size(Dimens.button), contentAlignment = Alignment.Center) {
                            thumbnail?.invoke()
                        }
                    }
                    )
            } else {
                null
            },
            trailingValue = last?.let {
                {
                    val large = LocalDensity.current.fontScale >= 1.3f
                    val alignment = if (large) Alignment.Start else Alignment.End
                    val width = if (large) Modifier else Modifier.widthIn(max = maxWidth * .42f)
                    Column(width, horizontalAlignment = alignment) {
                        Text(it.value, style = MaterialTheme.typography.bodyMedium, textAlign = if (large) TextAlign.Start else TextAlign.End)
                        Text(
                            it.date.shortDate(),
                            style = InShapeType.nav,
                            color = colors.textSecondary
                        )
                    }
                }
            },
            onClick = onClick,
            showChevron = false
        )
    }
}

@Composable
fun ExercisePicker(
    data: JournalSnapshot,
    photos: PhotoStore,
    onChoose: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Page(modifier) {
        ExercisePickerList(data, photos, onChoose, onCreate = onCreate)
    }
}

@Composable
fun ExercisePickerList(
    data: JournalSnapshot,
    photos: PhotoStore,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    excluded: Set<String> = emptySet(),
    onCreate: (() -> Unit)? = null
) {
    var search by rememberSaveable { mutableStateOf("") }
    val firstPhotos = remember(data.photos) { firstPhotosByExercise(data.photos) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        SearchField(search, { search = it }, stringResource(R.string.find_exercise), Modifier.fillMaxWidth())
        if (onCreate != null) OutlineButton(stringResource(R.string.create_exercise), onCreate)
        val exercises = data.exercises.filter { !it.archived && it.id !in excluded }
        val matching = exercises.filter { it.name.contains(search.trim(), true) || it.nickname.contains(search.trim(), true) }
        if (matching.isEmpty()) {
            Text(if (exercises.isEmpty()) stringResource(R.string.no_exercises_to_add) else stringResource(R.string.no_matching_exercises))
        } else {
            ListCard {
                matching.forEach { exercise ->
                    item {
                        BoxWithConstraints {
                            ListRow(
                                exercise.name.exerciseDisplayName(LocalContext.current),
                                subtitle = exercise.category.categoryLabel(LocalContext.current),
                                leading = exerciseThumbnail(
                                    exercise,
                                    firstPhotos[exercise.id],
                                    photos,
                                    Dimens.pickerThumbnail,
                                    hasArtworkColumns()
                                ),
                                onClick = { onChoose(exercise.id) },
                                showChevron = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun firstPhotosByExercise(photos: List<PhotoAsset>) = buildMap {
    photos.forEach { putIfAbsent(it.exerciseId, it) }
}

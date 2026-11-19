package com.emmah.inshape.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.Workout
import com.emmah.inshape.media.PhotoStore
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.FilterPill
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.ScreenTabs
import com.emmah.inshape.ui.components.SearchField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.displayDate
import com.emmah.inshape.ui.theme.hasArtworkColumns
import java.time.LocalDate

val exerciseCategories =
    listOf("Machine", "Free weight", "Bodyweight", "Warm-up", "Stretching", "Mobility")

data class LastRecordedValue(val value: String, val date: LocalDate)
fun lastRecordedValues(history: List<Workout>): Map<String, LastRecordedValue> = buildMap {
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
                val loaded = items.flatMap { item ->
                    item.completedSets.mapNotNull { set ->
                        set.load?.toBigDecimal()?.let { Triple(it, set.load, item.row.basis) }
                    }
                }.maxByOrNull { it.first }
                val duration = items.firstNotNullOfOrNull { it.row.durationSeconds }
                val value = when {
                    loaded != null -> MeasurementBasis.fromKey(loaded.third).formatValue(loaded.second)
                    duration != null -> com.emmah.inshape.ui.theme.formatDuration(duration)
                    else -> "Done"
                }
                put(id, LastRecordedValue(value, LocalDate.parse(workout.row.journalDate)))
            }
        }
    }
}

@Composable
fun LibraryScreen(
    data: JournalSnapshot,
    onExercise: (String) -> Unit,
    onRoutine: (String) -> Unit,
    vm: JournalViewModel,
    modifier: Modifier = Modifier
) {
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var location by rememberSaveable { mutableStateOf<String?>(null) }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var locationsMenu by rememberSaveable { mutableStateOf(false) }
    val history = data.history
    val recorded = remember(history) { lastRecordedValues(history) }
    val today = LocalDate.now(vm.container.clock)
    val colors = InShapeTheme.colors
    Page(modifier) {
        ScreenHeader("Library", trailing = {
            OutlineButton(if (segment == 0) "New routine" else "New exercise", {
                if (segment == 0) onRoutine("new") else onExercise("new")
            }, icon = Icons.Outlined.Add)
        })
        ScreenTabs(listOf("Routines", "Exercises"), segment, { segment = it })
        if (segment == 0) {
            if (data.routines.isEmpty()) {
                EmptyState(
                    "Make it your routine",
                    "Add the exercises you want to remember. Planned values stay separate from your recorded work.",
                    action = "New routine",
                    onAction = { onRoutine("new") },
                    icon = Icons.Outlined.Add
                )
            } else {
                ListCard {
                    data.routines.forEach { routine ->
                        item {
                            val count = data.routineItems.count { it.routineId == routine.id }
                            val description = routine.description.takeIf(String::isNotBlank)
                            val duration = routine.durationMinutes?.let { "about $it min" }
                            val exercises = pluralStringResource(R.plurals.exercise_count, count, count)
                            ListRow(
                                routine.title,
                                subtitle = listOfNotNull(exercises, duration).joinToString(", "),
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
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchField(search, { search = it }, "Search exercises", Modifier.weight(1f))
                FilledIconToggleButton(
                    favorites,
                    {
                        favorites = it
                    },
                    Modifier.size(
                        Dimens.touch
                    ),
                    shape = InShapeShapes.field,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = colors.surface,
                        checkedContainerColor = colors.surface,
                        checkedContentColor = colors.ink,
                        contentColor = colors.textSecondary
                    )
                ) {
                    Icon(
                        if (favorites) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        "Favorites only"
                    )
                }
            }
            val locations = data.exercises.filter { !it.archived && it.location.isNotBlank() }.map { it.location }.distinct()
            val activeLocation = location?.takeIf { it in locations }
            if (locations.isNotEmpty()) {
                Box {
                    FilterPill(
                        activeLocation ?: "All locations",
                        activeLocation != null,
                        { locationsMenu = true },
                        showDropdown = true
                    )
                    AppMenu(locationsMenu, { locationsMenu = false }) {
                        MenuAction("All locations", Icons.Outlined.Place, {
                            location = null
                            locationsMenu = false
                        })
                        locations.forEach { label ->
                            MenuAction(label, Icons.Outlined.Place, {
                                location = label
                                locationsMenu = false
                            })
                        }
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
            ) {
                (
                    listOf("All") +
                        exerciseCategories
                    ).forEach { label ->
                    FilterPill(label, category == label, {
                        category =
                            label
                    })
                }
            }
            val matching = data.exercises.filter {
                !it.archived && (!favorites || it.favorite) &&
                    (category == "All" || it.category == category) &&
                    (activeLocation == null || it.location == activeLocation) &&
                    listOf(it.name, it.nickname, it.equipmentName).any { value ->
                        value.contains(search, true)
                    }
            }
            if (matching.isEmpty()) {
                EmptyState("No matching exercises", "Change your filters or add an exercise.")
            } else {
                ListCard {
                    matching.forEach { exercise ->
                        item {
                            BoxWithConstraints {
                                val showArtwork = hasArtworkColumns()
                                val last = recorded[exercise.id]
                                val thumbnail = exerciseThumbnail(
                                    exercise,
                                    data.photos.firstOrNull { it.exerciseId == exercise.id },
                                    vm.container.photos,
                                    Dimens.touch,
                                    showArtwork &&
                                        (last == null || maxWidth >= Dimens.libraryThumbnailTextWidth || last.value.length <= 10)
                                )
                                ListRow(
                                    exercise.name,
                                    subtitle = exercise.nickname.ifBlank {
                                        listOf(
                                            exercise.category,
                                            exercise.equipmentName
                                        ).filter(String::isNotBlank).joinToString(", ")
                                    },
                                    leading = if (showArtwork || thumbnail != null) {
                                        (
                                            {
                                                Box(Modifier.size(Dimens.photoThumbnail), contentAlignment = Alignment.Center) {
                                                    thumbnail?.invoke()
                                                }
                                            }
                                            )
                                    } else {
                                        null
                                    },
                                    trailing = last?.let {
                                        {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(it.value, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    it.date.displayDate(
                                                        if (it.date in
                                                            today.minusDays(6)..today
                                                        ) {
                                                            "EEE"
                                                        } else {
                                                            "d MMM"
                                                        }
                                                    ),
                                                    style = InShapeType.nav,
                                                    color = colors.textSecondary
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onExercise(exercise.id) },
                                    showChevron = false
                                )
                            }
                        }
                    }
                }
            }
        }
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        SearchField(search, { search = it }, "Find exercise", Modifier.fillMaxWidth())
        if (onCreate != null) OutlineButton("Create exercise", onCreate)
        val exercises = data.exercises.filter { !it.archived && it.id !in excluded }
        val matching = exercises.filter { it.name.contains(search.trim(), true) || it.nickname.contains(search.trim(), true) }
        if (matching.isEmpty()) {
            Text(if (exercises.isEmpty()) "No exercises to add." else "No matching exercises.")
        } else {
            ListCard {
                matching.forEach { exercise ->
                    item {
                        BoxWithConstraints {
                            ListRow(
                                exercise.name,
                                subtitle = exercise.equipmentName,
                                leading = exerciseThumbnail(
                                    exercise,
                                    data.photos.firstOrNull { it.exerciseId == exercise.id },
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

package com.emmah.inshape.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.onClick
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Workout
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.FilterPill
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.ScreenHeader
import com.emmah.inshape.ui.components.SearchField
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.displayDate
import java.time.LocalDate

val exerciseCategories =
    listOf("Machine", "Free weight", "Bodyweight", "Warm-up", "Stretching", "Mobility")
val measurementBases = listOf("kg", "level", "bodyweight", "assistance_kg", "duration")

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
                    loaded != null -> when (loaded.third) {
                        "level" -> "level ${loaded.second}"
                        "assistance_kg" -> "${loaded.second} kg assistance"
                        else -> "${loaded.second} ${loaded.third}"
                    }

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
        SegmentedControl(listOf("Routines", "Exercises"), segment, { segment = it })
        if (segment == 0) {
            if (data.routines.isEmpty()) {
                EmptyState(
                    "Make it your routine",
                    "Add the exercises you want to remember. Planned values stay separate from your recorded work."
                )
            } else {
                ListCard {
                    data.routines.forEach { routine ->
                        item {
                            val count = data.routineItems.count { it.routineId == routine.id }
                            val detail = routine.description.takeIf(String::isNotBlank)
                                ?: routine.durationMinutes?.let { "about $it min" }
                            val exercises = pluralStringResource(R.plurals.exercise_count, count, count)
                            ListRow(
                                routine.title,
                                subtitle = listOfNotNull(exercises, detail).joinToString(", "),
                                onClick = { onRoutine(routine.id) }
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
            ) {
                if (locations.isNotEmpty()) {
                    Box {
                        FilterPill(
                            activeLocation ?: "All locations",
                            activeLocation != null,
                            { locationsMenu = true },
                            showDropdown = true
                        )
                        AppMenu(locationsMenu, { locationsMenu = false }) {
                            DropdownMenuItem({ Text("All locations") }, {
                                location = null
                                locationsMenu = false
                            })
                            locations.forEach { label ->
                                DropdownMenuItem({ Text(label) }, {
                                    location = label
                                    locationsMenu = false
                                })
                            }
                        }
                    }
                }
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
                            val last = recorded[exercise.id]
                            ListRow(
                                exercise.name,
                                subtitle = exercise.nickname.ifBlank {
                                    listOf(
                                        exercise.category,
                                        exercise.equipmentName
                                    ).filter(String::isNotBlank).joinToString(", ")
                                },
                                leading = data.photos.firstOrNull { it.exerciseId == exercise.id }?.let { photo ->
                                    {
                                        PhotoImage(
                                            photo,
                                            vm.container.photos,
                                            Modifier.size(Dimens.touch).clip(InShapeShapes.field),
                                            maxSize = 256
                                        )
                                    }
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

@Composable
fun ExercisePicker(data: JournalSnapshot, onChoose: (String) -> Unit, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    Page(modifier) {
        SearchField(search, { search = it }, "Find exercise", Modifier.fillMaxWidth())
        OutlineButton("Create exercise", onCreate)
        val matching = data.exercises.filter {
            !it.archived &&
                (it.name.contains(search, true) || it.nickname.contains(search, true))
        }
        if (matching.isNotEmpty()) {
            ListCard {
                matching.forEach { exercise ->
                    item {
                        ListRow(exercise.name, subtitle = exercise.equipmentName, onClick = {
                            onChoose(exercise.id)
                        }, showChevron = false)
                    }
                }
            }
        }
        if (data.exercises.none { !it.archived }) Text("Create an exercise to add it here.")
    }
}

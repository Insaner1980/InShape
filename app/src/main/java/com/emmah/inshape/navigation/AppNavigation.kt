// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.emmah.inshape.SaveState
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.Routine
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.data.Workout
import com.emmah.inshape.feature.checkin.CheckInScreen
import com.emmah.inshape.feature.history.HistoryScreen
import com.emmah.inshape.feature.history.WorkoutDetailScreen
import com.emmah.inshape.feature.library.ExerciseEditor
import com.emmah.inshape.feature.library.ExerciseScreen
import com.emmah.inshape.feature.library.LibraryScreen
import com.emmah.inshape.feature.library.RoutineEditor
import com.emmah.inshape.feature.progress.ProgressScreen
import com.emmah.inshape.feature.settings.SettingsScreen
import com.emmah.inshape.feature.today.TodayScreen
import com.emmah.inshape.feature.workout.FinishScreen
import com.emmah.inshape.feature.workout.WorkoutScreen
import com.emmah.inshape.feature.workout.WorkoutTimingEditor
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.SaveStatus
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

enum class Destination { Today, History, Library, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    container: AppContainer,
    modifier: Modifier = Modifier,
    entryAction: Pair<String, Long>? = null,
    consumeEntry: () -> Unit = {},
    initialRoute: String = "Today",
    initialExpandedRoutineId: String? = null,
    vm: JournalViewModel = viewModel(
        factory = remember(container) { viewModelFactory { initializer { JournalViewModel(container) } } }
    )
) {
    val currentConsumeEntry by rememberUpdatedState(consumeEntry)
    val data by vm.snapshot.collectAsStateWithLifecycle()
    val saving by vm.saveState.collectAsStateWithLifecycle()
    val largeText = LocalDensity.current.fontScale > 1.3f
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "Today"
    val tabs =
        listOf(Destination.Today, Destination.History, Destination.Library)
    val labels = listOf(R.string.today, R.string.history, R.string.library)
    val icons =
        listOf(
            Icons.Outlined.Home,
            Icons.Outlined.CalendarMonth,
            Icons.Outlined.FitnessCenter
        )
    val isMain = route in tabs.map { it.name } || route.startsWith("History?")
    var today by remember { mutableStateOf(LocalDate.now(container.clock)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        today = LocalDate.now(container.clock)
        vm.refresh()
        container.onForeground()
    }
    LaunchedEffect(today) {
        vm.refresh()
        val nextDate = today.plusDays(1).atStartOfDay(container.clock.zone).toInstant()
        delay(Duration.between(container.clock.instant(), nextDate).toMillis().coerceAtLeast(1000))
        today = LocalDate.now(container.clock)
    }
    val uiSettings = remember(container) { container.context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { container.onForeground() }
    fun requestWorkoutNotification() {
        if (!container.integrationsEnabled || Build.VERSION.SDK_INT < 33) return
        if (container.context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !uiSettings.getBoolean("workout_notification_asked", false)
        ) {
            uiSettings.edit { putBoolean("workout_notification_asked", true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun finishRoute(id: String): String = if (data?.workout(id)?.row?.timingNeedsReview == true ||
        data?.workout(id)?.row?.kind == "PAST"
    ) {
        "timing/$id/finish"
    } else {
        "finish/$id"
    }
    fun start(source: StartSource) {
        vm.write {
            val id = startWorkout(source)
            requestWorkoutNotification()
            nav.navigate("workout/$id") {
                launchSingleTop =
                    true
            }
        }
    }
    LaunchedEffect(entryAction, data != null) {
        if (data != null && entryAction != null) {
            when (entryAction.first) {
                MainActivity.START_OR_RESUME -> start(StartSource.Empty)

                MainActivity.OPEN_WORKOUT, MainActivity.OPEN_FINISH -> {
                    val active = data?.active
                    if (active == null) {
                        nav.navigate("Today") { launchSingleTop = true }
                    } else {
                        nav.navigate("workout/${active.row.id}") { launchSingleTop = true }
                        if (entryAction.first ==
                            MainActivity.OPEN_FINISH
                        ) {
                            nav.navigate(finishRoute(active.row.id)) { launchSingleTop = true }
                        }
                    }
                }

                else -> nav.navigate("Today") { popUpTo("Today") { inclusive = true } }
            }
            currentConsumeEntry()
        }
    }
    fun exercise(id: String, item: String = "") {
        nav.navigate(
            if (id ==
                "new"
            ) {
                "exercise-edit/new"
            } else {
                "exercise/$id?item=$item"
            }
        )
    }
    val workoutId = entry?.arguments?.getString("id")
    Scaffold(
        modifier = modifier,
        topBar = {
            val hasScreenHeader = when (route.substringBefore('/')) {
                "checkin", "exercise", "workout", "finish" -> true
                else -> false
            }
            if (!isMain && !hasScreenHeader) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                when {
                                    route.startsWith(
                                        "workout/"
                                    ) -> data?.workout(workoutId ?: "")?.row?.title
                                        ?: "Workout"

                                    route.startsWith("exercise-edit") -> "Edit exercise"

                                    route.startsWith("exercise/") -> "Exercise"

                                    route.startsWith("routine/") -> "Routine"

                                    route.startsWith("checkin/") -> "Check-in"

                                    route.startsWith("timing/") -> "Review workout"

                                    route.startsWith("detail/") -> "Workout details"

                                    route == "Export" -> "Export your journal"

                                    else -> ""
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton({
                            nav.popBackStack()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                stringResource(R.string.back)
                            )
                        }
                    },
                    expandedHeight = if (largeText) Dimens.topBarLarge else Dimens.rowMinTwoLine,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = InShapeTheme.colors.background,
                        scrolledContainerColor = InShapeTheme.colors.background,
                        navigationIconContentColor = InShapeTheme.colors.text
                    )
                )
            }
        },
        bottomBar = {
            Column(
                modifier = if (!isMain &&
                    saving != SaveState.Idle
                ) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                }
            ) {
                if (saving !=
                    SaveState.Idle
                ) {
                    Box(
                        Modifier.padding(horizontal = Dimens.inner, vertical = Dimens.innerTiny)
                    ) {
                        SaveStatus(saving, vm::retry)
                    }
                }
                if (isMain) HorizontalDivider(color = InShapeTheme.colors.divider)
                if (isMain) {
                    NavigationBar(
                        modifier = Modifier.heightIn(
                            min = if (largeText) Dimens.navBarLarge else Dimens.navBar
                        ),
                        containerColor = InShapeTheme.colors.background,
                        tonalElevation = Dimens.zero
                    ) {
                        tabs.forEachIndexed { i, tab ->
                            NavigationBarItem(
                                route == tab.name ||
                                    (tab == Destination.History && route.startsWith("History?")),
                                {
                                    nav.navigate(tab.name) {
                                        popUpTo(nav.graph.startDestinationId) {
                                            saveState =
                                                true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icons[i], null) },
                                label = {
                                    Text(
                                        stringResource(labels[i]),
                                        style = if (route ==
                                            tab.name ||
                                            (
                                                tab == Destination.History &&
                                                    route.startsWith("History?")
                                                )
                                        ) {
                                            InShapeType.navSelected
                                        } else {
                                            InShapeType.nav
                                        }
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = InShapeTheme.colors.text,
                                    selectedTextColor = InShapeTheme.colors.text,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = InShapeTheme.colors.textTertiary,
                                    unselectedTextColor = InShapeTheme.colors.textTertiary
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        val state = data
        if (state ==
            null
        ) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            NavHost(nav, initialRoute, Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable("Today") {
                    TodayScreen(state, today, {
                        nav.navigate("Settings")
                    }, ::start, {
                        nav.navigate("Library")
                    }, {
                        val screen = if (it == state.active?.row?.id) "workout" else "detail"
                        nav.navigate("$screen/$it")
                    }, clock = container.clock) { com.emmah.inshape.health.HealthToday(state.preferences, container) }
                }
                composable(
                    "History?view={view}&exercise={exercise}",
                    arguments = listOf(
                        androidx.navigation.navArgument("view") { defaultValue = "calendar" },
                        androidx.navigation.navArgument("exercise") {
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStack ->
                    HistoryScreen(
                        state,
                        today,
                        vm,
                        { nav.navigate("detail/$it") },
                        {
                            if (nav.currentBackStackEntry === backStack && backStack.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                                nav.navigate("workout/$it")
                            }
                        },
                        initialView = backStack.arguments?.getString("view") ?: "calendar",
                        initialExerciseId = backStack.arguments?.getString("exercise")
                    )
                }
                composable("Library") {
                    LibraryScreen(state, {
                        exercise(it)
                    }, { start(StartSource.Routine(it)) }, vm, initialExpandedId = initialExpandedRoutineId)
                }
                composable("Settings") { SettingsScreen(state, vm) { nav.navigate("Export") } }
                composable("Export") { com.emmah.inshape.transfer.ExportScreen(state, vm) }
                composable("exercise/{id}?item={item}") { backStack ->
                    val id = backStack.arguments!!.getString("id")!!
                    ExerciseScreen(id, backStack.arguments?.getString("item"), state, vm, {
                        nav.navigate("exercise-edit/$id")
                    }, {
                        exercise(it)
                    }, {
                        nav.navigate("detail/$it")
                    }, { nav.navigate("History?view=progress&exercise=$id") }, { nav.popBackStack() })
                }
                composable("exercise-edit/{id}") { backStack ->
                    ExerciseEditor(backStack.arguments!!.getString("id")!!, state, vm, onSave = { id ->
                        nav.popBackStack()
                        exercise(id)
                    })
                }
                composable("routine/{id}") { backStack ->
                    RoutineEditor(backStack.arguments!!.getString("id")!!, state, vm, {
                        nav.popBackStack()
                    }, { start(StartSource.Routine(it)) }, { exercise("new") })
                }
                composable("workout/{id}") { backStack ->
                    val id = backStack.arguments!!.getString("id")!!
                    WorkoutScreen(id, state, vm, { exerciseId, item ->
                        exercise(exerciseId, item)
                    }, {
                        nav.navigate("checkin/$id/BEFORE")
                    }, { nav.navigate(finishRoute(id)) }, { nav.popBackStack("Today", false) }, { nav.popBackStack() })
                }
                composable("finish/{id}") { backStack ->
                    val id = backStack.arguments!!.getString("id")!!
                    FinishScreen(id, state, vm, { nav.popBackStack() }, {
                        nav.navigate("detail/$id") { popUpTo("workout/$id") { inclusive = true } }
                        nav.navigate("checkin/$id/AFTER")
                    }, { nav.navigate("Settings") }, { nav.popBackStack("Today", false) })
                }
                composable("timing/{id}/{mode}") { backStack ->
                    val id = backStack.arguments!!.getString("id")!!
                    val correcting =
                        backStack.arguments?.getString("mode") == "correct"
                    WorkoutTimingEditor(id, state, vm, correcting, onSave = {
                        if (correcting) {
                            nav.popBackStack()
                        } else {
                            nav.navigate("detail/$id") {
                                popUpTo("workout/$id") {
                                    inclusive =
                                        true
                                }
                            }
                            nav.navigate("checkin/$id/AFTER")
                        }
                    })
                }
                composable("detail/{id}") { backStack ->
                    val id = backStack.arguments!!.getString("id")!!
                    WorkoutDetailScreen(id, state, vm, {
                        start(StartSource.Repeat(id))
                    }, {
                        nav.navigate("checkin/$id/$it")
                    }, {
                        nav.navigate("timing/$id/correct")
                    }, { nav.popBackStack() }, { exerciseId, item -> exercise(exerciseId, item) })
                }
                composable("checkin/{id}/{phase}") { backStack ->
                    val id = backStack.arguments!!.getString("id")!!
                    val phase = backStack.arguments!!.getString("phase")!!
                    CheckInScreen(id, phase, state, vm, {
                        if (nav.currentBackStackEntry === backStack && backStack.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                            nav.popBackStack()
                        }
                    })
                }
            }
        }
    }
}

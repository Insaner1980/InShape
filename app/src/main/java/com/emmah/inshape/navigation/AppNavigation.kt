package com.emmah.inshape.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import com.emmah.inshape.*
import com.emmah.inshape.R
import com.emmah.inshape.data.*
import com.emmah.inshape.feature.today.TodayScreen
import com.emmah.inshape.feature.library.*
import com.emmah.inshape.feature.workout.*
import com.emmah.inshape.feature.history.*
import com.emmah.inshape.feature.checkin.CheckInScreen
import com.emmah.inshape.feature.settings.SettingsScreen
import com.emmah.inshape.feature.progress.ProgressScreen
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import kotlinx.coroutines.delay
import java.time.*

enum class Destination { Today, History, Progress, Library, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(container: AppContainer, entryAction: Pair<String, Long>? = null, consumeEntry: () -> Unit = {}, initialRoute: String = "Today", initialExpandedRoutineId: String? = null) {
    val vm: JournalViewModel = viewModel(factory = remember(container) { viewModelFactory { initializer { JournalViewModel(container) } } })
    val data by vm.snapshot.collectAsStateWithLifecycle()
    val saving by vm.saveState.collectAsStateWithLifecycle()
    val largeText = LocalDensity.current.fontScale > 1.3f
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "Today"
    val tabs = listOf(Destination.Today, Destination.History, Destination.Progress, Destination.Library)
    val labels = listOf(R.string.today, R.string.history, R.string.progress, R.string.library)
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.CalendarMonth, Icons.Outlined.BarChart, Icons.Outlined.FitnessCenter)
    val isMain = route in tabs.map { it.name } || route == "Progress?exercise={exercise}"
    var today by remember { mutableStateOf(LocalDate.now(container.clock)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now(container.clock); vm.refresh(); container.onForeground() }
    LaunchedEffect(today) {
        vm.refresh()
        val nextDate = today.plusDays(1).atStartOfDay(container.clock.zone).toInstant()
        delay(Duration.between(container.clock.instant(), nextDate).toMillis().coerceAtLeast(1000))
        today = LocalDate.now(container.clock)
    }
    fun start(source: StartSource) { vm.write { val id = startWorkout(source); nav.navigate("workout/$id") { launchSingleTop = true } } }
    LaunchedEffect(entryAction, data != null) {
        if (data != null && entryAction != null) {
            if (entryAction.first == MainActivity.START_OR_RESUME) start(StartSource.Empty)
            else nav.navigate("Today") { popUpTo("Today") { inclusive = true } }
            consumeEntry()
        }
    }
    fun exercise(id: String, item: String = "") { nav.navigate(if (id == "new") "exercise-edit/new" else "exercise/$id?item=$item") }
    val workoutId = entry?.arguments?.getString("id")
    Scaffold(
        topBar = { if (!isMain && !route.startsWith("checkin/") && !route.startsWith("exercise/")) TopAppBar(title = { Column { Text(when {
            route.startsWith("workout/") -> data?.workout(workoutId ?: "")?.row?.title ?: "Workout"
            route.startsWith("exercise-edit") -> "Edit exercise"
            route.startsWith("exercise/") -> "Exercise"
            route.startsWith("routine/") -> "Routine"
            route.startsWith("checkin/") -> "Check-in"
            route.startsWith("timing/") -> "Review workout"
            route.startsWith("detail/") -> "Workout details"
            route == "Export" -> "Export your journal"
            else -> "Settings"
        }, style = MaterialTheme.typography.titleLarge)
            if (route.startsWith("workout/") && workoutId != null) data?.workout(workoutId)?.let { workout ->
                workout.row.start?.let { Text("Started ${Instant.parse(it).atZone(ZoneId.of(workout.row.zoneId)).toLocalTime().withSecond(0).withNano(0)}", style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary) }
            }
        } }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } }, actions = {
            if (route.startsWith("workout/") && workoutId != null) OutlineButton("Finish", { nav.navigate("timing/$workoutId/finish") })
        }, expandedHeight = if (largeText) Dimens.topBarLarge else Dimens.rowMinTwoLine, colors = TopAppBarDefaults.topAppBarColors(containerColor = InShapeTheme.colors.background, scrolledContainerColor = InShapeTheme.colors.background, navigationIconContentColor = InShapeTheme.colors.text)) },
        bottomBar = {
            Column(modifier = if (!isMain && saving != SaveState.Idle) Modifier.navigationBarsPadding() else Modifier) {
                if (saving != SaveState.Idle) Box(Modifier.padding(horizontal = Dimens.inner, vertical = Dimens.innerTiny)) { SaveStatus(saving, vm::retry) }
                if (isMain) HorizontalDivider(color = InShapeTheme.colors.divider)
                if (isMain) NavigationBar(modifier = Modifier.heightIn(min = if (largeText) Dimens.navBarLarge else Dimens.navBar), containerColor = InShapeTheme.colors.surface, tonalElevation = Dimens.zero) { tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(route == tab.name || (tab == Destination.Progress && route.startsWith("Progress?")), { nav.navigate(tab.name) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icons[i], null) }, label = { Text(stringResource(labels[i]), style = if (route == tab.name || (tab == Destination.Progress && route.startsWith("Progress?"))) InShapeType.navSelected else InShapeType.nav) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = InShapeTheme.colors.onAccentContainer, selectedTextColor = InShapeTheme.colors.text,
                            indicatorColor = InShapeTheme.colors.accentContainer, unselectedIconColor = InShapeTheme.colors.textSecondary, unselectedTextColor = InShapeTheme.colors.textSecondary))
                } }
            }
        },
    ) { padding ->
        val state = data
        if (state == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        else NavHost(nav, initialRoute, Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("Today") { TodayScreen(state, today, { nav.navigate("Settings") }, ::start, { nav.navigate("workout/$it") }, { nav.navigate("Library") }, { nav.navigate("detail/$it") }) { com.emmah.inshape.health.HealthToday(state.preferences, container) } }
            composable("History") { HistoryScreen(state, today, vm, { nav.navigate("detail/$it") }, { nav.navigate("workout/$it") }) }
            composable("Progress") { ProgressScreen(state, today) }
            composable("Progress?exercise={exercise}") { backStack -> ProgressScreen(state, today, backStack.arguments?.getString("exercise")) }
            composable("Library") { LibraryScreen(state, { exercise(it) }, { start(StartSource.Routine(it)) }, vm, initialExpandedRoutineId) }
            composable("Settings") { SettingsScreen(state, vm) { nav.navigate("Export") } }
            composable("Export") { com.emmah.inshape.transfer.ExportScreen(state, vm) }
            composable("exercise/{id}?item={item}") { backStack ->
                val id = backStack.arguments!!.getString("id")!!
                ExerciseScreen(id, backStack.arguments?.getString("item"), state, vm, { nav.navigate("exercise-edit/$id") }, { exercise(it) }, { nav.navigate("detail/$it") }, { nav.navigate("Progress?exercise=$id") }, { nav.popBackStack() })
            }
            composable("exercise-edit/{id}") { backStack -> ExerciseEditor(backStack.arguments!!.getString("id")!!, state, vm) { id -> nav.popBackStack(); exercise(id) } }
            composable("routine/{id}") { backStack -> RoutineEditor(backStack.arguments!!.getString("id")!!, state, vm, { nav.popBackStack() }, { start(StartSource.Routine(it)) }, { exercise("new") }) }
            composable("workout/{id}") { backStack ->
                val id = backStack.arguments!!.getString("id")!!
                WorkoutScreen(id, state, vm, { exerciseId, item -> exercise(exerciseId, item) }, { exercise("new") }, { nav.navigate("checkin/$id/AFTER") }, { nav.navigate("timing/$id/finish") }, { nav.popBackStack("Today", false) })
            }
            composable("timing/{id}/{mode}") { backStack ->
                val id = backStack.arguments!!.getString("id")!!; val correcting = backStack.arguments?.getString("mode") == "correct"
                WorkoutTimingEditor(id, state, vm, correcting) { if (correcting) nav.popBackStack() else nav.navigate("detail/$id") { popUpTo("workout/$id") { inclusive = true } } }
            }
            composable("detail/{id}") { backStack ->
                val id = backStack.arguments!!.getString("id")!!
                WorkoutDetailScreen(id, state, vm, { start(StartSource.Repeat(id)) }, { nav.navigate("checkin/$id/$it") }, { nav.navigate("timing/$id/correct") }, { nav.popBackStack() }, { exerciseId, item -> exercise(exerciseId, item) })
            }
            composable("checkin/{id}/{phase}") { backStack ->
                val id = backStack.arguments!!.getString("id")!!; val phase = backStack.arguments!!.getString("phase")!!
                CheckInScreen(id, phase, state, vm, { nav.navigate("checkin/$id/$it") { popUpTo("checkin/$id/$phase") { inclusive = true } } }, { nav.popBackStack() })
            }
        }
    }
}

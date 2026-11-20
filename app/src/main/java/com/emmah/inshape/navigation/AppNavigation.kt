// This file is named for its functions or UI role; its accompanying model is secondary.
@file:Suppress("MatchingDeclarationName")

package com.emmah.inshape.navigation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.emmah.inshape.SaveState
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.StartSource
import com.emmah.inshape.feature.checkin.CheckInScreen
import com.emmah.inshape.feature.history.HistoryScreen
import com.emmah.inshape.feature.history.WorkoutDetailScreen
import com.emmah.inshape.feature.library.ExerciseEditor
import com.emmah.inshape.feature.library.ExerciseScreen
import com.emmah.inshape.feature.library.LibraryScreen
import com.emmah.inshape.feature.library.RoutineEditor
import com.emmah.inshape.feature.music.MiniPlayer
import com.emmah.inshape.feature.music.MusicCatalog
import com.emmah.inshape.feature.music.MusicHome
import com.emmah.inshape.feature.music.MusicPlayback
import com.emmah.inshape.feature.music.MusicTrack
import com.emmah.inshape.feature.music.NowPlaying
import com.emmah.inshape.feature.music.PlaylistDetail
import com.emmah.inshape.feature.music.rememberMusicPlayback
import com.emmah.inshape.feature.settings.SettingsScreen
import com.emmah.inshape.feature.today.TodayScreen
import com.emmah.inshape.feature.workout.FinishScreen
import com.emmah.inshape.feature.workout.WorkoutScreen
import com.emmah.inshape.feature.workout.WorkoutTimingEditor
import com.emmah.inshape.ui.components.LocalPageSnackbar
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay

private const val NOW_PLAYING_ROUTE = "music-now-playing"

private val mainTabs = listOf(Destination.Today, Destination.History, Destination.Library)

private enum class Destination { Today, History, Library }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    container: AppContainer,
    modifier: Modifier = Modifier,
    entryAction: Pair<String, Long>? = null,
    consumeEntry: () -> Unit = {},
    initialRoute: String = "Today",
    musicFixture: MusicCatalog? = null,
    initialMusic: List<MusicTrack> = emptyList(),
    vm: JournalViewModel = viewModel(
        factory = remember(container) { viewModelFactory { initializer { JournalViewModel(container) } } }
    )
) {
    val data by vm.snapshot.collectAsStateWithLifecycle()
    val snapshotFailure by vm.snapshotFailure.collectAsStateWithLifecycle()
    val saving by vm.saveState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    JournalFailureMessages(saving, snapshotFailure, data != null, snackbar, vm)
    val largeText = LocalDensity.current.fontScale > 1.3f
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "Today"
    val selectedTab = when {
        route.startsWith("History?") -> Destination.History
        else -> mainTabs.firstOrNull { it.name == route }
    }
    val isMain = selectedTab != null
    val playback = rememberMusicPlayback(container, initialMusic)
    var today by remember { mutableStateOf(LocalDate.now(container.clock)) }
    var zone by remember { mutableStateOf(container.clock.zone) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        zone = container.clock.zone
        today = LocalDate.now(container.clock)
        vm.refresh()
        container.onForeground()
    }
    DisposableEffect(container) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                zone = container.clock.zone
                today = LocalDate.now(container.clock)
            }
        }
        ContextCompat.registerReceiver(
            container.context,
            receiver,
            IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { container.context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(today, zone) {
        val currentDay = today
        vm.refresh()
        while (today == currentDay) {
            val nextDate = currentDay.plusDays(1).atStartOfDay(container.clock.zone).toInstant()
            delay(Duration.between(container.clock.instant(), nextDate).toMillis().coerceAtLeast(1000))
            today = LocalDate.now(container.clock)
        }
    }
    val requestWorkoutNotification = rememberWorkoutNotificationRequest(container)
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
    HandleEntryAction(entryAction, data, nav, ::start, consumeEntry)
    fun exercise(id: String, item: String) {
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
            NavigationHeader(route, workoutId ?: entry?.arguments?.getString("kind"), isMain) { nav.popBackStack() }
        },
        bottomBar = {
            val inMusic = route == "Music" || route.startsWith("music-playlist/")
            if (selectedTab != null || inMusic) {
                NavigationBottomBar(nav, selectedTab, inMusic, playback, largeText)
            }
        }
    ) { padding ->
        val state = data
        if (state ==
            null
        ) {
            JournalLoading(snapshotFailure != null, vm::refresh, Modifier.fillMaxSize().padding(padding))
        } else {
            CompositionLocalProvider(LocalPageSnackbar provides snackbar) {
                NavHost(nav, initialRoute, Modifier.padding(padding).consumeWindowInsets(padding)) {
                    journalDestinations(state, today, vm, container, nav, ::start, ::exercise)
                    musicDestinations(container, nav, playback, musicFixture, state)
                    composable("Settings") {
                        SettingsScreen(state, vm, onAssistant = { nav.navigate("AssistantSettings") }) { nav.navigate("Export") }
                    }
                    composable("AssistantSettings") {
                        com.emmah.inshape.feature.assistant.AssistantSettings(
                            container.assistantCredentials, container.assistant, vm, state.assistantMemories
                        )
                    }
                    composable("Export") { com.emmah.inshape.transfer.ExportScreen(state, vm) }
                    libraryEditors(state, vm, nav, ::start, ::exercise)
                    activeWorkoutDestination(state, vm, nav, playback, ::exercise)
                    finishDestinations(state, vm, nav)
                    workoutHistoryDestinations(state, vm, nav, ::start, ::exercise)
                }
            }
        }
    }
}

@Composable
private fun JournalFailureMessages(
    saving: SaveState,
    snapshotFailure: Long?,
    hasJournal: Boolean,
    snackbar: SnackbarHostState,
    vm: JournalViewModel
) {
    val retry = stringResource(R.string.retry)
    val loadFailure = stringResource(R.string.load_journal_failed)
    LaunchedEffect(saving) {
        val failure = saving as? SaveState.Failed ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = failure.message,
            actionLabel = if (failure.retryable) retry else null,
            duration = if (failure.retryable) SnackbarDuration.Indefinite else SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) vm.retry()
    }
    LaunchedEffect(snapshotFailure, hasJournal) {
        if (snapshotFailure != null && hasJournal &&
            snackbar.showSnackbar(loadFailure, actionLabel = retry) == SnackbarResult.ActionPerformed
        ) {
            vm.refresh()
        }
    }
}

@Composable
private fun rememberWorkoutNotificationRequest(container: AppContainer): () -> Unit {
    val uiSettings = remember(container) { container.context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { container.onForeground() }
    return request@{
        if (!container.integrationsEnabled || Build.VERSION.SDK_INT < 33) return@request
        if (container.context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !uiSettings.getBoolean("workout_notification_asked", false)
        ) {
            uiSettings.edit { putBoolean("workout_notification_asked", true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun HandleEntryAction(
    entryAction: Pair<String, Long>?,
    data: JournalSnapshot?,
    nav: NavHostController,
    start: (StartSource) -> Unit,
    consumeEntry: () -> Unit
) {
    val latestData by rememberUpdatedState(data)
    val currentConsumeEntry by rememberUpdatedState(consumeEntry)
    LaunchedEffect(entryAction, data != null) {
        if (latestData != null && entryAction != null) {
            when (entryAction.first) {
                MainActivity.START_OR_RESUME -> start(StartSource.Empty)

                MainActivity.OPEN_WORKOUT, MainActivity.OPEN_FINISH -> {
                    val active = latestData?.active
                    if (active == null) {
                        nav.navigate("Today") { launchSingleTop = true }
                    } else {
                        nav.navigate("workout/${active.row.id}") { launchSingleTop = true }
                        if (entryAction.first ==
                            MainActivity.OPEN_FINISH
                        ) {
                            nav.navigate("finish/${active.row.id}") { launchSingleTop = true }
                        }
                    }
                }

                MainActivity.OPEN_MUSIC -> nav.navigate("Music") { launchSingleTop = true }

                else -> nav.navigate("Today") { popUpTo("Today") { inclusive = true } }
            }
            currentConsumeEntry()
        }
    }
}

@Composable
private fun NavigationHeader(route: String, workoutId: String?, isMain: Boolean, onBack: () -> Unit) {
    val hasScreenHeader = when (route.substringBefore('/')) {
        "checkin", "exercise", "workout", "finish", "detail", "routine", "Music",
        "music-playlist", "program-setup", "program-next", NOW_PLAYING_ROUTE -> true
        else -> false
    }
    if (!isMain && !hasScreenHeader) {
        val title = when {
            route.startsWith("exercise-edit") -> if (workoutId == "new") stringResource(R.string.new_exercise) else stringResource(R.string.edit_exercise)
            route == "timing/{id}/correct" -> stringResource(R.string.edit_workout)
            route == "Settings" -> stringResource(R.string.settings)
            route == "AssistantSettings" -> stringResource(R.string.assistant)
            route == "Export" -> stringResource(R.string.export)
            route == "guide/{key}" -> stringResource(R.string.exercise_guide)
            route.startsWith("journal-questions") -> stringResource(R.string.ask_journal)
            route.startsWith("routine-proposal") -> stringResource(if (workoutId == "new") R.string.build_routine else R.string.adapt_workout)
            route.startsWith("assistant-import") -> stringResource(if (workoutId == "equipment") R.string.import_equipment else R.string.import_program)
            route.startsWith("playlist-assistant") -> stringResource(if (workoutId == "new") R.string.playlist_assistant else R.string.edit_playlist)
            route.startsWith("track-energy") -> stringResource(R.string.track_energy)
            else -> ""
        }
        PushedHeader(onBack = onBack, title = title)
    }
}

@Composable
private fun NavigationBottomBar(
    nav: NavHostController,
    selectedTab: Destination?,
    inMusic: Boolean,
    playback: MusicPlayback,
    largeText: Boolean
) {
    val labels = listOf(R.string.today, R.string.history, R.string.library)
    val icons =
        listOf(
            Icons.Outlined.Home,
            Icons.Outlined.CalendarMonth,
            Icons.Outlined.FitnessCenter
        )
    Column(if (selectedTab == null) Modifier.navigationBarsPadding() else Modifier) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MiniPlayer(
                playback,
                if (inMusic) stringResource(R.string.open_now_playing) else stringResource(R.string.open_music),
                { nav.navigate(if (inMusic) NOW_PLAYING_ROUTE else "Music") { launchSingleTop = true } },
                Modifier.widthIn(max = Dimens.maxPageWidth).padding(
                    horizontal = Dimens.screenPadding,
                    vertical = Dimens.innerSmall
                )
            )
        }
        if (selectedTab != null) {
            HorizontalDivider(color = InShapeTheme.colors.divider)
            NavigationBar(
                modifier = Modifier.heightIn(
                    min = if (largeText) Dimens.navBarLarge else Dimens.navBar
                ),
                containerColor = InShapeTheme.colors.background,
                tonalElevation = Dimens.zero
            ) {
                mainTabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selectedTab == tab,
                        {
                            if (selectedTab != tab) {
                                nav.navigate(tab.name) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(icons[i], null) },
                        label = {
                            Text(
                                stringResource(labels[i]),
                                style = if (selectedTab == tab) {
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

@Composable
private fun JournalLoading(hasSnapshotFailure: Boolean, retry: () -> Unit, modifier: Modifier) {
    Box(
        modifier,
        contentAlignment = Alignment.Center
    ) {
        if (hasSnapshotFailure) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.load_journal_failed))
                OutlineButton(stringResource(R.string.retry), retry)
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

private fun NavGraphBuilder.journalDestinations(
    state: JournalSnapshot,
    today: LocalDate,
    vm: JournalViewModel,
    container: AppContainer,
    nav: NavHostController,
    start: (StartSource) -> Unit,
    exercise: (String, String) -> Unit
) {
    composable("Today") {
        val enrollment = state.programEnrollment
        val program = enrollment?.takeIf { it.catalogVersion == container.programs.schemaVersion }
            ?.let { container.programs.program(it.programId) }
        val day = enrollment?.let { program?.sequence?.getOrNull(it.nextDayIndex) }
        TodayScreen(
            state,
            today,
            onSettings = { nav.navigate("Settings") },
            onMusic = { nav.navigate("Music") },
            onStart = start,
            onWorkout = {
                val screen = if (it == state.active?.row?.id) "workout" else "detail"
                nav.navigate("$screen/$it")
            },
            clock = container.clock,
            onCreateRoutine = { nav.navigate("routine/new") },
            nextProgramLabel = if (program != null && day != null) stringResource(R.string.program_next_day, program.name, day) else null,
            onProgram = { program?.let { nav.navigate("program-next/${it.id}") } }
        ) {
            com.emmah.inshape.health.HealthToday(state.preferences, container)
        }
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
                if (nav.isActive(backStack)) {
                    nav.navigate("workout/$it")
                }
            },
            initialView = backStack.arguments?.getString("view") ?: "calendar",
            initialExerciseId = backStack.arguments?.getString("exercise"),
            onQuestions = {
                val id = backStack.arguments?.getString("exercise").orEmpty()
                nav.navigate(if (id.isBlank()) "journal-questions" else "journal-questions?exercise=$id")
            }
        )
    }
    composable(
        "journal-questions?exercise={exercise}&workout={workout}",
        arguments = listOf(
            androidx.navigation.navArgument("exercise") { nullable = true; defaultValue = null },
            androidx.navigation.navArgument("workout") { nullable = true; defaultValue = null }
        )
    ) { entry ->
        com.emmah.inshape.feature.assistant.JournalEvidenceScreen(
            state, today, entry.arguments?.getString("exercise"), entry.arguments?.getString("workout")
        ) { nav.navigate("detail/$it") }
    }
    composable("Library") {
        LibraryScreen(state, {
            exercise(it, "")
        }, { nav.navigate("routine/$it") }, vm,
            onProgram = { nav.navigate("program/$it") },
            onRoutineProposal = { nav.navigate("routine-proposal/new") },
            onImport = { nav.navigate("assistant-import/$it") }
        )
    }
    composable("routine-proposal/{id}") { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        val workout = state.workout(id)
        if (id == "new" || workout != null) {
            com.emmah.inshape.feature.assistant.RoutineProposalScreen(state, workout, vm) { saved ->
                nav.popBackStack()
                if (saved != null) nav.navigate("routine/$saved")
            }
        }
    }
    composable("assistant-import/{kind}") { entry ->
        com.emmah.inshape.feature.assistant.AssistantImportScreen(
            entry.arguments?.getString("kind") == "equipment", state, vm, { nav.navigate("AssistantSettings") }
        ) { id, equipment ->
            nav.popBackStack()
            if (id != null) nav.navigate(if (equipment) "exercise/$id?item=" else "routine/$id")
        }
    }
    composable("program/{id}") { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        com.emmah.inshape.feature.program.ProgramScreen(container.programs, id, { nav.navigate("program-setup/$id") }) {
            nav.navigate("guide/$it")
        }
    }
    listOf(true, false).forEach { enrolling ->
        composable(if (enrolling) "program-setup/{id}" else "program-next/{id}") { entry ->
            container.programs.program(entry.arguments?.getString("id").orEmpty())?.let { program ->
                com.emmah.inshape.feature.program.ProgramSetupScreen(
                    program, state, vm, enrolling,
                    onBack = { nav.popBackStack() },
                    onDone = {
                        nav.navigate("Today") {
                            popUpTo("Today") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onStart = { start(StartSource.Program(it)) }
                )
            }
        }
    }
    composable("guide/{key}") { entry ->
        com.emmah.inshape.feature.program.ProgramGuideScreen(container.programs, entry.arguments?.getString("key").orEmpty())
    }
}

private fun NavGraphBuilder.musicDestinations(
    container: AppContainer,
    nav: NavHostController,
    playback: MusicPlayback,
    musicFixture: MusicCatalog?,
    state: JournalSnapshot
) {
    composable("Music") {
        MusicHome(container.music, playback, { nav.navigate("music-playlist/$it") }, {
            if (!nav.popBackStack()) nav.navigate("Today")
        }, musicFixture,
            onPlaylistAssistant = { nav.navigate("playlist-assistant/new") },
            onEnergy = { nav.navigate("track-energy/$it") }
        )
    }
    composable("music-playlist/{id}") { backStack ->
        PlaylistDetail(backStack.arguments!!.getString("id")!!, container.music, playback, {
            if (!nav.popBackStack()) nav.navigate("Music")
        }, musicFixture, onAssistant = { nav.navigate("playlist-assistant/${backStack.arguments?.getString("id")}") })
    }
    composable("playlist-assistant/{id}") { entry ->
        val id = entry.arguments?.getString("id")?.takeUnless { it == "new" }
        val previous = state.history.filter { it.qualifiesForAttendance }
            .maxWithOrNull(compareBy({ it.row.journalDate }, { it.row.createdAt }, { it.row.id }))
        com.emmah.inshape.feature.music.PlaylistAssistantScreen(
            container, previous, id, onSettings = { nav.navigate("AssistantSettings") }
        ) { saved ->
            nav.popBackStack()
            if (saved != id) nav.navigate("music-playlist/$saved")
        }
    }
    composable("track-energy/{id}") { entry ->
        com.emmah.inshape.feature.music.TrackEnergyScreen(container.music, entry.arguments?.getString("id").orEmpty()) {
            nav.popBackStack()
        }
    }
    composable(NOW_PLAYING_ROUTE) {
        NowPlaying(playback) { nav.popBackStack() }
    }
}

private fun NavGraphBuilder.libraryEditors(
    state: JournalSnapshot,
    vm: JournalViewModel,
    nav: NavHostController,
    start: (StartSource) -> Unit,
    exercise: (String, String) -> Unit
) {
    composable("exercise/{id}?item={item}") { backStack ->
        val id = backStack.arguments!!.getString("id")!!
        ExerciseScreen(id, backStack.arguments?.getString("item"), state, vm, {
            nav.navigate("exercise-edit/$id")
        }, {
            exercise(it, "")
        }, {
            nav.navigate("detail/$it")
        }, { nav.navigate("History?view=progress&exercise=$id") }, { nav.popBackStack() }, onGuide = { nav.navigate("guide/$it") })
    }
    composable("exercise-edit/{id}") { backStack ->
        ExerciseEditor(
            backStack.arguments!!.getString("id")!!,
            state,
            vm,
            onCreated = { id ->
                if (nav.isActive(backStack)) nav.navigate("exercise-edit/$id") {
                    popUpTo("exercise-edit/new") { inclusive = true }
                }
            },
            onArchived = { if (nav.isActive(backStack)) nav.popBackStack() }
        )
    }
    composable("routine/{id}") { backStack ->
        RoutineEditor(backStack.arguments!!.getString("id")!!, state, vm, {
            if (nav.isActive(backStack)) nav.popBackStack()
        }, { id ->
            if (nav.isActive(backStack)) nav.navigate("routine/$id") {
                popUpTo("routine/new") { inclusive = true }
            }
        }, { id ->
            if (nav.isActive(backStack)) start(StartSource.Routine(id))
        }, { exercise("new", "") })
    }
}

private fun NavGraphBuilder.activeWorkoutDestination(
    state: JournalSnapshot,
    vm: JournalViewModel,
    nav: NavHostController,
    playback: MusicPlayback,
    exercise: (String, String) -> Unit
) {
    composable("workout/{id}") { backStack ->
        val id = backStack.arguments!!.getString("id")!!
        WorkoutScreen(
            id, state, vm, { exerciseId, item ->
                exercise(exerciseId, item)
            }, {
                nav.navigate("checkin/$id/BEFORE")
            }, {
                if (nav.isActive(backStack)) {
                    if (state.workout(id)?.isPastEntry == true) nav.showFinishedWorkout(id)
                    else nav.navigate("finish/$id")
                }
            },
            {
                if (nav.isActive(backStack)) {
                    if (state.workout(id)?.isPastEntry == true) nav.popBackStack()
                    else nav.popBackStack("Today", false)
                }
            }, { nav.popBackStack() },
            musicPlayback = playback.takeIf { state.active?.row?.id == id },
            onMusic = { nav.navigate(if (playback.state.value.song != null) NOW_PLAYING_ROUTE else "Music") },
            onAssistantSettings = { nav.navigate("AssistantSettings") },
            onAdaptWorkout = { nav.navigate("routine-proposal/$id") }
        )
    }
}

private fun NavGraphBuilder.workoutHistoryDestinations(
    state: JournalSnapshot,
    vm: JournalViewModel,
    nav: NavHostController,
    start: (StartSource) -> Unit,
    exercise: (String, String) -> Unit
) {
    composable("detail/{id}") { backStack ->
        val id = backStack.arguments!!.getString("id")!!
        WorkoutDetailScreen(id, state, vm, {
            start(StartSource.Repeat(id))
        }, {
            nav.navigate("checkin/$id/$it")
        }, {
            nav.navigate("timing/$id/correct")
        }, { nav.popBackStack() }, { exerciseId, item -> exercise(exerciseId, item) },
            onQuestions = { nav.navigate("journal-questions?workout=$id") }
        )
    }
    composable("checkin/{id}/{phase}") { backStack ->
        val id = backStack.arguments!!.getString("id")!!
        val phase = backStack.arguments!!.getString("phase")!!
        CheckInScreen(id, phase, state, vm, {
            if (nav.isActive(backStack)) {
                nav.popBackStack()
            }
        })
    }
}

private fun NavGraphBuilder.finishDestinations(state: JournalSnapshot, vm: JournalViewModel, nav: NavHostController) {
    composable("finish/{id}") { backStack ->
        val id = backStack.arguments!!.getString("id")!!
        FinishScreen(id, state, vm, { if (nav.currentBackStackEntry === backStack) nav.popBackStack() }, {
            nav.finishIfCurrent(backStack, id)
        }, {
            if (nav.currentBackStackEntry === backStack) nav.popBackStack("Today", false)
        })
    }
    composable("timing/{id}/correct") { backStack ->
        val id = backStack.arguments!!.getString("id")!!
        WorkoutTimingEditor(id, state, vm, onSave = {
            if (nav.isActive(backStack)) nav.popBackStack()
        })
    }
}

private fun NavHostController.isActive(entry: NavBackStackEntry): Boolean =
    currentBackStackEntry === entry && entry.lifecycle.currentState != Lifecycle.State.DESTROYED

private fun NavHostController.showFinishedWorkout(id: String) {
    navigate("detail/$id") { popUpTo("workout/$id") { inclusive = true } }
}

private fun NavHostController.finishIfCurrent(entry: NavBackStackEntry, id: String) {
    if (currentBackStackEntry === entry) showFinishedWorkout(id)
}

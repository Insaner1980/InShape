package com.emmah.inshape.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.newId
import com.emmah.inshape.health.HealthPrivacyActivity
import com.emmah.inshape.health.HealthSettings
import com.emmah.inshape.reminders.ReminderScheduler
import com.emmah.inshape.transfer.BackupControls
import com.emmah.inshape.ui.components.GroupLabel
import com.emmah.inshape.ui.components.InShapeSwitch
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.Stepper
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.workoutTime
import com.emmah.inshape.widget.WidgetUpdater
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(data: JournalSnapshot, vm: JournalViewModel, modifier: Modifier = Modifier, onExport: () -> Unit = {}) {
    var name by rememberSaveable { mutableStateOf(data.preferences.name) }
    var nameEditSession by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val colors = InShapeTheme.colors
    val scope = rememberCoroutineScope()
    val preferences = data.preferences
    val scheduler = remember(context) { ReminderScheduler(context) }
    var notificationsAllowed by remember { mutableStateOf(scheduler.allowed()) }
    var hasWidget by remember { mutableStateOf(WidgetUpdater.hasWidgets(context)) }
    var widgetMessage by rememberSaveable { mutableStateOf("") }
    var editReminderTime by rememberSaveable { mutableStateOf(false) }
    val mode by vm.container.themeSettings.mode.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAllowed =
                scheduler.allowed()
            vm.container.onForeground()
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = scheduler.allowed()
        hasWidget =
            WidgetUpdater.hasWidgets(context)
    }
    val days = preferences.reminderDays.sorted().map {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }
    val reminderStatus = when {
        !preferences.remindersEnabled -> null

        preferences.remindersHeld -> "Paused after restore"

        !notificationsAllowed -> "Notifications blocked in Android"

        days.isEmpty() -> "Choose at least one day"

        else -> (
            if (days.size >
                1
            ) {
                days.dropLast(1).joinToString(", ") + " and " + days.last()
            } else {
                days.single()
            }
            ) +
            " at ${LocalTime.parse(preferences.reminderTime).workoutTime(DateFormat.is24HourFormat(context))}"
    }
    Page(modifier) {
        GroupLabel("Profile")
        ListCard {
            item {
                KeyValueRow("Name", preferences.name.ifBlank { "Add name" }, onClick = {
                    name = preferences.name
                    nameEditSession = newId()
                })
            }
            item {
                ListRow("Weekly goal", titleStyle = MaterialTheme.typography.bodyLarge, subtitle = "Workouts per week", trailing = {
                    Stepper(preferences.weeklyGoal?.toString() ?: "Off", {
                        vm.write {
                            val value = snapshot().preferences
                            updatePreferences(
                                value.copy(
                                    weeklyGoal = value.weeklyGoal?.takeIf {
                                        it >
                                            1
                                    }?.minus(1)
                                )
                            )
                        }
                    }, {
                        vm.write {
                            val value = snapshot().preferences
                            updatePreferences(
                                value.copy(
                                    weeklyGoal =
                                        value.weeklyGoal?.let { Math.addExact(it, 1) } ?: 3
                                )
                            )
                        }
                    }, preferences.weeklyGoal != null, "Decrease weekly goal", "Increase weekly goal")
                })
            }
        }
        GroupLabel("Reminders")
        ListCard {
            item {
                val subtitle = if (preferences.remindersHeld) "Paused after restore" else reminderStatus
                ListRow("Workout reminders", titleStyle = MaterialTheme.typography.bodyLarge, subtitle = subtitle, trailing = {
                    InShapeSwitch(preferences.remindersEnabled, { enabled ->
                        vm.write {
                            updatePreferences(
                                snapshot().preferences.copy(
                                    remindersEnabled = enabled,
                                    remindersHeld = false
                                )
                            )
                        }
                        if (enabled && Build.VERSION.SDK_INT >= 33 &&
                            !notificationsAllowed
                        ) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }, "Enable workout reminders")
                })
            }
            if (preferences.remindersHeld) {
                item {
                    TextAction("Resume", {
                        vm.write { updatePreferences(snapshot().preferences.copy(remindersHeld = false)) }
                    })
                }
            }
            if (preferences.remindersEnabled) {
                item {
                    Column(
                        Modifier.padding(vertical = Dimens.innerSmall),
                        verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            DayOfWeek.entries.forEach { day ->
                                val fullName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                                val checked = day.value in preferences.reminderDays
                                androidx.compose.foundation.layout.Box(
                                    Modifier.weight(1f).heightIn(min = Dimens.touch)
                                        .semantics { contentDescription = fullName }
                                        .toggleable(checked, role = Role.Checkbox) {
                                            vm.write {
                                                val value = snapshot().preferences
                                                val days = if (day.value in value.reminderDays) {
                                                    value.reminderDays - day.value
                                                } else {
                                                    (value.reminderDays + day.value).sorted()
                                                }
                                                updatePreferences(value.copy(reminderDays = days, remindersHeld = false))
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        Modifier.size(Dimens.reminderDay),
                                        shape = InShapeShapes.pill,
                                        color = if (checked) colors.ink else colors.background,
                                        border = BorderStroke(Dimens.line, if (checked) colors.ink else colors.textSecondary)
                                    ) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                                                style = InShapeType.navSelected,
                                                color = if (checked) colors.onInk else colors.text
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        KeyValueRow(
                            "Time",
                            LocalTime.parse(preferences.reminderTime).workoutTime(DateFormat.is24HourFormat(context)),
                            numeric = true,
                            onClick = { editReminderTime = true }
                        )
                        if (!notificationsAllowed) {
                            OutlineButton("Notification settings", {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            })
                        }
                    }
                }
            }
        }
        HealthSettings(data, vm)
        GroupLabel("Appearance")
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            SegmentedControl(
                ThemeMode.entries.map {
                    it.name
                },
                mode.ordinal,
                {
                    vm.container.themeSettings.set(ThemeMode.entries[it])
                }
            )
        }
        GroupLabel("Your data")
        BackupControls(vm, preferences.lastBackupAt) {
            item {
                ListRow(
                    "Export CSV or PDF",
                    titleStyle = MaterialTheme.typography.bodyLarge,
                    onClick = onExport
                )
            }
            item {
                ListRow(
                    "Home screen widget",
                    titleStyle = MaterialTheme.typography.bodyLarge,
                    subtitle = if (hasWidget) "Placed" else "Not placed yet",
                    onClick = {
                        scope.launch {
                            widgetMessage = if (WidgetUpdater.requestPin(context)) {
                                "Confirm placement in your launcher. InShape checks placement when you return."
                            } else {
                                "Touch and hold an empty area of your home screen, choose Widgets, then InShape."
                            }
                        }
                    }
                )
            }
        }
        if (widgetMessage.isNotEmpty()) {
            Text(
                widgetMessage,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        GroupLabel("Privacy")
        ListCard {
            item {
                ListRow("Privacy and permissions", onClick = {
                    context.startActivity(Intent(context, HealthPrivacyActivity::class.java))
                })
            }
        }
    }
    val session = nameEditSession
    if (session != null) {
        AlertDialog(
            onDismissRequest = { nameEditSession = null },
            title = { Text("Name") },
            text = { TextField(name, "Name", { name = it }, singleLine = true) },
            confirmButton = {
                TextAction("Save", {
                    val value = name
                    vm.write {
                        updatePreferences(snapshot().preferences.copy(name = value))
                        if (nameEditSession == session && name == value) nameEditSession = null
                    }
                })
            },
            dismissButton = { TextAction("Cancel", { nameEditSession = null }) }
        )
    }
    if (editReminderTime) {
        com.emmah.inshape.ui.components.RecordedTimePicker("Reminder time", preferences.reminderTime, { time ->
            vm.write { updatePreferences(snapshot().preferences.copy(reminderTime = time, remindersHeld = false)) }
            editReminderTime = false
        }, { editReminderTime = false })
    }
}

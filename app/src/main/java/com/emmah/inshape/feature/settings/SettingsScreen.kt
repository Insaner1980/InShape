package com.emmah.inshape.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.health.HealthSettings
import com.emmah.inshape.reminders.ReminderScheduler
import com.emmah.inshape.transfer.BackupControls
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.FilterPill
import com.emmah.inshape.ui.components.GroupLabel
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.InShapeSwitch
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.SegmentedControl
import com.emmah.inshape.ui.components.Stepper
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.widget.WidgetUpdater
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(data: JournalSnapshot, vm: JournalViewModel, modifier: Modifier = Modifier, onExport: () -> Unit = {}) {
    var name by rememberSaveable { mutableStateOf(data.preferences.name) }
    val context = LocalContext.current
    val colors = InShapeTheme.colors
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
            " at ${preferences.reminderTime}"
    }
    Page(modifier) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        HealthSettings(data, vm)
        Text(
            "Completed workouts appear in Google Health and other apps connected to Health Connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        GroupLabel("Workouts")
        ListCard {
            item {
                ListRow("Name", titleStyle = MaterialTheme.typography.bodyLarge, trailing = {
                    BasicTextField(
                        name,
                        { value ->
                            name = value
                            vm.write {
                                updatePreferences(snapshot().preferences.copy(name = value))
                            }
                        },
                        Modifier.width(Dimens.inlineField).heightIn(min = Dimens.touch).semantics {
                            contentDescription =
                                "Name for your greeting"
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = colors.textSecondary,
                            textAlign = TextAlign.End
                        ),
                        cursorBrush = SolidColor(colors.ink),
                        decorationBox = { field ->
                            Box(contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
                                if (name.isEmpty()) {
                                    Text(
                                        "Add name",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textSecondary
                                    )
                                }
                                field()
                            }
                        }
                    )
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
            item {
                ListRow("Workout reminders", titleStyle = MaterialTheme.typography.bodyLarge, subtitle = reminderStatus, trailing = {
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
            if (preferences.remindersEnabled) {
                item {
                    Column(
                        Modifier.padding(vertical = Dimens.innerSmall),
                        verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                    ) {
                        ActionRow {
                            DayOfWeek.entries.forEach { day ->
                                val fullName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                                FilterPill(
                                    fullName.take(
                                        3
                                    ),
                                    day.value in preferences.reminderDays,
                                    {
                                        vm.write {
                                            val value = snapshot().preferences
                                            updatePreferences(
                                                value.copy(
                                                    reminderDays = if (day.value in
                                                        value.reminderDays
                                                    ) {
                                                        value.reminderDays - day.value
                                                    } else {
                                                        (
                                                            value.reminderDays +
                                                                day.value
                                                            ).sorted()
                                                    },
                                                    remindersHeld = false
                                                )
                                            )
                                        }
                                    },
                                    Modifier.semantics { contentDescription = fullName }
                                )
                            }
                        }
                        ListRow("Time", titleStyle = MaterialTheme.typography.bodyLarge, trailing = {
                            Text(
                                preferences.reminderTime,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary
                            )
                        }, onClick = {
                            editReminderTime = true
                        })
                        if (!notificationsAllowed) {
                            OutlineButton("Notification settings", {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            })
                        }
                        if (preferences.remindersHeld) {
                            OutlineButton("Activate these reminders", {
                                vm.write {
                                    updatePreferences(
                                        snapshot().preferences.copy(remindersHeld = false)
                                    )
                                }
                            })
                        }
                    }
                }
            }
        }
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
                        widgetMessage =
                            if (WidgetUpdater.requestPin(
                                    context
                                )
                            ) {
                                "Confirm placement in your launcher. InShape checks placement when you return."
                            } else {
                                "Touch and hold an empty area of your home screen, choose Widgets, then InShape."
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
        Text(
            "Your journal is stored on this phone and works offline. A full backup includes your " +
                "journal, routines, drafts and original photos; keep it somewhere private.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
    if (editReminderTime) {
        com.emmah.inshape.ui.components.RecordedTimePicker("Reminder time", preferences.reminderTime, { time ->
            vm.write { updatePreferences(snapshot().preferences.copy(reminderTime = time, remindersHeld = false)) }
            editReminderTime = false
        }, { editReminderTime = false })
    }
}

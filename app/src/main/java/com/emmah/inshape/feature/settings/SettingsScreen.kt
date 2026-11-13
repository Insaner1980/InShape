package com.emmah.inshape.feature.settings

import android.Manifest
import android.os.Build
import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.reminders.ReminderScheduler
import com.emmah.inshape.widget.WidgetUpdater
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import java.time.*
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(data: JournalSnapshot, vm: JournalViewModel, onExport: () -> Unit = {}) {
    var name by rememberSaveable { mutableStateOf(data.preferences.name) }
    val context = LocalContext.current
    val colors = InShapeTheme.colors
    val preferences = data.preferences
    val scheduler = remember(context) { ReminderScheduler(context) }
    var notificationsAllowed by remember { mutableStateOf(scheduler.allowed()) }
    var hasWidget by remember { mutableStateOf(WidgetUpdater.hasWidgets(context)) }
    var widgetMessage by rememberSaveable { mutableStateOf("") }
    val mode by vm.container.themeSettings.mode.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = scheduler.allowed(); vm.container.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsAllowed = scheduler.allowed(); hasWidget = WidgetUpdater.hasWidgets(context) }
    val days = preferences.reminderDays.sorted().map { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    val reminderStatus = when {
        !preferences.remindersEnabled -> null
        preferences.remindersHeld -> "Paused after restore"
        !notificationsAllowed -> "Notifications blocked in Android"
        days.isEmpty() -> "Choose at least one day"
        else -> (if (days.size > 1) days.dropLast(1).joinToString(", ") + " and " + days.last() else days.single()) + " at ${preferences.reminderTime}"
    }
    Page {
        GroupLabel("You")
        ListCard {
            item { ListRow("Name", titleStyle = InShapeType.settingsRow, trailing = {
                BasicTextField(name, { value -> name = value; vm.write { updatePreferences(snapshot().preferences.copy(name = value)) } }, Modifier.width(Dimens.inlineField).heightIn(min = Dimens.touch).semantics { contentDescription = "Name for your greeting" }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textSecondary, textAlign = TextAlign.End), cursorBrush = SolidColor(colors.mark), decorationBox = { field ->
                        Box(contentAlignment = androidx.compose.ui.Alignment.CenterEnd) { if (name.isEmpty()) Text("Add name", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary); field() }
                    })
            }) }
            item { ListRow("Weekly goal", titleStyle = InShapeType.settingsRow, subtitle = "Workouts per week", trailing = {
                Stepper(preferences.weeklyGoal?.toString() ?: "Off", {
                    vm.write { val value = snapshot().preferences; updatePreferences(value.copy(weeklyGoal = value.weeklyGoal?.takeIf { it > 1 }?.minus(1))) }
                }, {
                    vm.write { val value = snapshot().preferences; updatePreferences(value.copy(weeklyGoal = value.weeklyGoal?.let { Math.addExact(it, 1) } ?: 3)) }
                }, preferences.weeklyGoal != null, "Decrease weekly goal", "Increase weekly goal")
            }) }
            item { ListRow("Workout reminders", titleStyle = InShapeType.settingsRow, subtitle = reminderStatus, trailing = {
                InShapeSwitch(preferences.remindersEnabled, { enabled ->
                    vm.write { updatePreferences(snapshot().preferences.copy(remindersEnabled = enabled, remindersHeld = false)) }
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, "Enable workout reminders")
            }) }
            if (preferences.remindersEnabled) item {
                Column(Modifier.padding(vertical = Dimens.innerSmall), verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    ActionRow { DayOfWeek.entries.forEach { day ->
                        val fullName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        FilterPill(fullName.take(3), day.value in preferences.reminderDays, { vm.write {
                            val value = snapshot().preferences
                            updatePreferences(value.copy(reminderDays = if (day.value in value.reminderDays) value.reminderDays - day.value else (value.reminderDays + day.value).sorted(), remindersHeld = false))
                        } }, Modifier.semantics { contentDescription = fullName })
                    } }
                    ListRow("Time", titleStyle = InShapeType.settingsRow, trailing = { Text(preferences.reminderTime, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary) }, onClick = {
                        val time = LocalTime.parse(preferences.reminderTime)
                        val themed = ContextThemeWrapper(context, if (colors.isDark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
                        TimePickerDialog(themed, { _, hour, minute -> vm.write { updatePreferences(snapshot().preferences.copy(reminderTime = LocalTime.of(hour, minute).toString(), remindersHeld = false)) } }, time.hour, time.minute, true).show()
                    })
                    if (!notificationsAllowed) SecondaryButton("Notification settings", { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) })
                    if (preferences.remindersHeld) SecondaryButton("Activate these reminders", { vm.write { updatePreferences(snapshot().preferences.copy(remindersHeld = false)) } })
                }
            }
        }
        GroupLabel("Appearance")
        InShapeCard(contentPadding = PaddingValues(Dimens.inner)) {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            SegmentedControl(ThemeMode.entries.map { it.name }, mode.ordinal, { vm.container.themeSettings.set(ThemeMode.entries[it]) }, onCard = true)
        }
        com.emmah.inshape.health.HealthSettings(data, vm)
        GroupLabel("Your data")
        com.emmah.inshape.transfer.BackupControls(vm, preferences.lastBackupAt) {
            item { ListRow("Export CSV or PDF", titleStyle = InShapeType.settingsRow, onClick = onExport) }
            item { ListRow("Home screen widget", titleStyle = InShapeType.settingsRow, subtitle = if (hasWidget) "On your home screen" else "Not placed yet", onClick = {
                widgetMessage = if (WidgetUpdater.requestPin(context)) "Confirm placement in your launcher. InShape checks placement when you return." else "Touch and hold an empty area of your home screen, choose Widgets, then InShape."
            }) }
        }
        if (widgetMessage.isNotEmpty()) Text(widgetMessage, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text("Your journal is stored on this phone and works offline. A full backup includes your journal, routines, drafts and original photos; keep it somewhere private.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

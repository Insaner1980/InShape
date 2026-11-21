package com.emmah.inshape.feature.settings

import com.emmah.inshape.ui.theme.finnishLocale
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.health.HealthPrivacyActivity
import com.emmah.inshape.health.HealthSettings
import com.emmah.inshape.reminders.ReminderScheduler
import com.emmah.inshape.transfer.BackupControls
import com.emmah.inshape.ui.components.InShapeSwitch
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.Stepper
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.InShapeType
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.hasArtworkColumns
import com.emmah.inshape.ui.theme.workoutTime
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    data: JournalSnapshot,
    vm: JournalViewModel,
    modifier: Modifier = Modifier,
    onAssistant: (() -> Unit)? = null,
    onExport: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = data.preferences
    val scheduler = remember(context) { ReminderScheduler(context) }
    var notificationsAllowed by remember { mutableStateOf(scheduler.allowed()) }
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
    }
    Page(modifier) {
        if (onAssistant != null) ListCard {
            item { ListRow(stringResource(R.string.assistant), subtitle = stringResource(R.string.assistant_connection), onClick = onAssistant) }
        }
        SectionHeader(stringResource(R.string.goals_and_reminders))
        ListCard {
            item {
                ListRow(
                    stringResource(R.string.weekly_goal),
                    subtitle = if (preferences.weeklyGoal == null) stringResource(R.string.no_weekly_goal) else stringResource(R.string.workouts_per_week),
                    trailing = {
                    Stepper((preferences.weeklyGoal ?: 0).toString(), {
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
                                        Math.addExact(value.weeklyGoal ?: 0, 1)
                                )
                            )
                        }
                    }, preferences.weeklyGoal != null, stringResource(R.string.decrease_goal), stringResource(R.string.increase_goal))
                })
            }
        }
        SettingsReminderCard(
            preferences,
            vm,
            notificationsAllowed,
            { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            { editReminderTime = true }
        )
        HealthSettings(data, vm)
        SectionHeader(stringResource(R.string.appearance))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
            ThemePreviews(mode, vm.container.themeSettings::set)
        }
        SectionHeader(stringResource(R.string.your_data))
        BackupControls(vm, preferences.lastBackupAt) {
            item {
                ListRow(
                    stringResource(R.string.export),
                    titleStyle = MaterialTheme.typography.bodyLarge,
                    onClick = onExport
                )
            }
        }
        SectionHeader(stringResource(R.string.privacy))
        ListCard {
            item {
                ListRow(stringResource(R.string.privacy_and_permissions), onClick = {
                    context.startActivity(Intent(context, HealthPrivacyActivity::class.java))
                })
            }
        }
    }
    if (editReminderTime) {
        com.emmah.inshape.ui.components.RecordedTimePicker(stringResource(R.string.reminder_time), preferences.reminderTime, { time ->
            vm.write { updatePreferences(snapshot().preferences.copy(reminderTime = time)) }
            editReminderTime = false
        }, { editReminderTime = false })
    }
}

@Composable
private fun SettingsReminderCard(
    preferences: Preferences,
    vm: JournalViewModel,
    notificationsAllowed: Boolean,
    requestPermission: () -> Unit,
    onEditTime: () -> Unit
) {
    val context = LocalContext.current
    val days = preferences.reminderDays.sorted().map {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, finnishLocale)
    }
    val reminderStatus = when {
        !preferences.remindersEnabled -> null

        !notificationsAllowed -> stringResource(R.string.notifications_blocked)

        days.isEmpty() -> stringResource(R.string.choose_at_least_one_day)

        else -> {
            val labels = if (days.size == 1) days.single()
            else stringResource(R.string.day_join, days.dropLast(1).joinToString(", "), days.last())
            stringResource(R.string.format_rest_days, labels, LocalTime.parse(preferences.reminderTime).workoutTime(true))
        }
    }
    ListCard {
        item {
            val held = preferences.remindersHeld
            val subtitle = reminderStatus.takeUnless { held }
            fun setReminders(enabled: Boolean) {
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
                    requestPermission()
                }
            }
            ListRow(
                stringResource(R.string.workout_reminders),
                subtitle = subtitle,
                trailing = { InShapeSwitch(preferences.remindersEnabled && !held, ::setReminders, stringResource(R.string.enable_workout_reminders)) }
            )
        }
        if (preferences.remindersEnabled && !preferences.remindersHeld) {
            item {
                Column(
                    Modifier.padding(vertical = Dimens.innerSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                ) {
                    ReminderDays(preferences, vm)
                    KeyValueRow(
                        stringResource(R.string.time),
                        LocalTime.parse(preferences.reminderTime).workoutTime(DateFormat.is24HourFormat(context)),
                        numeric = true,
                        onClick = onEditTime
                    )
                    if (!notificationsAllowed) {
                        OutlineButton(stringResource(R.string.notification_settings), {
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
}

@Composable
private fun ReminderDays(preferences: Preferences, vm: JournalViewModel) {
    val colors = InShapeTheme.colors
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            val fullName = day.getDisplayName(TextStyle.FULL, finnishLocale)
            val checked = day.value in preferences.reminderDays
            Box(
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
                            updatePreferences(value.copy(reminderDays = days))
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
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            day.getDisplayName(TextStyle.SHORT, finnishLocale),
                            style = InShapeType.navSelected,
                            color = if (checked) colors.onInk else colors.text
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreviews(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = InShapeTheme.colors
    val choices = listOf(
        ThemeMode.System to R.drawable.art_settings_theme_system,
        ThemeMode.Light to R.drawable.art_settings_theme_light,
        ThemeMode.Dark to R.drawable.art_settings_theme_dark
    )
    fun Modifier.themeOption(mode: ThemeMode): Modifier {
        val isSelected = selected == mode
        return then(if (isSelected) Modifier.border(Dimens.ring, colors.ink, InShapeShapes.field) else Modifier)
            .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(mode) })
    }
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        if (hasArtworkColumns()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                choices.forEach { (mode, artwork) ->
                    Column(
                        Modifier.weight(1f).themeOption(mode).padding(Dimens.innerTiny),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)
                    ) {
                        Image(painterResource(artwork), null, Modifier.widthIn(max = Dimens.themePreviewWidth).fillMaxWidth().aspectRatio(160f / 124f))
                        Text(stringResource(mode.label()))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                choices.forEach { (mode, artwork) ->
                    val isSelected = selected == mode
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dimens.touch).themeOption(mode).padding(horizontal = Dimens.innerSmall),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(painterResource(artwork), null, Modifier.size(Dimens.touch))
                        Text(stringResource(mode.label()), Modifier.weight(1f))
                        RadioButton(isSelected, onClick = null)
                    }
                }
            }
        }
    }
}

internal fun ThemeMode.label() = when (this) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

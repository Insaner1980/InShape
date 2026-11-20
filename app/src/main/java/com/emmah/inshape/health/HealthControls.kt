package com.emmah.inshape.health

import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import androidx.compose.material.icons.outlined.LockOpen
import com.emmah.inshape.ui.theme.workoutTime
import com.emmah.inshape.ui.theme.formatDuration
import com.emmah.inshape.ui.components.KeyValueRow
import android.text.format.DateFormat
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.Preferences
import com.emmah.inshape.data.Workout
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.InShapeSwitch
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.ResponsiveRow
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.SelectField
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

// Slot content: siblings use the caller's layout and spacing, so there is no single modifier root.
@Suppress("ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
fun ColumnScope.HealthSettings(data: JournalSnapshot, vm: JournalViewModel) {
    val context = LocalContext.current
    var grants by remember { mutableStateOf(emptySet<String>()) }
    var sources by remember { mutableStateOf(emptyList<String>()) }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var refreshFailed by remember { mutableStateOf(false) }
    var settingsOpenFailed by remember { mutableStateOf(false) }
    var heldRemoval by remember { mutableStateOf<String?>(null) }
    val gateway = vm.container.healthGateway
    val available =
        gateway.availability() == HealthConnectClient.SDK_AVAILABLE &&
            vm.container.integrationsEnabled
    LaunchedEffect(available, data.preferences.readHeartRate, refreshGeneration) {
        sources = emptyList()
        if (!available) {
            grants = emptySet()
            return@LaunchedEffect
        }
        try {
            val currentGrants = gateway.grants()
            coroutineContext.ensureActive()
            grants = currentGrants
            if (data.preferences.readHeartRate && HealthCapability.HEART.permission in currentGrants) {
                val context = gateway.readContext(
                    Preferences(readHeartRate = true),
                    ZonedDateTime.now(vm.container.clock)
                )
                coroutineContext.ensureActive()
                sources = context.heartSources
            }
            refreshFailed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            grants = emptySet()
            refreshFailed = true
        }
    }
    val request =
        rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) {
            refreshGeneration++
            vm.container.onForeground()
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshGeneration++ }
    SectionHeader(stringResource(R.string.health_connect))
    if (!available) {
        Text(stringResource(R.string.not_available_on_phone), style = MaterialTheme.typography.bodyMedium, color = InShapeTheme.colors.textSecondary)
    }
    HealthCapabilitiesCard(data.preferences, vm, available, grants, { request.launch(it) }) {
        try {
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            settingsOpenFailed = false
        } catch (_: android.content.ActivityNotFoundException) {
            settingsOpenFailed = true
        }
    }
    val hasHeartRateAccess = data.preferences.readHeartRate && HealthCapability.HEART.permission in grants
    if (available && hasHeartRateAccess && sources.isNotEmpty()) {
        SelectField(
            stringResource(R.string.heart_rate_source),
            data.preferences.heartRateSource
                ?: if (sources.size ==
                    1
                ) {
                    sources.single()
                } else {
                    stringResource(R.string.choose_source)
                },
            sources,
            { source ->
                vm.write {
                    updatePreferences(snapshot().preferences.copy(heartRateSource = source))
                }
            }
        )
    }
    if (data.outbox.isNotEmpty()) {
        Text(
            stringResource(R.string.pending_health_operations, data.outbox.size),
            style = MaterialTheme.typography.bodySmall
        )
        data.outbox.mapNotNull {
            it.error
        }.distinct().forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.error)
        }
        OutlineButton(stringResource(R.string.retry_operations), {
            HealthSyncWorker.enqueue(context, retryNow = true)
        }, enabled = available)
        if (available && data.outbox.any { it.operation == "DELETE" } &&
            HealthCapability.SHARE.permission !in grants
        ) {
            OutlineButton(stringResource(R.string.allow_removals), {
                request.launch(setOf(HealthCapability.SHARE.permission))
            })
        }
    }
    if (data.healthStates.any {
            it.restoredHold
        }
    ) {
        Text(
            stringResource(R.string.health_hold_message),
            style = MaterialTheme.typography.bodySmall
        )
    }
    data.healthStates.filter { it.status == "REMOVAL_HELD" }.forEach { state ->
        Text(
            stringResource(R.string.health_removal_confirmation_needed, state.workoutId),
            style = MaterialTheme.typography.bodySmall
        )
        TextAction(stringResource(R.string.remove_previous_shared), {
            heldRemoval = state.workoutId
        }, enabled = available, color = InShapeTheme.colors.error, icon = Icons.Outlined.Delete)
    }
    if (available && refreshFailed) {
        Text(
            stringResource(R.string.health_unreachable),
            style = MaterialTheme.typography.bodySmall,
            color = InShapeTheme.colors.error
        )
    }
    if (settingsOpenFailed) {
        Text(
            stringResource(R.string.open_android_health_settings),
            style = MaterialTheme.typography.bodySmall,
            color = InShapeTheme.colors.error
        )
    }
    heldRemoval?.let { id ->
        ConfirmDialog(
            stringResource(R.string.remove_old_shared_question),
            stringResource(R.string.remove_health_old_message, id),
            stringResource(R.string.confirm_removal),
            destructive = true,
            {
                heldRemoval =
                    null
                vm.write { vm.container.healthSharing.removeShared(id) }
            },
            { heldRemoval = null }
        )
    }
}

@Composable
private fun HealthCapabilitiesCard(
    preferences: Preferences,
    vm: JournalViewModel,
    available: Boolean,
    grants: Set<String>,
    requestPermission: (Set<String>) -> Unit,
    openSettings: () -> Unit
) {
    val capabilities = HealthCapability.entries.filter { available || it.enabled(preferences) }
    if (capabilities.isEmpty()) return
    ListCard {
        capabilities.forEach { capability ->
            item {
                val enabled = capability.enabled(preferences)
                val needsGrant = available && capability.permission !in grants
                ListRow(
                    if (capability == HealthCapability.HEART) stringResource(R.string.latest_heart_rate) else stringResource(capability.label),
                    titleStyle = MaterialTheme.typography.bodyLarge,
                    subtitle = if (enabled && needsGrant) stringResource(R.string.permission_needed) else null,
                    trailing = {
                        InShapeSwitch(enabled, { checked ->
                            vm.write {
                                updatePreferences(capability.change(snapshot().preferences, checked))
                            }
                            if (checked && needsGrant) requestPermission(setOf(capability.permission))
                        }, stringResource(capability.label), enabled = available || enabled)
                    }
                )
                if (enabled && needsGrant) {
                    TextAction(stringResource(R.string.allow_access), { requestPermission(setOf(capability.permission)) }, icon = Icons.Outlined.LockOpen)
                }
            }
        }
        if (available) {
            item {
                ListRow(stringResource(R.string.manage_health), titleStyle = MaterialTheme.typography.bodyLarge, onClick = openSettings)
            }
        }
    }
}

// Slot content: siblings use the caller's layout and spacing, so there is no single modifier root.
@Suppress("ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
fun HealthToday(preferences: Preferences, container: AppContainer) {
    val readsEnabled = preferences.readSteps || preferences.readSleep || preferences.readHeartRate
    if (!container.integrationsEnabled || !readsEnabled) {
        return
    }
    var context by remember(preferences.readSteps, preferences.readSleep, preferences.readHeartRate, preferences.heartRateSource) {
        mutableStateOf<HealthContext?>(null)
    }
    var generation by remember { mutableIntStateOf(0) }
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { generation++ }
    LaunchedEffect(
        preferences.readSteps,
        preferences.readSleep,
        preferences.readHeartRate,
        preferences.heartRateSource,
        generation
    ) {
        try {
            context = container.healthGateway.readContext(preferences, ZonedDateTime.now(container.clock))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            context = null
        }
    }
    val values = listOfNotNull(
        context?.steps?.let { stringResource(R.string.steps_today) to it.toString() },
        context?.sleepSeconds?.let { stringResource(R.string.sleep_last_night) to formatDuration(it) },
        context?.heart?.let { sample ->
            stringResource(R.string.heart_rate) to stringResource(R.string.heart_rate_at_time, sample.beats, sample.time.workoutTime(container.clock.zone, uses24Hours))
        }
    ).take(2)
    if (values.isNotEmpty()) {
        InShapeCard(color = InShapeTheme.colors.surface, inset = true, gap = Dimens.zero) {
            values.forEach { (label, value) -> KeyValueRow(label, value, numeric = true) }
        }
    }
}

@Composable
fun WorkoutHealthControls(workout: Workout, data: JournalSnapshot, vm: JournalViewModel, modifier: Modifier = Modifier) {
    val id = workout.row.id
    val state = data.healthStates.find { it.workoutId == id }
    if (state == null && !data.preferences.shareWorkouts) return
    var remove by remember { mutableStateOf(false) }
    var reconcile by remember { mutableStateOf(false) }
    val readPermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val request =
        rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { result ->
            if (readPermission in result) vm.write { vm.container.healthSharing.reconcile(id) }
        }
    InShapeCard(modifier) {
        SectionHeader(stringResource(R.string.health_connect))
        Text(
            state?.error
                ?: if (state?.restoredHold ==
                    true
                ) {
                    stringResource(R.string.sharing_on_hold)
                } else {
                    state?.status?.lowercase()?.replace('_', ' ')
                        ?: stringResource(R.string.not_shared)
                }
        )
        if (!HealthSharing.eligible(
                workout
            )
        ) {
            Text(
                stringResource(R.string.sharing_needs_timing)
            )
        }
        if (state?.status == "SHARE_UNCONFIRMED" || state?.platformId != null ||
            (state?.acknowledgedRevision ?: 0) > 0
        ) {
            TextAction(stringResource(R.string.remove_shared_copy), {
                remove = true
            }, color = InShapeTheme.colors.error, icon = Icons.Outlined.Delete)
        }
        if (state?.restoredHold == true && data.preferences.shareWorkouts &&
            HealthSharing.eligible(workout)
        ) {
            OutlineButton(stringResource(R.string.reconcile_workout), {
                reconcile =
                    true
            })
        }
    }
    if (remove) {
        ConfirmDialog(
            stringResource(R.string.remove_shared_question),
            stringResource(R.string.remove_health_message),
            stringResource(R.string.remove_shared_copy),
            destructive = true,
            {
                remove =
                    false
                vm.write { vm.container.healthSharing.removeShared(id) }
            },
            { remove = false }
        )
    }
    if (reconcile) {
        ConfirmDialog(
            stringResource(R.string.reconcile_question),
            stringResource(R.string.reconcile_health_message),
            stringResource(R.string.continue_action),
            destructive = false,
            {
                reconcile =
                    false
                request.launch(setOf(readPermission))
            },
            { reconcile = false }
        )
    }
}

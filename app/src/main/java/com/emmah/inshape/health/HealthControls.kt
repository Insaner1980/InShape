package com.emmah.inshape.health

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.emmah.inshape.*
import com.emmah.inshape.data.*
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

@Composable
fun HealthSettings(data: JournalSnapshot, vm: JournalViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var grants by remember { mutableStateOf(emptySet<String>()) }
    var sources by remember { mutableStateOf(emptyList<String>()) }
    var message by remember { mutableStateOf("") }
    var heldRemoval by remember { mutableStateOf<String?>(null) }
    val gateway = vm.container.healthGateway
    val available = gateway.availability() == HealthConnectClient.SDK_AVAILABLE && vm.container.integrationsEnabled
    fun refresh() { scope.launch {
        if (!vm.container.integrationsEnabled) return@launch
        try {
            grants = gateway.grants()
            if (data.preferences.readHeartRate && HealthCapability.HEART.permission in grants) sources = gateway.readContext(Preferences(readHeartRate = true), ZonedDateTime.now(vm.container.clock)).heartSources
        } catch (_: Exception) { message = "Health Connect could not be reached. Your local journal is available." }
    } }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh(); vm.container.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    LaunchedEffect(data.preferences.readHeartRate) { refresh() }
    GroupLabel("Health Connect")
    ListCard {
        HealthCapability.entries.forEach { capability -> item {
            val enabled = capability.enabled(data.preferences)
            ListRow(if (capability == HealthCapability.HEART) "Latest heart rate" else capability.label, titleStyle = InShapeType.settingsRow,
                subtitle = if (enabled && !available) "Unavailable" else if (enabled && capability.permission !in grants) "Permission needed" else null,
                trailing = { InShapeSwitch(enabled, { checked ->
                    vm.write { updatePreferences(capability.change(snapshot().preferences, checked)) }
                    if (checked && available && capability.permission !in grants) request.launch(setOf(capability.permission))
                }, capability.label, enabled = available || enabled) })
            if (enabled && available && capability.permission !in grants) TextAction("Allow ${capability.label.lowercase()}", { request.launch(setOf(capability.permission)) })
        } }
        item { ListRow("Privacy and permissions", titleStyle = InShapeType.settingsRow, onClick = { context.startActivity(Intent(context, HealthPrivacyActivity::class.java)) }) }
        if (available) item { ListRow("Manage Health Connect", titleStyle = InShapeType.settingsRow, onClick = {
            try { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) } catch (_: android.content.ActivityNotFoundException) { message = "Open Android Settings and search for Health Connect." }
        }) }
    }
    Text("Workout summaries can be shared; steps, sleep and heart rate are optional context.", style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
    if (!available) Text(if (gateway.availability() == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) "Install or update Health Connect to connect on this device." else "Health Connect is unavailable on this device.", style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
    if (data.preferences.readHeartRate && sources.isNotEmpty()) SelectField("Heart rate source", data.preferences.heartRateSource ?: if (sources.size == 1) sources.single() else "Choose a source", sources, { source -> vm.write { updatePreferences(snapshot().preferences.copy(heartRateSource = source)) } })
    if (data.outbox.isNotEmpty()) {
        Text("${data.outbox.size} sharing or removal operations pending", style = MaterialTheme.typography.bodySmall)
        data.outbox.mapNotNull { it.error }.distinct().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.error) }
        SecondaryButton("Retry pending operations", { HealthSyncWorker.enqueue(context) }, enabled = available)
        if (available && data.outbox.any { it.operation == "DELETE" } && HealthCapability.SHARE.permission !in grants) SecondaryButton("Allow pending removals", { request.launch(setOf(HealthCapability.SHARE.permission)) })
    }
    if (data.healthStates.any { it.restoredHold }) Text("Restored or removed records stay on hold. Open an individual workout to reconcile and share it explicitly.", style = MaterialTheme.typography.bodySmall)
    data.healthStates.filter { it.status == "REMOVAL_HELD" }.forEach { state ->
        Text("A previously requested removal needs confirmation on this installation. Workout ${state.workoutId}", style = MaterialTheme.typography.bodySmall)
        SecondaryButton("Remove previously deleted shared workout", { heldRemoval = state.workoutId }, enabled = available)
    }
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.error)
    heldRemoval?.let { id -> ConfirmDialog("Remove the old shared summary?", "This removes only InShape's Health Connect summary for workout $id. No restored local workout is deleted. Access is required before removal can finish.", "Confirm removal", { heldRemoval = null; vm.write { vm.container.healthSharing.removeShared(id) } }, { heldRemoval = null }) }
}

@Composable
fun HealthToday(preferences: Preferences, container: AppContainer) {
    if (!container.integrationsEnabled || (!preferences.readSteps && !preferences.readSleep && !preferences.readHeartRate)) return
    var context by remember { mutableStateOf<HealthContext?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { generation++ }
    LaunchedEffect(preferences.readSteps, preferences.readSleep, preferences.readHeartRate, preferences.heartRateSource, generation) {
        try { context = container.healthGateway.readContext(preferences, ZonedDateTime.now(container.clock)); error = false }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { context = null; error = true }
    }
    SectionLabel("Health context")
    if (error) Text("Health Connect is unavailable. Your journal is ready to use.", style = MaterialTheme.typography.bodySmall)
    else if (context == null) Text("Loading optional context…", style = MaterialTheme.typography.bodySmall)
    listOf("Steps today" to context?.steps, "Sleep last night" to context?.sleep, "Latest recorded heart rate" to context?.heart)
        .filter { it.second != null }.chunked(2).forEach { row -> ResponsiveRow {
            row.forEach { (label, value) -> InShapeCard(Modifier.weight(1f)) {
                Text(value!!.text, style = MaterialTheme.typography.headlineSmall)
                Text(label, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
                Text(value.period, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
                if (value.sources.isNotEmpty()) Text("Source: ${value.sources.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                value.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.error) }
            } }
        } }
}

@Composable
fun WorkoutHealthControls(workout: Workout, data: JournalSnapshot, vm: JournalViewModel) {
    val id = workout.row.id; val state = data.healthStates.find { it.workoutId == id }
    if (state == null && !data.preferences.shareWorkouts) return
    var remove by remember { mutableStateOf(false) }; var reconcile by remember { mutableStateOf(false) }
    val readPermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        if (readPermission in result) vm.write { vm.container.healthSharing.reconcile(id) }
    }
    InShapeCard {
        SectionLabel("Health Connect")
        Text(state?.error ?: if (state?.restoredHold == true) "On hold until you explicitly reconcile this record" else state?.status?.lowercase()?.replace('_', ' ') ?: "Not shared")
        if (!HealthSharing.eligible(workout)) Text("Sharing needs performed work and confirmed actual start and end times. Correct timing above if known.")
        if (state?.platformId != null || (state?.acknowledgedRevision ?: 0) > 0) SecondaryButton("Remove shared copy", { remove = true })
        if (state?.restoredHold == true && data.preferences.shareWorkouts && HealthSharing.eligible(workout)) SecondaryButton("Reconcile and share this workout", { reconcile = true })
    }
    if (remove) ConfirmDialog("Remove the shared summary?", "Keep your local workout. Its Health Connect copy will be removed when access is available, and this workout will stay on hold.", "Remove shared copy", { remove = false; vm.write { vm.container.healthSharing.removeShared(id) } }, { remove = false })
    if (reconcile) ConfirmDialog("Reconcile this workout?", "Exercise-read access is needed to find this app's existing summary and preserve its identity. Only this selected workout will be queued for sharing.", "Continue", { reconcile = false; request.launch(setOf(readPermission)) }, { reconcile = false })
}

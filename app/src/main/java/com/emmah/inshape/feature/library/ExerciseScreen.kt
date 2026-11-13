package com.emmah.inshape.feature.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.*
import com.emmah.inshape.feature.workout.itemSummary
import com.emmah.inshape.media.PhotoStore
import com.emmah.inshape.ui.components.*
import com.emmah.inshape.ui.theme.*
import com.emmah.inshape.feature.progress.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExerciseScreen(id: String, sessionItemId: String?, data: JournalSnapshot, vm: JournalViewModel, onEdit: () -> Unit, onExercise: (String) -> Unit, onWorkout: (String) -> Unit, onChart: () -> Unit, onBack: () -> Unit) {
    val exercise = data.exercises.find { it.id == id }
    val historical = data.items.find { it.id == sessionItemId }
    if (exercise == null && historical == null) { Page { EmptyState("Exercise not found", "It may have been removed by a restore.") }; return }
    val photos = data.photos.filter { it.exerciseId == id }
    var selectedId by remember(id) { mutableStateOf<String?>(null) }
    val selected = photos.find { it.id == selectedId } ?: photos.firstOrNull()
    var fullPhoto by remember { mutableStateOf<PhotoAsset?>(null) }
    var removal by remember { mutableStateOf<PhotoAsset?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var replacement by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.write { uris.forEach { vm.container.photos.importPhoto(id, it) } }
    }
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val assetId = replacement
        if (uri != null && assetId != null) vm.write { vm.container.photos.replacePhoto(assetId, uri) }
        replacement = null
    }
    val context = LocalContext.current
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (exercise != null) IconToggleButton(exercise.favorite, { vm.write { saveExercise(exercise.copy(favorite = it)) } }) { Icon(if (exercise.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorite") }
            SecondaryButton("Edit", onEdit, enabled = exercise != null)
        }
        Text(historical?.name ?: exercise!!.name, style = MaterialTheme.typography.headlineLarge)
        ActionRow { listOfNotNull(exercise?.muscles?.takeIf { it.isNotBlank() }, historical?.category ?: exercise?.category).forEach { Pill(it) } }
        if (selected != null) PhotoImage(selected, vm.container.photos, Modifier.fillMaxWidth().aspectRatio(1.79f).clip(InShapeShapes.card).clickable { fullPhoto = selected })
        else Surface(Modifier.fillMaxWidth().heightIn(min = Dimens.photoPlaceholder), color = InShapeTheme.colors.surfaceInset, shape = InShapeShapes.card) {
            Column(Modifier.padding(Dimens.inner), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(Dimens.iconLarge)); Text("Your photo of the machine", color = InShapeTheme.colors.onAccentContainer)
                SecondaryButton("Add photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = exercise != null)
            }
        }
        if (photos.isNotEmpty()) ActionRow {
            photos.forEach { photo -> PhotoImage(photo, vm.container.photos, Modifier.size(Dimens.photoThumbnail).clip(InShapeShapes.tile).clickable { selectedId = photo.id }) }
            SecondaryButton("Add photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = exercise != null, icon = Icons.Outlined.Add)
        }
        selected?.let { photo -> ActionRow {
            TextAction("View full photo", { fullPhoto = photo })
            TextAction("Replace photo", { replacement = photo.id; replace.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            TextAction("Remove photo", { removal = photo }, color = InShapeTheme.colors.error)
        } }
        SectionLabel("Machine settings")
        InShapeCard {
            if (historical != null) { Text("Setup saved for this workout"); Text(historical.setup.ifBlank { "No settings recorded" }) }
            else if (exercise!!.settings.isEmpty()) Text("Add your settings using Edit.", color = InShapeTheme.colors.textSecondary)
            else BoxWithConstraints {
                val columns = if (maxWidth >= Dimens.setFallback && LocalDensity.current.fontScale < 1.5f) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    exercise.settings.chunked(columns).forEach { settings -> Row(horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                        settings.forEach { setting -> InShapeCard(Modifier.weight(1f), color = InShapeTheme.colors.surfaceInset) { Text(setting.label, color = InShapeTheme.colors.textSecondary); Text(setting.value, style = MaterialTheme.typography.titleMedium) } }
                        if (settings.size < columns) Spacer(Modifier.weight(1f))
                    } }
                }
            }
        }
        SectionLabel("My instructions")
        InShapeCard {
            Text(exercise?.instructions?.ifBlank { "No instructions recorded" } ?: "No instructions recorded")
            exercise?.links?.forEach { link -> TextAction(link.label.ifBlank { link.url }, {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                catch (_: android.content.ActivityNotFoundException) { linkError = "No app is available to open this link. Your saved link is kept." }
                catch (_: SecurityException) { linkError = "This link could not be opened. Your saved link is kept." }
            }) }
            if (exercise?.notes?.isNotBlank() == true) Text(exercise.notes)
            linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        SectionLabel("Alternatives")
        InShapeCard {
            val alternatives = data.exercises.filter { it.id in (exercise?.alternatives ?: emptyList()) }
            if (alternatives.isEmpty()) Text("Choose your own alternatives using Edit.")
            alternatives.forEach { SecondaryButton(it.name, { onExercise(it.id) }) }
        }
        InShapeCard {
            ResponsiveRow { Text("History", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextAction("See chart", onChart) }
            val history = data.history.mapNotNull { workout -> workout.items.find { it.row.exerciseId == id && it.hasPerformedWork }?.let { workout to it } }
            if (history.isEmpty()) Text("No completed performance yet.")
            history.firstOrNull()?.second?.row?.let { row ->
                calculatePersonalBest(data.history, ExerciseMetricQuery(id, row.equipmentId, row.basis, DateRange(LocalDate.MIN, LocalDate.MAX), ExerciseMetric.LOAD_AT_REPS))?.let { best ->
                    InShapeCard(color = InShapeTheme.colors.surfaceInset) { Text("Best load for 10+ reps", style = MaterialTheme.typography.titleMedium); Text("${best.point.value?.stripTrailingZeros()?.toPlainString()} ${row.basis}, ${best.point.date.displayDate()}") }
                }
            }
            Column { history.forEach { (workout, item) ->
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = "View workout") { onWorkout(workout.row.id) }.padding(vertical = Dimens.itemGap)) {
                    Text(LocalDate.parse(workout.row.journalDate).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), color = InShapeTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(itemSummary(item), style = MaterialTheme.typography.titleMedium)
                }; HorizontalDivider()
            } }
        }
    }
    fullPhoto?.let { photo -> Dialog(onDismissRequest = { fullPhoto = null }) {
        Surface(shape = InShapeShapes.card) { Column(Modifier.padding(Dimens.itemGap)) { PhotoImage(photo, vm.container.photos, Modifier.fillMaxWidth().weight(1f, false)); PrimaryButton("Close photo", { fullPhoto = null }) } }
    } }
    removal?.let { photo -> ConfirmDialog("Remove photo?", "This photo will be removed from your exercise library.", "Remove", { vm.write { vm.container.photos.removePhoto(photo.id) }; removal = null }, { removal = null }) }
}

@Composable
fun PhotoImage(asset: PhotoAsset, store: PhotoStore, modifier: Modifier = Modifier) {
    var failed by remember(asset.path) { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, asset.path) {
        try { value = store.thumbnail(asset); failed = value == null }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Exercise photo", modifier, contentScale = ContentScale.Fit)
    else Surface(modifier, color = InShapeTheme.colors.surfaceInset) { Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Text(if (failed) "Photo could not be loaded. Try replacing it." else "Loading photo") } }
}

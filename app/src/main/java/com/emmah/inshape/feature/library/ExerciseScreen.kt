package com.emmah.inshape.feature.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.DateRange
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.MeasurementBasis
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.feature.progress.ExerciseMetric
import com.emmah.inshape.feature.progress.ExerciseMetricQuery
import com.emmah.inshape.feature.progress.calculatePersonalBest
import com.emmah.inshape.feature.workout.itemSummary
import com.emmah.inshape.media.PhotoStore
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.InShapeCard
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.components.TextAction
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.LightColors
import com.emmah.inshape.ui.theme.displayDate
import com.emmah.inshape.ui.theme.hasArtworkColumns
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// This screen exposes distinct navigation actions plus the standard layout modifier.
@Suppress("LongParameterList")
@Composable
fun ExerciseScreen(
    id: String,
    sessionItemId: String?,
    data: JournalSnapshot,
    vm: JournalViewModel,
    onEdit: () -> Unit,
    onExercise: (String) -> Unit,
    onWorkout: (String) -> Unit,
    onChart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exercise = data.exercises.find { it.id == id }
    val historical = data.items.find { it.id == sessionItemId }
    if (exercise == null &&
        historical == null
    ) {
        Page(modifier, topBar = { PushedHeader(onBack) }) { EmptyState("Exercise not found", "It may have been removed by a restore.") }
        return
    }
    val photos = data.photos.filter { it.exerciseId == id }
    var selectedId by remember(id) { mutableStateOf<String?>(null) }
    val selected = photos.find { it.id == selectedId } ?: photos.firstOrNull()
    var fullPhoto by remember { mutableStateOf<PhotoAsset?>(null) }
    var removal by remember { mutableStateOf<PhotoAsset?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var replacement by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(20)
        ) { uris ->
            vm.importExercisePhotos(id, uris)
        }
    val replace =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val assetId = replacement
            if (uri != null &&
                assetId != null
            ) {
                vm.write { vm.container.photos.replacePhoto(assetId, uri) }
            }
            replacement = null
        }
    val context = LocalContext.current
    var photoMenu by remember { mutableStateOf(false) }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Page(modifier, topBar = {
        PushedHeader(onBack, endInset = true) {
            if (exercise !=
                null
            ) {
                IconToggleButton(exercise.favorite, {
                    vm.write { saveExercise(exercise.copy(favorite = it)) }
                }) {
                    Icon(
                        if (exercise.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        "Favorite"
                    )
                }
            }
            OutlineButton("Edit", onEdit, enabled = exercise != null)
        }
    }) {
        Text(historical?.name ?: exercise!!.name, style = MaterialTheme.typography.headlineMedium)
        ActionRow {
            listOfNotNull(
                exercise?.muscles?.takeIf { it.isNotBlank() },
                historical?.category ?: exercise?.category
            ).forEach { Pill(it) }
        }
        if (selected !=
            null
        ) {
            Box(Modifier.fillMaxWidth()) {
                PhotoImage(
                    selected,
                    vm.container.photos,
                    Modifier.fillMaxWidth().aspectRatio(Dimens.PHOTO_ASPECT).clip(InShapeShapes.card)
                        .clickable(onClickLabel = "View full photo") { fullPhoto = selected }
                )
                Box(Modifier.align(Alignment.TopEnd).padding(Dimens.innerSmall)) {
                    Surface(shape = InShapeShapes.pill, color = InShapeTheme.colors.background) {
                        IconButton({ photoMenu = true }, Modifier.size(Dimens.touch)) {
                            Icon(Icons.Outlined.MoreVert, "Photo options")
                        }
                    }
                    AppMenu(photoMenu, { photoMenu = false }) {
                        MenuAction("Replace photo", Icons.Outlined.PhotoCamera, {
                            photoMenu = false
                            replacement = selected.id
                            replace.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        MenuAction("Remove photo", Icons.Outlined.Delete, {
                            photoMenu = false
                            removal = selected
                        }, destructive = true)
                    }
                }
            }
        } else {
            val artwork = if (historical != null) {
                equipmentArtwork(historical.equipmentName, historical.name)
            } else {
                exercise?.let { equipmentArtwork(it.equipmentName, it.name) }
            }?.drawable ?: R.drawable.art_exercises_exercise_equipment
            BoxWithConstraints(
                Modifier.fillMaxWidth().heightIn(min = Dimens.photoPlaceholder)
                    .clip(InShapeShapes.card).background(ArtworkWash.Equipment.brush()).padding(Dimens.inner),
                contentAlignment = Alignment.Center
            ) {
                val prompt: @Composable () -> Unit = {
                    Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(Dimens.iconLarge))
                    Text("Your photo of the machine", color = InShapeTheme.colors.text)
                    OutlineButton("Add photo", pickPhoto, enabled = exercise != null)
                }
                if (hasArtworkColumns()) {
                    val artSize = minOf(160.dp, maxWidth * .4f, maxWidth - 184.dp)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                            prompt()
                        }
                        Image(painterResource(artwork), null, Modifier.size(artSize))
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)
                    ) {
                        Image(painterResource(artwork), null, Modifier.size(112.dp))
                        prompt()
                    }
                }
            }
        }
        if (photos.isNotEmpty()) {
            ActionRow {
                photos.forEach { photo ->
                    PhotoImage(
                        photo,
                        vm.container.photos,
                        Modifier.size(Dimens.photoThumbnail).clip(InShapeShapes.field).clickable {
                            selectedId =
                                photo.id
                        }
                    )
                }
                if (exercise != null) {
                    Surface(
                        pickPhoto,
                        Modifier.size(Dimens.photoThumbnail),
                        shape = InShapeShapes.field,
                        border = BorderStroke(Dimens.line, InShapeTheme.colors.outline)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Add, "Add photo")
                        }
                    }
                }
            }
        }
        SectionHeader("Machine settings")
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            if (historical !=
                null
            ) {
                Text("Setup saved for this workout")
                Text(historical.setup.ifBlank { "No settings recorded" })
            } else if (exercise!!.settings.isEmpty()) {
                Text("Add your settings using Edit.", color = InShapeTheme.colors.textSecondary)
            } else {
                ListCard {
                    exercise.settings.forEach { setting ->
                        item {
                            KeyValueRow(setting.label, setting.value)
                        }
                    }
                }
            }
        }
        SectionHeader("My instructions")
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            Text(
                exercise?.instructions?.ifBlank { "No instructions recorded" }
                    ?: "No instructions recorded"
            )
            if (exercise?.notes?.isNotBlank() == true) Text(exercise.notes)
        }
        exercise?.links?.takeIf { it.isNotEmpty() }?.let { links ->
            SectionHeader("Links")
            ListCard {
                links.forEach { link ->
                    item {
                        val uri = link.url.toUri()
                        val host = uri.host.orEmpty().ifBlank { link.url }
                        ListRow(
                            link.label.ifBlank { host },
                            subtitle = host,
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    null,
                                    Modifier.size(Dimens.iconSmall),
                                    tint = InShapeTheme.colors.textSecondary
                                )
                            },
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: android.content.ActivityNotFoundException) {
                                    linkError = "No app is available to open this link. Your saved link is kept."
                                } catch (_: SecurityException) {
                                    linkError = "This link could not be opened. Your saved link is kept."
                                }
                            }
                        )
                    }
                }
            }
            linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        SectionHeader("Alternatives")
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            val alternatives = data.exercises.filter {
                it.id in
                    (exercise?.alternatives ?: emptyList())
            }
            if (alternatives.isEmpty()) Text("Choose your own alternatives using Edit.")
            ListCard {
                alternatives.forEach { alternative ->
                    item { ListRow(alternative.name, onClick = { onExercise(alternative.id) }) }
                }
            }
        }
        SectionHeader("History", action = "See chart", onAction = onChart)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            val history = data.history.mapNotNull { workout ->
                workout.items.find {
                    it.row.exerciseId ==
                        id &&
                        it.hasPerformedWork
                }?.let { workout to it }
            }
            if (history.isEmpty()) Text("No completed performance yet.")
            history.firstOrNull()?.second?.row?.let { row ->
                calculatePersonalBest(
                    data.history,
                    ExerciseMetricQuery(
                        id,
                        row.equipmentId,
                        row.basis,
                        DateRange(LocalDate.MIN, LocalDate.MAX),
                        ExerciseMetric.LOAD_AT_REPS
                    )
                )?.let { best ->
                    InShapeCard(inset = true) {
                        Text(
                            "Best load for 10+ reps",
                            style = MaterialTheme.typography.labelSmall,
                            color = InShapeTheme.colors.textSecondary
                        )
                        best.point.value?.let { value ->
                            Text(
                                MeasurementBasis.fromKey(row.basis).formatValue(value.stripTrailingZeros().toPlainString()),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(best.point.date.displayDate(), color = InShapeTheme.colors.textSecondary)
                    }
                }
            }
            Column {
                history.forEach { (workout, item) ->
                    Column(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "View workout") {
                            onWorkout(workout.row.id)
                        }.padding(vertical = Dimens.itemGap)
                    ) {
                        Text(
                            LocalDate.parse(
                                workout.row.journalDate
                            ).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                            color = InShapeTheme.colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(itemSummary(item), style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    fullPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { fullPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Column(Modifier.fillMaxSize().background(LightColors.ink).safeDrawingPadding()) {
                IconButton({ fullPhoto = null }, Modifier.size(Dimens.touch)) {
                    Icon(Icons.Outlined.Close, "Close photo", tint = LightColors.onInk)
                }
                PhotoImage(photo, vm.container.photos, Modifier.fillMaxWidth().weight(1f), maxSize = 2048)
            }
        }
    }
    removal?.let { photo ->
        ConfirmDialog("Remove photo?", "This photo will be removed from your exercise library.", "Remove", destructive = true, {
            vm.write { vm.container.photos.removePhoto(photo.id) }
            removal =
                null
        }, { removal = null })
    }
}

internal fun JournalViewModel.importExercisePhotos(exerciseId: String, uris: List<Uri>) {
    if (uris.isEmpty()) return
    // The write queue retries this closure after earlier photos have already been committed.
    var nextPhoto = 0
    write {
        while (nextPhoto < uris.size) {
            container.photos.importPhoto(exerciseId, uris[nextPhoto])
            nextPhoto++
        }
    }
}

@Composable
fun PhotoImage(asset: PhotoAsset, store: PhotoStore, modifier: Modifier = Modifier, maxSize: Int = 1024) {
    var failed by remember(asset.path) { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, asset.path, maxSize) {
        try {
            value = store.thumbnail(asset, maxSize)
            failed = value == null
        } catch (
            cancelled: kotlinx.coroutines.CancellationException
        ) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    if (bitmap !=
        null
    ) {
        Image(
            bitmap!!.asImageBitmap(),
            "Exercise photo",
            modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Surface(modifier, color = InShapeTheme.colors.surface) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    if (failed) "Photo could not be loaded. Try replacing it." else "Loading photo"
                )
            }
        }
    }
}

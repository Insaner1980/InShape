package com.emmah.inshape.feature.library

import com.emmah.inshape.ui.UiException
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ShowChart
import com.emmah.inshape.feature.workout.machineSettingsSummary
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.Workout
import com.emmah.inshape.ui.components.TextAction
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.Exercise
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.data.PhotoAsset
import com.emmah.inshape.feature.workout.itemSummary
import com.emmah.inshape.media.PhotoStore
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.KeyValueRow
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.Pill
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.theme.ArtworkWash
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.LightColors
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.hasArtworkColumns
import java.time.LocalDate

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
    modifier: Modifier = Modifier,
    onGuide: (String) -> Unit = {}
) {
    val exercise = data.exercises.find { it.id == id }
    val historical = data.items.find { it.id == sessionItemId }
    if (exercise == null &&
        historical == null
    ) {
        Page(modifier, topBar = { PushedHeader(onBack) }) { EmptyState(stringResource(R.string.exercise_missing), stringResource(R.string.exercise_missing_restore)) }
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
    var photoMenu by remember { mutableStateOf(false) }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Page(modifier, topBar = {
        PushedHeader(onBack, trailingText = true) {
            if (exercise !=
                null
            ) {
                IconToggleButton(exercise.favorite, {
                    vm.write { setExerciseFavorite(exercise.id, it) }
                }) {
                    Icon(
                        if (exercise.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        stringResource(R.string.favorite)
                    )
                }
            }
            TextAction(stringResource(R.string.edit), onEdit, icon = Icons.Outlined.Edit, enabled = exercise != null)
        }
    }) {
        Text((historical?.name ?: exercise!!.name).exerciseDisplayName(LocalContext.current), style = MaterialTheme.typography.headlineLarge)
        val guide = historical?.plan?.catalogExerciseId ?: data.programLinks.firstOrNull { it.exerciseId == id }?.catalogExerciseId
        if (guide != null && com.emmah.inshape.feature.program.programArtwork(guide) != null) {
            OutlineButton(stringResource(R.string.movement_guide), { onGuide(guide) })
        }
        com.emmah.inshape.feature.assistant.AssistantMemories(data.assistantMemories.filter { it.exerciseId == id }, vm)
        ActionRow {
            listOfNotNull(
                exercise?.muscles?.takeIf { it.isNotBlank() },
                (historical?.category ?: exercise?.category)?.categoryLabel(LocalContext.current)
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
                        .clickable(onClickLabel = stringResource(R.string.view_full_photo)) { fullPhoto = selected }
                )
                Box(Modifier.align(Alignment.TopEnd).padding(Dimens.innerSmall)) {
                    Surface(shape = InShapeShapes.pill, color = InShapeTheme.colors.background) {
                        IconButton({ photoMenu = true }, Modifier.size(Dimens.touch)) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.photo_options))
                        }
                    }
                    AppMenu(photoMenu, { photoMenu = false }) {
                        MenuAction(stringResource(R.string.replace_photo), Icons.Outlined.PhotoCamera, {
                            photoMenu = false
                            replacement = selected.id
                            replace.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        MenuAction(stringResource(R.string.remove_photo), Icons.Outlined.Delete, {
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
            ExercisePhotoPlaceholder(artwork, exercise != null, pickPhoto)
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
                            Icon(Icons.Outlined.Add, stringResource(R.string.add_photo))
                        }
                    }
                }
            }
        }
        val none = stringResource(R.string.none)
    SectionHeader(stringResource(R.string.machine_settings))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            if (historical !=
                null
            ) {
                Text(stringResource(R.string.setup_saved))
                Text(machineSettingsSummary(historical.setup).ifBlank { stringResource(R.string.no_settings_recorded) })
            } else if (exercise!!.settings.none { it.value.isNotBlank() && !it.value.equals(none, ignoreCase = true) && !it.value.equals("None", ignoreCase = true) }) {
                Text(stringResource(R.string.add_settings_hint), color = InShapeTheme.colors.textSecondary)
            } else {
                ListCard {
                    exercise.settings.filter { it.value.isNotBlank() && !it.value.equals(none, ignoreCase = true) && !it.value.equals("None", ignoreCase = true) }.forEach { setting ->
                        item {
                            KeyValueRow(setting.label.settingLabel(LocalContext.current), setting.value.settingValue(LocalContext.current))
                        }
                    }
                }
            }
        }
        SectionHeader(stringResource(R.string.notes))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            val notes = listOfNotNull(exercise?.instructions, exercise?.notes).filter(String::isNotBlank)
            if (notes.isEmpty()) Text(stringResource(R.string.no_notes)) else notes.forEach { Text(it) }
        }
        ExerciseLinks(exercise, linkError, onError = { linkError = it })
        SectionHeader(stringResource(R.string.alternatives))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
            val alternatives = data.exercises.filter {
                it.id in
                    (exercise?.alternatives ?: emptyList())
            }
            if (alternatives.isEmpty()) Text(stringResource(R.string.choose_alternatives_hint))
            ListCard {
                alternatives.forEach { alternative ->
                    item { ListRow(alternative.name, onClick = { onExercise(alternative.id) }) }
                }
            }
        }
        ExerciseHistory(id, data, onChart, onWorkout)
    }
    fullPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { fullPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Column(Modifier.fillMaxSize().background(LightColors.ink).safeDrawingPadding()) {
                IconButton({ fullPhoto = null }, Modifier.size(Dimens.touch)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.close_photo), tint = LightColors.onInk)
                }
                PhotoImage(photo, vm.container.photos, Modifier.fillMaxWidth().weight(1f), maxSize = 2048)
            }
        }
    }
    removal?.let { photo ->
        ConfirmDialog(stringResource(R.string.remove_photo_question), stringResource(R.string.remove_photo_message), stringResource(R.string.remove), destructive = true, {
            vm.write { vm.container.photos.removePhoto(photo.id) }
            removal =
                null
        }, { removal = null })
    }
}

@Composable
private fun ExercisePhotoPlaceholder(@DrawableRes artwork: Int, canAddPhoto: Boolean, onAdd: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = Dimens.photoPlaceholder)
            .clip(InShapeShapes.card).background(ArtworkWash.Equipment.brush()).padding(Dimens.inner),
        contentAlignment = Alignment.Center
    ) {
        val prompt: @Composable () -> Unit = {
            Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(Dimens.iconLarge))
            Text(stringResource(R.string.no_photo), color = InShapeTheme.colors.textSecondary)
            OutlineButton(stringResource(R.string.add_photo), onAdd, enabled = canAddPhoto)
        }
        if (hasArtworkColumns()) {
            val artSize = minOf(Dimens.exercisePhotoArtworkWidth, maxWidth * .4f, maxWidth - Dimens.artworkTextMinWidth)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap),
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
                Image(painterResource(artwork), null, Modifier.size(Dimens.exercisePhotoCompactArtwork))
                prompt()
            }
        }
    }
}

@Composable
private fun ExerciseLinks(exercise: Exercise?, linkError: String?, onError: (String) -> Unit) {
    val context = LocalContext.current
    exercise?.links?.takeIf { it.isNotEmpty() }?.let { links ->
        SectionHeader(stringResource(R.string.links))
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
                                onError(context.getString(R.string.link_no_app))
                            } catch (_: SecurityException) {
                                onError(context.getString(R.string.link_open_failed))
                            }
                        }
                    )
                }
            }
        }
        linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExerciseHistory(id: String, data: JournalSnapshot, onChart: () -> Unit, onWorkout: (String) -> Unit) {
    SectionHeader(stringResource(R.string.history), trailing = {
        TextAction(stringResource(R.string.see_chart), onChart, icon = Icons.Outlined.ShowChart)
    })
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
        val history = data.history.mapNotNull { workout ->
            workout.items.find {
                it.row.exerciseId ==
                    id &&
                    it.hasPerformedWork
            }?.let { workout to it }
        }
        if (history.isEmpty()) Text(stringResource(R.string.no_performed_exercise))
        Column {
            history.sortedWith(compareByDescending<Pair<Workout, WorkoutItem>> { it.first.row.journalDate }
                .thenByDescending { it.first.row.createdAt }).take(5).forEach { (workout, item) ->
                Column(
                    Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.view_workout)) {
                        onWorkout(workout.row.id)
                    }.padding(vertical = Dimens.itemGap)
                ) {
                    Text(
                        LocalDate.parse(workout.row.journalDate).shortDate(),
                        color = InShapeTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(itemSummary(LocalContext.current, item), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
        }
    }
}

internal fun JournalViewModel.importExercisePhotos(exerciseId: String, uris: List<Uri>) {
    if (uris.isEmpty()) return
    // The write queue retries this closure after earlier photos have already been committed.
    var nextPhoto = 0
    var rejected = 0
    write {
        while (nextPhoto < uris.size) {
            try {
                container.photos.importPhoto(exerciseId, uris[nextPhoto])
            } catch (_: IllegalArgumentException) {
                rejected++
            }
            nextPhoto++
        }
        if (rejected > 0) throw UiException(R.plurals.photo_import_failed, quantity = rejected)
    }
}

@Composable
fun PhotoImage(asset: PhotoAsset, store: PhotoStore, modifier: Modifier = Modifier, maxSize: Int = 1024) {
    val result by key(asset, maxSize, store) {
        produceState<Pair<android.graphics.Bitmap?, Boolean>>(null to false) {
            val thumbnail = try {
                store.thumbnail(asset, maxSize)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            value = thumbnail to (thumbnail == null)
        }
    }
    val (bitmap, failed) = result
    if (bitmap !=
        null
    ) {
        Image(
            bitmap.asImageBitmap(),
            stringResource(R.string.exercise_photo),
            modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Surface(modifier, color = InShapeTheme.colors.surface) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    if (failed) stringResource(R.string.photo_load_failed) else stringResource(R.string.loading_photo)
                )
            }
        }
    }
}

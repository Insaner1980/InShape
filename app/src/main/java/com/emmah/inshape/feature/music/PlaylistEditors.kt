package com.emmah.inshape.feature.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emmah.inshape.R
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.BottomActionBar
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.DialogAction
import com.emmah.inshape.ui.components.EditorSheet
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.ReorderHandle
import com.emmah.inshape.ui.components.SearchField
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.components.reorderActions
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun PlaylistDetailEditor(
    store: MusicStore,
    catalog: MusicCatalog,
    playlist: SavedPlaylist,
    action: String?,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    when (action) {
        "add" -> PlaylistEditor(store, catalog.songs, playlist = playlist, onClose = onClose)

        "edit" -> EditSavedPlaylist(store, playlist, onClose)

        "rename" -> EditSavedPlaylist(store, playlist, onClose, renameOnly = true)

        "delete" -> DeletePlaylistDialog(onClose) {
            store.delete(playlist.id)
            onClose()
            onDelete()
        }
    }
}

@Composable
internal fun PlaylistEditor(
    store: MusicStore,
    songs: List<MusicTrack>,
    onClose: () -> Unit,
    playlist: SavedPlaylist? = null,
    firstSong: String? = null,
    onCreate: (String) -> Unit = { onClose() }
) {
    var name by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOfNotNull(firstSong)) }
    val existing = remember(playlist) { playlist?.tracks?.map { it.id }?.toSet().orEmpty() }
    val shown = remember(songs, existing, search, selected) {
        val query = search.trim()
        songs.filter {
            it.id !in existing && if (it.available) {
                it.title.contains(query, true) || it.artist.contains(query, true)
            } else {
                it.id in selected
            }
        }
    }
    val addableIds = remember(songs, existing) { songs.map { it.id }.toSet() - existing }
    val selectedIds = selected.filter { it in addableIds }
    val count = selectedIds.size
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.playlist_save_failed)
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }
    val canEdit = { !saving }
    MusicEditor(
        title = if (playlist == null) stringResource(R.string.new_playlist) else stringResource(R.string.add_songs),
        onClose = { if (!saving) onClose() },
        snackbar = snackbar,
        primaryAction = {
            PrimaryButton(
                when {
                    playlist == null -> stringResource(R.string.create_playlist)
                    count == 0 -> stringResource(R.string.add_songs)
                    else -> stringResource(R.string.add_songs_count, pluralStringResource(R.plurals.song_count, count, count))
                },
                onClick = {
                    if (!saving) {
                        saving = true
                        scope.launch {
                            try {
                                if (playlist == null) {
                                    onCreate(store.create(name, selectedIds))
                                } else {
                                    store.add(playlist.id, selectedIds)
                                    onClose()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                launch { snackbar.showSnackbar(saveFailed) }
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving && if (playlist == null) name.isNotBlank() else count > 0,
                size = ButtonSize.Large
            )
        }
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.screenPadding)) {
            item {
                PlaylistEditorFields(
                    name = name.takeIf { playlist == null },
                    onNameChange = { name = it },
                    search = search,
                    onSearchChange = { search = it },
                    hasAvailableSongs = songs.any { it.available },
                    shownEmpty = shown.isEmpty(),
                    canEdit = canEdit
                )
            }
            items(shown, key = { it.id }) { track ->
                val checked = track.id in selected
                TrackCheckbox(track, checked, enabled = canEdit(), onToggle = { include ->
                    if (canEdit()) selected = if (include) selected + track.id else selected - track.id
                })
                MusicDivider()
            }
        }
    }
}

@Composable
private fun PlaylistEditorFields(
    name: String?,
    onNameChange: (String) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    hasAvailableSongs: Boolean,
    shownEmpty: Boolean,
    canEdit: () -> Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        if (name != null) {
            TextField(name, stringResource(R.string.playlist_name), {
                if (canEdit()) onNameChange(it)
            }, singleLine = true, enabled = canEdit())
        }
        if (hasAvailableSongs) {
            SearchField(search, { if (canEdit()) onSearchChange(it) }, stringResource(R.string.search_songs), enabled = canEdit())
        }
        if (shownEmpty) {
            EmptyState(stringResource(R.string.no_songs_to_add), Modifier.padding(vertical = Dimens.inner))
        }
    }
}

@Composable
internal fun EditSavedPlaylist(store: MusicStore, playlist: SavedPlaylist, onClose: () -> Unit, renameOnly: Boolean = false) {
    var name by rememberSaveable(playlist.id) { mutableStateOf(playlist.name) }
    var order by rememberSaveable(playlist.id) { mutableStateOf(playlist.tracks.map { it.id }) }
    var savedName by remember(playlist.id) { mutableStateOf(playlist.name) }
    var savedOrder by remember(playlist.id) { mutableStateOf(playlist.tracks.map { it.id }) }
    val writeLock = remember(playlist.id) { Mutex() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var closing by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    suspend fun saveLatest(): Boolean = writeLock.withLock {
        val nextName = name.trim()
        val nextOrder = order
        try {
            if (nextName.isNotEmpty() && nextName != savedName) {
                store.rename(playlist.id, nextName)
                savedName = nextName
            }
            if (nextOrder != savedOrder) {
                store.saveOrder(playlist.id, nextOrder)
                savedOrder = nextOrder
            }
            saveFailed = false
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            saveFailed = true
            false
        }
    }
    LaunchedEffect(playlist.id) {
        snapshotFlow { name to order }.collect { saveLatest() }
    }
    val retry = stringResource(R.string.retry)
    val saveError = stringResource(R.string.playlist_save_failed)
    LaunchedEffect(saveFailed) {
        while (saveFailed && snackbar.showSnackbar(
                saveError,
                actionLabel = retry,
                duration = SnackbarDuration.Indefinite
            ) == SnackbarResult.ActionPerformed
        ) {
            saveLatest()
        }
    }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Back can run before the autosave collector receives the latest draft.
            if (saveLatest()) onClose() else closing = false
        }
    }
    val nameField: @Composable () -> Unit = {
        TextField(
            name,
            stringResource(R.string.playlist_name),
            { if (!closing) name = it },
            singleLine = true,
            error = stringResource(R.string.playlist_name_required).takeIf { name.isBlank() },
            enabled = !closing
        )
    }
    if (renameOnly) {
        EditorSheet(stringResource(R.string.rename_playlist), ::close) {
            nameField()
            PlaylistSnackbarHost(snackbar)
            DialogAction(stringResource(R.string.close), ::close, Modifier.align(Alignment.End), enabled = !closing)
        }
        return
    }

    var menu by remember { mutableStateOf<String?>(null) }
    val byId = remember(playlist.tracks) { playlist.tracks.associateBy { it.id } }
    fun move(id: String, offset: Int) {
        val index = order.indexOf(id)
        if (index < 0) return
        val target = (index + offset).coerceIn(order.indices)
        if (target != index) order = order.toMutableList().apply { add(target, removeAt(index)) }
    }
    val canEdit = { !closing }
    MusicEditor(stringResource(R.string.edit_playlist), ::close, snackbar) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.screenPadding)) {
            item { nameField() }
            items(order, key = { it }) { id ->
                val track = byId[id] ?: return@items
                val index = order.indexOf(id)
                ReorderPlaylistRow(
                    track,
                    canEdit = canEdit,
                    canMoveUp = canEdit() && index > 0,
                    canMoveDown = canEdit() && index < order.lastIndex,
                    menuOpen = menu == id,
                    onMenuChange = { menu = if (it) id else null },
                    onMove = { move(id, it) },
                    onRemove = { order = order - id }
                )
                MusicDivider()
            }
        }
    }
}

@Composable
private fun ReorderPlaylistRow(
    track: MusicTrack,
    canEdit: () -> Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    menuOpen: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val move: (Int) -> Unit = { if (canEdit()) onMove(it) }
    ListRow(
        track.title,
        modifier = Modifier.reorderActions(canMoveUp, canMoveDown, move),
        subtitle = if (track.available) track.artist else stringResource(R.string.unavailable_artist, track.artist),
        leading = { ReorderHandle(track.title, enabled = canEdit(), onMove = move) },
        trailing = {
            Box {
                IconButton({ onMenuChange(true) }, enabled = canEdit()) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.named_options, track.title))
                }
                AppMenu(canEdit() && menuOpen, { onMenuChange(false) }) {
                    if (canMoveUp) {
                        MenuAction(stringResource(R.string.move_up), Icons.Outlined.ArrowUpward, {
                            onMenuChange(false)
                            move(-1)
                        })
                    }
                    if (canMoveDown) {
                        MenuAction(stringResource(R.string.move_down), Icons.Outlined.ArrowDownward, {
                            onMenuChange(false)
                            move(1)
                        })
                    }
                    MenuAction(stringResource(R.string.remove_from_playlist), Icons.Outlined.Delete, {
                        onMenuChange(false)
                        if (canEdit()) onRemove()
                    }, destructive = true)
                }
            }
        }
    )
}

@Composable
private fun TrackCheckbox(track: MusicTrack, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.rowMinTwoLine)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)
    ) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        TrackLabels(track, Modifier.weight(1f).padding(vertical = Dimens.innerSmall))
    }
}

@Composable
private fun MusicEditor(
    title: String,
    onClose: () -> Unit,
    snackbar: SnackbarHostState,
    primaryAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = InShapeTheme.colors.background, contentColor = InShapeTheme.colors.text) {
            MusicColumn(Modifier.safeDrawingPadding().imePadding()) {
                PushedHeader(onClose, title = title)
                content()
                PlaylistSnackbarHost(snackbar)
                if (primaryAction != null) BottomActionBar(primaryAction)
            }
        }
    }
}

@Composable
private fun PlaylistSnackbarHost(state: SnackbarHostState) {
    val colors = InShapeTheme.colors
    SnackbarHost(state) { entry ->
        Snackbar(entry, containerColor = colors.ink, contentColor = colors.onInk, actionColor = colors.onInk)
    }
}

@Composable
internal fun AddSongDialog(store: MusicStore, playlists: List<SavedPlaylist>, trackId: String, onClose: () -> Unit, onCreate: () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun close() {
        if (!saving) onClose()
    }
    EditorSheet(stringResource(R.string.add_to_playlist), ::close) {
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = Dimens.bodyHeight)) {
            items(playlists, key = { it.id }) { playlist ->
                val added = playlist.tracks.any { it.id == trackId }
                ListRow(
                    playlist.name,
                    subtitle = stringResource(R.string.already_added).takeIf { added },
                    trailing = if (added) ({ Icon(Icons.Outlined.Check, null) }) else null,
                    enabled = !saving && !added,
                    showChevron = false,
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                store.add(playlist.id, listOf(trackId))
                                onClose()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = true
                            } finally {
                                saving = false
                            }
                        }
                    }
                )
            }
        }
        if (error) Text(stringResource(R.string.add_song_failed))
        DialogAction(stringResource(R.string.new_playlist), {
            onClose()
            onCreate()
        }, Modifier.align(Alignment.End), enabled = !saving)
    }
}

@Composable
private fun DeletePlaylistDialog(onClose: () -> Unit, onDelete: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun close() {
        if (!saving) onClose()
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(R.string.delete_playlist_question)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                Text(stringResource(R.string.music_files_kept))
                if (error) Text(stringResource(R.string.update_playlist_failed))
            }
        },
        confirmButton = {
            DialogAction(stringResource(R.string.delete_playlist), {
                saving = true
                scope.launch {
                    try {
                        onDelete()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = true
                    } finally {
                        saving = false
                    }
                }
            }, destructive = true, enabled = !saving)
        },
        dismissButton = { DialogAction(stringResource(R.string.cancel), ::close, enabled = !saving) }
    )
}

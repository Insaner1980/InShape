package com.emmah.inshape.feature.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.BottomActionBar
import com.emmah.inshape.ui.components.ButtonSize
import com.emmah.inshape.ui.components.DialogAction
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

        "edit" -> ReorderPlaylist(store, playlist, onClose)

        "rename" -> PlaylistPrompt("Rename playlist", "Save", onClose, initialName = playlist.name) { name ->
            store.rename(playlist.id, name)
            onClose()
        }

        "delete" -> PlaylistPrompt(
            "Delete playlist?",
            "Delete playlist",
            onClose,
            message = "Delete ${playlist.name} and its song list?",
            destructive = true
        ) {
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
    firstSong: String? = null
) {
    var name by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOfNotNull(firstSong)) }
    val existing = remember(playlist) { playlist?.tracks?.map { it.id }?.toSet().orEmpty() }
    val shown = remember(songs, existing, search) {
        val query = search.trim()
        songs.filter { it.available && it.id !in existing && (it.title.contains(query, true) || it.artist.contains(query, true)) }
    }
    MusicEditor(
        title = if (playlist == null) "New playlist" else "Add songs",
        onClose = onClose,
        saveEnabled = if (playlist == null) name.isNotBlank() else selected.isNotEmpty(),
        onSave = {
            if (playlist == null) store.create(name, selected) else store.add(playlist.id, selected)
        }
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.screenPadding)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                    if (playlist == null) {
                        TextField(name, "Playlist name", { name = it }, singleLine = true)
                    }
                    if (songs.any { it.available }) {
                        SearchField(search, { search = it }, "Find songs", Modifier.fillMaxWidth())
                    }
                    if (shown.isEmpty()) {
                        val message = if (search.isBlank()) "Choose a music folder with more songs." else "Try another word."
                        EmptyState("No songs to add", message, Modifier.padding(vertical = Dimens.inner))
                    }
                }
            }
            items(shown, key = { it.id }) { track ->
                val checked = track.id in selected
                TrackCheckbox(track, checked, {
                    selected = if (checked) selected - track.id else selected + track.id
                })
                MusicDivider()
            }
        }
    }
}

@Composable
private fun ReorderPlaylist(store: MusicStore, playlist: SavedPlaylist, onClose: () -> Unit) {
    var order by rememberSaveable(playlist.id) { mutableStateOf(playlist.tracks.map { it.id }) }
    var menu by remember { mutableStateOf<String?>(null) }
    val byId = remember(playlist.tracks) { playlist.tracks.associateBy { it.id } }
    fun move(id: String, offset: Int) {
        val index = order.indexOf(id)
        if (index < 0) return
        val target = (index + offset).coerceIn(order.indices)
        if (target != index) order = order.toMutableList().apply { add(target, removeAt(index)) }
    }
    MusicEditor("Edit playlist", onClose, saveEnabled = true, onSave = { store.saveOrder(playlist.id, order) }) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.screenPadding)) {
            items(order, key = { it }) { id ->
                val track = byId[id] ?: return@items
                val index = order.indexOf(id)
                val canMoveUp = index > 0
                val canMoveDown = index < order.lastIndex
                ListRow(
                    track.title,
                    modifier = Modifier.reorderActions(canMoveUp, canMoveDown) { move(id, it) },
                    subtitle = if (track.available) track.artist else "${track.artist}, unavailable",
                    leading = { ReorderHandle(track.title) { move(id, it) } },
                    trailing = {
                        Box {
                            IconButton({ menu = id }) { Icon(Icons.Outlined.MoreVert, "Options for ${track.title}") }
                            AppMenu(menu == id, { menu = null }) {
                                if (canMoveUp) {
                                    MenuAction("Move up", Icons.Outlined.ArrowUpward, {
                                        menu = null
                                        move(id, -1)
                                    })
                                }
                                if (canMoveDown) {
                                    MenuAction("Move down", Icons.Outlined.ArrowDownward, {
                                        menu = null
                                        move(id, 1)
                                    })
                                }
                                MenuAction("Remove from playlist", Icons.Outlined.Delete, {
                                    menu = null
                                    order = order - id
                                }, destructive = true)
                            }
                        }
                    }
                )
                MusicDivider()
            }
        }
    }
}

@Composable
private fun TrackCheckbox(track: MusicTrack, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Dimens.rowMinTwoLine).toggleable(checked, role = Role.Checkbox, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
    ) {
        Checkbox(checked, onCheckedChange = null)
        TrackLabels(track, Modifier.weight(1f).padding(vertical = Dimens.innerSmall))
    }
}

@Composable
private fun MusicEditor(
    title: String,
    onClose: () -> Unit,
    saveEnabled: Boolean,
    onSave: suspend () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun close() {
        if (!saving) onClose()
    }
    Dialog(::close, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = InShapeTheme.colors.background, contentColor = InShapeTheme.colors.text) {
            MusicColumn(Modifier.safeDrawingPadding().imePadding()) {
                PushedHeader(::close, title = title)
                content()
                BottomActionBar(scrolledContent = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                        if (error) Text("Could not save playlist. Try again.", color = InShapeTheme.colors.error)
                        PrimaryButton(
                            "Save",
                            {
                                saving = true
                                scope.launch {
                                    try {
                                        onSave()
                                        onClose()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = true
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = saveEnabled && !saving,
                            size = ButtonSize.Large
                        )
                    }
                }
            }
        }
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
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text("Add to playlist") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = Dimens.bodyHeight)) {
                    items(playlists, key = { it.id }) { playlist ->
                        val added = playlist.tracks.any { it.id == trackId }
                        ListRow(
                            playlist.name,
                            subtitle = "Already added".takeIf { added },
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
                if (error) Text("Could not add the song. Try again.")
            }
        },
        confirmButton = {
            DialogAction("New playlist", {
                onClose()
                onCreate()
            }, enabled = !saving)
        },
        dismissButton = { DialogAction("Cancel", ::close, enabled = !saving) }
    )
}

@Composable
private fun PlaylistPrompt(
    title: String,
    confirm: String,
    onClose: () -> Unit,
    initialName: String? = null,
    message: String? = null,
    destructive: Boolean = false,
    action: suspend (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(initialName) { mutableStateOf(initialName.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun close() {
        if (!saving) onClose()
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                if (message != null) Text(message)
                if (initialName != null) {
                    TextField(name, "Playlist name", { name = it }, singleLine = true)
                }
                if (error) Text("Could not update playlist. Try again.")
            }
        },
        confirmButton = {
            DialogAction(confirm, {
                saving = true
                scope.launch {
                    try {
                        action(name)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = true
                    } finally {
                        saving = false
                    }
                }
            }, destructive = destructive, enabled = !saving && (initialName == null || name.isNotBlank()))
        },
        dismissButton = { DialogAction("Cancel", ::close, enabled = !saving) }
    )
}

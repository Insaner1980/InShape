package com.emmah.inshape.feature.music

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.R
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.MenuAction
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.ScreenTabs
import com.emmah.inshape.ui.components.SearchField
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.formatElapsed
import com.emmah.inshape.ui.theme.hasArtworkColumns

@Composable
internal fun MusicHome(
    store: MusicStore,
    playback: MusicPlayback,
    onPlaylist: (String) -> Unit,
    fixture: MusicCatalog? = null,
    onPlay: () -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    val observedCatalog by store.catalog.collectAsStateWithLifecycle()
    val catalog = fixture ?: observedCatalog
    val scanning by store.scanning.collectAsStateWithLifecycle()
    val message by store.message.collectAsStateWithLifecycle()
    val playing by playback.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var firstSong by rememberSaveable { mutableStateOf<String?>(null) }
    var folderMenu by rememberSaveable { mutableStateOf(false) }
    var addSong by rememberSaveable { mutableStateOf<String?>(null) }
    val playlistScroll = rememberLazyListState()
    val songScroll = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { store.scan(it) }
        }
    }
    fun chooseFolder() {
        picker.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { store.checkAccess() }
    val songs = remember(catalog.songs, search) {
        val query = search.trim()
        catalog.songs.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val hasFolder = catalog.source != null
    val needsFolder = !hasFolder && catalog.songs.isEmpty()
    fun newPlaylist() {
        editor = "new"
        firstSong = null
    }
    val newButton: @Composable () -> Unit = {
        OutlineButton("New playlist", ::newPlaylist, icon = Icons.Filled.Add)
    }
    val folderActions: @Composable () -> Unit = {
        if (hasFolder) {
            Box {
                IconButton({ folderMenu = true }) { Icon(Icons.Filled.MoreVert, "Music folder options") }
                AppMenu(folderMenu, { folderMenu = false }) {
                    MenuAction("Choose folder", Icons.Outlined.FolderOpen, {
                        folderMenu = false
                        chooseFolder()
                    }, enabled = !scanning)
                    MenuAction("Refresh", Icons.Outlined.Refresh, {
                        folderMenu = false
                        store.scan()
                    }, enabled = !scanning && !catalog.accessLost)
                }
            }
        }
    }
    MusicColumn {
        onClose?.let { close ->
            PushedHeader(close, title = "Music", endInset = !largeText && !hasFolder) {
                if (!largeText) newButton()
                folderActions()
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().imePadding().padding(horizontal = Dimens.screenPadding),
            state = if (selected == 0) playlistScroll else songScroll
        ) {
            item(key = "music-header") {
                Column(
                    Modifier.padding(top = Dimens.screenTop, bottom = Dimens.itemGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)
                ) {
                    if (onClose == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { MusicTitle() }
                            if (!largeText) newButton()
                            folderActions()
                        }
                    }
                    if (largeText) newButton()
                }
                ScreenTabs(listOf("Playlists", "Songs"), selected, { selected = it })
                if (scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Dimens.itemGap))
                    Text("Scanning music folder…", Modifier.padding(top = Dimens.innerSmall))
                    com.emmah.inshape.ui.components.TextAction("Cancel scan", store::cancelScan)
                }
                MusicStatus(playing.error ?: message)
                if (selected == 1 && !needsFolder) {
                    SearchField(search, { search = it }, "Search songs", Modifier.padding(top = Dimens.itemGap))
                }
                if (catalog.accessLost) MusicStatus("Music folder access was lost.")
                if (!scanning) {
                    val padding = Modifier.padding(top = Dimens.itemGap)
                    when {
                        needsFolder -> EmptyState(
                            "Add your music",
                            "Choose a folder on this phone. InShape plays the files from there.",
                            padding,
                            "Choose music folder",
                            ::chooseFolder,
                            Icons.Outlined.FolderOpen
                        )

                        selected == 0 && catalog.playlists.isEmpty() -> EmptyState(
                            "No playlists yet",
                            "Make a playlist for your workouts.",
                            padding,
                            "New playlist",
                            ::newPlaylist,
                            Icons.Filled.Add
                        )

                        selected == 1 && songs.isEmpty() && search.isNotBlank() -> EmptyState(
                            "No matching songs",
                            "Try another word.",
                            padding
                        )

                        selected == 1 && songs.isEmpty() -> EmptyState(
                            "No songs found",
                            "Choose a folder that contains audio files.",
                            padding
                        )
                    }
                }
                Box(Modifier.size(Dimens.itemGap))
            }
            if (selected == 0) {
                items(catalog.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist, { onPlaylist(playlist.id) }) {
                        playback.start(playlist.tracks)
                        onPlay()
                    }
                    if (playlist.id != catalog.playlists.last().id) MusicDivider()
                }
            } else {
                items(songs, key = { it.id }) { song ->
                    SongRow(song, {
                        playback.start(songs, song.id)
                        onPlay()
                    }) {
                        SongMenu(song, { playback.playAfterCurrent(song) }, { addSong = song.id })
                    }
                    MusicDivider()
                }
            }
        }
    }
    if (editor == "new") {
        PlaylistEditor(store, catalog.songs, onClose = { editor = null }, firstSong = firstSong)
    }
    addSong?.let { id ->
        AddSongDialog(store, catalog.playlists, id, onClose = { addSong = null }, onCreate = {
            editor = "new"
            firstSong = id
        })
    }
}

@Composable
private fun MusicTitle() {
    Text("Music", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PlaylistRow(playlist: SavedPlaylist, open: () -> Unit, play: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Open ${playlist.name}", onClick = open)
                .padding(vertical = Dimens.innerSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.inner),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicArtwork(playlistCover(playlist), 68.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                SongCount(playlist.tracks.size)
            }
        }
        IconButton(play, Modifier.size(Dimens.touch), enabled = playlist.tracks.any { it.available }) {
            Icon(Icons.Filled.PlayArrow, "Play ${playlist.name}", Modifier.size(Dimens.iconLarge))
        }
    }
}

@Composable
internal fun PlaylistDetail(
    id: String,
    store: MusicStore,
    playback: MusicPlayback,
    onBack: () -> Unit,
    fixture: MusicCatalog? = null,
    onPlay: () -> Unit = {}
) {
    val observedCatalog by store.catalog.collectAsStateWithLifecycle()
    val catalog = fixture ?: observedCatalog
    val playlist = catalog.playlists.find { it.id == id }
    val playing by playback.state.collectAsStateWithLifecycle()
    var edit by rememberSaveable(id) { mutableStateOf<String?>(null) }
    MusicColumn {
        PushedHeader(onBack, title = "Playlist") {
            if (playlist != null) PlaylistMenu { edit = it }
        }
        if (playlist == null) return@MusicColumn
        val canPlay = playlist.tracks.any { it.available }
        val largeText = LocalDensity.current.fontScale >= 1.3f
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.screenPadding)) {
            item(key = "playlist-header") {
                Column(
                    Modifier.fillMaxWidth().padding(top = Dimens.screenTop, bottom = Dimens.itemGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.inner)
                ) {
                    PlaylistHeader(playlist)
                    val play: @Composable (Modifier) -> Unit = { modifier ->
                        PrimaryButton("Play", {
                            playback.start(playlist.tracks)
                            onPlay()
                        }, modifier, Icons.Filled.PlayArrow, canPlay)
                    }
                    val shuffle: @Composable (Modifier) -> Unit = { modifier ->
                        OutlineButton("Shuffle", {
                            playback.start(playlist.tracks, shuffle = true)
                            onPlay()
                        }, modifier, canPlay, Icons.Filled.Shuffle)
                    }
                    if (largeText) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
                            play(Modifier.fillMaxWidth())
                            shuffle(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                            play(Modifier.weight(1f))
                            shuffle(Modifier.weight(1f))
                        }
                    }
                }
                MusicStatus(playing.error)
                if (playlist.tracks.isEmpty()) {
                    EmptyState(
                        "No songs in this playlist",
                        "Add songs to play during your workouts.",
                        Modifier.padding(bottom = Dimens.itemGap),
                        "Add songs",
                        { edit = "add" },
                        Icons.Filled.Add
                    )
                }
            }
            items(playlist.tracks, key = { it.id }) { track ->
                SongRow(track, {
                    playback.start(playlist.tracks, track.id)
                    onPlay()
                })
                MusicDivider()
            }
        }
    }
    if (playlist != null) PlaylistDetailEditor(store, catalog, playlist, edit, { edit = null }, onBack)
}

@Composable
private fun PlaylistHeader(playlist: SavedPlaylist) {
    val cover = playlistCover(playlist)
    val wide = (cover as? MusicCover.Bundled)?.wide
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val regular = hasArtworkColumns()
        val height = (maxWidth / 2.4f).coerceIn(112.dp, 180.dp)
        val protectedWidth = maxWidth * .44f
        val textWidth = protectedWidth - Dimens.inner
        val density = LocalDensity.current
        val headline = MaterialTheme.typography.headlineSmall
        val showPanorama = wide != null && regular && LocalLayoutDirection.current == LayoutDirection.Ltr &&
            with(density) {
                !measurer.measure(
                    playlist.name,
                    headline,
                    maxLines = 3,
                    constraints = Constraints(
                        maxWidth = textWidth.roundToPx(),
                        maxHeight = (height - 56.dp).roundToPx()
                    )
                ).hasVisualOverflow
            }
        if (showPanorama) {
            Box(
                Modifier.fillMaxWidth().height(height).clip(InShapeShapes.card),
                contentAlignment = Alignment.CenterStart
            ) {
                Image(painterResource(wide), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = .84f),
                            .45f to Color.Black.copy(alpha = .72f),
                            1f to Color.Black.copy(alpha = .12f)
                        )
                    )
                )
                Box(Modifier.widthIn(max = protectedWidth).padding(start = Dimens.inner)) {
                    PlaylistTitle(playlist, Color.White, Color.White)
                }
            }
        } else if (!regular || wide != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.inner)) {
                MusicArtwork(cover, 104.dp)
                PlaylistTitle(playlist)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.inner)) {
                MusicArtwork(cover, 104.dp)
                Box(Modifier.weight(1f).padding(top = Dimens.innerTiny)) { PlaylistTitle(playlist) }
            }
        }
    }
}

@Composable
private fun PlaylistTitle(
    playlist: SavedPlaylist,
    titleColor: Color = InShapeTheme.colors.text,
    countColor: Color = InShapeTheme.colors.textSecondary
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
        Text(playlist.name, Modifier.semantics { heading() }, color = titleColor, style = MaterialTheme.typography.headlineSmall)
        SongCount(playlist.tracks.size, countColor)
    }
}

@Composable
private fun SongCount(count: Int, color: Color = InShapeTheme.colors.textSecondary) {
    Text(
        pluralStringResource(R.plurals.song_count, count, count),
        color = color,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun PlaylistMenu(onAction: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.size(Dimens.touch)) { Icon(Icons.Filled.MoreVert, "Playlist options") }
        AppMenu(open, { open = false }) {
            MenuAction("Add songs", Icons.Filled.Add, {
                open = false
                onAction("add")
            })
            MenuAction("Edit playlist", Icons.AutoMirrored.Filled.QueueMusic, {
                open = false
                onAction("edit")
            })
            MenuAction("Rename", Icons.Filled.Edit, {
                open = false
                onAction("rename")
            })
            MenuAction("Delete playlist", Icons.Filled.DeleteOutline, {
                open = false
                onAction("delete")
            }, destructive = true)
        }
    }
}

@Composable
private fun SongMenu(song: MusicTrack, next: () -> Unit, add: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.size(Dimens.touch)) { Icon(Icons.Filled.MoreVert, "Options for ${song.title}") }
        AppMenu(open, { open = false }) {
            if (song.available) {
                MenuAction("Play next", Icons.AutoMirrored.Filled.QueueMusic, {
                    open = false
                    next()
                })
            }
            MenuAction("Add to playlist", Icons.Filled.Add, {
                open = false
                add()
            })
        }
    }
}

@Composable
internal fun SongRow(track: MusicTrack, play: () -> Unit, actions: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = Dimens.rowMinTwoLine)
                .clickable(enabled = track.available, onClickLabel = "Play ${track.title}", role = Role.Button, onClick = play)
                .padding(vertical = Dimens.innerSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackLabels(track, Modifier.weight(1f), Arrangement.spacedBy(Dimens.innerTiny))
            Text(songTime(track.durationMs), style = MaterialTheme.typography.bodyMedium)
        }
        actions?.invoke()
    }
}

@Composable
internal fun TrackLabels(track: MusicTrack, modifier: Modifier = Modifier, arrangement: Arrangement.Vertical = Arrangement.Top) {
    Column(modifier, verticalArrangement = arrangement) {
        Text(track.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (track.available) track.artist else "${track.artist}, unavailable",
            style = MaterialTheme.typography.bodyMedium,
            color = InShapeTheme.colors.textSecondary
        )
    }
}

@Composable
internal fun MusicColumn(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.maxPageWidth).fillMaxSize(), content = content)
    }
}

@Composable
internal fun MusicDivider() = HorizontalDivider(thickness = Dimens.line, color = InShapeTheme.colors.divider)

@Composable
private fun MusicStatus(message: String?) {
    if (message != null) Text(message, Modifier.padding(vertical = Dimens.innerSmall), color = InShapeTheme.colors.textSecondary)
}

@Composable
internal fun MusicChooser(store: MusicStore, playback: MusicPlayback, onClose: () -> Unit, fixture: MusicCatalog? = null) {
    var playlist by rememberSaveable { mutableStateOf<String?>(null) }
    val holder: SaveableStateHolder = rememberSaveableStateHolder()
    Dialog(
        onDismissRequest = { if (playlist == null) onClose() else playlist = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = InShapeTheme.colors.background, contentColor = InShapeTheme.colors.text) {
            Box(Modifier.safeDrawingPadding()) {
                val id = playlist
                if (id == null) {
                    holder.SaveableStateProvider("chooser-home") {
                        MusicHome(store, playback, { playlist = it }, fixture, onPlay = onClose, onClose = onClose)
                    }
                } else {
                    PlaylistDetail(id, store, playback, { playlist = null }, fixture, onPlay = onClose)
                }
            }
        }
    }
}

internal fun songTime(milliseconds: Long?) = milliseconds?.let { formatElapsed(it / 1_000).removePrefix("0") } ?: "—"

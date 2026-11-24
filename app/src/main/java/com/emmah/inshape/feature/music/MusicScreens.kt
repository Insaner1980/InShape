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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.R
import com.emmah.inshape.ui.components.AppMenu
import com.emmah.inshape.ui.components.EmptyState
import com.emmah.inshape.ui.components.HeaderIconButton
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
    onBack: () -> Unit,
    fixture: MusicCatalog? = null,
    onPlaylistAssistant: (() -> Unit)? = null,
    onEnergy: ((String) -> Unit)? = null
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
    val hasFolder = catalog.source != null
    val needsFolder = !hasFolder && catalog.songs.isEmpty()
    fun newPlaylist() {
        editor = "new"
        firstSong = null
    }
    var addMenu by remember { mutableStateOf(false) }
    MusicColumn {
        PushedHeader(onBack, title = stringResource(R.string.music)) {
            Box {
                HeaderIconButton(Icons.Filled.Add, stringResource(R.string.new_playlist), {
                    if (selected == 0) addMenu = true else newPlaylist()
                })
                AppMenu(addMenu, { addMenu = false }) {
                    MenuAction(stringResource(R.string.new_playlist), Icons.Filled.Add, {
                        addMenu = false
                        newPlaylist()
                    })
                    if (onPlaylistAssistant != null && catalog.songs.isNotEmpty()) {
                        MenuAction(stringResource(R.string.playlist_with_assistant), Icons.Outlined.AutoAwesome, {
                            addMenu = false
                            onPlaylistAssistant()
                        })
                    }
                }
            }
            MusicFolderActions(
                folderMenu,
                { folderMenu = it },
                canChoose = !scanning,
                canRefresh = hasFolder && !scanning && !catalog.accessLost,
                onChoose = ::chooseFolder,
                onRefresh = { store.scan() }
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().imePadding().padding(horizontal = Dimens.screenPadding),
            state = if (selected == 0) playlistScroll else songScroll
        ) {
            item(key = "music-header") {
                ScreenTabs(listOf(stringResource(R.string.playlists), stringResource(R.string.songs)), selected, { selected = it })
                MusicScanningStatus(scanning, store::cancelScan)
                MusicStatus(message)
                MusicStatus(playing.error)
                if (selected == 1 && !needsFolder) {
                    SearchField(search, { search = it }, stringResource(R.string.search_songs), Modifier.padding(top = Dimens.itemGap))
                }
                if (catalog.accessLost) MusicStatus(stringResource(R.string.music_access_lost))
                if (!scanning) {
                    MusicHomeEmptyState(
                        needsFolder,
                        selectedTab = selected,
                        playlistsEmpty = catalog.playlists.isEmpty(),
                        songsEmpty = songs.isEmpty(),
                        hasSearch = search.isNotBlank(),
                        onChooseFolder = ::chooseFolder,
                        onNewPlaylist = ::newPlaylist
                    )
                }
                Box(Modifier.size(Dimens.itemGap))
            }
            if (selected == 0) {
                items(catalog.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist, { onPlaylist(playlist.id) }) {
                        playback.start(playlist.tracks)
                    }
                    if (playlist.id != catalog.playlists.last().id) MusicDivider()
                }
            } else {
                items(songs, key = { it.id }) { song ->
                    SongRow(song, {
                        playback.start(songs, song.id)
                    }) {
                        SongMenu(song, {
                            playback.playAfterCurrent(song)
                        }, { addSong = song.id }, onEnergy?.let { choose -> { choose(song.id) } })
                    }
                    MusicDivider()
                }
            }
        }
    }
    if (editor == "new") {
        PlaylistEditor(store, catalog.songs, onClose = { editor = null }, firstSong = firstSong, onCreate = { editor = it })
    } else {
        observedCatalog.playlists.firstOrNull { it.id == editor }?.let { playlist ->
            EditSavedPlaylist(store, playlist, onClose = { editor = null })
        }
    }
    addSong?.let { id ->
        AddSongDialog(store, catalog.playlists, id, onClose = { addSong = null }, onCreate = {
            editor = "new"
            firstSong = id
        })
    }
}

@Composable
private fun MusicScanningStatus(scanning: Boolean, onCancel: () -> Unit) {
    if (!scanning) return
    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Dimens.itemGap))
    Text(stringResource(R.string.scanning_music), Modifier.padding(top = Dimens.innerSmall))
    com.emmah.inshape.ui.components.TextAction(stringResource(R.string.cancel_scan), onCancel, icon = Icons.Outlined.Close)
}

@Composable
private fun MusicFolderActions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    canChoose: Boolean,
    canRefresh: Boolean,
    onChoose: () -> Unit,
    onRefresh: () -> Unit
) {
    Box {
        HeaderIconButton(Icons.Filled.MoreVert, stringResource(R.string.music_folder_options), { onExpandedChange(true) })
        AppMenu(expanded, { onExpandedChange(false) }) {
            MenuAction(stringResource(R.string.choose_folder), Icons.Outlined.FolderOpen, {
                onExpandedChange(false)
                onChoose()
            }, enabled = canChoose)
            MenuAction(stringResource(R.string.refresh), Icons.Outlined.Refresh, {
                onExpandedChange(false)
                onRefresh()
            }, enabled = canRefresh)
        }
    }
}

@Composable
private fun MusicHomeEmptyState(
    needsFolder: Boolean,
    selectedTab: Int,
    playlistsEmpty: Boolean,
    songsEmpty: Boolean,
    hasSearch: Boolean,
    onChooseFolder: () -> Unit,
    onNewPlaylist: () -> Unit
) {
    val padding = Modifier.padding(top = Dimens.itemGap)
    when {
        needsFolder -> EmptyState(
            stringResource(R.string.add_music),
            padding,
            stringResource(R.string.choose_music_folder),
            onChooseFolder,
            Icons.Outlined.FolderOpen
        )

        selectedTab == 0 && playlistsEmpty -> EmptyState(
            stringResource(R.string.no_playlists),
            padding,
            stringResource(R.string.new_playlist),
            onNewPlaylist,
            Icons.Filled.Add
        )

        selectedTab == 1 && songsEmpty && hasSearch -> EmptyState(stringResource(R.string.no_matching_songs), padding)

        selectedTab == 1 && songsEmpty -> EmptyState(stringResource(R.string.no_songs_found), padding)
    }
}

@Composable
private fun PlaylistRow(playlist: SavedPlaylist, open: () -> Unit, play: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_named, playlist.name), onClick = open)
                .padding(vertical = Dimens.innerSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.inner),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicArtwork(playlistCover(playlist), Dimens.playlistArtworkThumbnail)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                SongCount(playlist.tracks.size)
            }
        }
        IconButton(play, Modifier.size(Dimens.touch), enabled = playlist.tracks.any { it.available }) {
            Icon(Icons.Filled.PlayArrow, stringResource(R.string.play_named, playlist.name), Modifier.size(Dimens.iconLarge))
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
    onAssistant: (() -> Unit)? = null
) {
    val observedCatalog by store.catalog.collectAsStateWithLifecycle()
    val catalog = fixture ?: observedCatalog
    val playlist = catalog.playlists.find { it.id == id }
    val playing by playback.state.collectAsStateWithLifecycle()
    var edit by rememberSaveable(id) { mutableStateOf<String?>(null) }
    MusicColumn {
        PushedHeader(onBack) {
            if (playlist != null) PlaylistMenu(onAssistant) { edit = it }
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
                        PrimaryButton(stringResource(R.string.play), {
                            playback.start(playlist.tracks)
                        }, modifier, Icons.Filled.PlayArrow, canPlay)
                    }
                    val shuffle: @Composable (Modifier) -> Unit = { modifier ->
                        OutlineButton(stringResource(R.string.shuffle), {
                            playback.start(playlist.tracks, shuffle = true)
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
                        stringResource(R.string.empty_playlist),
                        Modifier.padding(bottom = Dimens.itemGap),
                        stringResource(R.string.add_songs),
                        { edit = "add" },
                        Icons.Filled.Add
                    )
                }
            }
            items(playlist.tracks, key = { it.id }) { track ->
                SongRow(track, {
                    playback.start(playlist.tracks, track.id)
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
        val height = (maxWidth / 2.4f).coerceIn(Dimens.playlistHeaderMinHeight, Dimens.playlistHeaderMaxHeight)
        val protectedWidth = maxWidth * .44f
        val textWidth = protectedWidth - Dimens.inner
        val density = LocalDensity.current
        val headline = MaterialTheme.typography.titleLarge
        val showPanorama = wide != null && regular && LocalLayoutDirection.current == LayoutDirection.Ltr &&
            with(density) {
                !measurer.measure(
                    playlist.name,
                    headline,
                    maxLines = 3,
                    constraints = Constraints(
                        maxWidth = textWidth.roundToPx(),
                        maxHeight = (height - Dimens.button).roundToPx()
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
                MusicArtwork(cover, Dimens.playlistCoverSize)
                PlaylistTitle(playlist)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.inner)) {
                MusicArtwork(cover, Dimens.playlistCoverSize)
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
        Text(playlist.name, Modifier.semantics { heading() }, color = titleColor, style = MaterialTheme.typography.titleLarge)
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
private fun PlaylistMenu(onAssistant: (() -> Unit)?, onAction: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(Icons.Filled.MoreVert, stringResource(R.string.playlist_options), { open = true })
        AppMenu(open, { open = false }, inHeader = true) {
            MenuAction(stringResource(R.string.add_songs), Icons.Filled.Add, {
                open = false
                onAction("add")
            })
            MenuAction(stringResource(R.string.edit_playlist), Icons.AutoMirrored.Filled.QueueMusic, {
                open = false
                onAction("edit")
            })
            if (onAssistant != null) {
                MenuAction(stringResource(R.string.edit_with_playlist_assistant), Icons.Outlined.AutoAwesome, {
                    open = false
                    onAssistant()
                })
            }
            MenuAction(stringResource(R.string.rename), Icons.Filled.Edit, {
                open = false
                onAction("rename")
            })
            MenuAction(stringResource(R.string.delete_playlist), Icons.Filled.DeleteOutline, {
                open = false
                onAction("delete")
            }, destructive = true)
        }
    }
}

@Composable
private fun SongMenu(song: MusicTrack, next: () -> Unit, add: () -> Unit, onEnergy: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({
            open = true
        }, Modifier.size(Dimens.touch)) { Icon(Icons.Filled.MoreVert, stringResource(R.string.named_options, song.title)) }
        AppMenu(open, { open = false }) {
            if (song.available) {
                MenuAction(stringResource(R.string.play_next), Icons.AutoMirrored.Filled.QueueMusic, {
                    open = false
                    next()
                })
            }
            MenuAction(stringResource(R.string.add_to_playlist), Icons.Filled.Add, {
                open = false
                add()
            })
            if (onEnergy != null) {
                MenuAction(stringResource(R.string.set_energy_label), Icons.AutoMirrored.Filled.QueueMusic, {
                    open = false
                    onEnergy()
                })
            }
        }
    }
}

@Composable
internal fun SongRow(track: MusicTrack, play: () -> Unit, actions: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = Dimens.rowMinTwoLine)
                .clickable(
                    enabled = track.available,
                    onClickLabel = stringResource(R.string.play_named, track.title),
                    role = Role.Button,
                    onClick = play
                )
                .padding(vertical = Dimens.innerSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackLabels(track, Modifier.weight(1f), Arrangement.spacedBy(Dimens.innerTiny))
        }
        actions?.invoke()
    }
}

@Composable
internal fun TrackLabels(track: MusicTrack, modifier: Modifier = Modifier, arrangement: Arrangement.Vertical = Arrangement.Top) {
    Column(modifier, verticalArrangement = arrangement) {
        Text(track.title, style = MaterialTheme.typography.bodyLarge)
        val artist = if (track.available) track.artist else stringResource(R.string.unavailable_artist, track.artist)
        Text(
            listOfNotNull(artist, track.durationMs?.let(::songTime)).joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = InShapeTheme.colors.textSecondary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

internal fun songTime(milliseconds: Long?) = milliseconds?.let { formatElapsed(it / 1_000).removePrefix("0") } ?: "—"

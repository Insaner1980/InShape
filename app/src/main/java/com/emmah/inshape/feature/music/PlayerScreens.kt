package com.emmah.inshape.feature.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.inshape.R
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.InShapeShapes
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.delay

@Composable
internal fun MiniPlayer(playback: MusicPlayback, openLabel: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by playback.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val maxLines = if (largeText) Int.MAX_VALUE else 1
    val track: @Composable (Modifier) -> Unit = { trackModifier ->
        Row(
            trackModifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                .padding(vertical = Dimens.innerSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.itemGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaybackBars(state.playing)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.innerTiny)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InShapeTheme.colors.textSecondary,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    val controls: @Composable RowScope.() -> Unit = {
        PlayPause(playback, state)
        IconButton(playback::skip, Modifier.size(Dimens.touch), enabled = state.canSkip) {
            Icon(Icons.Filled.SkipNext, "Next song")
        }
    }
    Surface(
        modifier.fillMaxWidth(),
        shape = InShapeShapes.field,
        color = InShapeTheme.colors.surface,
        contentColor = InShapeTheme.colors.text
    ) {
        val contentPadding = Modifier.padding(horizontal = Dimens.innerSmall)
        if (largeText) {
            Column(contentPadding) {
                track(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = controls)
            }
        } else {
            Row(contentPadding, verticalAlignment = Alignment.CenterVertically) {
                track(Modifier.weight(1f))
                controls()
            }
        }
    }
}

@Composable
private fun PlayPause(playback: MusicPlayback, state: PlaybackState) {
    val pause = state.playing || state.playRequested
    IconButton(playback::toggle, Modifier.size(Dimens.touch), enabled = state.song != null) {
        Icon(if (pause) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (pause) "Pause" else "Play")
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun NowPlaying(playback: MusicPlayback, onBack: () -> Unit) {
    val state by playback.state.collectAsStateWithLifecycle()
    LaunchedEffect(playback, state.playing) {
        if (playback is SessionPlayback && state.playing) {
            while (true) {
                playback.updatePosition()
                delay(350)
            }
        }
    }
    MusicColumn {
        PushedHeader(onBack, title = "Now playing")
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.screenPadding)) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Dimens.inner),
                    verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val song = state.song
                    if (song == null) {
                        Text("Nothing is playing")
                    } else {
                        val colors = InShapeTheme.colors
                        MusicArtwork(song.uri, Dimens.nowPlayingCover) { PlaybackRibbon(state.playing) }
                        Text(song.title, style = MaterialTheme.typography.headlineSmall)
                        Text(song.artist, color = colors.textSecondary)
                        PlaybackTime(state, playback::seek)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(playback::previous, Modifier.size(Dimens.transport), enabled = state.canGoBack) {
                                Icon(Icons.Filled.SkipPrevious, "Previous song", Modifier.size(Dimens.transportIcon))
                            }
                            val pause = state.playing || state.playRequested
                            FilledIconButton(
                                playback::toggle,
                                Modifier.size(Dimens.playButton),
                                shape = InShapeShapes.pill,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.ink, contentColor = colors.onInk)
                            ) {
                                Icon(
                                    if (pause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (pause) "Pause" else "Play",
                                    Modifier.size(Dimens.playIcon)
                                )
                            }
                            IconButton(playback::skip, Modifier.size(Dimens.transport), enabled = state.canSkip) {
                                Icon(Icons.Filled.SkipNext, "Next song", Modifier.size(Dimens.transportIcon))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconToggleButton(state.shuffle, { playback.toggleShuffle() }, Modifier.size(Dimens.touch)) {
                                Icon(
                                    Icons.Filled.Shuffle,
                                    if (state.shuffle) "Shuffle, on" else "Shuffle, off",
                                    tint = if (state.shuffle) colors.text else colors.textTertiary
                                )
                            }
                            IconButton(playback::stop, Modifier.size(Dimens.touch)) { Icon(Icons.Filled.Stop, "Stop") }
                            IconButton(
                                playback::cycleRepeat,
                                Modifier.size(Dimens.touch).semantics {
                                    stateDescription = when (state.repeat) {
                                        MusicRepeat.Off -> "Off"
                                        MusicRepeat.All -> "All"
                                        MusicRepeat.One -> "One"
                                    }
                                }
                            ) {
                                val description = when (state.repeat) {
                                    MusicRepeat.Off -> stringResource(R.string.music_repeat_off)
                                    MusicRepeat.All -> stringResource(R.string.music_repeat_all)
                                    MusicRepeat.One -> stringResource(R.string.music_repeat_one)
                                }
                                Icon(
                                    if (state.repeat == MusicRepeat.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                    description,
                                    tint = if (state.repeat == MusicRepeat.Off) colors.textTertiary else colors.text
                                )
                            }
                        }
                    }
                    if (state.buffering) Text("Buffering")
                    state.error?.let { Text(it, color = InShapeTheme.colors.textSecondary) }
                }
            }
            if (state.queue.isNotEmpty()) {
                item {
                    Text("Queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = Dimens.innerSmall))
                }
                items(state.queue, key = { it.index }) { entry ->
                    val current = entry.index == state.song?.index
                    Column {
                        ListRow(
                            entry.title,
                            modifier = Modifier.semantics { if (current) stateDescription = "Current song" },
                            subtitle = entry.artist,
                            titleStyle = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            trailing = if (current) ({ PlaybackBars(state.playing) }) else null,
                            onClick = { playback.jump(entry.index) },
                            showChevron = false
                        )
                        MusicDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackTime(state: PlaybackState, seek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val duration = state.duration
    var drag by remember(state.song?.index, state.song?.id) { mutableStateOf<Float?>(null) }
    val ratio = if (duration == null) 0f else (state.position.toDouble() / duration).toFloat().coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        Slider(
            value = drag ?: ratio,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                val value = drag
                if (value != null && duration != null) seek((value.toDouble() * duration).toLong())
                drag = null
            },
            modifier = Modifier.semantics { contentDescription = "Seek" },
            enabled = duration != null && state.seekable
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(songTime(drag?.let { ratio -> duration?.let { (it.toDouble() * ratio).toLong() } } ?: state.position))
            Text(songTime(duration))
        }
    }
}

package com.emmah.inshape.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.emmah.inshape.ui.components.TextAction
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
            Icon(Icons.Filled.SkipNext, stringResource(R.string.next_song))
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
        Icon(
            if (pause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (pause) stringResource(R.string.pause) else stringResource(R.string.play)
        )
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
        PushedHeader(onBack, title = stringResource(R.string.now_playing))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.screenPadding)) {
            item {
                NowPlayingDetails(state, playback)
            }
            if (state.queue.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.queue),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = Dimens.innerSmall)
                    )
                }
                items(state.queue, key = { it.occurrence }) { entry ->
                    val current = entry.occurrence == state.song?.occurrence
                    val currentSong = stringResource(R.string.current_song)
                    Column {
                        ListRow(
                            entry.title,
                            modifier = Modifier.semantics { if (current) stateDescription = currentSong },
                            subtitle = entry.artist,
                            trailing = if (current) ({ PlaybackBars(state.playing) }) else null,
                            onClick = { playback.jump(entry) },
                            showChevron = false
                        )
                        MusicDivider()
                    }
                }
                if (!state.playing && !state.playRequested) {
                    item {
                        TextAction(
                            stringResource(R.string.clear_queue),
                            playback::stop,
                            color = InShapeTheme.colors.error,
                            icon = Icons.Outlined.ClearAll
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingDetails(state: PlaybackState, playback: MusicPlayback) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Dimens.inner),
        verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val song = state.song
        if (song == null) {
            Text(stringResource(R.string.nothing_playing))
        } else {
            val colors = InShapeTheme.colors
            MusicArtwork(song.uri, Dimens.nowPlayingCover)
            Text(song.title, Modifier.padding(top = Dimens.sectionGap - Dimens.innerSmall), style = MaterialTheme.typography.titleLarge)
            Text(song.artist, color = colors.textSecondary)
            PlaybackTime(state, playback::seek)
            NowPlayingControls(state, playback)
        }
        if (state.buffering) Text(stringResource(R.string.buffering))
        state.error?.let { Text(it, color = InShapeTheme.colors.textSecondary) }
    }
}

@Composable
private fun NowPlayingControls(state: PlaybackState, playback: MusicPlayback) {
    val colors = InShapeTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconToggleButton(state.shuffle, { playback.toggleShuffle() }, Modifier.size(Dimens.touch)) {
            Icon(
                Icons.Filled.Shuffle,
                if (state.shuffle) stringResource(R.string.shuffle_on) else stringResource(R.string.shuffle_off),
                tint = if (state.shuffle) colors.text else colors.textTertiary
            )
        }
        IconButton(playback::previous, Modifier.size(Dimens.touch), enabled = state.canGoBack) {
            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous_song), Modifier.size(Dimens.transportIcon))
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
                if (pause) stringResource(R.string.pause) else stringResource(R.string.play),
                Modifier.size(Dimens.playIcon)
            )
        }
        IconButton(playback::skip, Modifier.size(Dimens.touch), enabled = state.canSkip) {
            Icon(Icons.Filled.SkipNext, stringResource(R.string.next_song), Modifier.size(Dimens.transportIcon))
        }
        val repeatStatus = stringResource(
            when (state.repeat) {
                MusicRepeat.Off -> R.string.off
                MusicRepeat.All -> R.string.all
                MusicRepeat.One -> R.string.one
            }
        )
        IconButton(
            playback::cycleRepeat,
            Modifier.size(Dimens.touch).semantics { stateDescription = repeatStatus }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackTime(state: PlaybackState, onSeek: (Long, QueuedSong) -> Unit, modifier: Modifier = Modifier) {
    val song = state.song
    val duration = state.duration
    val colors = InShapeTheme.colors
    val enabled = duration != null && state.seekable
    val sliderColors = SliderDefaults.colors(activeTrackColor = colors.ink, inactiveTrackColor = colors.surface)
    var drag by remember(song?.occurrence) { mutableStateOf<Float?>(null) }
    val ratio = if (duration == null) 0f else (state.position.toDouble() / duration).toFloat().coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.innerSmall)) {
        key(song?.occurrence) {
            val slider = remember { SliderState(value = ratio) }
            SideEffect { slider.value = drag ?: ratio }
            val seekLabel = stringResource(R.string.seek)
            Slider(
                state = slider,
                onValueChange = { value ->
                    slider.value = value
                    drag = value
                },
                onValueChangeFinished = {
                    val value = drag
                    if (value != null && duration != null && song != null) {
                        onSeek((value.toDouble() * duration).toLong(), song)
                    }
                    drag = null
                },
                modifier = Modifier.semantics { contentDescription = seekLabel },
                enabled = enabled,
                colors = sliderColors,
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState,
                        enabled = enabled,
                        colors = sliderColors,
                        thumbTrackGapSize = Dimens.zero,
                        drawStopIndicator = null
                    )
                },
                thumb = {
                    Box(
                        Modifier.size(Dimens.chartDot * 2)
                            .background(if (enabled) colors.ink else colors.textTertiary, InShapeShapes.pill)
                    )
                }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(songTime(drag?.let { ratio -> duration?.let { (it.toDouble() * ratio).toLong() } } ?: state.position))
            Text(songTime(duration))
        }
    }
}

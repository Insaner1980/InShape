package com.emmah.inshape.feature.music

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.emmah.inshape.AppContainer
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class QueuedSong(val index: Int, val id: String, val title: String, val artist: String, val uri: String?)

enum class MusicRepeat { Off, All, One }

data class PlaybackState(
    val song: QueuedSong? = null,
    val queue: List<QueuedSong> = emptyList(),
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val playRequested: Boolean = false,
    val canSkip: Boolean = false,
    val canGoBack: Boolean = false,
    val duration: Long? = null,
    val position: Long = 0,
    val seekable: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: MusicRepeat = MusicRepeat.Off,
    val error: String? = null
)

interface MusicPlayback {
    val state: StateFlow<PlaybackState>
    fun start(tracks: List<MusicTrack>, startId: String? = null, shuffle: Boolean = false)
    fun playAfterCurrent(track: MusicTrack)
    fun toggle()
    fun skip()
    fun previous()
    fun seek(position: Long)
    fun jump(index: Int)
    fun toggleShuffle()
    fun cycleRepeat()
    fun stop()
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberMusicPlayback(container: AppContainer, initial: List<MusicTrack>, observe: Boolean): MusicPlayback {
    val playback = remember(container, initial) {
        if (container.integrationsEnabled) SessionPlayback(container.context) else IsolatedPlayback(initial)
    }
    if (playback is SessionPlayback && observe) {
        LifecycleStartEffect(playback) {
            playback.connect()
            onStopOrDispose { playback.disconnect() }
        }
    }
    return playback
}

private class IsolatedPlayback(initial: List<MusicTrack>) : MusicPlayback {
    override val state = MutableStateFlow(PlaybackState())
    private val durations = mutableMapOf<String, Long?>()

    init {
        if (initial.isNotEmpty()) start(initial)
    }

    override fun start(tracks: List<MusicTrack>, startId: String?, shuffle: Boolean) {
        val ordered = tracks.filter { it.available }.let { if (shuffle) it.shuffled() else it }
        durations.putAll(ordered.associate { it.id to it.durationMs })
        val queue = ordered.mapIndexed { index, track -> QueuedSong(index, track.id, track.title, track.artist, track.uri) }
        val first = queue.firstOrNull { it.id == startId } ?: queue.firstOrNull() ?: return
        state.value = state.value.copy(queue = queue, shuffle = shuffle, playing = true)
        jump(first.index)
    }

    override fun playAfterCurrent(track: MusicTrack) {
        val current = state.value
        if (current.song == null) {
            start(listOf(track))
            toggle()
        } else {
            val queue = current.queue.toMutableList()
            val index = queue.indexOf(current.song) + 1
            durations[track.id] = track.durationMs
            queue.add(index, QueuedSong((queue.maxOfOrNull { it.index } ?: 0) + 1, track.id, track.title, track.artist, track.uri))
            state.value = current.copy(queue = queue, canSkip = true)
        }
    }

    override fun toggle() {
        state.value = state.value.copy(playing = !state.value.playing)
    }

    override fun skip() {
        move(1)
    }

    override fun previous() {
        if (state.value.position > 3_000) seek(0) else move(-1)
    }

    private fun move(offset: Int) {
        val current = state.value
        current.queue.getOrNull(current.queue.indexOf(current.song) + offset)?.let {
            jump(it.index)
            state.value = state.value.copy(playing = current.playing)
        }
    }

    override fun seek(position: Long) {
        state.value = state.value.copy(position = position.coerceAtLeast(0))
    }

    override fun jump(index: Int) {
        val current = state.value
        val song = current.queue.find { it.index == index } ?: return
        val duration = durations[song.id]
        state.value = current.copy(
            song = song,
            position = 0,
            duration = duration,
            seekable = duration != null,
            playing = true,
            canGoBack = duration != null || song != current.queue.first(),
            canSkip = song != current.queue.last()
        )
    }

    override fun toggleShuffle() {
        state.value = state.value.copy(shuffle = !state.value.shuffle)
    }

    override fun cycleRepeat() {
        state.value = state.value.copy(repeat = MusicRepeat.entries[(state.value.repeat.ordinal + 1) % MusicRepeat.entries.size])
    }

    override fun stop() {
        state.value = PlaybackState()
    }
}

@UnstableApi
internal class SessionPlayback(private val context: Context) : MusicPlayback {
    private val playbackState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = playbackState
    private var connection: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pendingCommands = ArrayDeque<Pair<SessionCommand, Bundle>>()
    private var inFlightCommands = 0
    private var releaseWhenIdle = false
    private val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        releaseWhenIdle = false
        if (connection != null) return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()

            override fun onDisconnected(controller: MediaController) {
                if (this@SessionPlayback.controller === controller) {
                    release()
                    playbackState.value = PlaybackState(error = "Music player disconnected.")
                }
            }
        }).buildAsync()
        connection = future
        future.addListener(
            {
                if (connection === future) {
                    controller = runCatching { future.get() }.getOrNull()
                    val active = controller
                    if (active == null) {
                        release()
                        playbackState.value = PlaybackState(error = "Could not connect to the music player.")
                    } else {
                        active.addListener(listener)
                        refresh()
                        pendingCommands.forEach { (command, args) -> sendToSession(active, command, args) }
                        pendingCommands.clear()
                        releaseIfIdle()
                    }
                }
            },
            executor
        )
    }

    fun disconnect() {
        // Leave the connection open until the service has handled commands accepted from the UI.
        releaseWhenIdle = true
        releaseIfIdle()
    }

    private fun releaseIfIdle() {
        if (releaseWhenIdle && pendingCommands.isEmpty() && inFlightCommands == 0) release()
    }

    private fun release() {
        releaseWhenIdle = false
        inFlightCommands = 0
        controller?.removeListener(listener)
        controller = null
        val future = connection
        connection = null
        if (future != null) MediaController.releaseFuture(future)
        pendingCommands.clear()
    }

    private fun refresh() {
        val player = controller ?: return
        val song = player.currentMediaItem?.toQueuedSong(player.currentMediaItemIndex)
        playbackState.value = PlaybackState(
            song = song,
            queue = player.queueIndices().map { player.getMediaItemAt(it).toQueuedSong(it) },
            playing = player.isPlaying,
            buffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            playRequested = player.playWhenReady && player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING),
            canSkip = player.hasNextMediaItem() && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
            canGoBack = song != null && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) &&
                (player.hasPreviousMediaItem() || player.isCurrentMediaItemSeekable),
            position = player.currentPosition.coerceAtLeast(0),
            duration = player.duration.takeIf { it > 0 },
            seekable = player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> MusicRepeat.One
                Player.REPEAT_MODE_ALL -> MusicRepeat.All
                else -> MusicRepeat.Off
            },
            error = player.sessionExtras.getString(MUSIC_ERROR)
        )
    }

    fun updatePosition() {
        val player = controller ?: return
        playbackState.value = playbackState.value.copy(position = player.currentPosition.coerceAtLeast(0))
    }

    private fun send(command: SessionCommand, args: Bundle = Bundle.EMPTY) {
        val active = controller
        if (active != null) {
            sendToSession(active, command, args)
        } else {
            if (command == chooseQueue || command == endPlayback) pendingCommands.clear()
            pendingCommands.addLast(command to args)
            if (connection == null) connect()
        }
    }

    private fun sendToSession(active: MediaController, command: SessionCommand, args: Bundle) {
        val result = active.sendCustomCommand(command, args)
        inFlightCommands++
        result.addListener({
            if (controller === active) {
                inFlightCommands--
                releaseIfIdle()
            }
        }, executor)
    }

    override fun start(tracks: List<MusicTrack>, startId: String?, shuffle: Boolean) {
        send(
            chooseQueue,
            Bundle().apply {
                putStringArrayList(MUSIC_IDS, ArrayList(tracks.map { it.id }))
                putString(MUSIC_START, startId)
                putBoolean(MUSIC_SHUFFLE, shuffle)
            }
        )
    }

    override fun playAfterCurrent(track: MusicTrack) {
        send(playNext, Bundle().apply { putStringArrayList(MUSIC_IDS, arrayListOf(track.id)) })
    }

    override fun toggle() {
        val player = controller ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    override fun skip() {
        controller?.seekToNextMediaItem()
    }

    override fun previous() {
        controller?.seekToPrevious()
    }

    override fun seek(position: Long) {
        controller?.seekTo(position)
    }

    override fun jump(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekToDefaultPosition(index)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    override fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun stop() = send(endPlayback)
}

private fun MediaItem.toQueuedSong(index: Int) = QueuedSong(
    index,
    mediaId,
    mediaMetadata.title?.toString().orEmpty(),
    mediaMetadata.artist?.toString().orEmpty(),
    mediaMetadata.extras?.getString("localUri")
)

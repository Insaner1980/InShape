package com.emmah.inshape.feature.music

import com.emmah.inshape.R
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueuedSong(
    val index: Int,
    val id: String,
    val title: String,
    val artist: String,
    val uri: String?,
    val occurrence: String = UUID.randomUUID().toString()
)

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
    fun seek(position: Long, song: QueuedSong? = state.value.song)
    fun jump(song: QueuedSong)
    fun toggleShuffle()
    fun cycleRepeat()
    fun stop()
    fun assistantGain(owner: String, reduction: Float?) {}
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberMusicPlayback(container: AppContainer, initial: List<MusicTrack>): MusicPlayback {
    val playback = remember(container, initial) {
        if (container.integrationsEnabled) SessionPlayback(container.context) else IsolatedPlayback(initial)
    }
    if (playback is SessionPlayback) {
        LifecycleStartEffect(playback) {
            playback.connect()
            onStopOrDispose { playback.disconnect() }
        }
    }
    return playback
}

internal class IsolatedPlayback(initial: List<MusicTrack>) : MusicPlayback {
    private val playbackState = MutableStateFlow(PlaybackState())
    override val state = playbackState.asStateFlow()
    private val durations = mutableMapOf<String, Long?>()

    init {
        if (initial.isNotEmpty()) start(initial)
    }

    override fun start(tracks: List<MusicTrack>, startId: String?, shuffle: Boolean) {
        val ordered = tracks.filter { it.available }.let { if (shuffle) it.shuffled() else it }
        durations.putAll(ordered.associate { it.id to it.durationMs })
        val queue = ordered.mapIndexed { index, track -> QueuedSong(index, track.id, track.title, track.artist, track.uri) }
        val first = queue.firstOrNull { it.id == startId } ?: queue.firstOrNull() ?: return
        playbackState.value = state.value.copy(queue = queue, shuffle = shuffle, playing = true)
        jump(first)
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
            playbackState.value = current.copy(queue = queue, canSkip = true)
        }
    }

    override fun toggle() {
        playbackState.value = state.value.copy(playing = !state.value.playing)
    }

    override fun skip() {
        move(1)
    }

    override fun previous() {
        val current = state.value
        if (current.seekable && (current.position > 3_000 || current.song == current.queue.firstOrNull())) {
            seek(0)
        } else {
            move(-1)
        }
    }

    private fun move(offset: Int) {
        val current = state.value
        current.queue.getOrNull(current.queue.indexOf(current.song) + offset)?.let {
            jump(it)
            playbackState.value = state.value.copy(playing = current.playing)
        }
    }

    override fun seek(position: Long, song: QueuedSong?) {
        val current = state.value
        if (song != null && current.song?.occurrence == song.occurrence && current.seekable) {
            playbackState.value = current.copy(position = position.coerceAtLeast(0))
        }
    }

    override fun jump(song: QueuedSong) {
        val current = state.value
        val entry = current.queue.find { it.occurrence == song.occurrence } ?: return
        val duration = durations[entry.id]
        playbackState.value = current.copy(
            song = entry,
            position = 0,
            duration = duration,
            seekable = duration != null,
            playing = true,
            canGoBack = duration != null || entry != current.queue.first(),
            canSkip = entry != current.queue.last()
        )
    }

    override fun toggleShuffle() {
        playbackState.value = state.value.copy(shuffle = !state.value.shuffle)
    }

    override fun cycleRepeat() {
        playbackState.value = state.value.copy(repeat = MusicRepeat.entries[(state.value.repeat.ordinal + 1) % MusicRepeat.entries.size])
    }

    override fun stop() {
        playbackState.value = PlaybackState()
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
    private var observing = false
    private var retryDelay = 500L
    private val handler = Handler(Looper.getMainLooper())
    private val retryConnection = Runnable(::connectIfNeeded)
    private val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        observing = true
        releaseWhenIdle = false
        connectIfNeeded()
    }

    private fun connectIfNeeded() {
        handler.removeCallbacks(retryConnection)
        if (connection != null) return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()

            override fun onDisconnected(controller: MediaController) {
                if (this@SessionPlayback.controller === controller) {
                    reconnectAfterFailure(context.getString(R.string.music_disconnected))
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
                        reconnectAfterFailure(context.getString(R.string.music_connect_failed))
                    } else {
                        retryDelay = 500L
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
        observing = false
        retryDelay = 500L
        handler.removeCallbacks(retryConnection)
        // Leave the connection open until the service has handled commands accepted from the UI.
        releaseWhenIdle = true
        releaseIfIdle()
    }

    private fun releaseIfIdle() {
        if (releaseWhenIdle && pendingCommands.isEmpty() && inFlightCommands == 0) release()
    }

    private fun reconnectAfterFailure(message: String) {
        release()
        playbackState.value = PlaybackState(error = message)
        if (observing) {
            handler.postDelayed(retryConnection, retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(8_000L)
        }
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
            if (command == endPlayback) pendingCommands.clear()
            if (command == chooseQueue) pendingCommands.removeAll { it.first != endPlayback }
            pendingCommands.addLast(command to args)
            if (connection == null) {
                releaseWhenIdle = false
                connectIfNeeded()
            }
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

    override fun seek(position: Long, song: QueuedSong?) {
        val active = controller ?: return
        if (song == null || active.currentMediaItem?.mediaId != song.occurrence) return
        sendToSession(
            active,
            seekInOccurrence,
            Bundle().apply {
                putString(MUSIC_OCCURRENCE, song.occurrence)
                putLong(MUSIC_POSITION, position)
            }
        )
    }

    override fun jump(song: QueuedSong) {
        val active = controller ?: return
        sendToSession(
            active,
            jumpToOccurrence,
            Bundle().apply { putString(MUSIC_OCCURRENCE, song.occurrence) }
        )
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

    override fun assistantGain(owner: String, reduction: Float?) {
        send(assistantGainChange, Bundle().apply {
            putString("owner", owner)
            if (reduction != null) putFloat("gain", reduction)
        })
    }
}

private fun MediaItem.toQueuedSong(index: Int) = QueuedSong(
    index,
    trackId,
    mediaMetadata.title?.toString().orEmpty(),
    mediaMetadata.artist?.toString().orEmpty(),
    mediaMetadata.extras?.getString("localUri"),
    mediaId
)

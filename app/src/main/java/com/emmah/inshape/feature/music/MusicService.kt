package com.emmah.inshape.feature.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.MainActivity
import com.emmah.inshape.R
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class QueueSnapshot(val ids: List<String>, val index: Int, val position: Long, val shuffleOrder: List<Int>?, val repeat: Int)

internal val chooseQueue = SessionCommand("com.emmah.inshape.CHOOSE_QUEUE", Bundle.EMPTY)
internal val playNext = SessionCommand("com.emmah.inshape.PLAY_NEXT", Bundle.EMPTY)
internal val endPlayback = SessionCommand("com.emmah.inshape.END_PLAYBACK", Bundle.EMPTY)
internal val jumpToOccurrence = SessionCommand("com.emmah.inshape.JUMP_TO_OCCURRENCE", Bundle.EMPTY)
internal val seekInOccurrence = SessionCommand("com.emmah.inshape.SEEK_IN_OCCURRENCE", Bundle.EMPTY)
internal val assistantGainChange = SessionCommand("com.emmah.inshape.ASSISTANT_GAIN", Bundle.EMPTY)
internal const val MUSIC_ERROR = "musicError"
internal const val MUSIC_IDS = "trackIds"
internal const val MUSIC_SHUFFLE = "shuffle"
internal const val MUSIC_START = "start"
internal const val MUSIC_OCCURRENCE = "occurrence"
internal const val MUSIC_POSITION = "position"
private const val TRACK_ID = "trackId"

@UnstableApi
class MusicService(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store get() = (application as InShapeApplication).container.music
    private val preferences by lazy { getSharedPreferences("music_playback", MODE_PRIVATE) }
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private var restoring = true
    private var restoration: Job? = null
    private var selection = 0L
    private val queueMutations = Mutex()
    private val failedOccurrences = mutableSetOf<String>()
    private var lastSnapshot: QueueSnapshot? = null
    private var assistantOwner: String? = null
    private var assistantWatchdog: Job? = null
    private var musicGain = 1f
    private var duckGain: Float? = null
    private var listeningWorkoutId: String? = null
    private var listeningInterval: ListeningInterval? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!restoring) saveQueue()
            reconcileListening()
        }

        override fun onVolumeChanged(volume: Float) {
            val reduction = duckGain
            if (reduction == null) {
                musicGain = volume
            } else if (volume != musicGain * reduction) {
                musicGain = volume
                this@MusicService.player.volume = volume * reduction
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            recoverPlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory { ContentDataSource(this) })
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMaxSeekToPreviousPositionMs(3_000)
            .build()
        val openMusic = PendingIntent.getActivity(
            this,
            135,
            Intent(this, MainActivity::class.java).setAction(MainActivity.OPEN_MUSIC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openMusic).setCallback(SessionCallback()).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(135)
                .setChannelId("music_playback")
                .setChannelName(R.string.music)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_music) }
        )
        player.addListener(playerListener)
        scope.launch {
            (application as InShapeApplication).container.repository.changes.collect { journal ->
                val workoutId = journal.active?.row?.id
                if (listeningWorkoutId != workoutId) {
                    listeningWorkoutId = workoutId
                    reconcileListening()
                }
            }
        }
        restoration = scope.launch {
            try {
                restoreQueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (restoring) showError(getString(R.string.restore_queue_failed))
            } finally {
                restoring = false
            }
        }
        scope.launch {
            while (isActive) {
                delay(12_000)
                if (player.isPlaying) {
                    checkpointListening()
                    failedOccurrences.clear()
                    saveQueue()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session.takeIf { controllerInfo.uid == Process.myUid() || controllerInfo.isTrusted }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val ownController = controller.uid == Process.myUid()
            if (!ownController && !controller.isTrusted && !session.isMediaNotificationController(controller)) {
                return MediaSession.ConnectionResult.reject()
            }
            val defaults = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                .build()
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            if (ownController) {
                commands.add(chooseQueue).add(playNext).add(endPlayback).add(jumpToOccurrence).add(seekInOccurrence).add(assistantGainChange)
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(defaults)
                .setAvailableSessionCommands(commands.build())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val result = SettableFuture.create<SessionResult>()
            if (controller.uid != Process.myUid()) {
                result.set(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                return result
            }
            if (command == assistantGainChange) {
                val owner = args.getString("owner")
                if (owner != null && owner.length <= 64) {
                    if (args.containsKey("gain")) setAssistantGain(owner, args.getFloat("gain").coerceIn(0f, 1f))
                    else releaseAssistantGain(owner)
                }
                result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                return result
            }
            if (command == chooseQueue || command == endPlayback) selection++
            val request = selection
            scope.launch {
                try {
                    queueMutations.withLock {
                        handleQueueCommand(command, args, request)
                    }
                    result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                } catch (cancelled: CancellationException) {
                    result.cancel(false)
                    throw cancelled
                } catch (_: Exception) {
                    if (request == selection) showError(getString(R.string.open_music_failed))
                    result.set(SessionResult(SessionError.ERROR_IO))
                }
            }
            return result
        }
    }

    private fun setAssistantGain(owner: String, gain: Float) {
        if (duckGain == null) musicGain = player.volume
        assistantOwner = owner
        duckGain = gain
        player.volume = musicGain * gain
        assistantWatchdog?.cancel()
        assistantWatchdog = scope.launch {
            delay(45_000)
            releaseAssistantGain(owner)
        }
    }

    private fun releaseAssistantGain(owner: String) {
        if (assistantOwner != owner) return
        assistantOwner = null
        duckGain = null
        assistantWatchdog?.cancel()
        assistantWatchdog = null
        player.volume = musicGain
    }

    private data class ListeningInterval(
        val event: ListeningEvent,
        val occurrenceId: String,
        val startedElapsed: Long
    )

    private fun reconcileListening() {
        val workout = listeningWorkoutId
        val item = player.currentMediaItem?.takeIf { player.isPlaying && player.volume > 0f && workout != null }
        val previous = listeningInterval
        if (previous != null && (item?.mediaId != previous.occurrenceId || workout != previous.event.workoutId)) {
            checkpointListening()
            listeningInterval = null
        }
        if (listeningInterval == null && item != null && workout != null) {
            listeningInterval = ListeningInterval(
                ListeningEvent(UUID.randomUUID().toString(), item.trackId, workout, java.time.Instant.now().toString(), 0),
                item.mediaId,
                android.os.SystemClock.elapsedRealtime()
            )
        }
    }

    private fun checkpointListening() {
        val interval = listeningInterval ?: return
        store.recordListening(interval.event.copy(playedMs = android.os.SystemClock.elapsedRealtime() - interval.startedElapsed))
    }

    private suspend fun handleQueueCommand(command: SessionCommand, args: Bundle, request: Long) {
        if (request != selection && command != endPlayback) return
        when (command) {
            chooseQueue -> prepareSelection(args, request)
            playNext -> insertNext(args, request)
            jumpToOccurrence -> jumpTo(args)
            seekInOccurrence -> seekIn(args)
            endPlayback -> {
                restoring = false
                player.stop()
                player.clearMediaItems()
                failedOccurrences.clear()
                showError(null)
                saveQueue()
            }
        }
    }

    private suspend fun prepareSelection(args: Bundle, request: Long) {
        val ids = args.getStringArrayList(MUSIC_IDS).orEmpty()
        val tracks = store.resolve(ids)
        if (request != selection) return
        if (tracks.isEmpty()) {
            showError(getString(R.string.no_available_songs))
            return
        }
        val shuffle = args.getBoolean(MUSIC_SHUFFLE)
        val startId = args.getString(MUSIC_START)
        val requestedIndex = ids.indexOf(startId).coerceAtLeast(0)
        val availableIds = tracks.map { it.id }.toSet()
        val firstId = ids.drop(requestedIndex).firstOrNull { it in availableIds }
        val order = tracks.indices.toList().let { if (shuffle) it.shuffled() else it }
        val start = if (shuffle) order.first() else tracks.indexOfFirst { it.id == firstId }.coerceAtLeast(0)
        restoring = false
        failedOccurrences.clear()
        showError(null)
        player.setMediaItems(tracks.map(MusicTrack::toMediaItem), start, 0L)
        if (shuffle) player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
        saveQueue()
    }

    private suspend fun insertNext(args: Bundle, request: Long) {
        val id = args.getStringArrayList(MUSIC_IDS)?.singleOrNull() ?: return
        val track = store.resolve(listOf(id)).singleOrNull()
        if (request != selection || track == null) {
            if (request == selection) showError(getString(R.string.song_unavailable))
            return
        }
        if (restoring) restoration?.join()
        if (request != selection) return
        val current = player.currentMediaItemIndex
        val shuffled = player.shuffleModeEnabled
        val order = player.queueIndices()
        val insertionIndex = if (shuffled) player.mediaItemCount else (current + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertionIndex, track.toMediaItem())
        if (shuffled) {
            val updated = order.toMutableList()
            updated.add(order.indexOf(current) + 1, insertionIndex)
            player.setShuffleOrder(DefaultShuffleOrder(updated.toIntArray(), System.nanoTime()))
        }
        if (order.isEmpty()) {
            player.pause()
            player.prepare()
        }
        saveQueue()
    }

    private fun jumpTo(args: Bundle) {
        val occurrence = args.getString(MUSIC_OCCURRENCE) ?: return
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == occurrence
        } ?: return
        player.seekToDefaultPosition(index)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    private fun seekIn(args: Bundle) {
        val occurrence = args.getString(MUSIC_OCCURRENCE) ?: return
        if (player.currentMediaItem?.mediaId == occurrence && player.isCurrentMediaItemSeekable) {
            player.seekTo(args.getLong(MUSIC_POSITION))
        }
    }

    private suspend fun restoreQueue() {
        val saved = withContext(ioDispatcher) {
            preferences.getString("queue", null)?.let {
                runCatching { Json.decodeFromString<QueueSnapshot>(it) }.getOrNull()
            }
        } ?: return
        val byId = store.resolve(saved.ids, requireAvailable = false).associateBy { it.id }
        if (!restoring) return
        val indices = saved.ids.indices.filter { saved.ids[it] in byId }
        if (indices.isEmpty()) return
        val index = indices.indexOf(saved.index)
        player.setMediaItems(
            indices.map { byId.getValue(saved.ids[it]).toMediaItem() },
            index.coerceAtLeast(0),
            if (index >= 0) saved.position.coerceAtLeast(0) else 0L
        )
        val order = saved.shuffleOrder?.mapNotNull { old -> indices.indexOf(old).takeIf { it >= 0 } }?.distinct()
        if (order?.size == indices.size) {
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
            player.shuffleModeEnabled = true
        }
        player.repeatMode = saved.repeat.coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
        // Prepare on the next play command. Reopening the app never starts audio.
    }

    private fun saveQueue() {
        val snapshot = QueueSnapshot(
            List(player.mediaItemCount) { player.getMediaItemAt(it).trackId },
            player.currentMediaItemIndex,
            player.currentPosition,
            player.queueIndices().takeIf { player.shuffleModeEnabled },
            player.repeatMode
        )
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        preferences.edit {
            if (snapshot.ids.isEmpty()) remove("queue") else putString("queue", Json.encodeToString(snapshot))
        }
    }

    private fun recoverPlayback() {
        val current = player.currentMediaItemIndex
        val title = player.currentMediaItem?.mediaMetadata?.title ?: getString(R.string.track)
        player.currentMediaItem?.mediaId?.let(failedOccurrences::add)
        val order = player.queueIndices()
        val split = order.indexOf(current) + 1
        val candidates = order.drop(split) + if (player.repeatMode == Player.REPEAT_MODE_ALL) order.take(split) else emptyList()
        val next = candidates.firstOrNull { player.getMediaItemAt(it).mediaId !in failedOccurrences }
        if (next == null) {
            player.pause()
            showError(getString(R.string.music_play_failed, title))
        } else {
            showError(getString(R.string.music_play_skipped, title))
            player.seekToDefaultPosition(next)
            player.prepare()
        }
    }

    private fun showError(message: String?) {
        session.setSessionExtras(Bundle().apply { putString(MUSIC_ERROR, message) })
    }

    override fun onDestroy() {
        if (!restoring) saveQueue()
        checkpointListening()
        assistantOwner?.let(::releaseAssistantGain)
        scope.cancel()
        player.removeListener(playerListener)
        session.release()
        player.release()
        super.onDestroy()
    }
}

private fun MusicTrack.toMediaItem() = MediaItem.Builder()
    .setMediaId(UUID.randomUUID().toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setExtras(
                Bundle().apply {
                    putString("localUri", uri)
                    putString(TRACK_ID, id)
                }
            )
            .build()
    )
    .build()

internal val MediaItem.trackId: String
    get() = mediaMetadata.extras?.getString(TRACK_ID) ?: mediaId

internal fun Player.queueIndices(): List<Int> {
    if (mediaItemCount == 0) return emptyList()
    val timeline = currentTimeline
    val shuffle = shuffleModeEnabled
    return buildList {
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET) {
            add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
    }
}

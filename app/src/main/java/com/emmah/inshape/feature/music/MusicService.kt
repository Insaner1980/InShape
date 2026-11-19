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
import kotlinx.coroutines.CancellationException
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
internal const val MUSIC_ERROR = "musicError"
internal const val MUSIC_IDS = "trackIds"
internal const val MUSIC_SHUFFLE = "shuffle"
internal const val MUSIC_START = "start"

@UnstableApi
class MusicService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store get() = (application as InShapeApplication).container.music
    private val preferences by lazy { getSharedPreferences("music_playback", MODE_PRIVATE) }
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private var restoring = true
    private var restoration: Job? = null
    private var selection = 0L
    private val queueMutations = Mutex()
    private val failedIndices = mutableSetOf<Int>()
    private var lastSnapshot: QueueSnapshot? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!restoring) saveQueue()
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
        restoration = scope.launch {
            try {
                restoreQueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (restoring) showError("Could not restore the listening queue.")
            } finally {
                restoring = false
            }
        }
        scope.launch {
            while (isActive) {
                delay(12_000)
                if (player.isPlaying) {
                    failedIndices.clear()
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
            if (ownController) commands.add(chooseQueue).add(playNext).add(endPlayback)
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
            if (command == chooseQueue || command == endPlayback) selection++
            val request = selection
            scope.launch {
                try {
                    queueMutations.withLock {
                        if (request != selection) return@withLock
                        when (command) {
                            chooseQueue -> prepareSelection(args, request)

                            playNext -> insertNext(args, request)

                            endPlayback -> {
                                restoring = false
                                player.stop()
                                player.clearMediaItems()
                                showError(null)
                                saveQueue()
                            }
                        }
                    }
                    result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                } catch (cancelled: CancellationException) {
                    result.cancel(false)
                    throw cancelled
                } catch (_: Exception) {
                    if (request == selection) showError("Could not open the selected music.")
                    result.set(SessionResult(SessionError.ERROR_IO))
                }
            }
            return result
        }
    }

    private suspend fun prepareSelection(args: Bundle, request: Long) {
        val ids = args.getStringArrayList(MUSIC_IDS).orEmpty()
        val tracks = store.resolve(ids)
        if (request != selection) return
        if (tracks.isEmpty()) {
            showError("No available songs in this selection.")
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
        failedIndices.clear()
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
            if (request == selection) showError("That song is unavailable.")
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

    private suspend fun restoreQueue() {
        val saved = withContext(Dispatchers.IO) {
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
            List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId },
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
        val title = player.currentMediaItem?.mediaMetadata?.title ?: "Song"
        failedIndices.add(current)
        val order = player.queueIndices()
        val split = order.indexOf(current) + 1
        val candidates = order.drop(split) + if (player.repeatMode == Player.REPEAT_MODE_ALL) order.take(split) else emptyList()
        val next = candidates.firstOrNull { it !in failedIndices }
        if (next == null) {
            player.pause()
            showError("Could not play $title. Choose another song or refresh the folder.")
        } else {
            showError("Could not play $title. Skipped to the next song.")
            player.seekToDefaultPosition(next)
            player.prepare()
        }
    }

    private fun showError(message: String?) {
        session.setSessionExtras(Bundle().apply { putString(MUSIC_ERROR, message) })
    }

    override fun onDestroy() {
        if (!restoring) saveQueue()
        scope.cancel()
        player.removeListener(playerListener)
        session.release()
        player.release()
        super.onDestroy()
    }
}

private fun MusicTrack.toMediaItem() = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setExtras(Bundle().apply { putString("localUri", uri) })
            .build()
    )
    .build()

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

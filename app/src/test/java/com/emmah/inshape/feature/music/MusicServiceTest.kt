package com.emmah.inshape.feature.music

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.room.Room
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.R
import java.io.FileNotFoundException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = InShapeApplication::class)
class MusicServiceTest {
    private val app = RuntimeEnvironment.getApplication() as InShapeApplication
    private val tree = DocumentsContract.buildTreeDocumentUri("com.emmah.inshape.test.documents", "music")
    private val dusk = MusicTrack("dusk", tree.toString(), "content://com.emmah.inshape.test.documents/dusk", "Dusk", "Piano", 120_000)
    private val pulse = dusk.copy(id = "pulse", uri = "content://com.emmah.inshape.test.documents/pulse", title = "Pulse")
    private val ownController = controller(Process.myUid())
    private val readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val preferences = app.getSharedPreferences("music_playback", Context.MODE_PRIVATE)
    private lateinit var service: ServiceController<MusicService>
    private lateinit var session: MediaSession

    @Before
    fun startService() {
        val database = Room.databaseBuilder(app, MusicDatabase::class.java, "music-journal.db").build()
        try {
            runBlocking { database.music().saveTracks(listOf(dusk, pulse)) }
        } finally {
            database.close()
        }
        app.contentResolver.takePersistableUriPermission(tree, readPermission)
        preferences.edit().putString(
            "queue",
            """{"ids":["dusk","removed","pulse"],"index":1,"position":42000,"shuffleOrder":[2,0,1],"repeat":2}"""
        ).apply()

        service = Robolectric.buildService(MusicService::class.java).create()
        session = checkNotNull(service.get().onGetSession(ownController))
        await("restored shuffle order and repeat mode") {
            val player = session.player
            player.mediaItemCount == 2 && player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_ALL
        }
    }

    @After
    fun stopService() {
        if (::service.isInitialized) service.destroy()
        app.container.close()
        preferences.edit().clear().apply()
        app.contentResolver.releasePersistableUriPermission(tree, readPermission)
        app.deleteDatabase("music-journal.db")
        app.deleteDatabase("journal.db")
    }

    @Test
    fun restoresPausedQueueAndPersistsChangesToTheSessionPlayer() {
        assertNull(service.get().onGetSession(controller(Process.myUid() + 1)))
        val player = session.player
        assertEquals(listOf(pulse.id, dusk.id), queue())
        assertEquals(dusk.id, player.currentMediaItem?.trackId)
        assertEquals(0L, player.currentPosition)
        assertFalse(player.isPlaying)
        assertFalse(player.playWhenReady)

        player.repeatMode = Player.REPEAT_MODE_ONE
        await("saved repeat mode") {
            JSONObject(preferences.getString("queue", null) ?: "{}").optInt("repeat") == Player.REPEAT_MODE_ONE
        }
        player.clearMediaItems()
        await("removed queue snapshot") { !preferences.contains("queue") }
    }

    @Test
    fun sessionRestrictsCustomCommandsAndRestoresBaseVolumeAfterDucking() {
        val callback = callback()
        val foreign = controller(Process.myUid() + 1)
        assertFalse(callback.onConnect(session, foreign).isAccepted)
        val trusted = controller(Process.myUid() + 1, trusted = true)
        val trustedCommands = callback.onConnect(session, trusted)
        assertTrue(trustedCommands.isAccepted)
        assertFalse(trustedCommands.availableSessionCommands.contains(chooseQueue))
        val ownerCommands = callback.onConnect(session, ownController)
        assertTrue(ownerCommands.isAccepted)
        assertTrue(ownerCommands.availableSessionCommands.contains(chooseQueue))
        assertFalse(ownerCommands.availablePlayerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(ownerCommands.availablePlayerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertEquals(SessionError.ERROR_PERMISSION_DENIED, callback.onCustomCommand(session, trusted, endPlayback, Bundle.EMPTY).get().resultCode)
        assertEquals(listOf(pulse.id, dusk.id), queue())

        command(assistantGainChange, gain("first", 0.5f))
        assertEquals(0.5f, session.player.volume, 0f)
        command(assistantGainChange, gain("second", 0.25f))
        command(assistantGainChange, gain("first"))
        assertEquals(0.25f, session.player.volume, 0f)
        command(assistantGainChange, gain("ignored".repeat(10), 0f))
        assertEquals(0.25f, session.player.volume, 0f)

        session.player.volume = 0.8f
        await("ducking applied to new volume") { session.player.volume == 0.2f }
        command(assistantGainChange, gain("second"))
        assertEquals(0.8f, session.player.volume, 0f)
        command(assistantGainChange, gain("watchdog", -1f))
        assertEquals(0f, session.player.volume, 0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46))
        assertEquals(0.8f, session.player.volume, 0f)
    }

    @Test
    fun insertsRepeatedSongsAfterCurrentOccurrenceAndHandlesUnavailableCommands() {
        val player = session.player
        command(playNext, tracks(pulse.id))
        assertEquals(listOf(pulse.id, dusk.id, pulse.id), queue())
        val physicalOccurrences = (0 until player.mediaItemCount).filter { player.getMediaItemAt(it).trackId == pulse.id }
        assertNotEquals(player.getMediaItemAt(physicalOccurrences[0]).mediaId, player.getMediaItemAt(physicalOccurrences[1]).mediaId)

        player.shuffleModeEnabled = false
        command(playNext, tracks(dusk.id))
        assertEquals(listOf(dusk.id, dusk.id, pulse.id, pulse.id), queue())
        command(playNext, tracks(pulse.id, dusk.id))
        assertEquals(4, player.mediaItemCount)
        command(playNext, tracks("unavailable"))
        assertEquals(app.getString(R.string.song_unavailable), session.sessionExtras.getString(MUSIC_ERROR))
        command(chooseQueue, tracks("unavailable"))
        assertEquals(app.getString(R.string.no_available_songs), session.sessionExtras.getString(MUSIC_ERROR))
        assertEquals(4, player.mediaItemCount)

        command(jumpToOccurrence, Bundle.EMPTY)
        command(jumpToOccurrence, occurrence("stale"))
        command(seekInOccurrence, occurrence("stale"))
        command(seekInOccurrence, occurrence(checkNotNull(player.currentMediaItem).mediaId))
        assertEquals(dusk.id, player.currentMediaItem?.trackId)
        assertEquals(0L, player.currentPosition)
        command(endPlayback)
        assertEquals(emptyList<String>(), queue())
        assertNull(session.sessionExtras.getString(MUSIC_ERROR))
        assertFalse(preferences.contains("queue"))
    }

    @Test
    fun skipsFailedOccurrenceAndStopsWhenRepeatQueueIsExhausted() {
        val player = session.player
        player.shuffleModeEnabled = false
        val listener = ReflectionHelpers.getField<Player.Listener>(service.get(), "playerListener")
        val failure = PlaybackException("Unreadable track", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        listener.onPlayerError(failure)
        assertEquals(pulse.id, player.currentMediaItem?.trackId)
        assertEquals(app.getString(R.string.music_play_skipped, dusk.title), session.sessionExtras.getString(MUSIC_ERROR))
        listener.onPlayerError(failure)
        assertFalse(player.playWhenReady)
        assertEquals(app.getString(R.string.music_play_failed, pulse.title), session.sessionExtras.getString(MUSIC_ERROR))
    }

    @Test
    fun playbackClientMirrorsSessionAndSendsCommandsThroughAnInProcessController() {
        val playback = SessionPlayback(app)
        val connection = MediaController.Builder(app, session.token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                ReflectionHelpers.callInstanceMethod<Unit>(playback, "refresh")
            }
        }).buildAsync()
        try {
            await("direct controller connection") { connection.isDone }
            val controller = connection.get()
            // Start the client from an established session, independent of Android's service binding.
            ReflectionHelpers.setField(playback, "controller", controller)
            val listener = ReflectionHelpers.getField<Player.Listener>(playback, "listener")
            controller.addListener(listener)
            ReflectionHelpers.callInstanceMethod<Unit>(playback, "refresh")

            val restored = playback.state.value
            assertEquals(dusk.id, restored.song?.id)
            assertEquals(dusk.title, restored.song?.title)
            assertEquals(dusk.artist, restored.song?.artist)
            assertEquals(dusk.uri, restored.song?.uri)
            assertEquals(listOf(pulse.id, dusk.id), restored.queue.map { it.id })
            assertEquals(MusicRepeat.All, restored.repeat)
            assertTrue(restored.shuffle)
            assertFalse(restored.playRequested)

            playback.toggleShuffle()
            await("shuffle disabled") { !playback.state.value.shuffle }
            playback.cycleRepeat()
            await("repeat one") { playback.state.value.repeat == MusicRepeat.One }
            playback.cycleRepeat()
            await("repeat off") { playback.state.value.repeat == MusicRepeat.Off }
            playback.cycleRepeat()
            await("repeat all") { playback.state.value.repeat == MusicRepeat.All }

            playback.skip()
            await("next track") { playback.state.value.song?.id == pulse.id }
            playback.seek(12_000L, restored.song)
            playback.seek(12_000L)
            playback.jump(checkNotNull(restored.song).copy(occurrence = "stale"))
            playback.updatePosition()
            assertEquals(0L, playback.state.value.position)
            playback.previous()
            await("previous track") { playback.state.value.song?.id == dusk.id }

            playback.playAfterCurrent(pulse)
            await("repeated track") { playback.state.value.queue.map { it.id } == listOf(dusk.id, pulse.id, pulse.id) }
            playback.assistantGain("guide", 0.3f)
            await("ducking") { session.player.volume == 0.3f }
            playback.assistantGain("guide", null)
            await("restored volume") { session.player.volume == 1f }
            playback.start(listOf(dusk.copy(id = "not-in-library")))
            await("unavailable track") { playback.state.value.error == app.getString(R.string.no_available_songs) }
            verifyPlayableSelection(playback)
        } finally {
            playback.disconnect()
            MediaController.releaseFuture(connection)
        }
    }

    private fun verifyPlayableSelection(playback: SessionPlayback) {
        val provider = Robolectric.buildContentProvider(PendingAudioProvider::class.java)
            .create(checkNotNull(tree.authority))
            .get()
        try {
            val missing = dusk.copy(id = "not-in-library")
            playback.start(listOf(dusk, missing, pulse), startId = missing.id)
            await("buffering next available track") {
                val state = playback.state.value
                state.song?.id == pulse.id && state.buffering && state.playRequested && state.error == null
            }
            assertEquals(listOf(dusk.id, pulse.id), playback.state.value.queue.map { it.id })

            playback.toggle()
            await("paused buffering") { !playback.state.value.playRequested && !playback.state.value.buffering }
            playback.toggle()
            await("resumed buffering") { playback.state.value.buffering }

            val first = playback.state.value.queue.first()
            playback.jump(first)
            await("jumped to occurrence") { playback.state.value.song?.occurrence == first.occurrence }
            playback.start(listOf(pulse), shuffle = true)
            await("shuffled replacement queue") {
                val state = playback.state.value
                state.shuffle && state.queue.map { it.id } == listOf(pulse.id) && state.playRequested
            }
            playback.stop()
            await("stopped pending audio") { playback.state.value.queue.isEmpty() && playback.state.value.error == null }
        } finally {
            provider.release.countDown()
        }
    }

    private fun queue(): List<String> = session.player.run { queueIndices().map { getMediaItemAt(it).trackId } }

    private fun callback(): MediaSession.Callback {
        // Invoke the installed callback in-process so JVM tests do not depend on the service Binder bridge.
        val implementation = ReflectionHelpers.callInstanceMethod<Any>(session, "getImpl")
        return ReflectionHelpers.getField(implementation, "callback")
    }

    private fun command(command: SessionCommand, args: Bundle = Bundle.EMPTY) {
        val result = callback().onCustomCommand(session, ownController, command, args)
        await(command.customAction) { result.isDone }
        assertEquals(SessionResult.RESULT_SUCCESS, result.get().resultCode)
    }

    private fun tracks(vararg ids: String) = Bundle().apply { putStringArrayList(MUSIC_IDS, ArrayList(ids.toList())) }

    private fun gain(owner: String, value: Float? = null) = Bundle().apply {
        putString("owner", owner)
        if (value != null) putFloat("gain", value)
    }

    private fun occurrence(id: String) = Bundle().apply {
        putString(MUSIC_OCCURRENCE, id)
        putLong(MUSIC_POSITION, 30_000L)
    }

    private fun controller(uid: Int, trusted: Boolean = false) =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(app.packageName, Process.myPid(), uid, 1, 1, trusted, Bundle.EMPTY, true)

    private fun await(description: String, condition: () -> Boolean) = runBlocking {
        val reached = withTimeoutOrNull(10_000) {
            while (!condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
            true
        } ?: false
        assertTrue("Expected $description", reached)
    }

    class PendingAudioProvider : ContentProvider() {
        val release = CountDownLatch(1)

        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            // Keep the player buffering without decoding or opening an audio output.
            try {
                release.await(15, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            throw FileNotFoundException(uri.toString())
        }

        override fun getType(uri: Uri): String = "audio/wav"

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}

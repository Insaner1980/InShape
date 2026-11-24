package com.emmah.inshape.feature.music

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import com.emmah.inshape.R
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.BaseCursor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicStoreTest {
    @Test
    fun cancellingBeforeDispatchReleasesTheScan() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "music-scan-cancellation-test.db"
        val tasks = ArrayDeque<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                tasks.addLast(block)
            }
        }
        val job = Job()
        val store = MusicStore(context, databaseName, CoroutineScope(job + dispatcher))
        fun runDispatchedTasks() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
        try {
            repeat(2) {
                store.scan()
                assertTrue(store.scanning.value)
                store.cancelScan()
                runDispatchedTasks()
                assertFalse(store.scanning.value)
            }
        } finally {
            job.cancel()
            runDispatchedTasks()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun incompleteFolderListingCanBeRefreshedWhileReadGrantRemains() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "music-refresh-test.db"
        val resolver = context.contentResolver
        val tree = DocumentsContract.buildTreeDocumentUri("com.emmah.inshape.test.documents", "music")
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, "music")
        val trackUri = DocumentsContract.buildDocumentUriUsingTree(tree, "music/track.mp3")
        val track = MusicTrack("track", tree.toString(), trackUri.toString(), "Track", "Artist", null)
        val database = Room.databaseBuilder(context, MusicDatabase::class.java, databaseName).build()
        try {
            database.music().saveSource(MusicSource(tree = tree.toString()))
            database.music().saveTracks(listOf(track))
        } finally {
            database.close()
        }
        val readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION
        resolver.takePersistableUriPermission(tree, readPermission)
        val job = Job()
        val store = MusicStore(context, databaseName, CoroutineScope(job + Dispatchers.IO))
        try {
            withTimeout(10_000) {
                val ready = store.catalog.first { it.songs.singleOrNull()?.available == true }
                assertFalse(ready.accessLost)

                shadowOf(resolver).setCursor(children, FolderCursor(loading = true))
                store.scan()
                val incomplete = store.catalog.first { it.songs.singleOrNull()?.available == false }
                assertFalse("Refresh must stay enabled while the read grant exists", incomplete.accessLost)
                assertEquals(context.getString(R.string.music_folder_unavailable), store.message.value)
                assertEquals(listOf(track), store.resolve(listOf(track.id)))

                shadowOf(resolver).setCursor(children, FolderCursor(loading = false))
                store.scan()
                store.scanning.first { !it }
                assertNull(store.message.value)
                assertTrue(store.resolve(listOf(track.id)).isEmpty())

                resolver.releasePersistableUriPermission(tree, readPermission)
                store.checkAccess()
                val revoked = store.catalog.first { it.accessLost }
                assertEquals(tree.toString(), revoked.source?.tree)
                assertFalse(revoked.songs.single().available)
            }
        } finally {
            job.cancelAndJoin()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun confirmingPlaylistIsIdempotentAndRejectsStaleReviews() = runBlocking {
        withMusicStore { store, _, tracks ->
            val (first, second) = tracks
            val id = store.confirmPlaylist("confirm", first.tree, null, "Initial", tracks)
            assertEquals(id, store.confirmPlaylist("confirm", first.tree, null, "Initial", tracks))
            val reviewed = withTimeout(10_000) { store.catalog.first { it.playlists.isNotEmpty() } }.playlists.single()

            val changedCommand = runCatching { store.confirmPlaylist("confirm", first.tree, null, "Different", tracks) }
            assertTrue(changedCommand.exceptionOrNull() is IllegalArgumentException)

            store.rename(id, "Edited elsewhere")
            val stalePlaylist = runCatching { store.confirmPlaylist("update", first.tree, reviewed, "Revision", listOf(second)) }
            assertTrue(stalePlaylist.exceptionOrNull() is IllegalArgumentException)

            store.setEnergy(first.id, "calm")
            val staleTrack = runCatching { store.confirmPlaylist("new", first.tree, null, "Out of date", tracks) }
            assertTrue(staleTrack.exceptionOrNull() is IllegalArgumentException)

            val latest = withTimeout(10_000) {
                store.catalog.first { catalog ->
                    catalog.playlists.any { it.name == "Edited elsewhere" && it.tracks.first().energy == "calm" }
                }
            }.playlists.single()
            assertEquals(id, latest.id)
            assertEquals(tracks.map { it.id }, latest.tracks.map { it.id })

            val reordered = latest.tracks.reversed()
            assertEquals(id, store.confirmPlaylist("fresh-review", first.tree, latest, "Approved", reordered))
            val saved = withTimeout(10_000) {
                store.catalog.first { catalog -> catalog.playlists.singleOrNull()?.name == "Approved" }
            }.playlists.single()
            assertEquals(reordered, saved.tracks)
        }
    }

    @Test
    fun listeningCheckpointsKeepTheHigherProgressAndHonorTrackDuration() = runBlocking {
        withMusicStore { store, dao, tracks ->
            val (short, long) = tracks
            fun event(id: String, trackId: String, playedMs: Long) =
                ListeningEvent(id, trackId, "workout", "2026-11-23T10:00:00Z", playedMs)

            assertNull(store.heardInWorkout("workout"))
            store.recordListening(event("short", short.id, 2_000))
            store.recordListening(event("long-one", long.id, 6_000))
            store.recordListening(event("long-two", long.id, 3_999))
            withTimeout(10_000) {
                while (dao.listening("workout").size != 3) delay(10)
            }
            assertEquals(setOf(short.id), store.heardInWorkout("workout"))

            store.recordListening(event("short", short.id, 1_000))
            store.recordListening(event("long-two", long.id, 4_000))
            withTimeout(10_000) {
                while (store.heardInWorkout("workout") != setOf(short.id, long.id)) delay(10)
            }
            assertEquals(2_000L, dao.listeningEvent("short")?.playedMs)
        }
    }

    private suspend fun withMusicStore(test: suspend (MusicStore, MusicDao, List<MusicTrack>) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "music-playlist-store-test.db"
        val resolver = context.contentResolver
        val tree = DocumentsContract.buildTreeDocumentUri("com.emmah.inshape.test.documents", "music")
        val first = MusicTrack("first", tree.toString(), "content://com.emmah.inshape.test.documents/first", "First", "Artist", 2_000)
        val second = first.copy(
            id = "second",
            uri = "content://com.emmah.inshape.test.documents/second",
            title = "Second",
            durationMs = 90_000
        )
        val tracks = listOf(first, second)
        val readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION
        resolver.takePersistableUriPermission(tree, readPermission)
        val database = Room.databaseBuilder(context, MusicDatabase::class.java, databaseName).build()
        val job = Job()
        val store = MusicStore(context, databaseName, CoroutineScope(job + Dispatchers.IO))
        try {
            val dao = database.music()
            dao.saveSource(MusicSource(tree = tree.toString()))
            dao.saveTracks(tracks)
            test(store, dao, tracks)
        } finally {
            job.cancelAndJoin()
            store.close()
            database.close()
            resolver.releasePersistableUriPermission(tree, readPermission)
            context.deleteDatabase(databaseName)
        }
    }

    private class FolderCursor(private val loading: Boolean) : BaseCursor() {
        override fun getExtras() = Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, loading) }

        override fun moveToNext() = false

        override fun close() = Unit
    }
}

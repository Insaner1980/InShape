package com.emmah.inshape

import android.content.Context
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.feature.music.MusicRepeat
import com.emmah.inshape.feature.music.SessionPlayback
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Run after selecting the seven-file music fixture on a disposable emulator. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as InShapeApplication).container
    private val player = SessionPlayback(context)

    private fun requireFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("musicFixture") == "true")
    }

    @Test
    fun localFormatsAdvanceAndSeekAndQueueCommandsKeepTheirOrder() {
        requireFixture()
        val tracks = runBlocking {
            withTimeout(20_000) { container.music.catalog.first { it.songs.size == 7 } }.songs
        }
        val byFile = tracks.associateBy { Uri.parse(it.uri).lastPathSegment!!.substringAfterLast('/') }
        fun awaitState(check: () -> Boolean) = compose.waitUntil(20_000, check)
        try {
            val playable = listOf(
                "01-mp3.mp3",
                "02-aac.m4a",
                "03-flac.flac",
                "04-pcm.wav",
                "05-vorbis.ogg",
                "06-opus.ogg"
            ).map(byFile::getValue)
            val observer = SessionPlayback(context)
            try {
                compose.runOnUiThread {
                    observer.connect()
                    player.start(playable.take(2))
                    player.playAfterCurrent(playable[2])
                    player.playAfterCurrent(playable[3])
                    player.disconnect()
                }
                val connectionOrder = listOf(playable[0], playable[3], playable[2], playable[1]).map { it.id }
                awaitState { observer.state.value.playing && observer.state.value.queue.map { it.id } == connectionOrder }
            } finally {
                compose.runOnUiThread { observer.disconnect() }
            }

            for (track in playable) {
                compose.runOnUiThread { player.start(listOf(track)) }
                awaitState { player.state.value.let { it.song?.id == track.id && it.playing && it.duration != null && it.seekable } }
                compose.runOnUiThread { player.seek(8_000) }
                awaitState {
                    compose.runOnUiThread { player.updatePosition() }
                    player.state.value.position > 8_100
                }
                assertTrue("Duration for ${track.title}", player.state.value.duration!! in 40_000L..44_000L)
            }

            compose.runOnUiThread {
                player.start(playable.take(4), shuffle = true)
                player.start(playable.drop(2), shuffle = true)
                player.playAfterCurrent(playable.first())
            }
            val finalTracks = (playable.drop(2) + playable.first()).map { it.id }.toSet()
            awaitState { player.state.value.shuffle && player.state.value.queue.map { it.id }.toSet() == finalTracks }
            val current = player.state.value.song!!.id
            val shuffledOrder = player.state.value.queue.map { it.id }
            assertEquals(playable.first().id, shuffledOrder[shuffledOrder.indexOf(current) + 1])
            compose.runOnUiThread { player.skip() }
            awaitState { player.state.value.song?.id == playable.first().id }
            for (repeat in listOf(MusicRepeat.All, MusicRepeat.One, MusicRepeat.Off)) {
                compose.runOnUiThread { player.cycleRepeat() }
                awaitState { player.state.value.repeat == repeat }
            }
            val damaged = byFile.getValue("07-damaged.mp3")
            compose.runOnUiThread { player.start(listOf(damaged, playable.first())) }
            awaitState { player.state.value.playing && player.state.value.song?.id == playable.first().id }
            assertTrue(player.state.value.error.orEmpty().contains(damaged.title))
            compose.runOnUiThread { player.toggle() }
            awaitState { !player.state.value.playRequested }
        } finally {
            compose.runOnUiThread { player.stop() }
            awaitState { player.state.value.song == null }
            compose.runOnUiThread { player.disconnect() }
        }
    }

    @Test
    fun playlistEditorSavesSelectionReorderAndRename() {
        requireFixture()
        val store = container.music
        val (first, second, third) = runBlocking {
            withTimeout(20_000) { store.catalog.first { it.songs.size == 7 } }.songs.filter { it.available }.take(3)
        }
        val name = "Editor verification"
        val renamed = "Renamed editor verification"
        fun options(action: String) {
            compose.onNodeWithContentDescription("Playlist options").performClick()
            compose.clickText(action)
        }
        fun filterSongs(current: String, query: String) {
            compose.onNode(hasText(current) and hasSetTextAction()).performTextReplacement(query)
        }
        fun selectSong(title: String) {
            val checkbox = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
            compose.onNode(hasText(title) and checkbox).performClick()
        }
        fun playlistHas(title: String, ids: List<String>) = runBlocking {
            withTimeout(20_000) {
                store.catalog.first { catalog ->
                    catalog.playlists.any { it.name == title && it.tracks.map { track -> track.id } == ids }
                }
            }
        }
        try {
            compose.clickText("Music")
            compose.clickText("New playlist")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Playlist name").performTextReplacement(name)
            filterSongs("Find songs", "No matching fixture")
            compose.onNodeWithText("No songs to add").assertIsDisplayed()
            filterSongs("No matching fixture", first.title)
            selectSong(first.title)
            filterSongs(first.title, second.title)
            selectSong(second.title)
            compose.clickText("Save")
            playlistHas(name, listOf(first.id, second.id))

            compose.clickText(name)
            options("Add songs")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            filterSongs("Find songs", third.title)
            selectSong(third.title)
            compose.clickText("Save")
            playlistHas(name, listOf(first.id, second.id, third.id))

            options("Edit playlist")
            compose.onNodeWithContentDescription("Options for ${third.title}").performClick()
            compose.clickText("Move up")
            compose.onNodeWithContentDescription("Options for ${first.title}").performClick()
            compose.clickText("Remove from playlist")
            compose.clickText("Save")
            playlistHas(name, listOf(third.id, second.id))

            options("Rename")
            compose.onNodeWithText("Playlist name").performTextReplacement(renamed)
            compose.clickText("Save")
            playlistHas(renamed, listOf(third.id, second.id))
            options("Delete playlist")
            compose.clickText("Cancel")
            compose.onNodeWithText(renamed).assertExists()
            options("Delete playlist")
            compose.clickText("Delete playlist")
            runBlocking {
                withTimeout(20_000) { store.catalog.first { catalog -> catalog.playlists.none { it.name == renamed } } }
            }
        } finally {
            runBlocking {
                store.catalog.value.playlists.filter { it.name == name || it.name == renamed }.forEach { store.delete(it.id) }
            }
        }
    }

    @Test
    fun libraryEditsAndJournalRestoreKeepMusicAndQueueIndependent() {
        requireFixture()
        runBlocking {
            val store = container.music
            val songs = withTimeout(20_000) { store.catalog.first { it.songs.size == 7 } }.songs.filter { it.available }
            val name = "Music verification playlist"
            val first = songs[0]
            val second = songs[1]
            store.create(name, listOf(first.id, second.id, first.id))
            val playlist = withTimeout(20_000) { store.catalog.first { catalog -> catalog.playlists.any { it.name == name } } }
                .playlists.single { it.name == name }
            val archive = File(context.cacheDir, "music-journal-verification.zip")
            try {
                assertEquals(listOf(first.id, second.id), playlist.tracks.map { it.id })
                store.saveOrder(playlist.id, listOf(second.id, first.id))
                store.rename(playlist.id, "$name renamed")
                val reordered = withTimeout(20_000) {
                    store.catalog.first { catalog -> catalog.playlists.any { it.id == playlist.id && it.name.endsWith("renamed") } }
                }.playlists.single { it.id == playlist.id }
                assertEquals(listOf(second.id, first.id), reordered.tracks.map { it.id })

                compose.runOnUiThread {
                    player.connect()
                    player.start(reordered.tracks)
                }
                compose.waitUntil(20_000) { player.state.value.queue.map { it.id } == reordered.tracks.map { it.id } }
                store.saveOrder(playlist.id, listOf(first.id))
                assertEquals(listOf(second.id, first.id), player.state.value.queue.map { it.id })

                val snapshot = container.repository.snapshot()
                assertTrue(snapshot.workouts.isEmpty())
                assertTrue(snapshot.exercises.isEmpty())
                container.backups.createBackup(Uri.fromFile(archive))
                container.backups.validateBackup(Uri.fromFile(archive)).use {
                    container.backups.replaceJournal(it, it.identity)
                }
                val afterRestore = withTimeout(20_000) {
                    store.catalog.first { catalog -> catalog.playlists.any { it.id == playlist.id && it.tracks.size == 1 } }
                }
                assertEquals(first.id, afterRestore.playlists.single { it.id == playlist.id }.tracks.single().id)
                assertEquals(listOf(second.id, first.id), player.state.value.queue.map { it.id })

                store.delete(playlist.id)
                assertEquals(listOf(second.id, first.id), player.state.value.queue.map { it.id })
                val refreshed = withTimeout(20_000) { store.catalog.first { catalog -> catalog.playlists.none { it.id == playlist.id } } }
                assertFalse(refreshed.songs.isEmpty())
            } finally {
                store.delete(playlist.id)
                archive.delete()
                compose.runOnUiThread { player.stop() }
                compose.waitUntil(20_000) { player.state.value.song == null }
                compose.runOnUiThread { player.disconnect() }
            }
        }
    }
}

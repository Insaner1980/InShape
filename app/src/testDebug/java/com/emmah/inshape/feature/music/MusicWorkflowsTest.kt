package com.emmah.inshape.feature.music

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.AppContainer
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.assistant.AssistantAnswer
import com.emmah.inshape.feature.assistant.AssistantGateway
import com.emmah.inshape.feature.assistant.DisabledAssistant
import com.emmah.inshape.quantity
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import com.emmah.inshape.ui.theme.ThemeMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MusicWorkflowsTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "music-workflows-${newId()}.db"
    private val tree = DocumentsContract.buildTreeDocumentUri("com.emmah.inshape.test.documents", "music")
    private val readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val dusk = track("a", "Dusk", "Piano", "calm")
    private val pulse = track("b", "Pulse", "Drums", "energetic")
    private val stride = track("c", "Stride", "Strings", "steady")
    private val missing = track("d", "Missing", "Piano", null).copy(available = false)
    private lateinit var app: AppContainer

    @After
    fun closeMusic() {
        if (::app.isInitialized) {
            app.close()
            val resolver = context.contentResolver
            if (resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }) {
                resolver.releasePersistableUriPermission(tree, readPermission)
            }
            context.deleteDatabase("music-$databaseName")
            context.deleteDatabase(databaseName)
            app.storageRoot.deleteRecursively()
        }
    }

    @Test
    fun createAddReorderRenameAndDeletePlaylistThroughMusicScreens() {
        seedMusic()
        showMusicHome()
        compose.onNodeWithContentDescription(ui(R.string.new_playlist)).performClick()
        compose.clickText(ui(R.string.new_playlist))
        compose.onNodeWithText(ui(R.string.create_playlist)).assertIsNotEnabled()
        compose.onNodeWithText(ui(R.string.playlist_name)).performTextReplacement("Warmup draft")
        searchFor("Unknown title")
        compose.onNodeWithText(ui(R.string.no_songs_to_add)).assertIsDisplayed()
        searchFor("Piano")
        selectTrack(dusk.title)
        searchFor(pulse.title)
        selectTrack(pulse.title)
        compose.clickText(ui(R.string.create_playlist))
        playlist("Warmup draft", dusk, pulse)

        waitForText(ui(R.string.edit_playlist))
        compose.onNodeWithText(ui(R.string.playlist_name)).performTextReplacement("Warmup")
        closeEditor()
        playlist("Warmup", dusk, pulse)

        compose.clickText("Warmup")
        playlistOptions(R.string.add_songs)
        compose.onNode(hasText(ui(R.string.add_songs)) and hasClickAction()).assertIsNotEnabled()
        searchFor(stride.title)
        selectTrack(stride.title)
        compose.clickText(ui(R.string.add_songs_count, quantity(R.plurals.song_count, 1)))
        playlist("Warmup", dusk, pulse, stride)

        playlistOptions(R.string.edit_playlist)
        compose.onNodeWithContentDescription(ui(R.string.named_options, stride.title)).performClick()
        compose.clickText(ui(R.string.move_up))
        compose.onNodeWithContentDescription(ui(R.string.named_options, dusk.title)).performClick()
        compose.clickText(ui(R.string.remove_from_playlist))
        closeEditor()
        playlist("Warmup", stride, pulse)

        playlistOptions(R.string.rename)
        compose.onNodeWithText(ui(R.string.playlist_name)).performTextReplacement("Ready for training")
        compose.clickText(ui(R.string.close))
        playlist("Ready for training", stride, pulse)
        playlistOptions(R.string.delete_playlist)
        compose.clickText(ui(R.string.cancel))
        compose.onNodeWithText("Ready for training").assertIsDisplayed()
        playlistOptions(R.string.delete_playlist)
        compose.clickText(ui(R.string.delete_playlist))
        assertEquals(emptyList<SavedPlaylist>(), catalog { it.playlists.isEmpty() }.playlists)
        compose.onNodeWithText(ui(R.string.no_playlists)).assertIsDisplayed()
    }

    @Test
    fun songSearchAndPlaylistMenuKeepUnavailableAndExistingSongsDistinct() {
        seedMusic()
        val existing = runBlocking { app.music.create("Warmup", listOf(dusk.id)) }
        runBlocking { app.music.create("Cooldown", emptyList()) }
        val playback = showMusicHome()
        compose.clickText(ui(R.string.songs))
        searchFor("no songs")
        compose.onNodeWithText(ui(R.string.no_matching_songs)).assertIsDisplayed()
        searchFor("piano")
        compose.onNodeWithText(missing.title).assertIsNotEnabled()
        compose.onNodeWithText(dusk.title).performClick()
        assertEquals(dusk.id, playback.state.value.song?.id)
        assertEquals(listOf(dusk.id), playback.state.value.queue.map { it.id })

        compose.onNodeWithContentDescription(ui(R.string.named_options, dusk.title)).performClick()
        compose.clickText(ui(R.string.add_to_playlist))
        compose.onNodeWithText(ui(R.string.already_added)).assertIsDisplayed()
        compose.onNodeWithText("Warmup").assertIsNotEnabled()
        compose.clickText("Cooldown")
        playlist("Cooldown", dusk)
        assertEquals(existing, playlist("Warmup", dusk).id)

        compose.onNodeWithContentDescription(ui(R.string.named_options, dusk.title)).performClick()
        compose.clickText(ui(R.string.set_energy_label))
        compose.clickText(ui(R.string.energy_energetic))
        val updated = catalog { it.songs.any { track -> track.id == dusk.id && track.energy == "energetic" } }
        assertEquals("energetic", updated.songs.single { it.id == dusk.id }.energy)
    }

    @Test
    fun unavailableSongCanBeKeptInANewPlaylistThatCannotPlay() {
        seedMusic()
        val playback = showMusicHome()
        compose.clickText(ui(R.string.songs))
        searchFor(missing.title)
        compose.onNodeWithContentDescription(ui(R.string.named_options, missing.title)).performClick()
        compose.onNodeWithText(ui(R.string.play_next)).assertDoesNotExist()
        compose.clickText(ui(R.string.add_to_playlist))
        compose.clickText(ui(R.string.new_playlist))

        val checkbox = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
        compose.onNode(hasText(missing.title) and checkbox).assertIsOn()
        compose.onNodeWithText(ui(R.string.playlist_name)).performTextReplacement("Keep for later")
        compose.clickText(ui(R.string.create_playlist))
        assertFalse(playlist("Keep for later", missing).tracks.single().available)
        waitForText(ui(R.string.edit_playlist))
        closeEditor()

        compose.clickText(ui(R.string.playlists))
        compose.onNodeWithContentDescription(ui(R.string.play_named, "Keep for later")).assertIsNotEnabled()
        compose.clickText("Keep for later")
        compose.onNodeWithText(ui(R.string.play)).assertIsNotEnabled()
        compose.onNodeWithText(ui(R.string.shuffle)).assertIsNotEnabled()
        assertTrue(playback.state.value.queue.isEmpty())
    }

    @Test
    @Config(qualifiers = "w320dp-h680dp")
    fun emptyPlaylistCanAddItsFirstTrackAndPlayAtLargeText() {
        seedMusic()
        val playlistId = runBlocking { app.music.create("First songs", emptyList()) }
        val playback = IsolatedPlayback(emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                InShapeTheme(ThemeMode.Light) {
                    PlaylistDetail(playlistId, app.music, playback, onBack = {})
                }
            }
        }
        waitForText(ui(R.string.empty_playlist))
        compose.onNodeWithText(ui(R.string.play)).assertIsNotEnabled()
        compose.clickText(ui(R.string.add_songs))
        searchFor(pulse.title)
        selectTrack(pulse.title)
        compose.clickText(ui(R.string.add_songs_count, quantity(R.plurals.song_count, 1)))
        playlist("First songs", pulse)
        compose.clickText(ui(R.string.shuffle))
        assertEquals(pulse.id, playback.state.value.song?.id)
        assertTrue(playback.state.value.shuffle)
        compose.clickText(ui(R.string.play))
        assertEquals(pulse.id, playback.state.value.song?.id)
        assertFalse(playback.state.value.shuffle)
    }

    @Test
    fun lostFolderAccessDisablesRefreshAndKeepsFolderSelectionAvailable() {
        seedMusic()
        showMusicHome()
        compose.onNodeWithContentDescription(ui(R.string.music_folder_options)).performClick()
        compose.onNodeWithText(ui(R.string.refresh)).assertIsEnabled()
        context.contentResolver.releasePersistableUriPermission(tree, readPermission)
        app.music.checkAccess()
        catalog { it.accessLost }
        compose.onNodeWithText(ui(R.string.refresh)).assertIsNotEnabled()
        compose.onNodeWithText(ui(R.string.choose_folder)).assertIsEnabled()
        compose.onNodeWithText(ui(R.string.music_access_lost)).assertExists()
    }

    @Test
    fun playlistSaveRollsBackAndCanRetry() {
        seedMusic()
        val stale = dusk.copy(id = "removed-song", title = "Removed song")
        compose.setContent {
            InShapeTheme(ThemeMode.Light) {
                PlaylistEditor(app.music, listOf(stale), onClose = {}, firstSong = stale.id)
            }
        }
        compose.onNodeWithText(ui(R.string.playlist_name)).performTextReplacement("Retry mix")
        compose.clickText(ui(R.string.create_playlist))
        waitForText(ui(R.string.playlist_save_failed))
        assertTrue(catalog { true }.playlists.isEmpty())
        selectTrack(stale.title)
        compose.clickText(ui(R.string.create_playlist))
        assertEquals(emptyList<MusicTrack>(), playlist("Retry mix").tracks)
    }

    @Test
    fun stalePlaylistOrderCanBeCorrected() {
        seedMusic()
        runBlocking { app.music.create("Existing playlist", listOf(dusk.id, pulse.id)) }
        val original = playlist("Existing playlist", dusk, pulse)
        val stale = dusk.copy(id = "removed-track", title = "Removed track")
        var closed by mutableStateOf(false)
        compose.setContent {
            InShapeTheme(ThemeMode.Light) {
                if (!closed) {
                    EditSavedPlaylist(app.music, original.copy(tracks = original.tracks + stale), onClose = { closed = true })
                }
            }
        }
        compose.onNodeWithContentDescription(ui(R.string.named_options, stale.title)).performClick()
        compose.clickText(ui(R.string.move_up))
        waitForText(ui(R.string.playlist_save_failed))
        assertEquals(original, playlist("Existing playlist", dusk, pulse))
        compose.clickText(ui(R.string.retry))
        waitForText(ui(R.string.playlist_save_failed))

        compose.onNodeWithContentDescription(ui(R.string.named_options, stale.title)).performClick()
        compose.clickText(ui(R.string.remove_from_playlist))
        compose.onNodeWithContentDescription(ui(R.string.named_options, pulse.title)).performClick()
        compose.clickText(ui(R.string.move_up))
        closeEditor()
        assertEquals(original.id, playlist("Existing playlist", pulse, dusk).id)
        compose.waitUntil(10_000) { closed }
    }

    @Test
    fun assistantCancelsAndRetriesPreview() {
        val requests = AtomicInteger()
        val cancelled = AtomicBoolean()
        val gateway = object : AssistantGateway by DisabledAssistant {
            override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer {
                assertEquals("A short workout mix", input)
                when (requests.incrementAndGet()) {
                    1 -> try {
                        awaitCancellation()
                    } finally {
                        cancelled.set(true)
                    }

                    2 -> return AssistantAnswer(Json.decodeFromString<JsonObject>("""{"minutes":0}"""), null)
                }
                val proposal = Json.decodeFromString<JsonObject>(
                    """{"minutes":1,"stages":["calm","energetic"],"name":"One minute"}"""
                )
                return AssistantAnswer(proposal, null)
            }
        }
        seedMusic(gateway)
        compose.setContent {
            InShapeTheme(ThemeMode.Light) {
                PlaylistAssistantScreen(app, previousWorkout = null, playlistId = null, onSettings = {}) {
                    error("The preview was not approved")
                }
            }
        }
        catalog { it.songs.isNotEmpty() }
        compose.onNodeWithText(ui(R.string.write_request)).performTextReplacement("A short workout mix")
        compose.clickText(ui(R.string.build_playlist_preview))
        compose.waitUntil(10_000) { requests.get() == 1 }
        compose.onNodeWithText(ui(R.string.build_playlist_preview)).assertIsNotEnabled()
        compose.clickText(ui(R.string.cancel_request))
        compose.waitUntil(10_000) { cancelled.get() }
        compose.onNodeWithText(ui(R.string.build_playlist_preview)).assertIsEnabled()

        compose.clickText(ui(R.string.build_playlist_preview))
        waitForText(ui(R.string.playlist_request_failed))
        compose.clickText(ui(R.string.build_playlist_preview))
        waitForText(pulse.title)
        val excessDuration = ui(R.string.longer_playlist, com.emmah.inshape.ui.theme.formatDuration(60))
        compose.onNodeWithText(excessDuration).assertExists()
        compose.clickText(ui(R.string.remove))
        compose.clickText(ui(R.string.remove))
        compose.onNodeWithText(ui(R.string.save_playlist)).assertIsNotEnabled()
        compose.clickText(ui(R.string.edit_request))
        compose.onNodeWithText("A short workout mix").assertExists()
    }

    @Test
    fun assistantCanRetryWithoutWorkoutCoverageAndSaveEditedPreview() {
        val gateway = object : AssistantGateway by DisabledAssistant {
            override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer {
                assertEquals("Start calmly and then pick up the pace", input)
                val proposal = Json.decodeFromString<JsonObject>(
                    """
                    {
                      "allowedArtists": ["Piano", "Drums"],
                      "excludedArtists": ["Strings"],
                      "excludePrevious": true,
                      "stages": ["calm", "energetic"],
                      "name": "Workout mix"
                    }
                    """.trimIndent()
                )
                return AssistantAnswer(proposal, null)
            }
        }
        seedMusic(gateway)
        var saved: String? = null
        compose.setContent {
            InShapeTheme(ThemeMode.Light) {
                PlaylistAssistantScreen(app, previousWorkout = null, playlistId = null, onSettings = {}) { saved = it }
            }
        }
        compose.onNodeWithText(ui(R.string.build_playlist_preview)).assertIsNotEnabled()
        catalog { it.songs.isNotEmpty() }
        compose.onNodeWithText(ui(R.string.write_request)).performTextReplacement("  Start calmly and then pick up the pace  ")
        compose.clickText(ui(R.string.build_playlist_preview))
        waitForText(ui(R.string.playlist_no_coverage))
        compose.onNodeWithText(ui(R.string.playlist_no_coverage)).assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.save_playlist)).assertIsNotEnabled()
        compose.clickText(ui(R.string.skip_previous_workout_filter))
        waitForText(pulse.title)
        compose.onNodeWithText(pulse.title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(ui(R.string.move_up)).performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.playlist_name)).performScrollTo().performTextReplacement("Training mix")
        compose.clickText(ui(R.string.save_playlist))
        val playlist = playlist("Training mix", pulse, dusk)
        compose.runOnIdle { assertEquals(playlist.id, saved) }
    }

    @Test
    @Config(qualifiers = "w320dp-h680dp")
    fun assistantWithoutKeyOffersSettings() {
        seedMusic()
        var settingsOpened = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                InShapeTheme(ThemeMode.Light) {
                    PlaylistAssistantScreen(
                        app,
                        previousWorkout = null,
                        playlistId = null,
                        onSettings = { settingsOpened = true },
                        onSave = { error("A missing key must prevent saving") }
                    )
                }
            }
        }
        catalog { it.songs.isNotEmpty() }
        compose.onNodeWithText(ui(R.string.write_request)).performTextReplacement("Songs for warmup")
        compose.clickText(ui(R.string.build_playlist_preview))
        compose.onNodeWithText(ui(R.string.assistant_error_no_key)).performScrollTo().assertIsDisplayed()
        compose.clickText(ui(R.string.assistant_settings))
        compose.runOnIdle { assertTrue(settingsOpened) }
    }

    private fun seedMusic(assistant: AssistantGateway? = null) {
        val database = Room.databaseBuilder(context, MusicDatabase::class.java, "music-$databaseName").build()
        try {
            runBlocking {
                database.music().saveSource(MusicSource(tree = tree.toString()))
                database.music().saveTracks(listOf(dusk, pulse, stride, missing))
            }
        } finally {
            database.close()
        }
        context.contentResolver.takePersistableUriPermission(tree, readPermission)
        app = AppContainer(context, databaseName = databaseName, integrationsEnabled = false, assistantFixture = assistant)
    }

    private fun showMusicHome(): IsolatedPlayback {
        val playback = IsolatedPlayback(emptyList())
        compose.setContent {
            InShapeTheme(ThemeMode.Light) {
                var playlistId by remember { mutableStateOf<String?>(null) }
                var energyId by remember { mutableStateOf<String?>(null) }
                val playlist = playlistId
                val energy = energyId
                when {
                    playlist != null -> PlaylistDetail(playlist, app.music, playback, onBack = { playlistId = null })
                    energy != null -> TrackEnergyScreen(app.music, energy, onSave = { energyId = null })
                    else -> MusicHome(app.music, playback, { playlistId = it }, {}, onEnergy = { energyId = it })
                }
            }
        }
        catalog { it.songs.size == 4 }
        compose.waitForIdle()
        return playback
    }

    private fun searchFor(query: String) {
        compose.onNode(hasContentDescription(ui(R.string.search_songs)) and hasSetTextAction()).performTextReplacement(query)
    }

    private fun selectTrack(title: String) {
        val checkbox = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
        compose.onNode(hasText(title) and checkbox).performClick()
    }

    private fun playlistOptions(action: Int) {
        compose.onNodeWithContentDescription(ui(R.string.playlist_options)).performClick()
        compose.clickText(ui(action))
    }

    private fun closeEditor() {
        compose.onNode(hasContentDescription(ui(R.string.back)) and hasAnyAncestor(isDialog())).performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun playlist(name: String, vararg tracks: MusicTrack): SavedPlaylist {
        val matches: (SavedPlaylist) -> Boolean = { it.name == name && it.tracks.map { song -> song.id } == tracks.map { song -> song.id } }
        return catalog { it.playlists.any(matches) }.playlists.single(matches)
    }

    private fun catalog(matches: (MusicCatalog) -> Boolean): MusicCatalog = runBlocking {
        withTimeout(10_000) { app.music.catalog.first { it.source != null && matches(it) } }
    }

    private fun track(id: String, title: String, artist: String, energy: String?): MusicTrack {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return MusicTrack(id, tree.toString(), uri.toString(), title, artist, 60_000, energy = energy)
    }
}

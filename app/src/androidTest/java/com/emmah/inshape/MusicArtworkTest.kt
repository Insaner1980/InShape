package com.emmah.inshape

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.feature.music.MusicCover
import com.emmah.inshape.feature.music.MusicTrack
import com.emmah.inshape.feature.music.SavedPlaylist
import com.emmah.inshape.feature.music.playlistCover
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MusicArtworkTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val audio by lazy {
        File(instrumentation.targetContext.cacheDir, "music-artwork-test.mp3").apply {
            instrumentation.context.assets.open("music-embedded-cover.mp3").use { source ->
                outputStream().use { source.copyTo(it) }
            }
        }
    }

    @After fun removeFixture() {
        File(instrumentation.targetContext.cacheDir, "music-artwork-test.mp3").delete()
    }

    @Test fun changingPlaylistOrCoverAvailabilityResolvesTheCorrectArt() {
        val unavailable = track(
            "unavailable",
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fmusic-artwork-test.mp3"
        )
        val readable = track("readable", Uri.fromFile(audio).toString())
        var playlist by mutableStateOf(SavedPlaylist("c", "First", listOf(unavailable)))
        var result: MusicCover? = null
        compose.setContent { result = playlistCover(playlist) }

        fun expect(cover: MusicCover.Bundled) {
            compose.waitUntil(10_000) { compose.runOnIdle { result == cover } }
        }
        expect(MusicCover.Bundled(R.drawable.art_music_night_drive_cover, R.drawable.art_music_night_drive))
        compose.runOnIdle { playlist = playlist.copy(id = "a", name = "Second") }
        expect(MusicCover.Bundled(R.drawable.art_music_heavy_rotation))
        compose.runOnIdle { playlist = playlist.copy(id = "b", name = "Third") }
        expect(MusicCover.Bundled(R.drawable.art_music_slow_motion))
        compose.runOnIdle { playlist = playlist.copy(name = "Renamed", tracks = listOf(unavailable, readable)) }
        compose.waitUntil(10_000) { compose.runOnIdle { result is MusicCover.Personal } }
        compose.runOnIdle {
            val personal = result as MusicCover.Personal
            assertEquals(16, personal.bitmap.width)
        }
        compose.runOnIdle { playlist = playlist.copy(tracks = emptyList()) }
        expect(MusicCover.Bundled(R.drawable.art_music_record_grooves))
    }

    private fun track(id: String, uri: String) = MusicTrack(id, "", uri, id, "Artist", 30_000)
}

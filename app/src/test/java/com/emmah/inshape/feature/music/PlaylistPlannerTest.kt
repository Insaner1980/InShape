package com.emmah.inshape.feature.music

import com.emmah.inshape.R
import com.emmah.inshape.testContext
import com.emmah.inshape.ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistPlannerTest {
    @Test fun replacedBytesOnTheSameUriDoNotInheritPlaylistIdentityOrEnergy() {
        val original = MusicTrack(
            "legacy",
            "tree",
            "content://same-track",
            "Title",
            "Artist",
            20_000,
            energy = "calm",
            contentFingerprint = "previous"
        )
        val rescanned = original.copy(id = "scanned", energy = null)
        assertEquals(original, identifyScannedTrack(rescanned, listOf(original)))
        val replacement = identifyScannedTrack(rescanned.copy(contentFingerprint = "different"), listOf(original))
        assertNotEquals(original.id, replacement.id)
        assertNull(replacement.energy)
    }

    @Test fun matchesFortyMinutesUsingOnlyEligibleTrackIds() {
        fun track(id: String, duration: Long?, artist: String = "Artist", available: Boolean = true) =
            MusicTrack(id, "tree", "file:$id", "Same title", artist, duration, available)
        val result = planPlaylist(
            testContext(),
            listOf(
                track("1", 960_000),
                track("2", 1_440_000),
                track("3", 1_400_000),
                track("banned", 2_400_000, "Excluded"),
                track("missing", 2_400_000, available = false),
                track("unknown", null)
            ),
            PlaylistRequest(40, emptySet(), setOf("Excluded")),
            emptySet()
        )
        assertEquals(listOf("1", "2"), result.tracks.map { it.id })
        assertEquals(2_400_000L, result.tracks.sumOf { it.durationMs!! })
        assertTrue(ui(R.string.playlist_unknown_lengths) in result.messages)
    }
}

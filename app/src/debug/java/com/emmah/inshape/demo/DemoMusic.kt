package com.emmah.inshape.demo

import com.emmah.inshape.BuildConfig
import com.emmah.inshape.feature.music.MusicCatalog
import com.emmah.inshape.feature.music.MusicTrack
import com.emmah.inshape.feature.music.PlaylistArtworkFamily
import com.emmah.inshape.feature.music.SavedPlaylist

internal object DemoMusic {
    const val NIGHT_DRIVE_ID = "night-drive"
    val states = setOf("MusicHome", "MusicPlaylist", "WorkoutWithMusic")
    val catalog = MusicCatalog(
        playlists = listOf(
            playlist(
                NIGHT_DRIVE_ID,
                "Night Drive",
                PlaylistArtworkFamily.NightDrive,
                listOf(
                    Triple("After Hours", "Northline", 222),
                    Triple("Tunnel Vision", "Low Signal", 248),
                    Triple("Coast Road", "Aster", 206),
                    Triple("Headlights", "Northline", 252),
                    Triple("Night Shift", "Velour", 235),
                    Triple("Long Way Home", "Aster", 204),
                    Triple("Between Streets", "Velour", 253),
                    Triple("Overpass", "Northline", 226),
                    Triple("Window Lights", "Aster", 232),
                    Triple("Empty Avenue", "Low Signal", 263),
                    Triple("Southbound", "Velour", 216),
                    Triple("First Light", "Northline", 241)
                )
            ),
            playlist(
                "heavy-rotation",
                "Heavy Rotation",
                PlaylistArtworkFamily.HeavyRotation,
                List(18) { Triple("Rotation ${it + 1}", "Low Signal", 190 + it * 3) }
            ),
            playlist(
                "slow-motion",
                "Slow Motion",
                PlaylistArtworkFamily.SlowMotion,
                List(9) { Triple("Tide ${it + 1}", "Aster", 224 + it * 7) }
            )
        )
    ).let { it.copy(songs = it.playlists.flatMap(SavedPlaylist::tracks)) }

    private fun playlist(id: String, name: String, family: PlaylistArtworkFamily, entries: List<Triple<String, String, Int>>) =
        SavedPlaylist(
            id,
            name,
            entries.mapIndexed { index, (title, artist, seconds) ->
                val artwork = "android.resource://${BuildConfig.APPLICATION_ID}/${family.square}"
                MusicTrack("$id-$index", "", artwork, title, artist, seconds * 1_000L)
            },
            fixtureArtwork = family
        )
}

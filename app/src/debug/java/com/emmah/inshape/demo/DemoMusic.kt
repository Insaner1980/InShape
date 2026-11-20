package com.emmah.inshape.demo

import com.emmah.inshape.BuildConfig
import com.emmah.inshape.feature.music.MusicCatalog
import com.emmah.inshape.feature.music.MusicTrack
import com.emmah.inshape.feature.music.PlaylistArtworkFamily
import com.emmah.inshape.feature.music.SavedPlaylist

internal object DemoMusic {
    const val NIGHT_DRIVE_ID = "night-drive"
    private const val LOW_SIGNAL = "Hiljainen taajuus"
    val states = setOf("MusicHome", "MusicPlaylist", "WorkoutWithMusic")
    val catalog = MusicCatalog(
        playlists = listOf(
            playlist(
                NIGHT_DRIVE_ID,
                "Yöajo",
                PlaylistArtworkFamily.NightDrive,
                listOf(
                    Triple("Illan jälkeen", "Northline", 222),
                    Triple("Tunnelinäkö", LOW_SIGNAL, 248),
                    Triple("Rantatie", "Aster", 206),
                    Triple("Ajovalot", "Northline", 252),
                    Triple("Yövuoro", "Velour", 235),
                    Triple("Pitkä kotimatka", "Aster", 204),
                    Triple("Katujen välissä", "Velour", 253),
                    Triple("Ylikulkusilta", "Northline", 226),
                    Triple("Ikkunoiden valot", "Aster", 232),
                    Triple("Tyhjä katu", LOW_SIGNAL, 263),
                    Triple("Kohti etelää", "Velour", 216),
                    Triple("Aamunkoitto", "Northline", 241)
                )
            ),
            playlist(
                "heavy-rotation",
                "Tehotoistossa",
                PlaylistArtworkFamily.HeavyRotation,
                List(18) { Triple("Kierros ${it + 1}", LOW_SIGNAL, 190 + it * 3) }
            ),
            playlist(
                "slow-motion",
                "Hidastus",
                PlaylistArtworkFamily.SlowMotion,
                List(9) { Triple("Aalto ${it + 1}", "Aster", 224 + it * 7) }
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

package com.emmah.inshape.feature.music

import org.junit.Assert.assertEquals
import org.junit.Test

class IsolatedPlaybackTest {
    private val first = track("first")
    private val second = track("second")

    @Test fun previousRestartsFirstTrackAndStepsBackFromSecondTrack() {
        val player = IsolatedPlayback(listOf(first, second))
        player.seek(1_000)
        player.previous()
        assertEquals(0L, player.state.value.position)

        player.skip()
        player.seek(1_000)
        player.previous()
        assertEquals(first.id, player.state.value.song?.id)
    }

    @Test fun queueJumpKeepsTheRequestedOccurrenceAfterInsertion() {
        val player = IsolatedPlayback(listOf(first, second, second))
        val occurrence = player.state.value.queue.last()
        player.playAfterCurrent(first)
        player.jump(occurrence)
        assertEquals(occurrence, player.state.value.song)
    }

    @Test fun previousQueueEntryAndSeekCannotTargetAReplacementOccurrenceOfTheSameSong() {
        val player = IsolatedPlayback(listOf(first))
        val oldEntry = player.state.value.queue.single()
        player.start(listOf(first))
        val current = player.state.value.queue.single()
        player.seek(1_000)
        player.jump(oldEntry)
        player.seek(6_000, oldEntry)
        assertEquals(current, player.state.value.song)
        assertEquals(1_000L, player.state.value.position)
    }

    private fun track(id: String) = MusicTrack(id, "tree", "uri", id, "Artist", 30_000)
}

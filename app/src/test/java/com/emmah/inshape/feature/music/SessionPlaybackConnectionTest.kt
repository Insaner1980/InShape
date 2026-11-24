package com.emmah.inshape.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import com.emmah.inshape.R
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionPlaybackConnectionTest {
    @Test
    fun queuedCommandsKeepStopBeforeLatestStartAndFailedConnectionsRetryOnlyWhileObserved() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val playback = SessionPlayback(context)
        val track = MusicTrack("track", "tree", "uri", "Track", "Artist", 30_000)
        val looper = shadowOf(Looper.getMainLooper())
        try {
            playback.playAfterCurrent(track)
            playback.start(listOf(track), track.id)
            playback.assistantGain("guide", 0.25f)
            playback.stop()
            playback.start(listOf(track), shuffle = true)
            playback.assistantGain("guide", null)
            val commands = ReflectionHelpers.getField<ArrayDeque<Pair<SessionCommand, Bundle>>>(playback, "pendingCommands")
            assertEquals(listOf(endPlayback, chooseQueue, assistantGainChange), commands.map { it.first })
            assertEquals(listOf(track.id), commands[1].second.getStringArrayList(MUSIC_IDS))
            assertTrue(commands[1].second.getBoolean(MUSIC_SHUFFLE))
            assertFalse(commands[2].second.containsKey("gain"))
            playback.disconnect()
            assertEquals(0, context.unbinds)
            assertEquals(1, context.binds)

            context.allowBinding = false
            ReflectionHelpers.callInstanceMethod<Unit>(playback, "release")
            playback.connect()
            looper.idle()
            assertEquals(context.getString(R.string.music_connect_failed), playback.state.value.error)
            val failedAttempts = context.binds
            looper.idleFor(Duration.ofMillis(500))
            assertEquals(failedAttempts + 1, context.binds)
            playback.disconnect()
            looper.idleFor(Duration.ofSeconds(8))
            assertEquals(failedAttempts + 1, context.binds)
        } finally {
            playback.disconnect()
            ReflectionHelpers.callInstanceMethod<Unit>(playback, "release")
        }
    }

    private class BindingContext(base: Context) : ContextWrapper(base) {
        var allowBinding = true
        var binds = 0
        var unbinds = 0

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds++
            return allowBinding
        }

        override fun unbindService(conn: ServiceConnection) {
            unbinds++
        }
    }
}

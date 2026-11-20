package com.emmah.inshape.feature.assistant

import com.emmah.inshape.feature.library.exerciseDisplayName
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.music.MusicPlayback
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private enum class SpeechMode(val label: Int) {
    SILENT(R.string.off), REQUEST(R.string.assistant_voice_request), INTRO(R.string.assistant_voice_intro)
}

private fun Context.speechSettings() = getSharedPreferences("assistant_voice", Context.MODE_PRIVATE)

private fun Context.speechMode(): SpeechMode = SpeechMode.entries.find { it.name == speechSettings().getString("mode", null) }
    ?: SpeechMode.SILENT

@Composable
internal fun AssistantVoiceSettings() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(context.speechMode()) }
    var choosing by remember { mutableStateOf(false) }
    Box {
        ListRow(stringResource(R.string.assistant_voice), subtitle = stringResource(mode.label), onClick = { choosing = true })
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            SpeechMode.entries.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(option.label)) }, onClick = {
                    mode = option
                    context.speechSettings().edit { putString("mode", option.name) }
                    choosing = false
                })
            }
        }
    }
}

@Composable
internal fun WorkoutSpeech(
    workoutId: String,
    item: WorkoutItem,
    gateway: AssistantGateway,
    music: MusicPlayback?
) {
    val context = LocalContext.current
    val mode = remember(context) { context.speechMode() }
    val scope = rememberCoroutineScope()
    var running by remember(item.row.id) { mutableStateOf<Job?>(null) }
    var error by remember(item.row.id) { mutableStateOf<String?>(null) }
    var cloudReview by remember(item.row.id) { mutableStateOf(false) }
    var previousItem by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    val introduction = stringResource(R.string.spoken_next, item.row.name.exerciseDisplayName(context))
    val exact = buildList {
        add(introduction)
        item.row.setup.takeIf(String::isNotBlank)?.let { add(context.getString(R.string.spoken_setup, it)) }
        item.sets.firstOrNull()?.plannedReps?.let {
            add(context.getString(R.string.spoken_target, it.localizedDisplay(context)))
        }
        item.completedSets.lastOrNull()?.let { set ->
            val loadFormat = when (item.row.basis) {
                "kg" -> R.string.spoken_last_load
                "level" -> R.string.spoken_last_level
                "assistance_kg" -> R.string.spoken_last_assistance
                else -> null
            }
            if (set.load != null && loadFormat != null) {
                add(context.getString(loadFormat, set.load.replace('.', ',')))
            }
            set.reps?.let {
                val reps = context.getString(if (set.perSide == true) R.string.rep_target_per_side else R.string.rep_value, it)
                add(context.getString(R.string.spoken_reps, reps))
            }
        }
        item.row.durationSeconds?.let { add(context.getString(R.string.spoken_time, it)) }
    }.joinToString(" ").take(400)

    fun play(text: String, cloud: Boolean) {
        running?.cancel()
        cloudReview = false
        error = null
        val owner = newId()
        running = scope.launch {
            try {
                if (cloud) {
                    val pcm = gateway.narrate(text)
                    music?.assistantGain(owner, 0.25f)
                    playPcm(pcm)
                } else {
                    val spoken = readFinnish(context, text) { music?.assistantGain(owner, 0.25f) }
                    if (!spoken) error = context.getString(R.string.offline_voice_missing)
                }
            } catch (failure: AssistantFailure) {
                error = context.getString(failure.kind.message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = context.getString(R.string.speech_failed)
            } finally {
                music?.assistantGain(owner, null)
            }
        }
    }
    val job = running
    LaunchedEffect(job) {
        if (job != null) {
            job.join()
            if (running === job) running = null
        }
    }
    LaunchedEffect(item.row.id) {
        if (mode == SpeechMode.INTRO && previousItem != null && previousItem != item.row.id) play(introduction, cloud = false)
        previousItem = item.row.id
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { running?.cancel() }
    DisposableEffect(context, item.row.id) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                running?.cancel()
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            running?.cancel()
            context.unregisterReceiver(receiver)
        }
    }
    if (mode != SpeechMode.SILENT) {
        if (running != null) OutlineButton(stringResource(R.string.stop_speech), { running?.cancel() })
        else {
            OutlineButton(stringResource(R.string.read_aloud), { play(exact, cloud = false) })
            OutlineButton(stringResource(R.string.openai_narration), { cloudReview = !cloudReview })
        }
        if (cloudReview) {
            Text(introduction)
            OutlineButton(stringResource(R.string.generate_speech), { play(introduction, cloud = true) })
        }
        error?.let { Text(it) }
    }
}

private suspend fun readFinnish(context: Context, text: String, onPlayback: () -> Unit): Boolean {
    var engine: TextToSpeech? = null
    try {
        val ready = withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { caller ->
                engine = TextToSpeech(context.applicationContext) { code ->
                    if (caller.isActive) caller.resume(code == TextToSpeech.SUCCESS)
                }
            }
        } == true
        val tts = engine ?: return false
        if (!ready) return false
        val finnish = tts.voices.orEmpty().firstOrNull { it.locale.language == "fi" && !it.isNetworkConnectionRequired }
            ?: return false
        if (tts.setVoice(finnish) != TextToSpeech.SUCCESS) return false
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        return withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { caller ->
                val id = newId()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && caller.isActive) caller.resume(true)
                    }
                    @Deprecated("Platform callback")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && caller.isActive) caller.resume(false)
                    }
                })
                onPlayback()
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR && caller.isActive) caller.resume(false)
            }
        } == true
    } finally {
        engine?.stop()
        engine?.shutdown()
    }
}

private suspend fun playPcm(samples: ByteArray) {
    require(samples.isNotEmpty() && samples.size % 2 == 0 && samples.size <= NARRATION_RATE * 60)
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(NARRATION_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size)
        .build()
    try {
        check(track.write(samples, 0, samples.size) == samples.size)
        track.play()
        delay(samples.size * 1000L / (2 * NARRATION_RATE) + 100)
    } finally {
        runCatching { track.stop() }
        track.release()
    }
}

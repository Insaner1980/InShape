package com.emmah.inshape.feature.assistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.data.newId
import com.emmah.inshape.feature.music.MusicPlayback
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AssistantMicrophone(
    gateway: AssistantGateway,
    exerciseHint: String,
    blocked: Boolean,
    music: MusicPlayback?,
    content: @Composable (trailingIcon: (@Composable () -> Unit)?) -> Unit,
    onResult: (String?, String?) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember(context) { WorkoutRecorder(context) }
    val owner = remember { newId() }
    var recording by remember { mutableStateOf(false) }
    var clipReady by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun finish() {
        if (recording) {
            recording = false
            clipReady = recorder.finish()
            music?.assistantGain(owner, null)
            if (!clipReady) onResult(null, context.getString(R.string.recording_save_failed))
        }
    }
    fun activate() {
        clipReady = false
        music?.assistantGain(owner, 0f)
        recording = recorder.start(::finish)
        if (!recording) {
            music?.assistantGain(owner, null)
            onResult(null, context.getString(R.string.no_microphone))
        }
    }
    fun cancel() {
        transcribing?.cancel()
        recorder.clear()
        music?.assistantGain(owner, null)
        recording = false
        clipReady = false
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) activate() else onResult(null, context.getString(R.string.microphone_denied))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { cancel() }
    DisposableEffect(recorder) {
        onDispose {
            recorder.clear()
            music?.assistantGain(owner, null)
        }
    }

    content(
        if (recording || transcribing != null || clipReady) null else {
            {
                IconButton(enabled = !blocked, onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        activate()
                    } else {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Icon(Icons.Outlined.Mic, stringResource(R.string.record_speech))
                }
            }
        }
    )
    when {
        recording -> {
            Text(stringResource(R.string.recording_status))
            ActionRow {
                OutlineButton(stringResource(R.string.stop_recording), onClick = ::finish)
                OutlineButton(stringResource(R.string.cancel_recording), onClick = ::cancel)
            }
        }
        transcribing != null -> OutlineButton(stringResource(R.string.cancel_transcription), onClick = ::cancel)
        clipReady -> {
            ActionRow {
                OutlineButton(stringResource(R.string.transcribe_recording), enabled = !blocked, onClick = {
                    transcribing = scope.launch {
                        try {
                            val clip = withContext(Dispatchers.IO) { recorder.readClip() }
                            val text = gateway.transcribe(clip, exerciseHint)
                            onResult(text, null)
                        } catch (failure: AssistantFailure) {
                            onResult(null, context.getString(failure.kind.message))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            onResult(null, context.getString(R.string.recording_unavailable))
                        } finally {
                            recorder.clear()
                            clipReady = false
                            transcribing = null
                        }
                    }
                })
                OutlineButton(stringResource(R.string.discard_recording), onClick = ::cancel)
            }
        }

    }
}

private class WorkoutRecorder(private val context: Context) {
    private var file: File? = null
    private var recorder: MediaRecorder? = null

    @Suppress("DEPRECATION")
    fun start(onLimit: () -> Unit): Boolean {
        clear()
        return try {
            val clip = File.createTempFile("inshape-recording-", ".m4a", context.cacheDir).also { file = it }
            val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            recorder = media
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioChannels(1)
            media.setAudioEncodingBitRate(64_000)
            media.setAudioSamplingRate(22_050)
            media.setMaxDuration(30_000)
            media.setMaxFileSize(2 * 1024 * 1024)
            media.setOnInfoListener { _, event, _ ->
                if (event == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    event == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    onLimit()
                }
            }
            media.setOutputFile(clip.absolutePath)
            media.prepare()
            media.start()
            true
        } catch (_: Exception) {
            clear()
            false
        }
    }

    fun finish(): Boolean {
        val media = recorder ?: return false
        recorder = null
        val stopped = runCatching { media.stop() }.isSuccess
        media.release()
        if (!stopped || (file?.length() ?: 0L) !in 1L..2L * 1024 * 1024) {
            clear()
            return false
        }
        return true
    }

    fun readClip(): ByteArray {
        val clip = requireNotNull(file)
        require(clip.length() in 1L..2L * 1024 * 1024)
        return clip.readBytes()
    }

    fun clear() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}

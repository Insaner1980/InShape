package com.emmah.inshape.feature.assistant

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AssistantMicrophoneFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val files = TemporaryFolder()

    @Test fun permissionAndRecordedClipControlTranscription() {
        val cache = files.newFolder("recording")
        val context = object : ContextWrapper(compose.activity) {
            override fun getCacheDir(): File = cache
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.RECORD_AUDIO) PackageManager.PERMISSION_DENIED
                else super.checkPermission(permission, pid, uid)
        }
        val grant = AtomicBoolean()
        val permission = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?
                ) {
                    assertTrue(contract is ActivityResultContracts.RequestPermission)
                    assertEquals(Manifest.permission.RECORD_AUDIO, input)
                    dispatchResult(requestCode, grant.get())
                }
            }
        }
        val outcome = AtomicReference<Pair<String?, String?>>()
        val requests = AtomicInteger()
        val fixtureClip = byteArrayOf(1, 2, 3, 4)
        val gateway = object : AssistantGateway by DisabledAssistant {
            override suspend fun transcribe(clip: ByteArray, exerciseHint: String): String {
                assertArrayEquals(fixtureClip, clip)
                assertEquals("Soutu", exerciseHint)
                requests.incrementAndGet()
                return "Tein 8 toistoa"
            }
        }
        var blocked by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalActivityResultRegistryOwner provides permission) {
                InShapeTheme {
                    Column {
                        AssistantMicrophone(gateway, "Soutu", blocked, null, { text, error -> outcome.set(text to error) }) { button ->
                            button?.invoke()
                        }
                    }
                }
            }
        }
        val microphone = compose.onNodeWithContentDescription(ui(R.string.record_speech))
        microphone.performClick()
        compose.waitUntil(10_000) { outcome.get() == (null to ui(R.string.microphone_denied)) }
        assertTrue(cache.listFiles().orEmpty().isEmpty())

        grant.set(true)
        microphone.performClick()
        waitFor(R.string.recording_status)
        compose.clickText(ui(R.string.stop_recording))
        compose.waitUntil(10_000) { outcome.get() == (null to ui(R.string.recording_save_failed)) }
        assertEquals(0, requests.get())
        assertTrue(cache.listFiles().orEmpty().isEmpty())

        microphone.performClick()
        waitFor(R.string.recording_status)
        cache.listFiles().orEmpty().single().writeBytes(fixtureClip)
        compose.clickText(ui(R.string.stop_recording))
        compose.runOnIdle { blocked = true }
        compose.onNodeWithText(ui(R.string.transcribe_recording)).assertIsNotEnabled()
        compose.runOnIdle { blocked = false }
        compose.clickText(ui(R.string.transcribe_recording))
        compose.waitUntil(10_000) { outcome.get() == ("Tein 8 toistoa" to null) }
        compose.waitUntil(10_000) { cache.listFiles().orEmpty().isEmpty() }
        assertEquals(1, requests.get())
    }

    private fun waitFor(id: Int) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(id)).fetchSemanticsNodes().isNotEmpty() }
    }
}

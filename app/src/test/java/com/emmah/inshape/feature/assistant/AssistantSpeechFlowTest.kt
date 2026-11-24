package com.emmah.inshape.feature.assistant

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.SessionItem
import com.emmah.inshape.data.WorkoutItem
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantSpeechFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val preferences get() = compose.activity.getSharedPreferences("assistant_voice", Context.MODE_PRIVATE)

    @Test fun voiceModeSelectionPersistsTheChosenSetting() {
        preferences.edit { putString("mode", "SILENT") }
        compose.setContent { InShapeTheme { AssistantVoiceSettings() } }
        compose.onNodeWithText(ui(R.string.off)).assertIsDisplayed()
        compose.clickText(ui(R.string.assistant_voice))
        compose.clickText(ui(R.string.assistant_voice_intro))
        assertEquals("INTRO", preferences.getString("mode", null))
        compose.onNodeWithText(ui(R.string.assistant_voice_intro)).assertIsDisplayed()
    }

    @Test fun cloudNarrationRequiresApprovalCanBeCancelledAndRejectsIncompletePcm() {
        preferences.edit { putString("mode", "REQUEST") }
        val requests = AtomicInteger()
        val lastText = AtomicReference<String>()
        val cancelled = AtomicBoolean()
        val gateway = object : AssistantGateway by DisabledAssistant {
            override suspend fun narrate(text: String): ByteArray {
                lastText.set(text)
                if (requests.incrementAndGet() == 1) {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.set(true)
                    }
                }
                return byteArrayOf(1)
            }
        }
        val row = SessionItem(
            workoutId = "fixture-workout", exerciseId = "fixture-exercise", equipmentId = "fixture-equipment",
            name = "Fixture press", category = "Machine", basis = "kg"
        )
        val approved = ui(R.string.spoken_next, row.name)
        compose.setContent {
            InShapeTheme { Column { WorkoutSpeech(row.workoutId, WorkoutItem(row, emptyList()), gateway, null) } }
        }
        compose.clickText(ui(R.string.openai_narration))
        compose.onNodeWithText(approved).assertIsDisplayed()
        assertEquals(0, requests.get())
        compose.clickText(ui(R.string.generate_speech))
        compose.waitUntil(10_000) { requests.get() == 1 }
        assertEquals(approved, lastText.get())
        compose.clickText(ui(R.string.stop_speech))
        compose.waitUntil(10_000) { cancelled.get() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(R.string.openai_narration)).fetchSemanticsNodes().isNotEmpty() }
        compose.clickText(ui(R.string.openai_narration))
        compose.clickText(ui(R.string.generate_speech))
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ui(R.string.speech_failed)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, requests.get())
    }

    @After fun clearVoicePreference() {
        preferences.edit { remove("mode") }
    }
}

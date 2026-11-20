package com.emmah.inshape.feature.assistant

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class UsageEntry(
    val operation: String,
    val model: String,
    val at: String,
    val input: Int?,
    val output: Int?
)

internal class AssistantUsageLog(context: Context) {
    private val prefs = context.getSharedPreferences("assistant_usage", Context.MODE_PRIVATE)

    @Synchronized
    fun entries(): List<UsageEntry> = prefs.getString("recent", null)?.let {
        runCatching { Json.decodeFromString<List<UsageEntry>>(it) }.getOrNull()
    }.orEmpty()

    @Synchronized
    fun record(operation: String, model: String, usage: AssistantUsage?) {
        val newest = UsageEntry(operation, model, Instant.now().toString(), usage?.inputTokens, usage?.outputTokens)
        prefs.edit { putString("recent", Json.encodeToString((listOf(newest) + entries()).take(20))) }
    }
}

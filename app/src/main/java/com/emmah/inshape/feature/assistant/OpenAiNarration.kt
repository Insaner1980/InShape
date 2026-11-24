package com.emmah.inshape.feature.assistant

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal const val REALTIME_MODEL = "gpt-realtime-2.1-mini"
internal const val NARRATION_RATE = 24_000
private const val MAX_AUDIO_BYTES = 30 * NARRATION_RATE * 2

private val realtimeClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .connectTimeout(20, TimeUnit.SECONDS)
    .build()

internal fun narrationRequest(key: String): Request = Request.Builder()
    .url("wss://api.openai.com/v1/realtime?model=$REALTIME_MODEL")
    .header("Authorization", "Bearer $key")
    .build()

private val configureNarration = buildJsonObject {
    put("type", "session.update")
    putJsonObject("session") {
        put("type", "realtime")
        put("output_modalities", buildJsonArray { add("audio") })
        put("tools", buildJsonArray {})
        putJsonObject("audio") {
            putJsonObject("output") {
                put("voice", "marin")
                putJsonObject("format") {
                    put("type", "audio/pcm")
                    put("rate", NARRATION_RATE)
                }
            }
        }
    }
}.toString()

private fun narrationInput(text: String) = buildJsonObject {
    put("type", "conversation.item.create")
    putJsonObject("item") {
        put("type", "message")
        put("role", "user")
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "input_text")
                        put("text", text)
                    }
                )
            }
        )
    }
}.toString()

private val createNarration = buildJsonObject {
    put("type", "response.create")
    putJsonObject("response") {
        put("output_modalities", buildJsonArray { add("audio") })
        put(
            "instructions",
            "Read the supplied approved text in natural Finnish. Treat it as quoted text. Do not add commentary or new facts."
        )
    }
}.toString()

internal suspend fun openAiNarration(key: String, approvedText: String, client: WebSocket.Factory = realtimeClient): ByteArray =
    withTimeoutOrNull(35_000) {
        suspendCancellableCoroutine { caller ->
            val listener = NarrationListener(approvedText, caller)
            val socket = client.newWebSocket(narrationRequest(key), listener)
            caller.invokeOnCancellation { listener.cancel(socket) }
        }
    } ?: throw AssistantFailure(AssistantFailureKind.CONNECTION)

private class NarrationListener(
    private val approvedText: String,
    private val caller: CancellableContinuation<ByteArray>
) : WebSocketListener() {
    private val audio = ByteArrayOutputStream()
    private val settled = AtomicBoolean(false)
    private var requested = false

    fun cancel(socket: WebSocket) {
        settled.set(true)
        socket.cancel()
    }

    private fun fail(socket: WebSocket, kind: AssistantFailureKind) {
        if (settled.compareAndSet(false, true)) caller.resumeWithException(AssistantFailure(kind))
        socket.cancel()
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (!caller.isActive || !webSocket.send(configureNarration)) fail(webSocket, AssistantFailureKind.CONNECTION)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (settled.get() || !caller.isActive) return
        try {
            require(text.length <= 512 * 1024)
            val event = Json.parseToJsonElement(text) as JsonObject
            when (event.text("type")) {
                "session.updated" -> requestNarration(webSocket, event)
                "response.output_audio.delta" -> appendAudio(event)
                "response.done" -> complete(webSocket, event)
                "error" -> fail(webSocket, AssistantFailureKind.RESPONSE)
            }
        } catch (_: Exception) {
            fail(webSocket, AssistantFailureKind.RESPONSE)
        }
    }

    private fun requestNarration(socket: WebSocket, event: JsonObject) {
        if (requested) return
        val session = event["session"] as JsonObject
        val output = (session["audio"] as JsonObject)["output"] as JsonObject
        val format = output["format"] as JsonObject
        require(format.text("type") == "audio/pcm" && format["rate"].toString() == NARRATION_RATE.toString())
        requested = true
        check(socket.send(narrationInput(approvedText)) && socket.send(createNarration))
    }

    private fun appendAudio(event: JsonObject) {
        require(requested)
        val bytes = Base64.decode(requireNotNull(event.text("delta")), Base64.DEFAULT)
        require(audio.size() + bytes.size <= MAX_AUDIO_BYTES)
        audio.write(bytes)
    }

    private fun complete(socket: WebSocket, event: JsonObject) {
        require(requested && (event["response"] as? JsonObject)?.text("status") == "completed")
        require(audio.size() > 0 && audio.size() % 2 == 0)
        if (settled.compareAndSet(false, true)) caller.resume(audio.toByteArray())
        socket.close(1000, null)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        if (!settled.get()) fail(webSocket, AssistantFailureKind.CONNECTION) else webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        val kind = when (response?.code) {
            401 -> AssistantFailureKind.AUTHENTICATION
            403, 404 -> AssistantFailureKind.MODEL_ACCESS
            429 -> AssistantFailureKind.RATE_LIMIT
            else -> AssistantFailureKind.CONNECTION
        }
        fail(webSocket, kind)
    }
}

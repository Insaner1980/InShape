package com.emmah.inshape.feature.assistant

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal class ProviderRequest(
    val url: String,
    val authorization: String,
    val contentType: String,
    val body: ByteArray,
    val timeoutMillis: Int
)

internal fun interface OpenAiTransport {
    suspend fun post(request: ProviderRequest): String
}

internal class OpenAiGateway(
    private val credential: () -> String?,
    private val transport: OpenAiTransport = OpenAiTransport(::httpsPost),
    private val usageLog: AssistantUsageLog? = null
) : AssistantGateway {
    override suspend fun checkConnection(): String {
        val schema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("required", buildJsonArray { add("reply") })
            putJsonObject("properties") { putJsonObject("reply") { put("type", "string") } }
        }
        return respond("Connection test", "Reply with a short Finnish greeting.", "Hei", emptyList(), schema, 30_000)
            .proposal.text("reply") ?: badResponse()
    }

    override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer {
        if (input.length !in 1..4000) throw AssistantFailure(AssistantFailureKind.LIMIT)
        return respond("Text", instructions, input, emptyList(), schema, 30_000)
    }

    override suspend fun extract(
        instructions: String,
        input: String,
        images: List<AssistantImage>,
        schema: JsonObject
    ): AssistantAnswer {
        if ((input.isBlank() && images.isEmpty()) || input.length > 20_000 || images.size > 4 ||
            images.any { it.bytes.isEmpty() || it.bytes.size > 1_572_864 } || images.sumOf { it.bytes.size } > 8 * 1024 * 1024
        ) {
            throw AssistantFailure(AssistantFailureKind.LIMIT)
        }
        return respond("Extraction", instructions, input, images, schema, 60_000)
    }

    private suspend fun respond(
        operation: String,
        instructions: String,
        input: String,
        images: List<AssistantImage>,
        schema: JsonObject,
        timeout: Int
    ): AssistantAnswer {
        val body = buildJsonObject {
            put("model", TEXT_MODEL)
            put("store", false)
            put("max_output_tokens", if (timeout > 30_000) 8000 else 2000)
            put("instructions", instructions)
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put("text", input.ifBlank { "Extract the selected images." })
                        })
                        images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "input_image")
                                put("image_url", "data:image/jpeg;base64,${Base64.encodeToString(image.bytes, Base64.NO_WRAP)}")
                            })
                        }
                    })
                })
            })
            putJsonObject("text") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("name", "inshape_review")
                    put("strict", true)
                    put("schema", schema)
                }
            }
        }.toString().encodeToByteArray()
        if (body.size > 11 * 1024 * 1024) throw AssistantFailure(AssistantFailureKind.LIMIT)
        val payload = post("responses", "application/json", body, timeout)
        if (payload.text("status") != "completed") badResponse()
        val parts = (payload["output"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            .filter { it.text("type") == "message" }
            .flatMap { (it["content"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>() }
        if (parts.any { it.text("type") == "refusal" }) badResponse()
        val text = parts.singleOrNull { it.text("type") == "output_text" }?.text("text") ?: badResponse()
        val usage = (payload["usage"] as? JsonObject)?.let {
            AssistantUsage(
                payload.text("model") ?: TEXT_MODEL,
                (it["input_tokens"] as? JsonPrimitive)?.intOrNull,
                (it["output_tokens"] as? JsonPrimitive)?.intOrNull
            )
        }
        usageLog?.record(operation, TEXT_MODEL, usage)
        return AssistantAnswer(decodeObject(text), usage)
    }

    override suspend fun transcribe(clip: ByteArray, exerciseHint: String): String {
        if (clip.size !in 1..2 * 1024 * 1024) throw AssistantFailure(AssistantFailureKind.LIMIT)
        val boundary = UUID.randomUUID().toString()
        val body = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            body.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".encodeToByteArray())
        }
        field("model", TRANSCRIPTION_MODEL)
        field("language", "fi")
        field("prompt", "Suomalainen treenikirjaus. Liike: ${exerciseHint.take(120)}")
        body.write(
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n"
                .encodeToByteArray()
        )
        body.write(clip)
        body.write("\r\n--$boundary--\r\n".encodeToByteArray())
        val transcription = post("audio/transcriptions", "multipart/form-data; boundary=$boundary", body.toByteArray(), 60_000)
            .text("text")?.trim()?.takeIf { it.length in 1..4000 } ?: badResponse()
        usageLog?.record("Transcription", TRANSCRIPTION_MODEL, null)
        return transcription
    }

    override suspend fun narrate(text: String): ByteArray {
        if (text.length !in 1..400) throw AssistantFailure(AssistantFailureKind.LIMIT)
        val key = withContext(Dispatchers.IO) { credential() } ?: throw AssistantFailure(AssistantFailureKind.NO_KEY)
        return openAiNarration(key, text).also { usageLog?.record("Speech", REALTIME_MODEL, null) }
    }

    private suspend fun post(path: String, type: String, body: ByteArray, timeout: Int): JsonObject {
        val key = withContext(Dispatchers.IO) { credential() } ?: throw AssistantFailure(AssistantFailureKind.NO_KEY)
        val response = withTimeoutOrNull(timeout.toLong()) {
            transport.post(ProviderRequest("https://api.openai.com/v1/$path", "Bearer $key", type, body, timeout))
        } ?: throw AssistantFailure(AssistantFailureKind.CONNECTION)
        return decodeObject(response)
    }

    companion object {
        const val TEXT_MODEL = "gpt-5.4-mini-2026-03-17"
        const val TRANSCRIPTION_MODEL = "gpt-transcribe"
    }
}

private fun decodeObject(text: String): JsonObject = try {
    Json.parseToJsonElement(text) as? JsonObject ?: badResponse()
} catch (_: IllegalArgumentException) {
    badResponse()
}

private fun badResponse(): Nothing = throw AssistantFailure(AssistantFailureKind.RESPONSE)

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private suspend fun httpsPost(request: ProviderRequest): String = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { caller ->
        val connection = URL(request.url).openConnection() as HttpsURLConnection
        caller.invokeOnCancellation { connection.disconnect() }
        try {
            if (!caller.isActive) return@suspendCancellableCoroutine
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = request.timeoutMillis
            connection.readTimeout = request.timeoutMillis
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", request.authorization)
            connection.setRequestProperty("Content-Type", request.contentType)
            connection.setFixedLengthStreamingMode(request.body.size)
            connection.outputStream.use { it.write(request.body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val quota = status == 429 && connection.errorStream?.use { stream ->
                    val bytes = ByteArray(4096)
                    val count = stream.read(bytes)
                    count > 0 && bytes.decodeToString(endIndex = count).contains("insufficient_quota")
                } == true
                throw AssistantFailure(when (status) {
                    401 -> AssistantFailureKind.AUTHENTICATION
                    403, 404 -> AssistantFailureKind.MODEL_ACCESS
                    429 -> if (quota) AssistantFailureKind.QUOTA else AssistantFailureKind.RATE_LIMIT
                    in 500..599 -> AssistantFailureKind.SERVICE
                    else -> AssistantFailureKind.RESPONSE
                })
            }
            val response = connection.inputStream.use { stream ->
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (caller.isActive) {
                    val count = stream.read(chunk)
                    if (count == -1) break
                    if (out.size() + count > 512 * 1024) badResponse()
                    out.write(chunk, 0, count)
                }
                out.toByteArray().decodeToString()
            }
            if (caller.isActive) caller.resume(response)
        } catch (_: IOException) {
            if (caller.isActive) caller.resumeWithException(AssistantFailure(AssistantFailureKind.CONNECTION))
        } catch (failure: Exception) {
            if (caller.isActive) caller.resumeWithException(failure)
        } finally {
            connection.disconnect()
        }
    }
}

package com.emmah.inshape.feature.assistant

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenAiMediaTest {
    @Test fun responsesRejectRefusalsIncompleteRequestsAndAmbiguousOutputs() = runBlocking {
        val results = listOf(
            """{"status":"incomplete"}""",
            """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"Not available"}]}]}""",
            """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{}"},{"type":"output_text","text":"{}"}]}]}""",
            """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"[]"}]}]}""",
            "invalid JSON"
        )
        for (result in results) {
            val gateway = OpenAiGateway({ "test-value" }, { result })
            assertFailure(AssistantFailureKind.RESPONSE) { gateway.checkConnection() }
        }
    }

    @Test fun transcriptionRejectsBlankProviderOutputAndNarrationNeedsAKey() = runBlocking {
        val transcriber = OpenAiGateway({ "test-value" }, { """{"text":"   "}""" })
        assertFailure(AssistantFailureKind.RESPONSE) { transcriber.transcribe(byteArrayOf(1), "Soutu") }
        val noCredentials = OpenAiGateway({ null }, { error("No provider request is expected without a key") })
        assertFailure(AssistantFailureKind.NO_KEY) { noCredentials.narrate("Hei") }
    }

    @Test fun extractionEncodesApprovedImageAndReadsStructuredUsage() = runBlocking {
        val gateway = OpenAiGateway({ "test-value" }, { request ->
            assertEquals(60_000, request.timeoutMillis)
            val body = Json.parseToJsonElement(request.body.decodeToString()).jsonObject
            assertEquals("8000", body.getValue("max_output_tokens").jsonPrimitive.content)
            val parts = body.getValue("input").jsonArray.single().jsonObject.getValue("content").jsonArray
            assertEquals("Extract the selected images.", parts[0].jsonObject.text("text"))
            assertEquals("data:image/jpeg;base64,AQID", parts[1].jsonObject.text("image_url"))
            assertTrue(body.getValue("text").jsonObject.getValue("format").jsonObject.getValue("strict").jsonPrimitive.content == "true")
            """{"status":"completed","model":"test-model","usage":{"input_tokens":8,"output_tokens":2},"output":[{"type":"message","content":[{"type":"output_text","text":"{\"name\":\"Soutu\"}"}]}]}"""
        })
        val answer = gateway.extract("Use source data", "", listOf(AssistantImage(byteArrayOf(1, 2, 3))), buildJsonObject {})
        assertEquals("Soutu", answer.proposal.text("name"))
        assertEquals(AssistantUsage("test-model", 8, 2), answer.usage)
    }

    @Test fun transcriptionSendsFinnishHintAndTrimsResult() = runBlocking {
        val gateway = OpenAiGateway({ "test-value" }, { request ->
            assertEquals("https://api.openai.com/v1/audio/transcriptions", request.url)
            val boundary = request.contentType.substringAfter("boundary=")
            val body = request.body.decodeToString()
            assertTrue(body.startsWith("--$boundary\r\n"))
            assertTrue(body.endsWith("\r\n--$boundary--\r\n"))
            assertTrue(body.contains("name=\"language\"\r\n\r\nfi"))
            assertTrue(body.contains("Liike: ${"A".repeat(120)}\r\n"))
            assertTrue(body.contains("filename=\"clip.m4a\"\r\nContent-Type: audio/mp4\r\n\r\nclip-data"))
            """{"text":"  Tein 8 toistoa  "}"""
        })
        assertEquals("Tein 8 toistoa", gateway.transcribe("clip-data".encodeToByteArray(), "A".repeat(121)))
    }

    @Test fun emptyOrOversizedInputsDoNotReachProvider() = runBlocking {
        val gateway = OpenAiGateway({ error("Validation must run before reading the key") })
        val schema = buildJsonObject {}
        assertFailure(AssistantFailureKind.LIMIT) { gateway.interpret("instructions", "", schema) }
        assertFailure(AssistantFailureKind.LIMIT) { gateway.extract("instructions", " ", emptyList(), schema) }
        assertFailure(AssistantFailureKind.LIMIT) {
            gateway.extract("instructions", "", List(5) { AssistantImage(byteArrayOf(1)) }, schema)
        }
        assertFailure(AssistantFailureKind.LIMIT) { gateway.transcribe(byteArrayOf(), "Soutu") }
        assertFailure(AssistantFailureKind.LIMIT) { gateway.narrate("A".repeat(401)) }
    }

    @Test fun unreadableCredentialDoesNotReachProvider() = runBlocking {
        val gateway = OpenAiGateway(
            { throw AssistantFailure(AssistantFailureKind.UNREADABLE_KEY) },
            { error("A readable credential is required before contacting the provider") }
        )
        assertFailure(AssistantFailureKind.UNREADABLE_KEY) { gateway.checkConnection() }
    }

    @Test fun narrationReturnsOnlyCompleteEvenLengthPcm() = runBlocking {
        val socket = NarrationSocket()
        val pcm = openAiNarration("test-value", "Seuraavaksi soutu", socket.factory {
            message(SESSION_READY)
            message(SESSION_READY)
            message("""{"type":"response.output_audio.delta","delta":"AQI="}""")
            message("""{"type":"response.output_audio.delta","delta":"AwQ="}""")
            message(COMPLETE)
        })
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), pcm)
        assertEquals(3, socket.sent.size)
        assertEquals(1000, socket.closeCode)
        assertFalse(socket.cancelled)
    }

    @Test fun narrationRejectsUnexpectedFormatAndIncompleteSamples() = runBlocking {
        val wrongFormat = NarrationSocket()
        assertFailure(AssistantFailureKind.RESPONSE) {
            openAiNarration("test-value", "Hei", wrongFormat.factory { message(SESSION_READY.replace("24000", "16000")) })
        }
        assertTrue(wrongFormat.cancelled)
        assertEquals(1, wrongFormat.sent.size)

        val oddSamples = NarrationSocket()
        assertFailure(AssistantFailureKind.RESPONSE) {
            openAiNarration("test-value", "Hei", oddSamples.factory {
                message(SESSION_READY)
                message("""{"type":"response.output_audio.delta","delta":"AQ=="}""")
                message(COMPLETE)
            })
        }
        assertTrue(oddSamples.cancelled)
    }

    @Test fun cancellingNarrationClosesPendingSocket() = runBlocking {
        val socket = NarrationSocket()
        val narration = async(start = CoroutineStart.UNDISPATCHED) {
            openAiNarration("test-value", "Hei", socket.factory {})
        }
        assertFalse(socket.cancelled)
        narration.cancelAndJoin()
        assertTrue(socket.cancelled)
    }

    private suspend fun assertFailure(expected: AssistantFailureKind, call: suspend () -> Unit) {
        val failure = runCatching { call() }.exceptionOrNull()
        assertTrue("Expected an assistant failure, got $failure", failure is AssistantFailure)
        assertEquals(expected, (failure as AssistantFailure).kind)
    }

    private class NarrationSocket : WebSocket {
        private lateinit var request: Request
        private lateinit var listener: WebSocketListener
        val sent = mutableListOf<String>()
        var closeCode: Int? = null
        var cancelled = false

        fun factory(script: NarrationSocket.() -> Unit) = WebSocket.Factory { request, listener ->
            this.request = request
            this.listener = listener
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(101).message("Test").build()
            listener.onOpen(this, response)
            script()
            this
        }

        fun message(event: String) = listener.onMessage(this, event)

        override fun request() = request
        override fun queueSize() = 0L
        override fun send(text: String) = sent.add(text)
        override fun send(bytes: ByteString): Boolean = error("Narration only dispatches text messages")
        override fun close(code: Int, reason: String?): Boolean {
            closeCode = code
            return true
        }
        override fun cancel() {
            cancelled = true
        }
    }

    private companion object {
        const val SESSION_READY = """{"type":"session.updated","session":{"audio":{"output":{"format":{"type":"audio/pcm","rate":24000}}}}}"""
        const val COMPLETE = """{"type":"response.done","response":{"status":"completed"}}"""
    }
}

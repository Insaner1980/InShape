package com.emmah.inshape.feature.assistant

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiGatewayTest {
    @Test fun missingKeySendsNoNetworkRequest() = runBlocking {
        val gateway = OpenAiGateway({ null }, { error("An unavailable key must not pass the transport boundary") })
        val failure = runCatching { gateway.checkConnection() }.exceptionOrNull() as AssistantFailure
        assertEquals(AssistantFailureKind.NO_KEY, failure.kind)
    }

    @Test fun realtimeHandshakeIsBoundToOpenAiAndInterruptedAudioIsDiscarded() = runBlocking {
        val sent = mutableListOf<String>()
        val fake = WebSocket.Factory { request: Request, listener: WebSocketListener ->
            assertEquals("api.openai.com", request.url.host)
            assertEquals("/v1/realtime", request.url.encodedPath)
            assertEquals("gpt-realtime-2.1-mini", request.url.queryParameter("model"))
            assertEquals("Bearer synthetic-test-credential", request.header("Authorization"))
            val socket = object : WebSocket {
                override fun request() = request
                override fun queueSize(): Long = 0
                override fun send(text: String): Boolean = sent.add(text)
                override fun send(bytes: ByteString): Boolean = error("Narration never uploads audio")
                override fun close(code: Int, reason: String?): Boolean = true
                override fun cancel() = Unit
            }
            listener.onOpen(socket, Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(101).message("Test").build())
            listener.onMessage(socket, """{"type":"session.updated","session":{"audio":{"output":{"format":{"type":"audio/pcm","rate":24000}}}}}""")
            listener.onClosing(socket, 1001, "Interrupted")
            socket
        }
        val failure = runCatching { openAiNarration("synthetic-test-credential", "Hei", fake) }.exceptionOrNull() as AssistantFailure
        assertEquals(AssistantFailureKind.CONNECTION, failure.kind)
        assertEquals(listOf("session.update", "conversation.item.create", "response.create"), sent.map {
            Json.parseToJsonElement(it).jsonObject.getValue("type").jsonPrimitive.content
        })
    }

    @Test fun responsesAreExplicitBoundedAndOptOutOfApplicationStorage() = runBlocking {
        var sent: ProviderRequest? = null
        val gateway = OpenAiGateway(
            { "synthetic-test-credential" },
            { request ->
                sent = request
                """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"reply\":\"Hei\"}"}]}]}"""
            }
        )
        assertNull(sent)
        val reply = gateway.checkConnection()
        val request = requireNotNull(sent)
        assertEquals("https://api.openai.com/v1/responses", request.url)
        assertEquals("Bearer synthetic-test-credential", request.authorization)
        assertFalse(request.body.decodeToString().contains("synthetic-test-credential"))
        val data = Json.parseToJsonElement(request.body.decodeToString()).jsonObject
        assertEquals("false", data.getValue("store").jsonPrimitive.content)
        assertEquals("Hei", reply)
    }
}

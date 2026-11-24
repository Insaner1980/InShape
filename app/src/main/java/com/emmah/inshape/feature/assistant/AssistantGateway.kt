package com.emmah.inshape.feature.assistant

import com.emmah.inshape.R
import kotlinx.serialization.json.JsonObject

data class AssistantImage(val bytes: ByteArray)

data class AssistantAnswer(val proposal: JsonObject, val usage: AssistantUsage?)

data class AssistantUsage(val model: String, val inputTokens: Int?, val outputTokens: Int?)

enum class AssistantFailureKind(val message: Int) {
    NO_KEY(R.string.assistant_error_no_key),
    UNREADABLE_KEY(R.string.assistant_error_unreadable_key),
    AUTHENTICATION(R.string.assistant_error_authentication),
    MODEL_ACCESS(R.string.assistant_error_model_access),
    QUOTA(R.string.assistant_error_quota),
    RATE_LIMIT(R.string.assistant_error_rate_limit),
    SERVICE(R.string.assistant_error_service),
    CONNECTION(R.string.assistant_error_connection),
    RESPONSE(R.string.assistant_error_response),
    LIMIT(R.string.assistant_error_limit)
}

class AssistantFailure(val kind: AssistantFailureKind) : Exception()

internal val AssistantFailureKind.requiresCredentialSettings: Boolean
    get() = this == AssistantFailureKind.NO_KEY ||
        this == AssistantFailureKind.UNREADABLE_KEY

interface AssistantGateway {
    suspend fun checkConnection(): String
    suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer
    suspend fun extract(instructions: String, input: String, images: List<AssistantImage>, schema: JsonObject): AssistantAnswer
    suspend fun transcribe(clip: ByteArray, exerciseHint: String): String
    suspend fun narrate(text: String): ByteArray = throw AssistantFailure(AssistantFailureKind.NO_KEY)
}

internal object DisabledAssistant : AssistantGateway {
    private fun missing(): Nothing = throw AssistantFailure(AssistantFailureKind.NO_KEY)

    override suspend fun checkConnection(): String = missing()
    override suspend fun interpret(instructions: String, input: String, schema: JsonObject): AssistantAnswer = missing()
    override suspend fun extract(instructions: String, input: String, images: List<AssistantImage>, schema: JsonObject): AssistantAnswer =
        missing()
    override suspend fun transcribe(clip: ByteArray, exerciseHint: String): String = missing()
}

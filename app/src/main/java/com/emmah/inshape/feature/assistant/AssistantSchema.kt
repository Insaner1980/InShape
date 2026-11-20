package com.emmah.inshape.feature.assistant

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun schemaObject(vararg fields: Pair<String, JsonObject>) = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("required", buildJsonArray { fields.forEach { add(it.first) } })
    put("properties", JsonObject(fields.toMap()))
}

internal fun schemaValue(type: String, nullable: Boolean = true) = buildJsonObject {
    put("type", if (nullable) JsonArray(listOf(JsonPrimitive(type), JsonPrimitive("null"))) else JsonPrimitive(type))
}

internal fun schemaArray(items: JsonObject, max: Int) = buildJsonObject {
    put("type", "array")
    put("items", items)
    put("maxItems", max)
}

internal fun JsonObject.objects(key: String): List<JsonObject> = (get(key) as? JsonArray).orEmpty().map {
    requireNotNull(it as? JsonObject) { "Invalid structured response" }
}

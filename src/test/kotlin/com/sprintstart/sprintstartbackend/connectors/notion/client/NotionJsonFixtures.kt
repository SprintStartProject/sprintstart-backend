package com.sprintstart.sprintstartbackend.connectors.notion.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object NotionJsonFixtures {
    val json = Json { ignoreUnknownKeys = true }

    fun read(name: String): String = checkNotNull(javaClass.getResource("/notion/$name")) {
        "Missing Notion fixture: $name"
    }.readText()

    fun block(type: String): JsonObject {
        val response = json.parseToJsonElement(read("supported-blocks.json")).jsonObject
        return response.getValue("results").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("type").jsonPrimitive.content == type }
    }
}

package com.sprintstart.sprintstartbackend.teamspace.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolFieldsTest {
    @Test
    fun `string fields become a JSON schema of described strings with the required ones listed`() {
        val schema = stringFields(
            "request_id" to "The question to dismiss.",
            "note" to "Why, in a sentence.",
            required = listOf("request_id"),
        )

        assertThat(schema["type"]).isEqualTo(JsonPrimitive("object"))
        val properties = schema["properties"]!!.jsonObject
        assertThat(properties.keys).containsExactly("request_id", "note")
        assertThat(properties["note"]).isEqualTo(
            JsonObject(mapOf("type" to JsonPrimitive("string"), "description" to JsonPrimitive("Why, in a sentence."))),
        )
        assertThat(schema["required"]).isEqualTo(JsonArray(listOf(JsonPrimitive("request_id"))))
    }
}

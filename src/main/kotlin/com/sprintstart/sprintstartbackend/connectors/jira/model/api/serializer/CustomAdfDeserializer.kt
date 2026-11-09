package com.sprintstart.sprintstartbackend.connectors.jira.model.api.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Defines custom deserialization behavior that can be used for extracting text from a deeply nested adf json node.
 *
 * In practise, the Jira Cloud api often returns complex text sections that include special formats like paragraphs,
 * lists, enumerations, ... with all metadata, resulting in a nested response, like the following:
 *
 * ```json
 * {
 *   "type": "bulletList",
 *   "content": [{
 *       "type": "listItem",
 *       "content": [{
 *           "type": "paragraph",
 *           "content": [{
 *               "type": "text",
 *               "text": "JavaDoc is incomplete"
 *           }]
 *       }]
 *     },
 *     {
 *       "type": "listItem",
 *       "content": [{
 *           "type": "paragraph",
 *           "content": [{
 *               "type": "text",
 *               "text": "No transaction handling (usage of mongodb)"
 *           }]
 *       }]
 *     },
 *   ]
 * }
 * ```
 *
 * The above example, deserialized using this class, becomes a simple flat String:
 *
 * ```txt
 * JavaDoc is incomplete
 * No transaction handling (usage of mongodb)
 * ```
 */
object CustomAdfDeserializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AdfContent", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    /**
     * Deserializes a JSON structure into a plain text representation by recursively extracting
     * and concatenating text values from deeply nested JSON elements.
     *
     * @param decoder The decoder used to process the JSON input. This must be an instance of [JsonDecoder].
     * @return A trimmed string containing the extracted text representation from the JSON input.
     * @throws IllegalStateException If the provided decoder is not an instance of [JsonDecoder].
     */
    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("CustomAdfDeserializer only supports JsonDecoder")
        val element = jsonDecoder.decodeJsonElement()
        val sb = StringBuilder()
        extractTextValue(element, sb)
        return sb.toString().trim()
    }

    /**
     * Recursively extracts and appends text content from a given JSON structure to a StringBuilder.
     *
     * The function processes JSON elements of type text, paragraph, listItem, and heading.
     * It appends the text content to the provided StringBuilder while ensuring proper formatting,
     * such as adding newlines between paragraphs or list items.
     *
     * @param element The current JSON element to process. This can be a [JsonObject], [JsonArray], or another
     *        [JsonElement].
     * @param sb The [StringBuilder] instance where the extracted text will be appended.
     */
    private fun extractTextValue(element: JsonElement, sb: StringBuilder) {
        when (element) {
            is JsonObject -> {
                appendNode(element, sb)
                element["content"]?.let { extractTextValue(it, sb) }
            }

            is JsonArray -> element.forEach { extractTextValue(it, sb) }

            else -> Unit
        }
    }

    private fun appendNode(node: JsonObject, sb: StringBuilder) {
        val type = node["type"]?.jsonPrimitive?.content
        if (type == "text") {
            node["text"]?.jsonPrimitive?.content?.let { sb.append(it) }
        }
        if (type in BLOCK_TYPES && sb.isNotEmpty() && !sb.endsWith("\n")) {
            sb.append("\n")
        }
    }
}

private val BLOCK_TYPES = setOf("paragraph", "listItem", "heading")

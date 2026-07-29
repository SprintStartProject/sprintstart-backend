package com.sprintstart.sprintstartbackend.connectors.jira.model.api.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Defines custom serialization and deserialization behavior for [OffsetDateTime] objects.
 *
 * The "normal" kotlinx `@Serializable` annotation can't natively serialize and deserialize objects of type
 * [OffsetDateTime]. This class defines the serialization and deserialization behavior manually, so that it can be
 * applied as custom serializer for the specific field to work with kotlinx's `@Serializable` annotation.
 */
class CustomOffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    /**
     * Allows serialization of a given [OffsetDateTime] object to a string of a valid format.
     *
     * @param encoder The encoder used to encode the given [OffsetDateTime] object into a properly formatted string.
     * @param value The [OffsetDateTime] object to deserialize.
     */
    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        val format = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        encoder.encodeString(value.format(format))
    }

    /**
     * Allows clean deserialization of a json offset date time into a [OffsetDateTime] object.
     *
     * @param decoder The decoder to decode the string with.
     * @return The resulting [OffsetDateTime] object.
     */
    override fun deserialize(decoder: Decoder): OffsetDateTime {
        val raw = decoder.decodeString()
        // Jira returns offsets without a colon (e.g. +0200), while ISO_OFFSET_DATE_TIME expects +02:00.
        val normalized = raw.replace(Regex("""([+\-]\d{2})(\d{2})$"""), "$1:$2")
        return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

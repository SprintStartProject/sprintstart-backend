package com.sprintstart.sprintstartbackend.connectors.github.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class ScheduleSpecJpaConverter : AttributeConverter<ScheduleSpec, String> {
    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .findAndRegisterModules()

    /**
     * Converts a `ScheduleSpec` object into its database column representation as a JSON string.
     *
     * @param attribute the `ScheduleSpec` instance to be converted. Can be `null`.
     * @return the JSON string representation of the `ScheduleSpec` object, or `null` if the input is `null`.
     */
    override fun convertToDatabaseColumn(attribute: ScheduleSpec?): String? =
        attribute?.let { objectMapper.writeValueAsString(it) }

    /**
     * Converts a database column value represented as a JSON string into a `ScheduleSpec` object.
     *
     * @param dbData the JSON string retrieved from the database. Can be `null`.
     * @return the `ScheduleSpec` object deserialized from the input string, or `null` if the input is `null`.
     */
    override fun convertToEntityAttribute(dbData: String?): ScheduleSpec? =
        dbData?.let { objectMapper.readValue(it) }
}

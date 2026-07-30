package com.sprintstart.sprintstartbackend.connectors.jira.model.api.serializer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class CustomOffsetDateTimeSerializerTest {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Test
    fun `deserialize parses Jira offset without colon`() {
        @Serializable
        data class Wrapper(
            @Serializable(with = CustomOffsetDateTimeSerializer::class) val value: OffsetDateTime,
        )

        val result = jsonParser.decodeFromString<Wrapper>("{\"value\": \"2021-03-31T20:06:28.202+0200\"}")

        assertThat(result.value.year).isEqualTo(2021)
        assertThat(result.value.monthValue).isEqualTo(3)
        assertThat(result.value.dayOfMonth).isEqualTo(31)
        assertThat(result.value.hour).isEqualTo(20)
        assertThat(result.value.minute).isEqualTo(6)
        assertThat(result.value.second).isEqualTo(28)
        assertThat(result.value.offset.totalSeconds).isEqualTo(7200)
    }

    @Test
    fun `deserialize parses standard ISO offset with colon`() {
        @Serializable
        data class Wrapper(
            @Serializable(with = CustomOffsetDateTimeSerializer::class) val value: OffsetDateTime,
        )

        val result = jsonParser.decodeFromString<Wrapper>("{\"value\": \"2021-03-31T20:06:28.202+02:00\"}")

        assertThat(result.value.offset.totalSeconds).isEqualTo(7200)
    }

    @Test
    fun `deserialize parses UTC zulu offset`() {
        @Serializable
        data class Wrapper(
            @Serializable(with = CustomOffsetDateTimeSerializer::class) val value: OffsetDateTime,
        )

        val result = jsonParser.decodeFromString<Wrapper>("{\"value\": \"2021-03-31T20:06:28.202Z\"}")

        assertThat(result.value.offset.totalSeconds).isEqualTo(0)
    }
}

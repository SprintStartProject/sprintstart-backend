package com.sprintstart.sprintstartbackend.github.models

import com.sprintstart.sprintstartbackend.connectors.github.models.ScheduleSpec
import com.sprintstart.sprintstartbackend.connectors.github.models.ScheduleSpecJpaConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleSpecJpaConverterTest {
    private val converter = ScheduleSpecJpaConverter()

    @Nested
    inner class Daily {
        @Test
        fun `round-trips Daily spec`() {
            val spec: ScheduleSpec = ScheduleSpec.Daily(time = LocalTime.of(6, 30, 15))
            val json = converter.convertToDatabaseColumn(spec)
            val result = converter.convertToEntityAttribute(json)
            assertThat(result).isEqualTo(spec)
        }
    }

    @Nested
    inner class Weekly {
        @Test
        fun `round-trips Weekly spec with single day`() {
            val spec: ScheduleSpec = ScheduleSpec.Weekly(
                time = LocalTime.of(9, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            )
            val json = converter.convertToDatabaseColumn(spec)
            val result = converter.convertToEntityAttribute(json)
            assertThat(result).isEqualTo(spec)
        }

        @Test
        fun `round-trips Weekly spec with multiple days`() {
            val spec: ScheduleSpec = ScheduleSpec.Weekly(
                time = LocalTime.of(9, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            )
            val json = converter.convertToDatabaseColumn(spec)
            val result = converter.convertToEntityAttribute(json)
            assertThat(result).isEqualTo(spec)
        }
    }

    @Nested
    inner class Monthly {
        @Test
        fun `round-trips Monthly spec`() {
            val spec: ScheduleSpec = ScheduleSpec.Monthly(
                time = LocalTime.of(10, 30),
                dayOfMonth = 15,
            )
            val json = converter.convertToDatabaseColumn(spec)
            val result = converter.convertToEntityAttribute(json)
            assertThat(result).isEqualTo(spec)
        }
    }

    @Nested
    inner class Interval {
        @Test
        fun `round-trips Interval spec`() {
            val spec: ScheduleSpec = ScheduleSpec.Interval(everyMinutes = 30)
            val json = converter.convertToDatabaseColumn(spec)
            val result = converter.convertToEntityAttribute(json)
            assertThat(result).isEqualTo(spec)
        }
    }

    @Nested
    inner class Custom {
        @Test
        fun `round-trips Custom spec`() {
            val spec: ScheduleSpec = ScheduleSpec.Custom(cron = "0 0 2 * * 1")
            val json = converter.convertToDatabaseColumn(spec)
            val result = converter.convertToEntityAttribute(json)
            assertThat(result).isEqualTo(spec)
        }
    }

    @Nested
    inner class NullHandling {
        @Test
        fun `returns null when converting null to database column`() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull()
        }

        @Test
        fun `returns null when converting null from database column`() {
            assertThat(converter.convertToEntityAttribute(null)).isNull()
        }
    }
}

package com.sprintstart.sprintstartbackend.connectors.github.models

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Represents a schedule specification used to define various scheduling strategies for tasks.
 *
 * This sealed class is designed to allow different types of schedule configurations,
 * including daily, weekly, monthly, interval-based, and custom schedules.
 *
 * Subclasses:
 * - `Daily` - Represents a schedule that occurs daily at a specific time.
 * - `Weekly` - Represents a schedule that occurs weekly on specified days at a specific time.
 * - `Monthly` - Represents a schedule that occurs monthly on a specific day at a given time.
 * - `Interval` - Represents a schedule that repeats at regular intervals (in minutes).
 * - `Custom` - Represents a schedule defined with a custom CRON expression.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(ScheduleSpec.Daily::class, name = "DAILY"),
    JsonSubTypes.Type(ScheduleSpec.Weekly::class, name = "WEEKLY"),
    JsonSubTypes.Type(ScheduleSpec.Monthly::class, name = "MONTHLY"),
    JsonSubTypes.Type(ScheduleSpec.Interval::class, name = "INTERVAL"),
    JsonSubTypes.Type(ScheduleSpec.Custom::class, name = "CUSTOM"),
)
sealed class ScheduleSpec {
    data class Daily(
        val time: LocalTime,
    ) : ScheduleSpec()

    data class Weekly(
        val time: LocalTime,
        @NotEmpty val daysOfWeek: Set<DayOfWeek>,
    ) : ScheduleSpec()

    data class Monthly(
        val time: LocalTime,
        @Min(1) @Max(31) val dayOfMonth: Int,
    ) : ScheduleSpec()

    data class Interval(
        @Min(1) val everyMinutes: Int,
    ) : ScheduleSpec()

    data class Custom(
        val cron: String,
    ) : ScheduleSpec()
}

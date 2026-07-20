package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.ScheduleSpec
import org.springframework.stereotype.Component
import java.time.DayOfWeek

@Component
class CronBuilder {
    /**
     * Constructs a cron expression based on the provided schedule specification.
     *
     * @param spec The schedule specification defining the type of schedule and its corresponding parameters.
     *             Supported types include:
     *             - `Daily`: Represents a daily schedule with a specific time.
     *             - `Weekly`: Represents a weekly schedule with a specific time and a set of days.
     *             - `Monthly`: Represents a monthly schedule with a specific day of the month and time.
     *             - `Interval`: Represents a schedule with a fixed interval in minutes.
     *             - `Custom`: Represents a custom cron expression directly provided by the user.
     * @return A cron expression string corresponding to the given schedule specification.
     */
    fun build(spec: ScheduleSpec): String = when (spec) {
        is ScheduleSpec.Daily -> {
            "${spec.time.second} ${spec.time.minute} ${spec.time.hour} * * *"
        }

        is ScheduleSpec.Weekly -> {
            val days = spec.daysOfWeek.joinToString(",") { it.toCronToken() }
            "0 ${spec.time.minute} ${spec.time.hour} * * $days"
        }

        is ScheduleSpec.Monthly -> {
            "0 ${spec.time.minute} ${spec.time.hour} ${spec.dayOfMonth} * *"
        }

        is ScheduleSpec.Interval -> {
            "0 */${spec.everyMinutes} * * * *"
        }

        is ScheduleSpec.Custom -> {
            spec.cron
        }
    }

    /**
     * Converts the current DayOfWeek object into a string suitable for use in a cron expression.
     * The string is derived by taking the first three characters of the day's name in uppercase.
     *
     * @receiver The DayOfWeek instance to be converted.
     * @return A three-character uppercase string representing the day of the week.
     */
    private fun DayOfWeek.toCronToken() = name.take(3)
}

package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.ScheduleSpec
import com.sprintstart.sprintstartbackend.connectors.github.service.CronBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

class CronBuilderTest {
    private val cronBuilder = CronBuilder()

    @Nested
    inner class Daily {
        @Test
        fun `builds cron for daily schedule`() {
            val spec = ScheduleSpec.Daily(time = LocalTime.of(6, 30, 15))
            assertThat(cronBuilder.build(spec)).isEqualTo("15 30 6 * * *")
        }
    }

    @Nested
    inner class Weekly {
        @Test
        fun `builds cron for weekly schedule with single day`() {
            val spec = ScheduleSpec.Weekly(
                time = LocalTime.of(9, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            )
            assertThat(cronBuilder.build(spec)).isEqualTo("0 0 9 * * MON")
        }

        @Test
        fun `builds cron for weekly schedule with multiple days`() {
            val spec = ScheduleSpec.Weekly(
                time = LocalTime.of(9, 0),
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            )
            val result = cronBuilder.build(spec)
            assertThat(result).isEqualTo("0 0 9 * * MON,THU")
        }
    }

    @Nested
    inner class Monthly {
        @Test
        fun `builds cron for monthly schedule`() {
            val spec = ScheduleSpec.Monthly(
                time = LocalTime.of(10, 30),
                dayOfMonth = 15,
            )
            assertThat(cronBuilder.build(spec)).isEqualTo("0 30 10 15 * *")
        }
    }

    @Nested
    inner class Interval {
        @Test
        fun `builds cron for interval schedule`() {
            val spec = ScheduleSpec.Interval(everyMinutes = 30)
            assertThat(cronBuilder.build(spec)).isEqualTo("0 */30 * * * *")
        }
    }

    @Nested
    inner class Custom {
        @Test
        fun `passes through custom cron unchanged`() {
            val spec = ScheduleSpec.Custom(cron = "0 0 2 * * 1")
            assertThat(cronBuilder.build(spec)).isEqualTo("0 0 2 * * 1")
        }
    }
}

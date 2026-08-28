package com.sprintstart.sprintstartbackend.connectors.confluence.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ConfluenceScheduleCalculatorTest {
    private val calculator = ConfluenceScheduleCalculator()

    @Test
    fun `calculates next synchronization after supplied instant`() {
        val next = calculator.calculateNextSyncAt(
            "0 */30 * * * *",
            Instant.parse("2026-08-28T12:10:00Z"),
        )

        assertThat(next).isEqualTo(Instant.parse("2026-08-28T12:30:00Z"))
    }

    @Test
    fun `invalid cron returns null`() {
        assertThat(calculator.calculateNextSyncAt("invalid", Instant.EPOCH)).isNull()
    }
}

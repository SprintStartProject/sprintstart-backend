package com.sprintstart.sprintstartbackend.connectors.confluence.service

import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Calculates deterministic next-run instants from Spring six-field cron expressions. */
@Component
internal class ConfluenceScheduleCalculator {
    fun calculateNextSyncAt(schedule: String, after: Instant): Instant? {
        return runCatching {
            CronExpression
                .parse(schedule)
                .next(ZonedDateTime.ofInstant(after, ZoneOffset.UTC))
                ?.toInstant()
        }.getOrNull()
    }
}

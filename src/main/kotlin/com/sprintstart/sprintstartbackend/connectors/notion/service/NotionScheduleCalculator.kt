package com.sprintstart.sprintstartbackend.connectors.notion.service

import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Calculates the next Notion synchronization time from a Spring six-field cron expression. */
@Component
internal class NotionScheduleCalculator {
    fun calculateNextSyncAt(schedule: String, after: Instant): Instant? {
        return runCatching {
            CronExpression
                .parse(schedule)
                .next(ZonedDateTime.ofInstant(after, ZoneOffset.UTC))
                ?.toInstant()
        }.getOrNull()
    }
}

package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqTrend
import com.sprintstart.sprintstartbackend.insights.repository.FaqQuestionRepository
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * How many questions a group received recently, and which way that is moving.
 *
 * A count alone cannot tell a topic that is picking up from one that was asked constantly a year
 * ago and never since — but for a PM those mean opposite things, so the panel needs both. The
 * comparison is deliberately crude (this window against the one before it): the signal being
 * surfaced is "look here" or "this has gone quiet", and anything finer would read as precision the
 * underlying data does not have.
 *
 * Measured over the questions actually stored for a group. Every question asked through chat is
 * stored, so the numbers are exact for a FAQ maintained that way; directly after a full rebuild,
 * which carries back only a redacted sample per group, they understate large groups until live
 * traffic accumulates again.
 */
@Component
class FaqTrendCalculator(
    private val faqQuestionRepository: FaqQuestionRepository,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Counts of a group's questions in the current window and the one before it.
     */
    data class Stats(
        val recentCount: Int,
        val previousCount: Int,
    ) {
        val trend: FaqTrend
            get() = when {
                recentCount > previousCount -> FaqTrend.RISING
                recentCount < previousCount -> FaqTrend.FADING
                // Equal counts are steady — except when both are zero, which is not a topic
                // holding its level but one nobody has asked about in two full windows.
                recentCount == 0 -> FaqTrend.FADING
                else -> FaqTrend.STEADY
            }

        operator fun plus(other: Stats) =
            Stats(recentCount + other.recentCount, previousCount + other.previousCount)

        companion object {
            val NONE = Stats(recentCount = 0, previousCount = 0)
        }
    }

    /**
     * Returns per-group statistics for a project, keyed by group id.
     *
     * Groups with no questions in either window are absent; callers should treat a miss as
     * [Stats.NONE] rather than as an error.
     */
    fun statsByGroup(projectId: UUID, now: Instant = Instant.now()): Map<UUID, Stats> {
        val window = Duration.ofDays(applicationConfig.insights.faq.trendWindowDays)
        val windowStart = now.minus(window)
        val previousStart = windowStart.minus(window)

        val recent = faqQuestionRepository
            .countPerGroupAskedBetween(projectId, windowStart, now)
            .associate { it.groupId to it.count.toInt() }
        val previous = faqQuestionRepository
            .countPerGroupAskedBetween(projectId, previousStart, windowStart)
            .associate { it.groupId to it.count.toInt() }

        return (recent.keys + previous.keys).associateWith { groupId ->
            Stats(
                recentCount = recent[groupId] ?: 0,
                previousCount = previous[groupId] ?: 0,
            )
        }
    }
}

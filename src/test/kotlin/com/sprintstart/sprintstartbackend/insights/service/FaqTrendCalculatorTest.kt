package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.FaqInsightsConfig
import com.sprintstart.sprintstartbackend.insights.insightsTestConfig
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqTrend
import com.sprintstart.sprintstartbackend.insights.repository.FaqQuestionRepository
import com.sprintstart.sprintstartbackend.insights.repository.projection.FaqGroupQuestionCount
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class FaqTrendCalculatorTest {
    private val projectId: UUID = UUID.randomUUID()
    private val groupId: UUID = UUID.randomUUID()
    private val now: Instant = Instant.parse("2026-08-14T00:00:00Z")

    private val faqQuestionRepository = mockk<FaqQuestionRepository>()
    private val calculator = FaqTrendCalculator(
        faqQuestionRepository,
        insightsTestConfig(faq = FaqInsightsConfig(trendWindowDays = 14)),
    )

    private val windowStart: Instant = Instant.parse("2026-07-31T00:00:00Z")
    private val previousStart: Instant = Instant.parse("2026-07-17T00:00:00Z")

    private fun givenCounts(recent: Long?, previous: Long?) {
        every { faqQuestionRepository.countPerGroupAskedBetween(projectId, windowStart, now) } returns
            listOfNotNull(recent?.let { FaqGroupQuestionCount(groupId, it) })
        every { faqQuestionRepository.countPerGroupAskedBetween(projectId, previousStart, windowStart) } returns
            listOfNotNull(previous?.let { FaqGroupQuestionCount(groupId, it) })
    }

    @Test
    fun `a topic asked more than before is rising`() {
        givenCounts(recent = 8, previous = 3)

        val stats = calculator.statsByGroup(projectId, now).getValue(groupId)

        assertEquals(8, stats.recentCount)
        assertEquals(3, stats.previousCount)
        assertEquals(FaqTrend.RISING, stats.trend)
    }

    @Test
    fun `a topic asked less than before is fading`() {
        givenCounts(recent = 2, previous = 9)

        assertEquals(FaqTrend.FADING, calculator.statsByGroup(projectId, now).getValue(groupId).trend)
    }

    @Test
    fun `a topic asked as often as before is steady`() {
        givenCounts(recent = 4, previous = 4)

        assertEquals(FaqTrend.STEADY, calculator.statsByGroup(projectId, now).getValue(groupId).trend)
    }

    @Test
    fun `a topic nobody asked in either window is fading, not steady`() {
        givenCounts(recent = null, previous = null)

        // Equal counts normally mean "holding its level", but two empty windows mean the topic
        // has gone quiet — the opposite signal for a PM deciding where to spend documentation
        // effort.
        assertEquals(FaqTrend.FADING, FaqTrendCalculator.Stats.NONE.trend)
        assertEquals(emptyMap<UUID, FaqTrendCalculator.Stats>(), calculator.statsByGroup(projectId, now))
    }

    @Test
    fun `a newly opened topic counts as rising from nothing`() {
        givenCounts(recent = 3, previous = null)

        assertEquals(FaqTrend.RISING, calculator.statsByGroup(projectId, now).getValue(groupId).trend)
    }

    @Test
    fun `category totals add up their groups' windows`() {
        val summed = FaqTrendCalculator.Stats(recentCount = 3, previousCount = 1) +
            FaqTrendCalculator.Stats(recentCount = 2, previousCount = 6)

        assertEquals(5, summed.recentCount)
        assertEquals(7, summed.previousCount)
        assertEquals(FaqTrend.FADING, summed.trend)
    }
}

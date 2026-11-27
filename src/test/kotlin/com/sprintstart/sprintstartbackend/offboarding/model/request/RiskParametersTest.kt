package com.sprintstart.sprintstartbackend.offboarding.model.request

import com.sprintstart.sprintstartbackend.offboarding.scoring.KnowledgeModel
import com.sprintstart.sprintstartbackend.offboarding.scoring.KnowledgeSettings
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.Ranking
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.offboarding.scoring.WeightProfile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class RiskParametersTest {
    private fun badRequestFor(parameters: RiskParameters) =
        assertThatThrownBy { parameters.toRequest() }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }

    @Test
    fun `leaving people are read as emails or logins and deduplicated`() {
        val request = RiskParameters(leaving = listOf("Ada@x.org", "ada@x.org", "ben")).toRequest()

        assertThat(request.departing).containsExactlyInAnyOrder(PersonKey("ada@x.org"), PersonKey("ben"))
    }

    @Test
    fun `settings that are not given keep their defaults`() {
        assertThat(RiskParameters(leaving = listOf("ada")).toRequest().profile).isEqualTo(WeightProfile())
    }

    @Test
    fun `given settings override the defaults one by one`() {
        val request = RiskParameters(
            leaving = listOf("ada"),
            halfLifeDays = 30.0,
            inactiveAfterDays = 90,
            authorThreshold = 0.5,
        ).toRequest()

        assertThat(request.profile).isEqualTo(
            WeightProfile(halfLifeDays = 30.0, inactiveAfterDays = 90, authorThreshold = 0.5),
        )
    }

    @Test
    fun `the way knowledge is decided can be chosen by name`() {
        val request = RiskParameters(
            leaving = listOf("ada"),
            knowledge = "time-weighted",
            knowsAtLeast = 2.0,
        ).toRequest()

        assertThat(request.profile.knowledge)
            .isEqualTo(KnowledgeSettings(KnowledgeModel.TIME_WEIGHTED, knowsAtLeast = 2.0))
    }

    @Test
    fun `the ranking can be chosen by name and defaults to the departing share`() {
        assertThat(RiskParameters(leaving = listOf("ada"), ranking = "placeholder").toRequest().profile.ranking)
            .isEqualTo(Ranking.PLACEHOLDER)
        assertThat(RiskParameters(leaving = listOf("ada")).toRequest().profile.ranking)
            .isEqualTo(Ranking.DEPARTING_SHARE)
    }

    @Test
    fun `an unknown ranking is a bad request`() {
        badRequestFor(RiskParameters(leaving = listOf("ada"), ranking = "luck"))
    }

    @Test
    fun `an unknown way of deciding knowledge is a bad request`() {
        badRequestFor(RiskParameters(leaving = listOf("ada"), knowledge = "guess"))
        badRequestFor(RiskParameters(leaving = listOf("ada"), knowsAtLeast = 0.0))
    }

    @Test
    fun `reviews count only when a weight for them is given`() {
        val without = RiskParameters(leaving = listOf("ada")).toRequest().profile
        val with = RiskParameters(leaving = listOf("ada"), reviewWeight = 0.5).toRequest().profile

        assertThat(without.sourceWeights).doesNotContainKey(SourceId.REVIEWS)
        assertThat(with.sourceWeights).containsEntry(SourceId.COMMITS, 1.0).containsEntry(SourceId.REVIEWS, 0.5)
    }

    @Test
    fun `tickets count only when a weight for them is given`() {
        val without = RiskParameters(leaving = listOf("ada")).toRequest().profile
        val with = RiskParameters(leaving = listOf("ada"), ticketWeight = 0.25).toRequest().profile

        assertThat(without.weightOf(SourceId.TICKETS)).isZero()
        assertThat(with.weightOf(SourceId.TICKETS)).isEqualTo(0.25)
    }

    @Test
    fun `documentation pages count like commits unless a weight for them is given`() {
        val without = RiskParameters(leaving = listOf("ada")).toRequest().profile
        val off = RiskParameters(leaving = listOf("ada"), pageEditWeight = 0.0).toRequest().profile

        assertThat(without.sourceWeights).containsEntry(SourceId.PAGE_EDITS, 1.0)
        assertThat(off.weightOf(SourceId.PAGE_EDITS)).isZero()
    }

    @Test
    fun `a negative review weight is a bad request`() {
        badRequestFor(RiskParameters(leaving = listOf("ada"), reviewWeight = -0.1))
    }

    @Test
    fun `the date is passed on and stays open when not given`() {
        val moment = Instant.parse("2026-05-01T00:00:00Z")

        assertThat(RiskParameters(leaving = listOf("ada"), asOf = moment).toRequest().asOf).isEqualTo(moment)
        assertThat(RiskParameters(leaving = listOf("ada")).toRequest().asOf).isNull()
    }

    @Test
    fun `nobody leaving is a bad request`() {
        badRequestFor(RiskParameters())
    }

    @Test
    fun `a blank person is a bad request`() {
        badRequestFor(RiskParameters(leaving = listOf("ada", " ")))
    }

    @Test
    fun `a setting out of range is a bad request`() {
        badRequestFor(RiskParameters(leaving = listOf("ada"), halfLifeDays = 0.0))
        badRequestFor(RiskParameters(leaving = listOf("ada"), inactiveAfterDays = -1))
        badRequestFor(RiskParameters(leaving = listOf("ada"), authorThreshold = 1.0))
    }

    @Test
    fun `the list limit defaults to 100 and stays within 1 and 1000`() {
        assertThat(RiskParameters().listLimit).isEqualTo(100)
        assertThat(RiskParameters(limit = 0).listLimit).isEqualTo(1)
        assertThat(RiskParameters(limit = 5_000).listLimit).isEqualTo(1000)
        assertThat(RiskParameters(limit = 25).listLimit).isEqualTo(25)
    }
}

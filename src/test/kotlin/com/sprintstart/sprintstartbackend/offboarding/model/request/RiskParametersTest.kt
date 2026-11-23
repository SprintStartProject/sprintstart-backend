package com.sprintstart.sprintstartbackend.offboarding.model.request

import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.ScoringProfile
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
        assertThat(RiskParameters(leaving = listOf("ada")).toRequest().profile).isEqualTo(ScoringProfile())
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
            ScoringProfile(halfLifeDays = 30.0, inactiveAfterDays = 90, authorThreshold = 0.5),
        )
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

package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.service.ArrivalEvidenceService
import com.sprintstart.sprintstartbackend.onboarding.service.ArrivalStepService
import com.sprintstart.sprintstartbackend.onboarding.service.ResolvedArrivalStep
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@WebMvcTest(MyArrivalController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class MyArrivalControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var arrivalStepService: ArrivalStepService

    @MockkBean
    private lateinit var arrivalEvidenceService: ArrivalEvidenceService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"

    private fun jwtWithSubject(
        subject: String,
        vararg roles: String,
    ): JwtRequestPostProcessor =
        jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })

    private val userJwt = jwtWithSubject(authId, "USER")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    @Test
    fun `getMyArrival should return 200 with the steps and per-rigor counts`() {
        every { arrivalStepService.forCaller(authId) } returns listOf(
            resolved("github-account", settledAt = Instant.parse("2026-08-01T09:00:00Z"), rigor = Rigor.DECLARED),
            resolved("vpn"),
        )

        mockMvc
            .perform(get("/api/v1/onboarding/me/arrival").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.steps.length()").value(2))
            .andExpect(jsonPath("$.steps[0].key").value("github-account"))
            .andExpect(jsonPath("$.steps[0].settled").value(true))
            .andExpect(jsonPath("$.steps[0].rigor").value("DECLARED"))
            .andExpect(jsonPath("$.steps[1].settled").value(false))
            .andExpect(jsonPath("$.steps[1].rigor").doesNotExist())
            .andExpect(jsonPath("$.declaredCount").value(1))
            .andExpect(jsonPath("$.observedCount").value(0))
            .andExpect(jsonPath("$.outstandingCount").value(1))
    }

    /**
     * The wire shape must not offer a single blended completion figure.
     *
     * A percentage would count a ticked box exactly like a passed check, and that conflation is
     * what makes such a number meaningless. Counts are per rigor with deliberately no total to
     * divide by; this test is what stops one being added as a convenience.
     */
    @Test
    fun `getMyArrival must not expose a blended completion figure`() {
        every { arrivalStepService.forCaller(authId) } returns listOf(
            resolved("github-account", settledAt = Instant.now(), rigor = Rigor.DECLARED),
            resolved("vpn"),
        )

        val body = mockMvc
            .perform(get("/api/v1/onboarding/me/arrival").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.progressPercentage").doesNotExist())
            .andExpect(jsonPath("$.percentComplete").doesNotExist())
            .andExpect(jsonPath("$.completion").doesNotExist())
            .andExpect(jsonPath("$.settledCount").doesNotExist())
            .andExpect(jsonPath("$.totalCount").doesNotExist())
            .andReturn()
            .response
            .contentAsString

        // Belt and braces: no field name anywhere in the payload suggests one number stands for
        // "how done are you", however it gets spelled.
        listOf("percent", "progress", "ratio", "score").forEach { banned ->
            check(!body.lowercase().contains("\"$banned")) { "response exposes a blended figure: $banned" }
        }
    }

    @Test
    fun `getMyArrival should return an empty list rather than an error when nothing is authored`() {
        every { arrivalStepService.forCaller(authId) } returns emptyList()

        mockMvc
            .perform(get("/api/v1/onboarding/me/arrival").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.steps.length()").value(0))
            .andExpect(jsonPath("$.outstandingCount").value(0))
    }

    @Test
    fun `getMyArrival should return 403 without the USER role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/arrival").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `confirm should return 200 and the settled step`() {
        every { arrivalStepService.confirmForCaller(authId, "badge") } returns
            resolved("badge", settledAt = Instant.parse("2026-08-02T10:00:00Z"), rigor = Rigor.DECLARED)

        mockMvc
            .perform(post("/api/v1/onboarding/me/arrival/badge/confirm").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("badge"))
            .andExpect(jsonPath("$.settled").value(true))
            .andExpect(jsonPath("$.rigor").value("DECLARED"))
    }

    @Test
    fun `confirm should return 400 for a step the system observes`() {
        every { arrivalStepService.confirmForCaller(authId, "github-account") } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST, "settled by OBSERVED, not by you")

        mockMvc
            .perform(post("/api/v1/onboarding/me/arrival/github-account/confirm").with(userJwt))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `confirm should return 404 for a step that does not apply`() {
        every { arrivalStepService.confirmForCaller(authId, "nope") } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "no such step")

        mockMvc
            .perform(post("/api/v1/onboarding/me/arrival/nope/confirm").with(userJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `confirm should return 403 without the USER role`() {
        mockMvc
            .perform(post("/api/v1/onboarding/me/arrival/badge/confirm").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    private fun resolved(
        key: String,
        settledAt: Instant? = null,
        rigor: Rigor? = null,
        settledBy: Rigor = Rigor.DECLARED,
        selfConfirmable: Boolean = true,
    ) = ResolvedArrivalStep(
        step = ArrivalStep(
            key = key,
            title = key,
            settledBy = settledBy,
            selfConfirmable = selfConfirmable,
        ),
        settledAt = settledAt,
        rigor = rigor,
    )
}

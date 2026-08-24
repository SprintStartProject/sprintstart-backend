package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.service.ArrivalStepService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(ArrivalAuthoringController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class ArrivalAuthoringControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var arrivalStepService: ArrivalStepService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor =
        jwt()
            .jwt { jwt ->
                jwt.subject("test-auth-id")
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })

    private val adminJwt = jwtWithRoles("ADMIN")
    private val pmJwt = jwtWithRoles("PM")
    private val hrJwt = jwtWithRoles("HR")
    private val userJwt = jwtWithRoles("USER")

    @Test
    fun `listing steps is open to HR as well as ADMIN and PM`() {
        every { arrivalStepService.listForAuthoring(null) } returns
            listOf(ArrivalStep(key = "vpn", title = "Request VPN access"))

        mockMvc
            .perform(get("/api/v1/onboarding/arrival-steps").with(hrJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].key").value("vpn"))
    }

    @Test
    fun `listing steps is refused to a plain user`() {
        mockMvc
            .perform(get("/api/v1/onboarding/arrival-steps").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `creating a step returns 201`() {
        every {
            arrivalStepService.create("vpn", null, "Request VPN access", null, null, 0, Rigor.DECLARED)
        } returns ArrivalStep(key = "vpn", title = "Request VPN access")

        mockMvc
            .perform(
                post("/api/v1/onboarding/arrival-steps")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"key":"vpn","title":"Request VPN access"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").value("vpn"))
    }

    @Test
    fun `creating a duplicate key returns 409`() {
        every { arrivalStepService.create(any(), any(), any(), any(), any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.CONFLICT, "already exists")

        mockMvc
            .perform(
                post("/api/v1/onboarding/arrival-steps")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"key":"vpn","title":"Request VPN access"}"""),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `creating a step is refused to HR, who may only read`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/arrival-steps")
                    .with(hrJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"key":"vpn","title":"Request VPN access"}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `a blank title is rejected before it reaches the service`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/arrival-steps")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"key":"vpn","title":"  "}"""),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) { arrivalStepService.create(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reordering takes the whole order`() {
        every { arrivalStepService.reorder(null, listOf("vpn", "badge")) } returns
            listOf(ArrivalStep(key = "vpn", title = "VPN"), ArrivalStep(key = "badge", title = "Badge"))

        mockMvc
            .perform(
                post("/api/v1/onboarding/arrival-steps/reorder")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"orderedKeys":["vpn","badge"]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].key").value("vpn"))
            .andExpect(jsonPath("$[1].key").value("badge"))
    }

    @Test
    fun `deleting a step returns 204`() {
        every { arrivalStepService.delete("vpn", null) } returns Unit

        mockMvc
            .perform(delete("/api/v1/onboarding/arrival-steps/vpn").with(adminJwt))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleting a step that does not exist returns 404`() {
        every { arrivalStepService.delete("nope", null) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "no such step")

        mockMvc
            .perform(delete("/api/v1/onboarding/arrival-steps/nope").with(adminJwt))
            .andExpect(status().isNotFound)
    }
}

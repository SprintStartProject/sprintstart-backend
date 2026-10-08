package com.sprintstart.sprintstartbackend.user.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.model.request.dashboard.DashboardLayoutItemPayload
import com.sprintstart.sprintstartbackend.user.model.request.dashboard.SaveDashboardLayoutRequest
import com.sprintstart.sprintstartbackend.user.model.response.dashboard.DashboardLayoutResponse
import com.sprintstart.sprintstartbackend.user.service.DashboardLayoutService
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import kotlin.test.assertEquals

@WebMvcTest(DashboardLayoutController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class DashboardLayoutControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var dashboardLayoutService: DashboardLayoutService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val path = "/api/v1/users/me/dashboard/layout"
    private val authId = "auth-alice"

    private fun jwtWithSubject(subject: String, vararg roles: String): JwtRequestPostProcessor =
        jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    private val userJwt = jwtWithSubject(authId, "USER")

    @Test
    fun `reads the caller's layout, resolved from the token`() {
        every { dashboardLayoutService.read(authId, 2) } returns DashboardLayoutResponse(
            version = 2,
            items = listOf(DashboardLayoutItemPayload("greeting", "wide")),
            updatedAt = Instant.parse("2026-09-30T10:00:00Z"),
        )

        mockMvc
            .perform(get(path).param("version", "2").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.items[0].id").value("greeting"))
            .andExpect(jsonPath("$.items[0].size").value("wide"))
            .andExpect(jsonPath("$.updatedAt").exists())

        verify(exactly = 1) { dashboardLayoutService.read(authId, 2) }
    }

    @Test
    fun `no stored layout is a 200 with no items, not a 404`() {
        every { dashboardLayoutService.read(authId, 2) } returns DashboardLayoutResponse(2, emptyList(), null)

        mockMvc
            .perform(get(path).param("version", "2").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
            .andExpect(jsonPath("$.updatedAt").doesNotExist())
    }

    @Test
    fun `a PM without the user role can arrange their own dashboard too`() {
        every { dashboardLayoutService.read("auth-pm", 2) } returns DashboardLayoutResponse(2, emptyList(), null)

        mockMvc
            .perform(get(path).param("version", "2").with(jwtWithSubject("auth-pm", "PM")))
            .andExpect(status().isOk)
    }

    @Test
    fun `writes the whole arrangement for the caller`() {
        val twoWidgets = """{"version":2,"items":[{"id":"skills","size":"small"},""" +
            """{"id":"greeting","size":"wide"}]}"""
        val request = slot<SaveDashboardLayoutRequest>()
        every { dashboardLayoutService.write(authId, capture(request)) } answers {
            DashboardLayoutResponse(request.captured.version, request.captured.items, Instant.now())
        }

        mockMvc
            .perform(
                put(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(twoWidgets)
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[1].id").value("greeting"))

        assertEquals(2, request.captured.version)
        assertEquals(listOf("skills", "greeting"), request.captured.items.map { it.id })
    }

    @Test
    fun `an item without an id is refused`() {
        mockMvc
            .perform(
                put(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"version":2,"items":[{"id":"","size":"small"}]}""")
                    .with(userJwt),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a layout without a version is refused`() {
        mockMvc
            .perform(
                put(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"version":0,"items":[]}""")
                    .with(userJwt),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `reset forgets the caller's layout`() {
        every { dashboardLayoutService.clear(authId) } just runs

        mockMvc
            .perform(delete(path).with(userJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { dashboardLayoutService.clear(authId) }
    }

    @Test
    fun `nobody can read a layout without signing in`() {
        mockMvc
            .perform(get(path).param("version", "2"))
            .andExpect(status().isUnauthorized)
    }
}

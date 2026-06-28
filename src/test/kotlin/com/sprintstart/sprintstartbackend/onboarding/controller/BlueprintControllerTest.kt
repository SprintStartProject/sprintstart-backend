package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.service.BlueprintService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(BlueprintController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class BlueprintControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean(relaxed = true)
    private lateinit var blueprintService: BlueprintService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `generateBlueprints should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/blueprints/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `listVersions should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/blueprints/backend/versions"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rollback should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/blueprints/backend/rollback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isUnauthorized)
    }
}

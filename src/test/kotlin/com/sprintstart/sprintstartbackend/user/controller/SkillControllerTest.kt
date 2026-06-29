package com.sprintstart.sprintstartbackend.user.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.model.dto.SkillAssessmentDto
import com.sprintstart.sprintstartbackend.user.model.dto.SkillDto
import com.sprintstart.sprintstartbackend.user.model.entity.SkillLevel
import com.sprintstart.sprintstartbackend.user.model.request.CreateSkillAssessmentRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateSkillRequest
import com.sprintstart.sprintstartbackend.user.service.SkillService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(SkillController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class SkillControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var skillService: SkillService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val userJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val adminJwt = jwt()
        .authorities(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_ADMIN"),
        )

    @Test
    fun `getAllSkills should return 200 and all skills for any valid role`() {
        val skillDto = SkillDto(id = UUID.randomUUID(), name = "Kotlin", roleId = UUID.randomUUID())
        every { skillService.getAllSkills() } returns listOf(skillDto)

        mockMvc
            .perform(
                get("/api/v1/skills")
                    .with(userJwt), // userJwt has ROLE_USER, which is allowed for this endpoint
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { skillService.getAllSkills() }
    }

    @Test
    fun `createSkill should return 201 and created skill for admins`() {
        val request = CreateSkillRequest("Kotlin", UUID.randomUUID())
        val skillDto = SkillDto(id = UUID.randomUUID(), name = "Kotlin", roleId = request.roleId)
        every { skillService.createSkill(request) } returns skillDto

        mockMvc
            .perform(
                post("/api/v1/skills")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { skillService.createSkill(request) }
    }

    @Test
    fun `createSkill should return 403 for normal users`() {
        val request = CreateSkillRequest("Kotlin", UUID.randomUUID())

        mockMvc
            .perform(
                post("/api/v1/skills")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { skillService.createSkill(any()) }
    }

    @Test
    fun `deleteSkill should return 204`() {
        val skillId = UUID.randomUUID()
        every { skillService.deleteSkill(skillId) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/skills/$skillId")
                    .with(adminJwt),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) { skillService.deleteSkill(skillId) }
    }

    @Test
    fun `getUserSkillAssessments should return 200`() {
        val userId = UUID.randomUUID()
        val assessment = SkillAssessmentDto(userId = userId, skillId = UUID.randomUUID(), level = SkillLevel.BEGINNER)
        every { skillService.getUserSkillAssessments(userId) } returns listOf(assessment)

        mockMvc
            .perform(
                get("/api/v1/users/$userId/skill-assessments/completed")
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { skillService.getUserSkillAssessments(userId) }
    }

    @Test
    fun `assessSkill should return 200`() {
        val request = CreateSkillAssessmentRequest(skillId = UUID.randomUUID(), level = SkillLevel.EXPERT)
        val assessment =
            SkillAssessmentDto(userId = UUID.randomUUID(), skillId = request.skillId, level = request.level)
        every { skillService.assessSkillForMe(any(), request) } returns assessment

        mockMvc
            .perform(
                post("/api/v1/skill-assessments")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { skillService.assessSkillForMe(any(), request) }
    }
}

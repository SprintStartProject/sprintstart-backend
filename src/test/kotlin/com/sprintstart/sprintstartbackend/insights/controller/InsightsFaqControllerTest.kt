package com.sprintstart.sprintstartbackend.insights.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDetailResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDocumentPreviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDocumentResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqGroupSummaryResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqQuestionResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshFaqResponse
import com.sprintstart.sprintstartbackend.insights.service.InsightsFaqService
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(InsightsFaqController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class InsightsFaqControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var insightsFaqService: InsightsFaqService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val groupId = UUID.randomUUID()

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject("test-auth-id")
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })
    }

    private val pmJwt = jwtWithRoles("PM")
    private val adminJwt = jwtWithRoles("ADMIN")
    private val userJwt = jwtWithRoles("USER")

    private fun buildOverview() = FaqOverviewResponse(
        groups = listOf(
            FaqGroupSummaryResponse(
                groupId = groupId,
                count = 14,
                question = "How do I get VPN access?",
                topDocuments = listOf(FaqDocumentPreviewResponse(id = "doc_001", title = "VPN Setup Guide")),
            ),
        ),
    )

    private fun buildDetail() = FaqDetailResponse(
        groupId = groupId,
        count = 14,
        questions = listOf(FaqQuestionResponse(id = UUID.randomUUID(), text = "How do I get VPN access?")),
        answeringDocuments = listOf(
            FaqDocumentResponse(
                id = "doc_001",
                title = "VPN Setup Guide",
                source = "confluence",
            ),
        ),
    )

    // ========================== Overview ==========================

    @Test
    fun `getFaqOverview should return 200 and groups for a PM`() {
        every { insightsFaqService.getFaqOverview() } returns buildOverview()

        mockMvc
            .perform(get("/api/v1/insights/faq").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { insightsFaqService.getFaqOverview() }
    }

    @Test
    fun `getFaqOverview should return 200 for an admin`() {
        every { insightsFaqService.getFaqOverview() } returns buildOverview()

        mockMvc
            .perform(get("/api/v1/insights/faq").with(adminJwt))
            .andExpect(status().isOk)
    }

    @Test
    fun `getFaqOverview should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/insights/faq"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getFaqOverview should return 403 for a non-PM role`() {
        mockMvc
            .perform(get("/api/v1/insights/faq").with(userJwt))
            .andExpect(status().isForbidden)
    }

    // ========================== Detail ==========================

    @Test
    fun `getFaqGroup should return 200 and detail for a PM`() {
        every { insightsFaqService.getFaqGroup(groupId) } returns buildDetail()

        mockMvc
            .perform(get("/api/v1/insights/faq/$groupId").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { insightsFaqService.getFaqGroup(groupId) }
    }

    @Test
    fun `getFaqGroup should return 404 when the group does not exist`() {
        every { insightsFaqService.getFaqGroup(groupId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "FAQ group with id $groupId not found")

        mockMvc
            .perform(get("/api/v1/insights/faq/$groupId").with(pmJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getFaqGroup should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/insights/faq/$groupId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getFaqGroup should return 403 for a non-PM role`() {
        mockMvc
            .perform(get("/api/v1/insights/faq/$groupId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    // ========================== Refresh ==========================

    @Test
    fun `refreshFaqGroups should return 200 and the group count for a PM`() {
        coEvery { insightsFaqService.refreshFaqGroups() } returns RefreshFaqResponse(groupCount = 3)

        val asyncResult = mockMvc
            .perform(post("/api/v1/insights/faq/refresh").with(pmJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        coVerify(exactly = 1) { insightsFaqService.refreshFaqGroups() }
    }

    @Test
    fun `refreshFaqGroups should return 401 when not authenticated`() {
        mockMvc
            .perform(post("/api/v1/insights/faq/refresh"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refreshFaqGroups should return 403 for a non-PM role`() {
        // The endpoint is a coroutine handler, so the security denial surfaces through the async
        // dispatch rather than on the initial response.
        val asyncResult = mockMvc
            .perform(post("/api/v1/insights/faq/refresh").with(userJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)

        coVerify(exactly = 0) { insightsFaqService.refreshFaqGroups() }
    }
}

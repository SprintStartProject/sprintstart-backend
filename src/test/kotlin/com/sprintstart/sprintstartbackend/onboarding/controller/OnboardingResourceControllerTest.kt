package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

@WebMvcTest(OnboardingResourceController::class)
class OnboardingResourceControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingService: OnboardingService

    private val stepId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val resourceId: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")

    @Test
    fun `createOnboardingResourceForStepId should return 201 and created resource`() {
        val request = CreateOnboardingResourceRequest(
            title = "Documentation",
            description = "Internal documentation",
            url = "https://example.com/docs",
        )
        val response = CreateOnboardingResourceResponse(
            id = resourceId,
            stepId = stepId,
            title = "Documentation",
            description = "Internal documentation",
            url = "https://example.com/docs",
        )

        every { onboardingService.createOnboardingResourceForStepId(stepId, request) } returns response

        mockMvc
            .post("/api/v1/onboarding/steps/$stepId/resources") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value(resourceId.toString()) }
                jsonPath("$.stepId") { value(stepId.toString()) }
                jsonPath("$.url") { value("https://example.com/docs") }
            }

        verify(exactly = 1) { onboardingService.createOnboardingResourceForStepId(stepId, request) }
    }

    @Test
    fun `getOnboardingResources should return 200 and all resources`() {
        val response = listOf(
            GetOnboardingResourcesResponse(
                id = resourceId,
                stepId = stepId,
                title = "Documentation",
                description = "Internal documentation",
                url = "https://example.com/docs",
            ),
        )

        every { onboardingService.getOnboardingResources() } returns response

        mockMvc
            .get("/api/v1/onboarding/resources")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(resourceId.toString()) }
                jsonPath("$[0].stepId") { value(stepId.toString()) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingResources() }
    }

    @Test
    fun `getOnboardingResourcesByStepId should return 200 and resources for step`() {
        val response = listOf(
            GetOnboardingResourceResponse(
                id = resourceId,
                stepId = stepId,
                title = "Documentation",
                description = "Internal documentation",
                url = "https://example.com/docs",
            ),
        )

        every { onboardingService.getOnboardingResourceByStepId(stepId) } returns response

        mockMvc
            .get("/api/v1/onboarding/steps/$stepId/resources")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(resourceId.toString()) }
                jsonPath("$[0].stepId") { value(stepId.toString()) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingResourceByStepId(stepId) }
    }

    @Test
    fun `getOnboardingResource should return 200 and resource`() {
        val response = GetOnboardingResourceResponse(
            id = resourceId,
            stepId = stepId,
            title = "Documentation",
            description = "Internal documentation",
            url = "https://example.com/docs",
        )

        every { onboardingService.getOnboardingResource(resourceId) } returns response

        mockMvc
            .get("/api/v1/onboarding/resources/$resourceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(resourceId.toString()) }
                jsonPath("$.stepId") { value(stepId.toString()) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingResource(resourceId) }
    }

    @Test
    fun `updateOnboardingResource should return 200 and updated resource`() {
        val request = UpdateOnboardingResourceRequest(
            title = "Updated documentation",
            description = "Updated internal documentation",
            url = "https://example.com/updated-docs",
        )
        val response = UpdateOnboardingResourceResponse(
            id = resourceId,
            stepId = stepId,
            title = "Updated documentation",
            description = "Updated internal documentation",
            url = "https://example.com/updated-docs",
        )

        every { onboardingService.updateOnboardingResource(resourceId, request) } returns response

        mockMvc
            .put("/api/v1/onboarding/resources/$resourceId") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(resourceId.toString()) }
                jsonPath("$.title") { value("Updated documentation") }
                jsonPath("$.url") { value("https://example.com/updated-docs") }
            }

        verify(exactly = 1) { onboardingService.updateOnboardingResource(resourceId, request) }
    }

    @Test
    fun `deleteOnboardingResource should return 204`() {
        every { onboardingService.deleteOnboardingResource(resourceId) } just Runs

        mockMvc
            .delete("/api/v1/onboarding/resources/$resourceId")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { onboardingService.deleteOnboardingResource(resourceId) }
    }
}

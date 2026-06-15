package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingResourceService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(OnboardingResourceController::class)
@Import(
    SecurityConfig::class,
)
@AutoConfigureMockMvc
class OnboardingResourceControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingResourceService: OnboardingResourceService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val stepId = UUID.randomUUID()
    private val resourceId = UUID.randomUUID()

    private val authId = "test-auth-id"
    private val adminAuthId = "test-admin-auth-id"

    private fun jwtWithSubject(
        subject: String,
        vararg roles: String,
    ): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim(
                    "realm_access",
                    mapOf("roles" to roles.toList()),
                )
            }.authorities(
                roles.map { role -> SimpleGrantedAuthority("ROLE_$role") },
            )
    }

    private val userJwt = jwtWithSubject(authId, "USER")
    private val adminJwt = jwtWithSubject(adminAuthId, "USER", "ADMIN")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    private fun buildCreateRequest() = CreateOnboardingResourceRequest(
        title = "Resource 1",
        description = "resource desc",
        url = "https://example.com/doc",
    )

    private fun buildUpdateRequest() = UpdateOnboardingResourceRequest(
        title = "Updated Resource",
        description = "updated desc",
        url = "https://example.com/updated",
    )

    private fun buildGetResourceResponse() = GetOnboardingResourceResponse(
        id = resourceId,
        stepId = stepId,
        title = "Resource 1",
        description = "resource desc",
        url = "https://example.com/doc",
    )

    private fun buildGetResourcesResponse() = GetOnboardingResourcesResponse(
        id = resourceId,
        stepId = stepId,
        title = "Resource 1",
        description = "resource desc",
        url = "https://example.com/doc",
    )

    private fun buildCreateResourceResponse() = CreateOnboardingResourceResponse(
        id = resourceId,
        stepId = stepId,
        title = "Resource 1",
        description = "resource desc",
        url = "https://example.com/doc",
    )

    private fun buildUpdateResourceResponse() = UpdateOnboardingResourceResponse(
        id = resourceId,
        stepId = stepId,
        title = "Updated Resource",
        description = "updated desc",
        url = "https://example.com/updated",
    )

    // ========================== /me endpoints ==========================

    @Test
    fun `getOnboardingResourcesForMe should return 200 and list of resources`() {
        every { onboardingResourceService.getOnboardingResourcesForMe(authId, stepId) } returns
            listOf(buildGetResourcesResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/resources").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.getOnboardingResourcesForMe(authId, stepId) }
    }

    @Test
    fun `getOnboardingResourcesForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/resources"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingResourcesForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/resources").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingResourceForMe should return 201 and created resource`() {
        val request = buildCreateRequest()
        every { onboardingResourceService.createOnboardingResourceForMe(authId, stepId, request) } returns
            buildCreateResourceResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/steps/$stepId/resources")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.createOnboardingResourceForMe(authId, stepId, request) }
    }

    @Test
    fun `createOnboardingResourceForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/steps/$stepId/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingResourceForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/steps/$stepId/resources")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingResourceForMe should return 200 and resource`() {
        every { onboardingResourceService.getOnboardingResourceForMe(authId, resourceId) } returns
            buildGetResourceResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/me/resources/$resourceId").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.getOnboardingResourceForMe(authId, resourceId) }
    }

    @Test
    fun `getOnboardingResourceForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/resources/$resourceId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingResourceForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/resources/$resourceId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingResourceForMe should return 404 when not found`() {
        every { onboardingResourceService.getOnboardingResourceForMe(authId, resourceId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/me/resources/$resourceId").with(userJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingResourceService.getOnboardingResourceForMe(authId, resourceId) }
    }

    @Test
    fun `updateOnboardingResourceForMe should return 200 and updated resource`() {
        val request = buildUpdateRequest()
        every { onboardingResourceService.updateOnboardingResourceForMe(authId, resourceId, request) } returns
            buildUpdateResourceResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/resources/$resourceId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.updateOnboardingResourceForMe(authId, resourceId, request) }
    }

    @Test
    fun `updateOnboardingResourceForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/me/resources/$resourceId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateOnboardingResourceForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/me/resources/$resourceId")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingResourceForMe should return 204`() {
        every { onboardingResourceService.deleteOnboardingResourceForMe(authId, resourceId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/me/resources/$resourceId").with(userJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingResourceService.deleteOnboardingResourceForMe(authId, resourceId) }
    }

    @Test
    fun `deleteOnboardingResourceForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/resources/$resourceId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingResourceForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/resources/$resourceId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getOnboardingResourcesByStepId should return 200 and list of resources`() {
        every { onboardingResourceService.getOnboardingResourcesByStepId(stepId) } returns
            listOf(buildGetResourcesResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId/resources").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.getOnboardingResourcesByStepId(stepId) }
    }

    @Test
    fun `getOnboardingResourcesByStepId should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId/resources"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingResourcesByStepId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId/resources").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingResourceForStepId should return 201 and created resource`() {
        val request = buildCreateRequest()
        every { onboardingResourceService.createOnboardingResourceForStepId(stepId, request) } returns
            buildCreateResourceResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/steps/$stepId/resources")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.createOnboardingResourceForStepId(stepId, request) }
    }

    @Test
    fun `createOnboardingResourceForStepId should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/steps/$stepId/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingResourceForStepId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/steps/$stepId/resources")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingResourceById should return 200 and resource`() {
        every { onboardingResourceService.getOnboardingResourceById(resourceId) } returns buildGetResourceResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/resources/$resourceId").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.getOnboardingResourceById(resourceId) }
    }

    @Test
    fun `getOnboardingResourceById should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/resources/$resourceId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingResourceById should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/resources/$resourceId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingResourceById should return 404 when not found`() {
        every { onboardingResourceService.getOnboardingResourceById(resourceId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/resources/$resourceId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingResourceService.getOnboardingResourceById(resourceId) }
    }

    @Test
    fun `updateOnboardingResourceById should return 200 and updated resource`() {
        val request = buildUpdateRequest()
        every { onboardingResourceService.updateOnboardingResourceById(resourceId, request) } returns
            buildUpdateResourceResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/resources/$resourceId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingResourceService.updateOnboardingResourceById(resourceId, request) }
    }

    @Test
    fun `updateOnboardingResourceById should return 401 when not authenticated`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/resources/$resourceId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateOnboardingResourceById should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/resources/$resourceId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `updateOnboardingResourceById should return 404 when not found`() {
        val request = buildUpdateRequest()
        every { onboardingResourceService.updateOnboardingResourceById(resourceId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                put("/api/v1/onboarding/resources/$resourceId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingResourceService.updateOnboardingResourceById(resourceId, request) }
    }

    @Test
    fun `deleteOnboardingResourceById should return 204`() {
        every { onboardingResourceService.deleteOnboardingResourceById(resourceId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/resources/$resourceId").with(adminJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingResourceService.deleteOnboardingResourceById(resourceId) }
    }

    @Test
    fun `deleteOnboardingResourceById should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/resources/$resourceId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingResourceById should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/resources/$resourceId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingResourceById should return 404 when not found`() {
        every { onboardingResourceService.deleteOnboardingResourceById(resourceId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(delete("/api/v1/onboarding/resources/$resourceId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingResourceService.deleteOnboardingResourceById(resourceId) }
    }
}

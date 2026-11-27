package com.sprintstart.sprintstartbackend.offboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.offboarding.model.exception.InterviewAiException
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.model.response.InterviewPlanResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.InterviewQuestionResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.service.InterviewPlanService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(controllers = [OffboardingInterviewController::class])
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OffboardingInterviewControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: InterviewPlanService

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val projectId = UUID.randomUUID()
    private val url = "/api/v1/projects/$projectId/offboarding/interview-questions"
    private val pmJwt = jwt().jwt { it.subject("pm-id") }.authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt().jwt { it.subject("user-id") }.authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val question = InterviewQuestionResponse(
        component = "github:acme/app/src",
        text = "Why does it retry?",
        type = "rationale",
        triggers = listOf("hotspot"),
        files = listOf("src/a.kt"),
        priority = 0.6,
        coverage = "partial",
        recordAnswer = "It retries.",
        sources = listOf("a.md"),
    )

    private fun allowManager() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
    }

    /** Sends the request and, when the suspending endpoint went asynchronous, its dispatch. */
    private fun send(jwt: RequestPostProcessor, vararg params: Pair<String, String>): ResultActions {
        val builder = post(url).with(jwt).with(csrf())
        params.forEach { (name, value) -> builder.param(name, value) }
        val first = mockMvc.perform(builder)
        val result = first.andReturn()
        return if (result.request.isAsyncStarted) mockMvc.perform(asyncDispatch(result)) else first
    }

    @Test
    fun `answers the plan of the departure`() {
        allowManager()
        val request = slot<RiskRequest>()
        coEvery { service.plan(ProjectId(projectId), capture(request), 25) } returns
            InterviewPlanResponse(listOf(question), emptyList(), 12)

        send(pmJwt, "leaving" to "Ada@X.org", "limit" to "25")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.files").value(12))
            .andExpect(jsonPath("$.questions[0].text").value("Why does it retry?"))
            .andExpect(jsonPath("$.questions[0].coverage").value("partial"))
            .andExpect(jsonPath("$.questions[0].recordAnswer").value("It retries."))
            .andExpect(jsonPath("$.answeredByRecords.length()").value(0))

        assertThat(request.captured.departing).containsExactly(PersonKey("ada@x.org"))
    }

    @Test
    fun `a failing AI service is a bad gateway`() {
        allowManager()
        coEvery { service.plan(any(), any(), any()) } throws InterviewAiException("down")

        send(pmJwt, "leaving" to "ada").andExpect(status().isBadGateway)
    }

    @Test
    fun `nobody leaving is a bad request`() {
        allowManager()

        send(pmJwt).andExpect(status().isBadRequest)

        coVerify(exactly = 0) { service.plan(any(), any(), any()) }
    }

    @Test
    fun `a member who does not manage the project is refused`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        send(userJwt, "leaving" to "ada").andExpect(status().isForbidden)
    }
}

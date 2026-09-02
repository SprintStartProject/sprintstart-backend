package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryAiClient
import com.sprintstart.sprintstartbackend.user.external.model.AiIndustryEvaluationResponse
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.exceptions.ProjectIndustryAiException
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class ProjectIndustryServiceTest {
    private val projectRepository = mockk<ProjectRepository>()
    private val projectIndustryAiClient = mockk<ProjectIndustryAiClient>()

    private val service = ProjectIndustryService(
        projectRepository = projectRepository,
        projectIndustryAiClient = projectIndustryAiClient,
    )

    private val projectId = UUID.randomUUID()

    @Test
    fun `evaluates and persists industry on project`() = runTest {
        val project = Project(id = projectId, name = "Test Project")
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Fintech / Banking",
            confidence = "high",
            evidence = listOf("Payment gateway", "Ledger service"),
        )

        val result = service.evaluateIndustry(projectId)

        assertEquals("Fintech / Banking", result.industry)
        assertEquals("high", result.confidence)
        assertEquals(listOf("Payment gateway", "Ledger service"), result.evidence)
        assertEquals("Fintech / Banking", project.industry)
        assertEquals("high", project.industryConfidence)
    }

    @Test
    fun `persists evaluation even with low confidence`() = runTest {
        val project = Project(
            id = projectId,
            name = "Test Project",
            industry = "Old Industry",
            industryConfidence = "high",
        )
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "E-Commerce",
            confidence = "low",
            evidence = emptyList(),
        )

        val result = service.evaluateIndustry(projectId)

        assertEquals("E-Commerce", result.industry)
        assertEquals("low", result.confidence)
        assertEquals("E-Commerce", project.industry)
        assertEquals("low", project.industryConfidence)
    }

    @Test
    fun `throws 404 when project does not exist`() = runTest {
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.evaluateIndustry(projectId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `propagates ProjectIndustryAiException when AI client fails`() = runTest {
        val project = Project(id = projectId, name = "Test Project")
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } throws
            ProjectIndustryAiException("AI service unavailable")

        assertThrows<ProjectIndustryAiException> {
            service.evaluateIndustry(projectId)
        }
    }
}

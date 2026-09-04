package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryAiClient
import com.sprintstart.sprintstartbackend.user.external.model.AiIndustryEvaluationResponse
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.exceptions.ProjectIndustryAiException
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class ProjectIndustryServiceTest {
    private val projectRepository = mockk<ProjectRepository>()
    private val projectIndustryAiClient = mockk<ProjectIndustryAiClient>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private val service = ProjectIndustryService(
        projectRepository = projectRepository,
        projectIndustryAiClient = projectIndustryAiClient,
        transactionManager = transactionManager,
    )

    private val projectId = UUID.randomUUID()

    @Test
    fun `evaluates and persists industry on project`() = runTest {
        val project = Project(id = projectId, name = "Test Project")
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { projectRepository.save(any()) } answers { firstArg() }
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

        verify(exactly = 1) {
            projectRepository.save(match { it.industry == "Fintech / Banking" && it.industryConfidence == "high" })
        }
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
        every { projectRepository.save(any()) } answers { firstArg() }
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

        verify(exactly = 1) {
            projectRepository.save(match { it.industry == "E-Commerce" && it.industryConfidence == "low" })
        }
    }

    @Test
    fun `throws 404 when project does not exist`() = runTest {
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.evaluateIndustry(projectId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
        coVerify(exactly = 0) { projectIndustryAiClient.evaluateIndustry(any()) }
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `propagates ProjectIndustryAiException when AI client fails`() = runTest {
        val project = Project(id = projectId, name = "Test Project")
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } throws
            ProjectIndustryAiException(
                statusCode = 503,
                body = "Service Unavailable",
                message = "AI service unavailable",
            )

        val exception = assertThrows<ProjectIndustryAiException> {
            service.evaluateIndustry(projectId)
        }

        assertEquals(503, exception.statusCode)
        assertEquals("Service Unavailable", exception.body)
        verify(exactly = 0) { projectRepository.save(any()) }
    }
}

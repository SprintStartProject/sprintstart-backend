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
        assertEquals(false, project.industryCustom)

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
    fun `evaluate resets a previously custom industry`() = runTest {
        val project = Project(
            id = projectId,
            name = "Test Project",
            industry = "Custom Industry",
            industryConfidence = null,
            industryCustom = true,
        )
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { projectRepository.save(any()) } answers { firstArg() }
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "E-Commerce",
            confidence = "medium",
            evidence = emptyList(),
        )

        service.evaluateIndustry(projectId)

        assertEquals(false, project.industryCustom)
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
    fun `setCustomIndustry sets industry, marks it custom and clears confidence`() {
        val project = Project(
            id = projectId,
            name = "Test Project",
            industry = "Old Industry",
            industryConfidence = "high",
        )
        every { projectRepository.findById(projectId) } returns Optional.of(project)

        val result = service.setCustomIndustry(projectId, "  Healthcare  ")

        assertEquals("Healthcare", result.industry)
        assertEquals(null, result.industryConfidence)
        assertEquals(true, result.industryCustom)
        assertEquals("Healthcare", project.industry)
        assertEquals(null, project.industryConfidence)
        assertEquals(true, project.industryCustom)
    }

    @Test
    fun `setCustomIndustry throws 404 when project does not exist`() {
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.setCustomIndustry(projectId, "Healthcare")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
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

    // ==========================================
    // getOrEvaluateIndustry (lazy evaluation)
    // ==========================================

    @Test
    fun `getOrEvaluateIndustry returns cached industry without AI call when already present`() = runTest {
        val project = Project(
            id = projectId,
            name = "Test Project",
            industry = "Healthcare",
            industryConfidence = "high",
        )
        every { projectRepository.findById(projectId) } returns Optional.of(project)

        val result = service.getOrEvaluateIndustry(projectId)

        assertEquals("Healthcare", result)
        coVerify(exactly = 0) { projectIndustryAiClient.evaluateIndustry(any()) }
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `getOrEvaluateIndustry evaluates and persists when industry is unset and confidence is medium`() = runTest {
        val project = Project(id = projectId, name = "Test Project", industry = null, industryConfidence = null)
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { projectRepository.save(any()) } answers { firstArg() }
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Automotive",
            confidence = "medium",
            evidence = listOf("CAN bus", "Telemetry"),
        )

        val result = service.getOrEvaluateIndustry(projectId)

        assertEquals("Automotive", result)
        assertEquals("Automotive", project.industry)
        assertEquals("medium", project.industryConfidence)
        verify(exactly = 1) {
            projectRepository.save(match { it.industry == "Automotive" && it.industryConfidence == "medium" })
        }
    }

    @Test
    fun `getOrEvaluateIndustry discards low confidence result and returns null without persisting`() = runTest {
        val project = Project(id = projectId, name = "Test Project", industry = null, industryConfidence = null)
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Gaming",
            confidence = "low",
            evidence = emptyList(),
        )

        val result = service.getOrEvaluateIndustry(projectId)

        assertEquals(null, result)
        assertEquals(null, project.industry)
        assertEquals(null, project.industryConfidence)
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `getOrEvaluateIndustry handles AI exception gracefully and returns null`() = runTest {
        val project = Project(id = projectId, name = "Test Project", industry = null)
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } throws
            ProjectIndustryAiException(statusCode = 500, body = "Error", message = "AI down")

        val result = service.getOrEvaluateIndustry(projectId)

        assertEquals(null, result)
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `getOrEvaluateIndustry returns null when project does not exist`() = runTest {
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val result = service.getOrEvaluateIndustry(projectId)

        assertEquals(null, result)
        coVerify(exactly = 0) { projectIndustryAiClient.evaluateIndustry(any()) }
    }

    // ==========================================
    // evaluateIndustryAutomatically (monotonicity & threshold)
    // ==========================================

    @Test
    fun `evaluateIndustryAutomatically persists when industry is unset and confidence is high`() = runTest {
        val project = Project(id = projectId, name = "Test Project", industry = null, industryConfidence = null)
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { projectRepository.save(any()) } answers { firstArg() }
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Quantum Computing",
            confidence = "high",
            evidence = listOf("Qubits"),
        )

        service.evaluateIndustryAutomatically(projectId)

        assertEquals("Quantum Computing", project.industry)
        assertEquals("high", project.industryConfidence)
        verify(exactly = 1) {
            projectRepository.save(match { it.industry == "Quantum Computing" && it.industryConfidence == "high" })
        }
    }

    @Test
    fun `evaluateIndustryAutomatically discards low confidence result for unset project`() = runTest {
        val project = Project(id = projectId, name = "Test Project", industry = null, industryConfidence = null)
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Guess",
            confidence = "low",
            evidence = emptyList(),
        )

        service.evaluateIndustryAutomatically(projectId)

        assertEquals(null, project.industry)
        assertEquals(null, project.industryConfidence)
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `evaluateIndustryAutomatically overwrites medium confidence with high confidence`() = runTest {
        val project = Project(
            id = projectId,
            name = "Test Project",
            industry = "Initial Domain",
            industryConfidence = "medium",
        )
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { projectRepository.save(any()) } answers { firstArg() }
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Refined Domain",
            confidence = "high",
            evidence = listOf("Strong evidence"),
        )

        service.evaluateIndustryAutomatically(projectId)

        assertEquals("Refined Domain", project.industry)
        assertEquals("high", project.industryConfidence)
        verify(exactly = 1) {
            projectRepository.save(match { it.industry == "Refined Domain" && it.industryConfidence == "high" })
        }
    }

    @Test
    fun `evaluateIndustryAutomatically does not overwrite high confidence with medium confidence`() = runTest {
        val project = Project(
            id = projectId,
            name = "Test Project",
            industry = "High Confidence Domain",
            industryConfidence = "high",
        )
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Worse Guess",
            confidence = "medium",
            evidence = listOf("Some evidence"),
        )

        service.evaluateIndustryAutomatically(projectId)

        assertEquals("High Confidence Domain", project.industry)
        assertEquals("high", project.industryConfidence)
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `evaluateIndustryAutomatically does not overwrite equal confidence`() = runTest {
        val project = Project(
            id = projectId,
            name = "Test Project",
            industry = "Existing Medium",
            industryConfidence = "medium",
        )
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } returns AiIndustryEvaluationResponse(
            industry = "Another Medium",
            confidence = "medium",
            evidence = listOf("Some evidence"),
        )

        service.evaluateIndustryAutomatically(projectId)

        assertEquals("Existing Medium", project.industry)
        assertEquals("medium", project.industryConfidence)
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `evaluateIndustryAutomatically logs and does not throw on AI failure`() = runTest {
        val project = Project(id = projectId, name = "Test Project")
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        coEvery { projectIndustryAiClient.evaluateIndustry(projectId) } throws
            ProjectIndustryAiException(statusCode = 500, body = "Crash", message = "AI unavailable")

        // Must not throw
        service.evaluateIndustryAutomatically(projectId)

        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `evaluateIndustryAutomatically does nothing when project does not exist`() = runTest {
        every { projectRepository.findById(projectId) } returns Optional.empty()

        // Must not throw
        service.evaluateIndustryAutomatically(projectId)

        coVerify(exactly = 0) { projectIndustryAiClient.evaluateIndustry(any()) }
    }
}

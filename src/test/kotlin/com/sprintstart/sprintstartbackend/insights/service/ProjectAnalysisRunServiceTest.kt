package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.model.dto.request.AnalysisFindingPayload
import com.sprintstart.sprintstartbackend.insights.model.dto.request.AnalysisTaskPayload
import com.sprintstart.sprintstartbackend.insights.model.dto.request.ProjectAnalysisLimits
import com.sprintstart.sprintstartbackend.insights.model.dto.request.SaveProjectAnalysisRunRequest
import com.sprintstart.sprintstartbackend.insights.model.entity.ProjectAnalysisRun
import com.sprintstart.sprintstartbackend.insights.repository.ProjectAnalysisRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

class ProjectAnalysisRunServiceTest {
    private val repository: ProjectAnalysisRunRepository = mockk(relaxed = true)
    private lateinit var service: ProjectAnalysisRunService

    private val projectId: UUID = UUID.randomUUID()

    private val findings = listOf(
        AnalysisFindingPayload("skips", "critical", "team", "2 skip requests waiting", "Anna and Ben", "/pm/team"),
        AnalysisFindingPayload("gaps", "warning", "gaps", "1 component without a README", "api"),
        AnalysisFindingPayload("inbox", "good", "escalations", "Inbox clear", ""),
    )

    private fun tasks(failed: Boolean = false) = listOf(
        AnalysisTaskPayload("team", "Team & open items", "done", "7 members"),
        AnalysisTaskPayload("gaps", "Knowledge gaps", if (failed) "failed" else "done"),
    )

    @BeforeEach
    fun setUp() {
        service = ProjectAnalysisRunService(repository)
        every { repository.save(any<ProjectAnalysisRun>()) } answers { firstArg() }
    }

    @Test
    fun `a stored run counts its findings by severity and survives the round trip`() {
        val saved = slot<ProjectAnalysisRun>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        val response = service.save(projectId, SaveProjectAnalysisRunRequest(72, findings, tasks()))

        assertEquals(72, response.score)
        assertEquals(mapOf("critical" to 1, "warning" to 1, "info" to 0, "good" to 1), response.counts)
        assertEquals(0, response.failedChecks)

        every { repository.findByProjectIdOrderByCreatedAtDesc(projectId, any()) } returns listOf(saved.captured)

        val read = service.list(projectId, 10).single()

        assertEquals(
            findings.map { listOf(it.id, it.severity, it.area, it.title, it.detail, it.to) },
            read.findings.map { listOf(it.id, it.severity, it.area, it.title, it.detail, it.to) },
        )
        assertEquals(
            tasks().map { listOf(it.id, it.label, it.status, it.note) },
            read.tasks.map { listOf(it.id, it.label, it.status, it.note) },
        )
        assertEquals(72, read.score)
    }

    @Test
    fun `a run with a failed check is stored without a score and with the failure counted`() {
        val response = service.save(projectId, SaveProjectAnalysisRunRequest(null, findings, tasks(failed = true)))

        assertNull(response.score)
        assertEquals(1, response.failedChecks)
    }

    @Test
    fun `a score on a run with a failed check is rejected with 400`() {
        val error = assertThrows<ResponseStatusException> {
            service.save(projectId, SaveProjectAnalysisRunRequest(90, findings, tasks(failed = true)))
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        verify(exactly = 0) { repository.save(any<ProjectAnalysisRun>()) }
    }

    @Test
    fun `storing a run trims the project's history to the kept runs`() {
        service.save(projectId, SaveProjectAnalysisRunRequest(72, findings, tasks()))

        verify { repository.deleteAllButNewest(projectId, ProjectAnalysisLimits.KEPT_RUNS) }
    }

    @Test
    fun `the read limit is clamped to the allowed range`() {
        val page = slot<Pageable>()
        every { repository.findByProjectIdOrderByCreatedAtDesc(projectId, capture(page)) } returns emptyList()

        service.list(projectId, 1000)
        assertEquals(ProjectAnalysisLimits.MAX_READ, page.captured.pageSize)

        service.list(projectId, 0)
        assertEquals(1, page.captured.pageSize)
    }

    @Test
    fun `a run whose stored results cannot be read is left out of the history`() {
        val broken = ProjectAnalysisRun(
            projectId = projectId,
            score = 50,
            failedChecks = 0,
            payload = "not json",
            createdAt = Instant.parse("2026-09-01T10:00:00Z"),
        )
        every { repository.findByProjectIdOrderByCreatedAtDesc(projectId, any()) } returns listOf(broken)

        assertEquals(emptyList<Any>(), service.list(projectId, 10))
    }

    @Test
    fun `deleting a project's runs goes through the repository and reports how many`() {
        every { repository.deleteByProjectId(projectId) } returns 3

        assertEquals(3, service.deleteForProject(projectId))
    }
}

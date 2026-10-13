package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.model.dto.request.AnalysisFindingPayload
import com.sprintstart.sprintstartbackend.insights.model.dto.request.AnalysisTaskPayload
import com.sprintstart.sprintstartbackend.insights.model.dto.request.ProjectAnalysisLimits
import com.sprintstart.sprintstartbackend.insights.model.dto.request.SaveProjectAnalysisRunRequest
import com.sprintstart.sprintstartbackend.insights.model.dto.response.AnalysisFindingResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.AnalysisTaskResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.ProjectAnalysisRunResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.ProjectAnalysisRun
import com.sprintstart.sprintstartbackend.insights.repository.ProjectAnalysisRunRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Keeps the history of a project's analyses, so "since the last run" means the same on every
 * device and for every PM of the project.
 *
 * The analysis is computed by the client, which already holds everything it reads; the server only
 * records the result. It does check what it can check: the counts and the number of failed checks
 * are derived here rather than taken from the request, and a run whose checks did not all finish
 * may not carry a score — the rule the client follows, enforced so a stored score always means a
 * complete run.
 *
 * Only the newest [ProjectAnalysisLimits.KEPT_RUNS] runs of a project are kept.
 */
@Service
class ProjectAnalysisRunService(
    private val projectAnalysisRunRepository: ProjectAnalysisRunRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Stores a finished analysis of [projectId] and drops the runs beyond the kept history.
     *
     * @throws ResponseStatusException 400 when the run has a score although a check failed.
     */
    @Transactional
    fun save(projectId: UUID, request: SaveProjectAnalysisRunRequest): ProjectAnalysisRunResponse {
        val failedChecks = request.tasks.count { it.status == FAILED }
        if (failedChecks > 0 && request.score != null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A project analysis with $failedChecks failed checks cannot have a health score",
            )
        }

        val run = projectAnalysisRunRepository.save(
            ProjectAnalysisRun(
                projectId = projectId,
                score = request.score,
                failedChecks = failedChecks,
                payload = json.encodeToString(
                    StoredPayload.serializer(),
                    StoredPayload(request.findings, request.tasks),
                ),
            ),
        )
        projectAnalysisRunRepository.deleteAllButNewest(projectId, ProjectAnalysisLimits.KEPT_RUNS)

        return toResponse(run, request.findings, request.tasks)
    }

    /**
     * The newest runs of [projectId], newest first.
     *
     * [limit] is clamped to 1..[ProjectAnalysisLimits.MAX_READ]. A run whose stored results can no
     * longer be read is left out rather than failing the whole history.
     */
    @Transactional(readOnly = true)
    fun list(projectId: UUID, limit: Int): List<ProjectAnalysisRunResponse> {
        val page = PageRequest.of(0, limit.coerceIn(1, ProjectAnalysisLimits.MAX_READ))
        return projectAnalysisRunRepository
            .findByProjectIdOrderByCreatedAtDesc(projectId, page)
            .mapNotNull { run ->
                decode(run)?.let { payload -> toResponse(run, payload.findings, payload.tasks) }
            }
    }

    /**
     * Deletes every run of [projectId] once the project itself is gone — nothing references the
     * project table, so the history would otherwise outlive it.
     *
     * In a transaction of its own: it runs after the project's deletion has committed.
     *
     * @return How many runs were deleted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun deleteForProject(projectId: UUID): Int = projectAnalysisRunRepository.deleteByProjectId(projectId)

    private fun decode(run: ProjectAnalysisRun): StoredPayload? =
        try {
            json.decodeFromString(StoredPayload.serializer(), run.payload)
        } catch (e: SerializationException) {
            logger.warn("Project analysis run {} could not be read and was left out", run.id, e)
            null
        }

    private fun toResponse(
        run: ProjectAnalysisRun,
        findings: List<AnalysisFindingPayload>,
        tasks: List<AnalysisTaskPayload>,
    ): ProjectAnalysisRunResponse {
        val counts = SEVERITIES.associateWith { severity -> findings.count { it.severity == severity } }
        return ProjectAnalysisRunResponse(
            id = run.id,
            at = run.createdAt,
            score = run.score,
            counts = counts,
            failedChecks = run.failedChecks,
            findings = findings.map { finding ->
                AnalysisFindingResponse(
                    id = finding.id,
                    severity = finding.severity,
                    area = finding.area,
                    title = finding.title,
                    detail = finding.detail,
                    to = finding.to,
                )
            },
            tasks = tasks.map { task ->
                AnalysisTaskResponse(id = task.id, label = task.label, status = task.status, note = task.note)
            },
        )
    }

    @Serializable
    private data class StoredPayload(
        val findings: List<AnalysisFindingPayload>,
        val tasks: List<AnalysisTaskPayload>,
    )

    private companion object {
        const val FAILED = "failed"
        val SEVERITIES = listOf("critical", "warning", "info", "good")
        val json = Json { ignoreUnknownKeys = true }
    }
}

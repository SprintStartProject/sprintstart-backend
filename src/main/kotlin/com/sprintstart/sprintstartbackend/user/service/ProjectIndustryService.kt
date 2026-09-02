package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryAiClient
import com.sprintstart.sprintstartbackend.user.external.model.AiIndustryEvaluationResponse
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Service for evaluating and persisting project industry domains via AI.
 *
 * Coordinates industry evaluation with the AI service and persists the result on the project.
 * Manual evaluation triggers always persist the evaluated industry and confidence, overriding
 * any previous values.
 */
@Service
class ProjectIndustryService(
    private val projectRepository: ProjectRepository,
    private val projectIndustryAiClient: ProjectIndustryAiClient,
) {
    /**
     * Evaluates the industry domain for a project via the AI service and persists the result.
     *
     * @param projectId Unique identifier of the project.
     * @return The AI evaluation response containing detected industry, confidence, and evidence.
     * @throws ResponseStatusException 404 when no project exists for [projectId].
     * @throws com.sprintstart.sprintstartbackend.user.model.exceptions.ProjectIndustryAiException
     *   when the AI service fails to respond or returns an error.
     */
    @Transactional
    @Tracked("Evaluating project industry")
    suspend fun evaluateIndustry(projectId: UUID): AiIndustryEvaluationResponse {
        val project = findProject(projectId)
        val response = projectIndustryAiClient.evaluateIndustry(projectId)

        project.industry = response.industry
        project.industryConfidence = response.confidence

        return response
    }

    /**
     * Finds a project by its unique identifier.
     *
     * @param id The unique identifier of the project to retrieve.
     * @return The project entity.
     * @throws ResponseStatusException 404 if no project is found with [id].
     */
    private fun findProject(id: UUID): Project {
        return projectRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project with id $id not found") }
    }
}

package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryAiClient
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import com.sprintstart.sprintstartbackend.user.external.model.AiIndustryEvaluationResponse
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.mapper.toIndustryResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectIndustryResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Service for evaluating and persisting project industry domains via AI.
 *
 * Coordinates industry evaluation with the AI service and persists the result on the project.
 * Manual evaluation triggers always persist the evaluated industry and confidence, overriding
 * any previous values. Automatic and lazy triggers enforce threshold and monotonicity rules.
 */
@Service
class ProjectIndustryService(
    private val projectRepository: ProjectRepository,
    private val projectIndustryAiClient: ProjectIndustryAiClient,
    transactionManager: PlatformTransactionManager,
) : ProjectIndustryApi {
    private val logger = LoggerFactory.getLogger(javaClass)

    // The AI call is a long-running suspend operation, so it must not run inside a
    // transaction (a DB connection would be pinned for its whole duration).
    private val txTemplate = TransactionTemplate(transactionManager)

    /**
     * Evaluates the industry domain for a project via the AI service and persists the result.
     *
     * Validates project existence before the AI call, runs the suspending AI evaluation outside
     * any transaction, and persists the result inside a transaction block with [TransactionTemplate].
     *
     * @param projectId Unique identifier of the project.
     * @return The AI evaluation response containing detected industry, confidence, and evidence.
     * @throws ResponseStatusException 404 when no project exists for [projectId].
     * @throws com.sprintstart.sprintstartbackend.user.model.exceptions.ProjectIndustryAiException
     *   when the AI service fails to respond or returns an error.
     */
    @Tracked("Evaluating project industry")
    override suspend fun evaluateIndustry(projectId: UUID): AiIndustryEvaluationResponse {
        withContext(Dispatchers.IO) { findProject(projectId) }
        val response = projectIndustryAiClient.evaluateIndustry(projectId)

        withContext(Dispatchers.IO) {
            txTemplate.executeWithoutResult {
                val project = findProject(projectId)
                project.industry = response.industry
                project.industryConfidence = response.confidence
                project.industryCustom = false
                projectRepository.save(project)
            }
        }

        return response
    }

    /**
     * Manually sets the industry for a project, marking it as custom rather than AI-evaluated.
     *
     * Clears any previously stored confidence, since it described an AI evaluation that this value
     * no longer represents. A later [evaluateIndustry] call overwrites this custom value.
     *
     * @param projectId Unique identifier of the project.
     * @param industry The industry to set, persisted trimmed.
     * @return The project's industry, confidence, and custom flag after the update.
     * @throws ResponseStatusException 404 when no project exists for [projectId].
     */
    @Transactional
    @Tracked("Setting project industry")
    fun setCustomIndustry(projectId: UUID, industry: String): ProjectIndustryResponse {
        val project = findProject(projectId)
        applyCustomIndustry(project, industry)
        return project.toIndustryResponse()
    }

    /**
     * Retrieves the persisted industry if present; otherwise queries the AI service lazily once.
     *
     * Persists only if the evaluated confidence is at least `medium`. Returns null on low confidence
     * or when evaluation fails, without throwing.
     */
    @Tracked("Getting or lazily evaluating project industry")
    override suspend fun getOrEvaluateIndustry(projectId: UUID): String? {
        val existingProject = withContext(Dispatchers.IO) { findProjectOrNull(projectId) } ?: return null
        if (!existingProject.industry.isNullOrBlank()) {
            return existingProject.industry
        }

        return try {
            val response = projectIndustryAiClient.evaluateIndustry(projectId)
            if (isEligibleConfidence(response.confidence) && response.industry.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    txTemplate.executeWithoutResult {
                        val project = findProject(projectId)
                        val shouldUpdate = project.industry.isNullOrBlank() ||
                            isHigherConfidence(response.confidence, project.industryConfidence)
                        if (shouldUpdate) {
                            project.industry = response.industry
                            project.industryConfidence = response.confidence
                            projectRepository.save(project)
                        }
                    }
                }
                response.industry
            } else {
                logger.debug(
                    "Lazy industry evaluation for project {} discarded due to low confidence: {}",
                    projectId,
                    response.confidence,
                )
                null
            }
        } catch (e: Exception) {
            logger.warn("Lazy industry evaluation failed for project {}: {}", projectId, e.message)
            null
        }
    }

    /**
     * Evaluates the project industry automatically (e.g. after an ingestion run) and persists
     * it only if confidence is at least `medium` and strictly higher than the current confidence.
     *
     * Failures are caught and logged so ingestion sync is never broken.
     */
    @Tracked("Evaluating project industry automatically")
    override suspend fun evaluateIndustryAutomatically(projectId: UUID) {
        try {
            val existingProject = withContext(Dispatchers.IO) { findProjectOrNull(projectId) } ?: run {
                logger.warn("Cannot auto-evaluate industry for non-existent project {}", projectId)
                return
            }

            val response = projectIndustryAiClient.evaluateIndustry(projectId)
            if (!isEligibleConfidence(response.confidence) || response.industry.isBlank()) {
                logger.debug(
                    "Auto industry evaluation for project {} discarded (confidence: {}, industry: '{}')",
                    projectId,
                    response.confidence,
                    response.industry,
                )
                return
            }

            withContext(Dispatchers.IO) {
                txTemplate.executeWithoutResult {
                    val project = findProject(projectId)
                    val shouldUpdate = project.industry.isNullOrBlank() ||
                        isHigherConfidence(response.confidence, project.industryConfidence)
                    if (shouldUpdate) {
                        logger.info(
                            "Updating industry for project {} from '{}' ({}) to '{}' ({})",
                            projectId,
                            project.industry,
                            project.industryConfidence,
                            response.industry,
                            response.confidence,
                        )
                        project.industry = response.industry
                        project.industryConfidence = response.confidence
                        projectRepository.save(project)
                    } else {
                        logger.debug(
                            "Auto industry evaluation for project {} discarded by monotonicity: " +
                                "stored confidence {} >= new {}",
                            projectId,
                            project.industryConfidence,
                            response.confidence,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Automatic industry evaluation failed for project {}: {}", projectId, e.message)
        }
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

    private fun findProjectOrNull(id: UUID): Project? {
        return projectRepository.findById(id).orElse(null)
    }

    private fun isEligibleConfidence(confidence: String?): Boolean {
        return confidenceRank(confidence) >= 1
    }

    private fun isHigherConfidence(newConfidence: String?, existingConfidence: String?): Boolean {
        return confidenceRank(newConfidence) > confidenceRank(existingConfidence)
    }

    private fun confidenceRank(confidence: String?): Int = when (confidence?.lowercase()) {
        "high" -> 2
        "medium" -> 1
        "low" -> 0
        else -> -1
    }

    companion object {
        /**
         * Applies a manually-set industry to [project]: the confidence is cleared because it is
         * meaningless for a value that did not come from the AI, and [Project.industryCustom] is
         * marked so callers (and the frontend) can tell it apart from an AI evaluation.
         */
        internal fun applyCustomIndustry(project: Project, industry: String) {
            project.industry = industry.trim()
            project.industryCustom = true
            project.industryConfidence = null
        }
    }
}

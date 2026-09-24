package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactAiIndexStatus
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactAiStatusItemResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactAiStatusResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactIngestStatusAiItem
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** Most ids one status request may ask about: one Knowledge Base page, and the AI's own cap. */
const val MAX_AI_STATUS_IDS = 100

/**
 * Tells the Knowledge Base whether each visible artifact is indexed for the AI assistant.
 *
 * A thin proxy over the AI service's read-only status endpoint. It never fails the page: an AI
 * outage turns into `aiAvailable = false` with UNKNOWN items, so the list still renders and the
 * frontend just hides the chips. Not `@Transactional`: the only database work is one id-only
 * lookup, and that annotation does not apply to suspend functions.
 */
@Service
class ArtifactAiStatusService(
    private val artifactRepository: ArtifactRepository,
    private val artifactIngestionClient: ArtifactIngestionClient,
    private val userApi: UserApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the AI index state of the requested artifacts that belong to the project.
     *
     * @param authId JWT subject; must have access to the project (same check as the list).
     * @param projectId The project whose artifacts are asked about.
     * @param artifactIds Requested ids; duplicates collapse, foreign or unknown ids are omitted
     *   silently so the endpoint never confirms that an id exists elsewhere.
     * @return Items in request order. Empty (and no AI call) when nothing visible was asked.
     * @throws ResponseStatusException `403` when the user has no access to the project.
     */
    suspend fun getAiStatus(authId: String, projectId: UUID, artifactIds: List<UUID>): ArtifactAiStatusResponse {
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project with id $projectId")
        }
        val visibleIds = visibleIds(projectId, artifactIds)
        if (visibleIds.isEmpty()) {
            return ArtifactAiStatusResponse(aiAvailable = true, items = emptyList())
        }
        return fetchStatuses(projectId, visibleIds)
    }

    private fun visibleIds(projectId: UUID, artifactIds: List<UUID>): List<UUID> {
        val requested = artifactIds.distinct()
        if (requested.isEmpty()) return emptyList()
        val inProject = artifactRepository.findIdsInProject(projectId, requested)
        return requested.filter { it in inProject }
    }

    private suspend fun fetchStatuses(projectId: UUID, visibleIds: List<UUID>): ArtifactAiStatusResponse {
        val aiItems = try {
            artifactIngestionClient.fetchIngestStatus(visibleIds).items
        } catch (e: Exception) {
            // A cancelled request must stay cancelled; anything else means "AI not answering".
            currentCoroutineContext().ensureActive()
            logger.warn(
                "AI status lookup for {} artifact(s) of project {} failed, reporting UNKNOWN: {}: {}",
                visibleIds.size,
                projectId,
                e.javaClass.simpleName,
                e.message,
            )
            null
        }
        if (aiItems == null) {
            return ArtifactAiStatusResponse(aiAvailable = false, items = visibleIds.map(::unknownItem))
        }
        val byId = aiItems.associateBy { it.artifactId.lowercase() }
        return ArtifactAiStatusResponse(
            aiAvailable = true,
            items = visibleIds.map { id -> byId[id.toString()]?.let { toItem(id, it) } ?: unknownItem(id) },
        )
    }

    private fun toItem(id: UUID, aiItem: ArtifactIngestStatusAiItem): ArtifactAiStatusItemResponse {
        val status = ArtifactAiIndexStatus.fromAi(aiItem.status)
        // UNKNOWN always carries nulls, whatever the AI sent, so it has a single meaning downstream.
        if (status == ArtifactAiIndexStatus.UNKNOWN) return unknownItem(id)
        return ArtifactAiStatusItemResponse(
            artifactId = id,
            status = status,
            updatedAt = aiItem.updatedAt,
            chunkCount = aiItem.chunkCount,
        )
    }

    private fun unknownItem(id: UUID) =
        ArtifactAiStatusItemResponse(
            artifactId = id,
            status = ArtifactAiIndexStatus.UNKNOWN,
            updatedAt = null,
            chunkCount = null,
        )
}

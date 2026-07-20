package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.insights.KnowledgeGapsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGapsRequest
import com.sprintstart.sprintstartbackend.insights.model.dto.request.SetComponentOwnersRequest
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapOwnerResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapsOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshKnowledgeGapsResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.ComponentOwner
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGap
import com.sprintstart.sprintstartbackend.insights.model.mapper.AiKnowledgeGapMapper
import com.sprintstart.sprintstartbackend.insights.model.mapper.KnowledgeGapResponseMapper
import com.sprintstart.sprintstartbackend.insights.repository.ComponentOwnerRepository
import com.sprintstart.sprintstartbackend.insights.repository.KnowledgeGapRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Serves the PM knowledge-gaps panel, keeps the AI classification cache up to date, and manages
 * component ownership.
 *
 * Read endpoints are served from the cache; owners are enriched at read time by matching a gap's
 * component against the component-ownership mapping and resolving the users via the user module. A
 * refresh delegates the detection to the AI service and rebuilds the cache.
 */
@Service
class KnowledgeGapsService(
    private val knowledgeGapRepository: KnowledgeGapRepository,
    private val componentOwnerRepository: ComponentOwnerRepository,
    private val knowledgeGapsAiClient: KnowledgeGapsAiClient,
    private val aiKnowledgeGapMapper: AiKnowledgeGapMapper,
    private val knowledgeGapResponseMapper: KnowledgeGapResponseMapper,
    private val userApi: UserApi,
    private val artifactIngestionApi: ArtifactIngestionApi,
) {
    /**
     * Returns all cached knowledge gaps, most severe first and then by component name.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all knowledge gaps")
    fun getKnowledgeGaps(): KnowledgeGapsOverviewResponse {
        val gaps = knowledgeGapRepository
            .findAll()
            .sortedWith(compareBy({ it.severity.ordinal }, { it.component }))
        val components = gaps.map { it.component }.distinct()
        val ownersByComponent = resolveOwners(components)
        val firstIngestedByComponent = artifactIngestionApi.getFirstIngestedAt(components)
        return knowledgeGapResponseMapper.toOverviewResponse(gaps, ownersByComponent, firstIngestedByComponent)
    }

    /**
     * Returns a single cached knowledge gap.
     *
     * @throws ResponseStatusException 404 if no gap with [gapId] exists.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving specific knowledge gap")
    fun getKnowledgeGap(gapId: UUID): KnowledgeGapResponse {
        val gap = knowledgeGapRepository.findById(gapId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge gap with id $gapId not found")
        }
        val owners = resolveOwners(listOf(gap.component))[gap.component] ?: emptyList()
        val firstIngested = artifactIngestionApi.getFirstIngestedAt(gap.component)
        return knowledgeGapResponseMapper.toResponse(gap, owners, firstIngested)
    }

    /**
     * Returns the currently assigned owners of a component.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving component owners")
    fun getComponentOwners(component: String): List<KnowledgeGapOwnerResponse> {
        return resolveOwners(listOf(component))[component] ?: emptyList()
    }

    /**
     * Replaces the owners of a component with the given users and returns the resolved owners.
     */
    @Transactional
    @Tracked("Setting component owners")
    fun setComponentOwners(request: SetComponentOwnersRequest): List<KnowledgeGapOwnerResponse> {
        componentOwnerRepository.deleteByComponent(request.component)
        val owners = request.userIds
            .distinct()
            .map { ComponentOwner(component = request.component, userId = it) }
        componentOwnerRepository.saveAll(owners)
        return resolveOwners(listOf(request.component))[request.component] ?: emptyList()
    }

    /**
     * Reclassifies knowledge gaps via the AI service and replaces the cache.
     *
     * The previous gaps are removed and the freshly classified result is stored as the new cache.
     * Component ownership is left untouched.
     *
     * @throws com.sprintstart.sprintstartbackend.insights.model.exceptions.KnowledgeGapsAiException
     *   if the AI service does not return a classification result.
     */
    @Tracked("Refreshing knowledge gaps")
    suspend fun refreshKnowledgeGaps(): RefreshKnowledgeGapsResponse {
        val aiResponse = knowledgeGapsAiClient.detectKnowledgeGaps(AiKnowledgeGapsRequest())
        val gaps: List<KnowledgeGap> = aiResponse.gaps.map { aiKnowledgeGapMapper.toEntity(it) }

        knowledgeGapRepository.deleteAll()
        knowledgeGapRepository.saveAll(gaps)

        return RefreshKnowledgeGapsResponse(gapCount = gaps.size)
    }

    /**
     * Resolves the owners of the given components into API DTOs.
     *
     * Loads the ownership rows for all requested components in one query, resolves the referenced
     * users through the user module in a single call, and groups the resulting owners by component.
     * The owner's role is derived from the user's first project role. Users that no longer exist are
     * dropped.
     */
    private fun resolveOwners(components: Collection<String>): Map<String, List<KnowledgeGapOwnerResponse>> {
        if (components.isEmpty()) {
            return emptyMap()
        }

        val mappings = componentOwnerRepository.findAllByComponentIn(components)
        if (mappings.isEmpty()) {
            return emptyMap()
        }

        val usersById = userApi
            .getUsersByIds(mappings.map { it.userId }.distinct())
            .associateBy { it.id }

        return mappings
            .groupBy { it.component }
            .mapValues { (_, rows) ->
                rows.mapNotNull { row ->
                    usersById[row.userId]?.let { user ->
                        KnowledgeGapOwnerResponse(
                            id = user.id.toString(),
                            username = user.username,
                            firstname = user.firstname,
                            lastname = user.lastname,
                            role = user.projectRoles.firstOrNull()?.name,
                        )
                    }
                }
            }
    }
}

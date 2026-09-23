package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * Custom repository fragment providing multi-criteria paginated projection queries
 * and aggregated facet calculations for project artifacts.
 */
interface ArtifactFacetRepository {
    /**
     * Resolves a paginated list of artifact projections using dynamic criteria without hydrating
     * the heavyweight `content` column.
     *
     * @param projectId Scopes artifacts to the target project.
     * @param criteria Filter criteria containing search text, types, sources, repos, and formats.
     * @param pageable Requested pagination and sorting.
     * @return Paginated page of artifact response projections.
     */
    fun findProjectArtifactsWithCriteria(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
        pageable: Pageable,
    ): Page<ArtifactResponse>

    /**
     * Resolves one artifact's metadata projection, scoped to a project the caller can see.
     *
     * Used to open a deep-linked artifact that is not on the page currently loaded, so it must
     * not hydrate `content` either — see [findProjectArtifactsWithCriteria].
     *
     * @param projectId Scopes the artifact to the target project.
     * @param artifactId The artifact to resolve.
     * @return The artifact projection, or null when it is not linked to that project.
     */
    fun findProjectArtifactById(
        projectId: UUID,
        artifactId: UUID,
    ): ArtifactResponse?

    /**
     * Calculates aggregated counts for types, sources, upload formats, and repositories
     * using the "count each would add" model (own-facet-excluded, other-facets-applied).
     *
     * @param projectId Scopes facet calculations to the target project.
     * @param criteria The currently active filter criteria.
     * @return Aggregated facet counts.
     */
    fun findFacets(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): ArtifactFacetsResponse
}

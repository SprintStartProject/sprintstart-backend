package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Persistence access for an artifact's project membership.
 *
 * Kept apart from [ArtifactRepository], which is about artifacts themselves: these queries resolve
 * artifacts *by the source they came from* so a source's project links can be applied to all of
 * them at once, and drop a deleted project across the corpus.
 */
interface ArtifactProjectRepository : Repository<Artifact, UUID> {
    /**
     * Returns every stored artifact of a GitHub component.
     *
     * The component is an `owner/repo` string; artifact source ids have the form
     * `github:owner/repo:TYPE:unique`, so they are matched by prefix.
     */
    @Query(
        "SELECT a FROM Artifact a WHERE a.sourceId LIKE CONCAT('github:', :component, ':%')",
    )
    fun findAllByComponent(
        @Param("component") component: String,
    ): List<Artifact>

    /**
     * Returns every stored artifact of a Jira instance.
     *
     * Jira issue artifacts store their web URL as `{instanceUrl}/browse/{key}`, so they are matched
     * by that prefix -- the Jira counterpart to [findAllByComponent].
     */
    @Query(
        "SELECT a FROM Artifact a " +
            "WHERE a.sourceSystem = com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem.JIRA " +
            "AND a.sourceUrl LIKE CONCAT(:instanceUrl, '/browse/%')",
    )
    fun findAllJiraArtifactsByInstanceUrl(
        @Param("instanceUrl") instanceUrl: String,
    ): List<Artifact>

    /**
     * Drops one project from every artifact that carries it.
     *
     * Written as a bulk delete on the join table rather than by loading the artifacts: a deleted
     * project can span the whole corpus, and none of the artifacts themselves change.
     *
     * @return The number of removed links.
     */
    @Modifying
    @Query(
        value = "DELETE FROM artifact_projects WHERE project_id = :projectId",
        nativeQuery = true,
    )
    fun deleteProjectLinks(
        @Param("projectId") projectId: UUID,
    ): Int
}

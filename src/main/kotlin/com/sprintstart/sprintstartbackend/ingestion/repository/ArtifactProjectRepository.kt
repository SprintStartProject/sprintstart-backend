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
     *
     * @param component The `owner/repo` whose artifacts should be returned.
     */
    fun findAllByComponent(component: String): List<Artifact> =
        findAllBySourceIdStartingWith(escapeLikeLiteral("github:$component:"))

    /**
     * Returns every stored artifact of a Jira instance.
     *
     * Jira issue artifacts store their web URL as `{instanceUrl}/browse/{key}`, so they are matched
     * by that prefix -- the Jira counterpart to [findAllByComponent].
     *
     * @param instanceUrl The Jira instance whose artifacts should be returned.
     */
    fun findAllJiraArtifactsByInstanceUrl(instanceUrl: String): List<Artifact> =
        findAllJiraArtifactsBySourceUrlStartingWith(escapeLikeLiteral("$instanceUrl/browse/"))

    /**
     * @param prefix A source-id prefix, already run through [escapeLikeLiteral].
     */
    @Query(
        "SELECT a FROM Artifact a WHERE a.sourceId LIKE CONCAT(:prefix, '%') ESCAPE '$LIKE_ESCAPE'",
    )
    fun findAllBySourceIdStartingWith(
        @Param("prefix") prefix: String,
    ): List<Artifact>

    /**
     * @param prefix A source-url prefix, already run through [escapeLikeLiteral].
     */
    @Query(
        "SELECT a FROM Artifact a " +
            "WHERE a.sourceSystem = com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem.JIRA " +
            "AND a.sourceUrl LIKE CONCAT(:prefix, '%') ESCAPE '$LIKE_ESCAPE'",
    )
    fun findAllJiraArtifactsBySourceUrlStartingWith(
        @Param("prefix") prefix: String,
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

/**
 * The `ESCAPE` character the prefix queries above declare.
 *
 * A backslash would be the conventional choice, but it is also an escape character in Kotlin, in
 * JPQL and in some JDBC drivers, and has to survive all three unchanged. `!` cannot appear in a
 * GitHub repository name or a host name at all, so it never even has to be escaped in practice.
 */
private const val LIKE_ESCAPE = "!"

/**
 * Makes a literal string safe to use as the fixed part of a `LIKE` pattern.
 *
 * `_` and `%` are wildcards, and `_` is legal in the values these queries match on: GitHub
 * repository names take it, and so do host names. Unescaped, connecting `acme/data_service` also
 * matched `acme/data-service`, and every artifact of a repository nobody named was re-scoped into
 * the project -- exactly the cross-project leak this propagation exists to prevent.
 *
 * The escape character itself is doubled first, so it cannot neutralize an escape added after it.
 */
internal fun escapeLikeLiteral(literal: String): String =
    literal
        .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
        .replace("%", LIKE_ESCAPE + "%")
        .replace("_", LIKE_ESCAPE + "_")

package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactAiSyncState
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Persistence access for stored artifacts and project-scoped artifact searches.
 *
 * One method per distinct read the rest of the system needs; the count tracks how many questions
 * are asked of artifacts, not a repository doing too many things.
 */
@Suppress("TooManyFunctions")
interface ArtifactRepository : JpaRepository<Artifact, UUID> {
    fun findBySourceId(sourceId: String): Artifact?

    fun findAllByIngestionRunId(runId: UUID): MutableList<Artifact>

    /**
     * Returns the next artifacts owed to the AI index, oldest first.
     *
     * Drives the incremental AI sync drainer: artifacts become `PENDING` as soon as they are
     * created or changed, so this deliberately spans runs -- a crawl that is still fetching has
     * pending artifacts worth embedding right now, and an artifact updated by a later run is owed
     * again even though its `ingestionRun` still points at the run that first created it.
     *
     * @param now Cut-off for the retry backoff; artifacts with no `aiSyncNextAttemptAt` are always
     * eligible.
     * @param pageable Caps the batch size (page 0 only -- drained rows leave the result set).
     */
    @Query(
        """
            SELECT a
            FROM Artifact a
            WHERE a.aiSyncState = com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactAiSyncState.PENDING
                AND (a.aiSyncNextAttemptAt IS NULL OR a.aiSyncNextAttemptAt <= :now)
            ORDER BY a.aiSyncNextAttemptAt ASC NULLS FIRST, a.ingestedAt ASC
        """,
    )
    fun findPendingAiSync(@Param("now") now: Instant, pageable: Pageable): List<Artifact>

    /**
     * Returns the artifacts a given GitHub account authored within one project.
     *
     * The basis for recognizing a hire's own prior work: with their declared `User.githubLogin`,
     * their issues and pull requests in the project's already-connected repositories can be found
     * without asking GitHub for anything new. Only issues and pull requests carry an author login
     * (see `Artifact.authorLogin`), so commits and files never match.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND a.authorLogin = :authorLogin
        """,
    )
    fun findAllByProjectIdAndAuthorLogin(
        @Param("projectId") projectId: UUID,
        @Param("authorLogin") authorLogin: String,
    ): List<Artifact>

    /**
     * Returns every artifact of one type within a project.
     *
     * Used to characterise a project's *repositories* rather than one person's work — for example
     * how long pull requests in each repo wait for their first response, which is a property of the
     * people who review there and cannot be derived from any single author's artifacts.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND a.artifactType = :artifactType
        """,
    )
    fun findAllByProjectIdAndArtifactType(
        @Param("projectId") projectId: UUID,
        @Param("artifactType") artifactType: ArtifactType,
    ): List<Artifact>

    /**
     * Returns every artifact of one type from one source system within a project.
     *
     * Exists because a tracked issue carries no `authorLogin` — the column is GitHub-only, and a
     * Jira issue's assignee lives inside its metadata JSON. Attribution therefore has to filter in
     * Kotlin, so the query narrows to the smallest honest set first: this project's issues from
     * this tracker, rather than every artifact it has.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND a.sourceSystem = :sourceSystem
                AND a.artifactType = :artifactType
        """,
    )
    fun findAllByProjectIdAndSourceSystemAndArtifactType(
        @Param("projectId") projectId: UUID,
        @Param("sourceSystem") sourceSystem: SourceSystem,
        @Param("artifactType") artifactType: ArtifactType,
    ): List<Artifact>

    fun countByAiSyncRunIdAndAiSyncState(runId: UUID, state: ArtifactAiSyncState): Long

    /**
     * Every project the run's artifacts belong to.
     *
     * An artifact can serve several projects (one repository connected to two of them), so this is
     * a flattened distinct set rather than one project per run.
     */
    @Query(
        "select distinct p from Artifact a join a.projectIdsInternal p where a.aiSyncRunId = :runId",
    )
    fun findDistinctProjectIdsByAiSyncRunId(runId: UUID): List<UUID>

    fun findAllByAiSyncRunIdAndAiSyncState(runId: UUID, state: ArtifactAiSyncState): List<Artifact>

    /**
     * Returns one artifact page limited to artifacts linked to the given project.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
        """,
    )
    fun findAllByProjectId(@Param("projectId") projectId: UUID, pageable: Pageable): Page<Artifact>

    /**
     * Returns one filtered artifact page limited to artifacts linked to the given project.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.artifactType) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.sourceSystem) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.metadata) LIKE LOWER(CONCAT('%', :filter, '%')))
        """,
    )
    fun searchByProjectId(
        @Param(
            "projectId",
        ) projectId: UUID,
        @Param("filter") filter: String, pageable: Pageable,
    ): Page<Artifact>

    fun deleteBySourceId(sourceId: String)

    /**
     * Returns one filtered artifact page across all projects.
     */
    @Query(
        """
            SELECT a
            FROM Artifact a
            WHERE LOWER(a.title) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.artifactType) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.sourceSystem) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.metadata) LIKE LOWER(CONCAT('%', :filter, '%'))
        """,
    )
    fun search(
        @Param("filter") filter: String, pageable: Pageable,
    ): Page<Artifact>

    /**
     * Returns the earliest ingestion timestamp across all artifacts of a GitHub component.
     *
     * The component is an `owner/repo` string; artifact source ids have the form
     * `github:owner/repo:TYPE:...`, so they are matched by prefix. Because existing artifacts are
     * updated in place on re-ingestion (their `ingestedAt` is immutable), the minimum is the time
     * the component was first ingested. Returns null when the component has no ingested artifacts.
     */
    @Query(
        "SELECT MIN(a.ingestedAt) FROM Artifact a WHERE a.sourceId LIKE CONCAT('github:', :component, ':%')",
    )
    fun findFirstIngestedAt(
        @Param("component") component: String,
    ): Instant?

    /**
     * Counts stored artifacts belonging to a GitHub component.
     *
     * The component is an `owner/repo` string; artifact source ids have the form
     * `github:owner/repo:TYPE:...`, so they are matched by prefix (mirroring [findFirstIngestedAt]).
     */
    @Query(
        "SELECT COUNT(a) FROM Artifact a WHERE a.sourceId LIKE CONCAT('github:', :component, ':%')",
    )
    fun countByComponent(
        @Param("component") component: String,
    ): Long

    /**
     * Counts stored artifacts belonging to a Jira instance.
     *
     * Jira issue artifacts store their web URL as `{instanceUrl}/browse/{key}`, so they are matched
     * by that prefix -- the Jira counterpart to [countByComponent] for GitHub.
     */
    @Query(
        "SELECT COUNT(a) FROM Artifact a " +
            "WHERE a.sourceSystem = com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem.JIRA " +
            "AND a.sourceUrl LIKE CONCAT(:instanceUrl, '/browse/%')",
    )
    fun countJiraArtifactsByInstanceUrl(
        @Param("instanceUrl") instanceUrl: String,
    ): Long
}

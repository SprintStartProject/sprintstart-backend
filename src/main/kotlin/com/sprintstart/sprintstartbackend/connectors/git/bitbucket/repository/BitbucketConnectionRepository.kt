package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
internal interface BitbucketConnectionRepository : JpaRepository<BitbucketConnection, UUID> {
    /**
     * Finds the connection for one repository addressed by its coordinates.
     *
     * @param workspace The Bitbucket workspace owning the repository.
     * @param slug The repository's slug within [workspace].
     * @return The connection, or `null` when the repository is not connected.
     */
    @Query(
        """
            SELECT b
            FROM BitbucketConnection b
            WHERE b.workspace = :workspace
            AND b.slug = :slug
        """,
    )
    fun findByWorkspaceAndSlug(
        @Param("workspace") workspace: String,
        @Param("slug") slug: String,
    ): BitbucketConnection?

    /**
     * Lists the connections linked to one project.
     *
     * The join is against the project-id collection, so a connection linked to several projects is
     * returned once — hence the distinct.
     *
     * @param projectId The project whose connections should be returned.
     */
    @Query(
        """
            SELECT DISTINCT b
            FROM BitbucketConnection b
            JOIN b.projectIdsInternal p
            WHERE p = :projectId
        """,
    )
    fun findAllByProjectId(@Param("projectId") projectId: UUID): List<BitbucketConnection>

    /**
     * Lists every connected repository of one workspace.
     *
     * The workspace metadata artifact belongs to no single repository, so its project links resolve
     * to the union of the projects of these connections.
     *
     * @param workspace The workspace whose connections should be returned.
     */
    @Query(
        """
            SELECT b
            FROM BitbucketConnection b
            WHERE b.workspace = :workspace
        """,
    )
    fun findAllByWorkspace(@Param("workspace") workspace: String): List<BitbucketConnection>

    /**
     * Writes only the connection state of one connection.
     *
     * Deliberately narrower than loading and saving the entity: the scheduled executor and the
     * collectors each save the connection for their own reasons, and a state write that carries a
     * stale snapshot of the whole row could resurrect a cursor one of them just advanced.
     *
     * @param repositoryId The connection whose state should change.
     * @param state The state to set.
     */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE BitbucketConnection b
        SET b.connectionState = :state
        WHERE b.id = :repositoryId
        """,
    )
    fun updateConnectionState(
        @Param("repositoryId") repositoryId: UUID,
        @Param("state") state: ConnectionState,
    )

    /**
     * Advances only the file cursor of one connection.
     *
     * Deliberately narrower than loading and saving the entity: the file, commit and pull-request
     * collectors run in parallel, and a cursor write that carries a stale snapshot of the whole row
     * would reset the cursors the others just advanced — or undo a `sourceEnabled` change made
     * mid-run.
     *
     * @param repositoryId The connection whose file cursor should advance.
     * @param sha The revision whose files were last ingested.
     */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE BitbucketConnection b
        SET b.lastSha = :sha
        WHERE b.id = :repositoryId
        """,
    )
    fun updateFileCursor(
        @Param("repositoryId") repositoryId: UUID,
        @Param("sha") sha: String,
    )

    /**
     * Advances only the commit cursor of one connection.
     *
     * Narrow for the same reason as [updateFileCursor]: parallel collectors must never write each
     * other's columns.
     *
     * @param repositoryId The connection whose commit cursor should advance.
     * @param sha The revision whose commits were last ingested.
     */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE BitbucketConnection b
        SET b.lastCommitsSyncedSha = :sha
        WHERE b.id = :repositoryId
        """,
    )
    fun updateCommitsCursor(
        @Param("repositoryId") repositoryId: UUID,
        @Param("sha") sha: String,
    )

    /**
     * Advances only the pull-request cursor of one connection.
     *
     * Narrow for the same reason as [updateFileCursor]: parallel collectors must never write each
     * other's columns.
     *
     * @param repositoryId The connection whose pull-request cursor should advance.
     * @param syncedAt The instant the successful fetch started.
     */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE BitbucketConnection b
        SET b.lastPullRequestsSyncAt = :syncedAt
        WHERE b.id = :repositoryId
        """,
    )
    fun updatePullRequestsCursor(
        @Param("repositoryId") repositoryId: UUID,
        @Param("syncedAt") syncedAt: Instant,
    )

    /**
     * Lists the connections of one workspace matching any of the given slugs.
     *
     * A plain slug list rather than workspace/slug tuples: discovery always scopes to one
     * workspace, and tuple `IN` lists cannot be bound as a query parameter.
     *
     * @param workspace The workspace the slugs belong to.
     * @param slugs The repository slugs to resolve.
     */
    @Query(
        """
            SELECT b
            FROM BitbucketConnection b
            WHERE b.workspace = :workspace
            AND b.slug IN :slugs
        """,
    )
    fun findAllByWorkspaceAndSlugIn(
        @Param("workspace") workspace: String,
        @Param("slugs") slugs: List<String>,
    ): List<BitbucketConnection>
}

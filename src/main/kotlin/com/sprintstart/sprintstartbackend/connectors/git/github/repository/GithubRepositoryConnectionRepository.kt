package com.sprintstart.sprintstartbackend.connectors.git.github.repository

import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubUser
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GithubRepositoryConnectionRepository : JpaRepository<GithubRepositoryConnection, UUID> {
    fun findByOwnerAndName(
        owner: String,
        name: String,
    ): GithubRepositoryConnection?

    fun findByUser(user: GithubUser): List<GithubRepositoryConnection>

    @Query(
        """
            SELECT DISTINCT r
            FROM GithubRepositoryConnection r
            JOIN r.projectIdsInternal p
            WHERE p = :projectId
        """,
    )
    fun findAllByProjectId(@Param("projectId") projectId: UUID): List<GithubRepositoryConnection>

    @Query(
        """
            SELECT DISTINCT p
            FROM GithubRepositoryConnection r
            JOIN r.projectIdsInternal p
            WHERE LOWER(r.owner) = LOWER(:owner)
        """,
    )
    fun findProjectIdsByOwner(@Param("owner") owner: String): Set<UUID>

    /**
     * Advances only the file cursor of one connection.
     *
     * Deliberately narrower than loading and saving the entity: the file, commit and pull-request
     * collectors run in parallel on their own copies of the row, and saving a whole copy writes the
     * cursors it read at the start of the run over the ones the others just advanced. A copy whose
     * snapshot was created in the same run also makes the merge try to insert a second snapshot for
     * the repository.
     *
     * @param repositoryId The connection whose file cursor should advance.
     * @param sha The revision whose files were last ingested.
     */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE GithubRepositoryConnection r
        SET r.lastSha = :sha
        WHERE r.id = :repositoryId
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
        UPDATE GithubRepositoryConnection r
        SET r.lastCommitsSyncedSha = :sha
        WHERE r.id = :repositoryId
        """,
    )
    fun updateCommitsCursor(
        @Param("repositoryId") repositoryId: UUID,
        @Param("sha") sha: String,
    )
}

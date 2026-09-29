package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface BitbucketConnectionRepository : JpaRepository<BitbucketConnection, UUID> {
    @Query(
        """
        select b from bitbucket_repositories b
        where b.workspace = :workspace
        and b.slug = :slug
        limit 1
    """,
        nativeQuery = true,
    )
    fun findByWorkspaceAndSlug(workspace: String, slug: String): BitbucketConnection?

    /**
     * Lists the connections linked to one project.
     *
     * The join is against the project-id collection rather than a mapped relation, so a connection
     * linked to several projects is returned once per matching project without duplicating itself —
     * hence the distinct.
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
     * Writes only the connection state of one connection.
     *
     * Deliberately narrower than loading and saving the entity: the scheduled executor and the
     * collectors each save the connection for their own reasons, and a state write that carries a
     * stale snapshot of the whole row could resurrect a cursor one of them just advanced.
     *
     * @param repositoryId The connection whose state should change.
     * @param state The state to set.
     */
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

    @Query(
        """
        SELECT b
        FROM BitbucketConnection b
        WHERE (b.workspace, b.slug) IN :combs
    """,
    )
    fun findExistingIdsByWorkspaceSlugCombinations(combs: List<Array<String>>): List<BitbucketConnection>
}

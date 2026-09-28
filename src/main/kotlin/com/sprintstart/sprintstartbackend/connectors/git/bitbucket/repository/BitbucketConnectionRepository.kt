package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import org.springframework.data.jpa.repository.JpaRepository
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
}

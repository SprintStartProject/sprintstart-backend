package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}

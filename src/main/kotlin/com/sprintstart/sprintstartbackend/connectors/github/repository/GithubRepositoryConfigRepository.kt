package com.sprintstart.sprintstartbackend.connectors.github.repository

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface GithubRepositoryConfigRepository : JpaRepository<GithubRepositoryConfig, UUID> {
    @Query(
        """
        SELECT * FROM gh_repository_configs c 
        WHERE c.next_sync_at <= :due
    """,
        nativeQuery = true,
    )
    fun findAllDue(due: Instant): List<GithubRepositoryConfig>
}

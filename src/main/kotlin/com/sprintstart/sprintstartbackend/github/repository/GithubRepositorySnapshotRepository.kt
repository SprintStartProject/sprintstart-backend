package com.sprintstart.sprintstartbackend.github.repository

import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface GithubRepositorySnapshotRepository : JpaRepository<GithubRepositorySnapshot, UUID> {
    @Query(
        """
        SELECT * FROM gh_repository_snapshots s 
        WHERE s.repository_id = :repositoryId 
        ORDER BY s.created_at DESC 
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findLatestByRepository(repositoryId: UUID): GithubRepositorySnapshot?
}

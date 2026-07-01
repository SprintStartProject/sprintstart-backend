package com.sprintstart.sprintstartbackend.github.repository

import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface GithubRepositorySnapshotRepository : JpaRepository<GithubRepositorySnapshot, UUID> {
    @Query("SELECT s FROM GithubRepositorySnapshot s WHERE s.id = :repositoryId")
    fun findLatestByRepository(@Param("repositoryId") repositoryId: UUID): GithubRepositorySnapshot?

    @Transactional
    @Modifying
    @Query(
        """UPDATE GithubRepositorySnapshot s
           SET s.lastCommitsSyncAt = :syncedAt,
               s.lastIssuesSyncAt = :syncedAt,
               s.lastPullRequestsSyncAt = :syncedAt
           WHERE s.id = :repositoryId""",
    )
    fun updateSyncTimestamps(
        @Param("repositoryId") repositoryId: UUID,
        @Param("syncedAt") syncedAt: Instant,
    )
}

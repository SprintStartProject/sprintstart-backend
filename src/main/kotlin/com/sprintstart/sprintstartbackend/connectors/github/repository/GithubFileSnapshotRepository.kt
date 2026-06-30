package com.sprintstart.sprintstartbackend.connectors.github.repository

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubFileSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface GithubFileSnapshotRepository : JpaRepository<GithubFileSnapshot, UUID> {
    @Query(
        """
        SELECT s FROM gh_file_snapshots s
        WHERE s.repository_id = :repositoryId
        AND s.path = :path
    """,
        nativeQuery = true,
    )
    fun findByCombinedId(
        repositoryId: UUID,
        path: String,
    ): GithubFileSnapshot?
}

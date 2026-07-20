package com.sprintstart.sprintstartbackend.connectors.github.repository

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import org.springframework.data.jpa.repository.JpaRepository
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
}

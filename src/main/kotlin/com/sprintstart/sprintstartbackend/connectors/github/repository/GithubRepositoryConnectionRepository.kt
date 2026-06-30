package com.sprintstart.sprintstartbackend.connectors.github.repository

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GithubRepositoryConnectionRepository : JpaRepository<GithubRepositoryConnection, UUID> {
    fun findByOwnerAndName(
        owner: String,
        name: String,
    ): GithubRepositoryConnection?

    fun findByUser(user: GithubUser): List<GithubRepositoryConnection>
}

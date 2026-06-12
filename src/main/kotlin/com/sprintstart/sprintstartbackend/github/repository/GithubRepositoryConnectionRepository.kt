package com.sprintstart.sprintstartbackend.github.repository

import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface GithubRepositoryConnectionRepository : JpaRepository<GithubRepositoryConnection, UUID> {
    fun findByOwnerAndName(
        owner: String,
        name: String,
    ): GithubRepositoryConnection?
}

package com.sprintstart.sprintstartbackend.starterwork.repository

import com.sprintstart.sprintstartbackend.starterwork.model.entity.GithubHistoryPrior
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Persistence access for the derived GitHub-history prior, keyed by user.
 */
interface GithubHistoryPriorRepository : JpaRepository<GithubHistoryPrior, UUID> {
    fun deleteAllByUserId(userId: UUID)
}

package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.GithubHistoryPrior
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Persistence access for the derived GitHub-history prior, keyed by user.
 */
interface GithubHistoryPriorRepository : JpaRepository<GithubHistoryPrior, UUID>

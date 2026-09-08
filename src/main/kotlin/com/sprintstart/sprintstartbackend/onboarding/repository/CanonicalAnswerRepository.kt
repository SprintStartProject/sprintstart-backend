package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CanonicalAnswerRepository : JpaRepository<CanonicalAnswer, UUID> {
    /** Every canonical answer on a project, for the PM to manage. */
    fun findAllByProjectIdOrderByUpdatedAtDesc(projectId: UUID): List<CanonicalAnswer>

    /** The answer pool the buddy's canonical-answer tool searches, across the caller's projects. */
    fun findAllByProjectIdIn(projectIds: Collection<UUID>): List<CanonicalAnswer>
}

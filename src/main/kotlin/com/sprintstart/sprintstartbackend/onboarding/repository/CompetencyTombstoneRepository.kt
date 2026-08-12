package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyTombstone
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CompetencyTombstoneRepository : JpaRepository<CompetencyTombstone, UUID> {
    fun findByKey(key: String): CompetencyTombstone?

    fun deleteByKey(key: String)
}

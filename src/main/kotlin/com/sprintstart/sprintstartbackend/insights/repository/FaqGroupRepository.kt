package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FaqGroupRepository : JpaRepository<FaqGroup, UUID> {
    /**
     * Returns all FAQ groups with the most frequently asked ones first.
     */
    fun findAllByOrderByOccurrenceCountDesc(): List<FaqGroup>
}

package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface FaqGroupRepository : JpaRepository<FaqGroup, UUID> {
    /**
     * Returns all FAQ groups with the most frequently asked ones first.
     */
    fun findAllByProjectIdOrderByOccurrenceCountDesc(projectId: UUID): List<FaqGroup>

    fun findByIdAndProjectId(id: UUID, projectId: UUID): Optional<FaqGroup>

    /**
     * Returns all groups of one category, most frequently asked first.
     *
     * Used when a category grows past its group ceiling and its duplicates have to be folded
     * together — the merge only ever looks at the category that actually overflowed.
     */
    fun findAllByProjectIdAndCategoryOrderByOccurrenceCountDesc(
        projectId: UUID,
        category: String,
    ): List<FaqGroup>

    fun deleteAllByProjectId(projectId: UUID)

    /** Clears cache rows from before insights were project-scoped. */
    fun deleteAllByProjectIdIsNull()
}

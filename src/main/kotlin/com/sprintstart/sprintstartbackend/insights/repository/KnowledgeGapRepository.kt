package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGap
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * Reads are project-scoped throughout: a gap detected for one project must never surface in
 * another's panel, and a gap id from a foreign project has to read as "not found".
 */
interface KnowledgeGapRepository : JpaRepository<KnowledgeGap, UUID> {
    fun findAllByProjectId(projectId: UUID): List<KnowledgeGap>

    /**
     * The project's gaps limited to the given components.
     *
     * Backs the "assigned to me" read: component ownership is not project-scoped, so the
     * components a user owns have to be intersected with the project being asked about
     * rather than looked up directly.
     */
    fun findAllByProjectIdAndComponentIn(projectId: UUID, components: Collection<String>): List<KnowledgeGap>

    fun findByIdAndProjectId(id: UUID, projectId: UUID): Optional<KnowledgeGap>

    fun deleteAllByProjectId(projectId: UUID)

    /** Clears cache rows from before insights were project-scoped. */
    fun deleteAllByProjectIdIsNull()
}

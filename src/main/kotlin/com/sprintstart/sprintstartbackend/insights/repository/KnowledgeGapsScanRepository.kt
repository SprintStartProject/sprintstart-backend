package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGapsScan
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Persistence access for the last knowledge-gaps scan of a project.
 */
interface KnowledgeGapsScanRepository : JpaRepository<KnowledgeGapsScan, UUID>

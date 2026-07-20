package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGap
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeGapRepository : JpaRepository<KnowledgeGap, UUID>

package com.sprintstart.sprintstartbackend.artifacts.repository

import com.sprintstart.sprintstartbackend.artifacts.model.entity.ArtifactSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ArtifactSummaryRepository : JpaRepository<ArtifactSummary, UUID>

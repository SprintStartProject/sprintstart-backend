package com.sprintstart.sprintstartbackend.upload.repository

import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UploadedArtifactRepository : JpaRepository<UploadedArtifact, UUID> {
    fun findByHashAndProjectId(hash: String, projectId: UUID): UploadedArtifact?

    fun findByIdAndProjectId(id: UUID, projectId: UUID): UploadedArtifact?

    fun findAllByProjectId(
        projectId: UUID,
    ): List<UploadedArtifact>
}

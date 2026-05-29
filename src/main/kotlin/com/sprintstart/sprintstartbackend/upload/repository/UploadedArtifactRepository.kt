package com.sprintstart.sprintstartbackend.upload.repository

import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UploadedArtifactRepository : JpaRepository<UploadedArtifact, UUID> {

    fun findByHash(hash: String): UploadedArtifact?

    fun existsByHash(hash: String): Boolean
}
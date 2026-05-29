package com.sprintstart.sprintstartbackend.upload.repository

import com.sprintstart.sprintstartbackend.upload.model.entity.ArtifactImage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ArtifactImageRepository : JpaRepository<ArtifactImage, UUID>
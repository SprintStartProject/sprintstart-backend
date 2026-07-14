package com.sprintstart.sprintstartbackend.upload.repository

import com.sprintstart.sprintstartbackend.upload.model.entity.LinkedImage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LinkedImageRepository : JpaRepository<LinkedImage, UUID> {
    fun deleteAllByMarkdownArtifactId(
        artifactId: UUID,
    )

    fun deleteAllByImageArtifactId(
        imageArtifactId: UUID,
    )
}

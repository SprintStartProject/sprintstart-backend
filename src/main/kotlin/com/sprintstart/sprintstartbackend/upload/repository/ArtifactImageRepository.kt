package com.sprintstart.sprintstartbackend.upload.repository

import com.sprintstart.sprintstartbackend.upload.model.entity.ArtifactImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ArtifactImageRepository : JpaRepository<ArtifactImage, UUID> {
    fun deleteAllByArtifactId(
        artifactId: UUID,
    )

    fun deleteAllByImageArtifactId(
        imageArtifactId: UUID,
    )
}

package com.sprintstart.sprintstartbackend.canonical.repository

import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import org.springframework.data.domain.Page
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.awt.print.Pageable
import java.util.UUID

interface ArtifactRepository : JpaRepository<Artifact, UUID> {
    fun findBySourceId(sourceId: String): Artifact?
    fun deleteBySourceId(sourceId: String)
    @Query(
        """
            SElECT a FROM Artifact a
            WHERE LOWER(a.title) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.artifactType) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.sourceSystem) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.repositoryFullName) LIKE LOWER(CONCAT('%', :filter, '%'))
        """
    )
    fun search(
        @Param("filter") filter: String, pageable: org.springframework.data.domain.Pageable
    ): Page<Artifact>

}

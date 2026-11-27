package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.dto.CommitMessage
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Reads only the messages of a project's commits, so finding the tickets they name loads nothing else. */
interface CommitMessageRepository : Repository<Artifact, UUID> {
    /** The message of every commit linked to a project. */
    @Query(
        """
            SELECT new com.sprintstart.sprintstartbackend.ingestion.model.dto.CommitMessage(a.id, a.content)
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND a.artifactType = com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType.COMMIT
        """,
    )
    fun findMessages(
        @Param("projectId") projectId: UUID,
    ): List<CommitMessage>
}

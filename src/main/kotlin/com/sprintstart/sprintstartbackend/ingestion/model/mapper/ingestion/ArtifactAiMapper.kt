package com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion

import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactAiIngestRequest
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ArtifactAiMapper {
    // .toList() copies out of the Hibernate-managed lazy collection into a plain immutable list
    // *while the session is still open* (the caller must run this within a transaction). Handing
    // out the live PersistentBag instead throws LazyInitializationException whenever the request
    // is later serialized -- e.g. for the outbound AI sync call, which runs after the read
    // transaction has already closed.
    fun toIngestRequest(artifact: Artifact) = ArtifactAiIngestRequest(
        artifactId = artifact.id.toString(),
        projectIds = artifact.projectIds.map(UUID::toString),
        sourceSystem = artifact.sourceSystem,
        sourceId = artifact.sourceId,
        sourceUrl = artifact.sourceUrl,
        artifactType = artifact.artifactType,
        title = artifact.title,
        bodyText = artifact.content,
        mime = artifact.mime,
        language = artifact.language,
        state = artifact.state,
        hasAssignee = artifact.hasAssignee,
        labels = artifact.labels.toList(),
    )
}

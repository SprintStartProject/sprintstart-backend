package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadArtifactMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

/**
 * Metadata is stored as a bare JSON object with no type tag, so reading it back into the sealed
 * [com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactMetadata] relies on DEDUCTION.
 * Without it, deserialization throws — which is what stalled the buddy's suggested-tasks tool.
 */
class ArtifactMetadataJsonMapperTest {
    private val mapper = ArtifactMetadataJsonMapper(jacksonObjectMapper())

    @Test
    fun `round-trips github metadata`() {
        val original = GithubArtifactMetadata(
            repositoryId = UUID.randomUUID(),
            repositoryFullName = "SprintStartProject/sprintstart-backend",
        )
        assertThat(mapper.fromJson(mapper.toJson(original))).isEqualTo(original)
    }

    @Test
    fun `round-trips upload metadata`() {
        val original = UploadArtifactMetadata(storagePath = "/tmp/x", actorId = UUID.randomUUID())
        assertThat(mapper.fromJson(mapper.toJson(original))).isEqualTo(original)
    }

    @Test
    fun `reads existing github rows that were persisted without a type discriminator`() {
        val id = UUID.randomUUID()
        // Exactly the shape already in the database (see the artifact table): no "type" property.
        val stored = """{"repositoryId":"$id","repositoryFullName":"SprintStartProject/repo"}"""

        val metadata = mapper.fromJson(stored)

        assertThat(metadata).isInstanceOf(GithubArtifactMetadata::class.java)
        assertThat((metadata as GithubArtifactMetadata).repositoryFullName)
            .isEqualTo("SprintStartProject/repo")
    }
}

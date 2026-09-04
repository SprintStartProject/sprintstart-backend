package com.sprintstart.sprintstartbackend.ingestion.model.dto

import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class ConfluenceArtifactMetadataTest {
    private val objectMapper = jacksonObjectMapper()
    private val metadataJsonMapper = ArtifactMetadataJsonMapper(objectMapper)

    @Test
    fun `serializes confluence page structure into artifact metadata json`() {
        val metadata = ConfluenceArtifactMetadata(
            sections = listOf(ArtifactSection(heading = "Deployment", level = 2)),
            tables = listOf("| Environment | Namespace |\n| --- | --- |\n| Production | prod |"),
            codeBlocks = listOf(ArtifactCodeBlock(language = "bash", code = "kubectl apply -f app.yaml")),
            relationships = listOf(
                ArtifactRelationship(
                    type = ArtifactRelationshipType.PARENT_OF,
                    targetSourceArtifactId = "123456",
                ),
            ),
            sourceAcl = """{"operation":"read"}""",
        )

        val json = metadataJsonMapper.toJson(metadata)
        val payload = objectMapper.readTree(json)

        assertThat(payload["sections"][0]["heading"].stringValue()).isEqualTo("Deployment")
        assertThat(payload["sections"][0]["level"].intValue()).isEqualTo(2)
        assertThat(payload["tables"][0].stringValue()).contains("| Production | prod |")
        assertThat(payload["codeBlocks"][0]["language"].stringValue()).isEqualTo("bash")
        assertThat(payload["codeBlocks"][0]["code"].stringValue()).isEqualTo("kubectl apply -f app.yaml")
        assertThat(payload["relationships"][0]["type"].stringValue()).isEqualTo("parent_of")
        assertThat(payload["relationships"][0]["targetSourceArtifactId"].stringValue()).isEqualTo("123456")
        assertThat(payload["sourceAcl"].stringValue()).isEqualTo("""{"operation":"read"}""")
    }

    @Test
    fun `defaults confluence structured metadata to empty collections`() {
        val json = metadataJsonMapper.toJson(ConfluenceArtifactMetadata())
        val payload = objectMapper.readTree(json)

        assertThat(payload["sections"]).isEmpty()
        assertThat(payload["tables"]).isEmpty()
        assertThat(payload["codeBlocks"]).isEmpty()
        assertThat(payload["relationships"]).isEmpty()
    }
}

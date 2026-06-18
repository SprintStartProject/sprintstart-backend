package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.IngestionAiClient
import com.sprintstart.sprintstartbackend.upload.events.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class ArtifactIngestionServiceTest {
    private val ingestionAiClient = mockk<IngestionAiClient>(relaxed = true)

    private lateinit var service: ArtifactIngestionService

    @BeforeEach
    fun setUp() {
        service = ArtifactIngestionService(ingestionAiClient)
    }

    @Test
    fun `uses upload artifact id when ingesting uploaded artifact`() = runTest {
        val artifactId = UUID.randomUUID()
        val event = ArtifactUploadedEvent(
            artifactId = artifactId,
            filename = "notes.md",
            storagePath = "/tmp/notes.md",
            mime = "text/markdown",
        )
        coEvery { ingestionAiClient.ingest(any()) } returns mockk()

        service.ingestUploadedArtifact(event, "# Notes")

        coVerify {
            ingestionAiClient.ingest(
                AiIngestRequest(
                    artifactId = artifactId.toString(),
                    filename = "notes.md",
                    content = "# Notes",
                ),
            )
        }
    }

    @Test
    fun `uses stable source-url-derived id when ingesting github file`() = runTest {
        val sourceUrl = "https://github.com/acme/repo/blob/sha/docs/guide.md"
        val expectedArtifactId =
            UUID.nameUUIDFromBytes(sourceUrl.toByteArray(StandardCharsets.UTF_8)).toString()
        coEvery { ingestionAiClient.ingest(any()) } returns mockk()

        service.ingestGithubFile("docs/guide.md", "# Guide", sourceUrl)

        coVerify {
            ingestionAiClient.ingest(
                AiIngestRequest(
                    artifactId = expectedArtifactId,
                    filename = "guide.md",
                    content = "# Guide",
                ),
            )
        }
    }

    @Test
    fun `keeps plain github filename unchanged`() = runTest {
        val sourceUrl = "https://github.com/acme/repo/blob/sha/README.md"
        val expectedArtifactId =
            UUID.nameUUIDFromBytes(sourceUrl.toByteArray(StandardCharsets.UTF_8)).toString()
        coEvery { ingestionAiClient.ingest(any()) } returns mockk()

        service.ingestGithubFile("README.md", "# Readme", sourceUrl)

        coVerify {
            ingestionAiClient.ingest(
                AiIngestRequest(
                    artifactId = expectedArtifactId,
                    filename = "README.md",
                    content = "# Readme",
                ),
            )
        }
    }
}

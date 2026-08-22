package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentRedirectResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.upload.external.api.UploadedArtifactReader
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Base64
import java.util.Optional
import java.util.UUID

class ArtifactServiceTest {
    private val userApi = mockk<UserApi>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val uploadedArtifactReader = mockk<UploadedArtifactReader>()
    private val service = ArtifactService(
        userApi = userApi,
        artifactRepository = artifactRepository,
        uploadedArtifactReader = uploadedArtifactReader,
    )

    private val authId = "auth-user"
    private val projectId = UUID.randomUUID()
    private val artifactId = UUID.randomUUID()
    private val uploadArtifactId = UUID.randomUUID()

    @Test
    fun `getArtifactContent decodes stored Base64 image content`() {
        val imageBytes = "fake-png-bytes".toByteArray()
        val encodedContent = Base64.getEncoder().encodeToString(imageBytes)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.UPLOAD,
                sourceId = uploadArtifactId.toString(),
                content = encodedContent,
                mime = "image/png",
            ),
        )

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentResponse

        assertThat(result.content).containsExactly(*imageBytes)
        assertThat(result.mime).isEqualTo("image/png")
        verify(exactly = 0) {
            uploadedArtifactReader.readBytes(any())
        }
    }

    @Test
    fun `getArtifactContent returns stored text content unchanged`() {
        val textContent = "hello"
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.UPLOAD,
                sourceId = uploadArtifactId.toString(),
                content = textContent,
                mime = "text/plain",
            ),
        )

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentResponse

        assertThat(result.content).containsExactly(*textContent.toByteArray())
        assertThat(result.mime).isEqualTo("text/plain")
        verify(exactly = 0) {
            uploadedArtifactReader.readBytes(any())
        }
    }

    @Test
    fun `getArtifactContent returns stored upload bytes for image artifacts`() {
        val imageBytes = byteArrayOf(1, 2, 3)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.UPLOAD,
                sourceId = uploadArtifactId.toString(),
                content = null,
                mime = "image/png",
            ),
        )
        every { uploadedArtifactReader.readBytes(uploadArtifactId) } returns imageBytes

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentResponse

        assertThat(result.content).containsExactly(1, 2, 3)
        assertThat(result.mime).isEqualTo("image/png")
        verify(exactly = 1) {
            uploadedArtifactReader.readBytes(uploadArtifactId)
        }
    }

    @Test
    fun `getArtifactContent returns stored upload bytes for pdf artifacts`() {
        val pdfBytes = "%PDF-1.7".toByteArray()
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.UPLOAD,
                sourceId = uploadArtifactId.toString(),
                content = null,
                mime = "application/pdf",
            ),
        )
        every { uploadedArtifactReader.readBytes(uploadArtifactId) } returns pdfBytes

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentResponse

        assertThat(result.content).containsExactly(*pdfBytes)
        assertThat(result.mime).isEqualTo("application/pdf")
        verify(exactly = 1) {
            uploadedArtifactReader.readBytes(uploadArtifactId)
        }
    }

    @Test
    fun `getArtifactContent returns text content without reading upload bytes`() {
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.UPLOAD,
                sourceId = uploadArtifactId.toString(),
                content = "# Notes",
                mime = "text/markdown",
            ),
        )

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentResponse

        assertThat(result.content).containsExactly(*"# Notes".toByteArray())
        assertThat(result.mime).isEqualTo("text/markdown")
        verify(exactly = 0) {
            uploadedArtifactReader.readBytes(any())
        }
    }

    @Test
    fun `getArtifactContent returns redirect for remote artifact without stored content`() {
        val sourceUrl = "https://github.com/owner/repo/blob/main/image.png"
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.GITHUB,
                sourceId = "repo:file:image.png",
                sourceUrl = sourceUrl,
                content = null,
                mime = "image/png",
            ),
        )

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentRedirectResponse

        assertThat(result.url).isEqualTo(sourceUrl)
        verify(exactly = 0) {
            uploadedArtifactReader.readBytes(any())
        }
    }

    @Test
    fun `getArtifactContent redirects non-upload artifact without reading UUID source id as upload`() {
        val sourceUrl = "https://jira.example.com/browse/SPR-123"
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.JIRA,
                sourceId = UUID.randomUUID().toString(),
                sourceUrl = sourceUrl,
                content = null,
                mime = null,
            ),
        )

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentRedirectResponse

        assertThat(result.url).isEqualTo(sourceUrl)
        verify(exactly = 0) {
            uploadedArtifactReader.readBytes(any())
        }
    }

    @Test
    fun `getArtifactContent redirects confluence page artifacts to source url`() {
        val sourceUrl = "https://example.atlassian.net/wiki/spaces/ENG/pages/123456"
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.CONFLUENCE,
                sourceId = "123456",
                sourceUrl = sourceUrl,
                content = null,
                mime = "text/html",
                artifactType = ArtifactType.PAGE,
            ),
        )

        val result = service.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = authId,
        ) as ArtifactContentRedirectResponse

        assertThat(result.url).isEqualTo(sourceUrl)
        verify(exactly = 0) {
            uploadedArtifactReader.readBytes(any())
        }
    }

    @Test
    fun `getArtifactContent returns not found when non-upload artifact has no content`() {
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findById(artifactId) } returns Optional.of(
            artifact(
                sourceSystem = SourceSystem.GITHUB,
                sourceId = "repo:file:README.md",
                sourceUrl = null,
                content = null,
                mime = "image/png",
            ),
        )

        assertThatThrownBy {
            service.getArtifactContent(
                projectId = projectId,
                artifactId = artifactId,
                authId = authId,
            )
        }.isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("404 NOT_FOUND")
    }

    private fun artifact(
        sourceSystem: SourceSystem,
        sourceId: String,
        sourceUrl: String? = null,
        content: String?,
        mime: String?,
        artifactType: ArtifactType = ArtifactType.FILE,
    ) = Artifact(
        sourceSystem = sourceSystem,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        artifactType = artifactType,
        title = "artifact",
        content = content,
        mime = mime,
        language = null,
        projectIdsInternal = mutableSetOf(projectId),
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = sourceSystem,
            status = IngestionRunStatus.COMPLETED,
        ),
        hash = "hash",
    )
}

package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.LinkedImageRepository
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import com.sprintstart.sprintstartbackend.upload.service.storage.ArtifactStorageService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UploadServiceTest {
    private val uploadedArtifactRepository = mockk<UploadedArtifactRepository>()
    private val linkedImageRepository = mockk<LinkedImageRepository>(relaxed = true)
    private val userApi = mockk<UserApi>()
    private val validationService = mockk<UploadValidationService>()
    private val storageService = mockk<ArtifactStorageService>()
    private val artifactLinkingService = mockk<ArtifactLinkingService>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = UploadService(
        uploadedArtifactRepository = uploadedArtifactRepository,
        linkedImageRepository = linkedImageRepository,
        userApi = userApi,
        validationService = validationService,
        storageService = storageService,
        artifactLinkingService = artifactLinkingService,
        publisher = publisher,
    )

    private val authId = "auth-user"
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val artifactId = UUID.randomUUID()

    private val file = MockMultipartFile(
        "files",
        "guide.md",
        "text/markdown",
        "# hello".toByteArray(),
    )

    @Test
    fun `upload stores artifact in project with resolved current user id`() {
        val savedArtifact = slot<UploadedArtifact>()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { validationService.validate(file) } returns Unit
        every { uploadedArtifactRepository.findByHashAndProjectId(any(), projectId) } returns null
        every { storageService.store(file, any()) } returns "uploads/guide.md"
        every { uploadedArtifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }
        every { artifactLinkingService.linkMarkdownImages(any(), any()) } returns emptySet()

        val result = service.upload(authId, listOf(file), projectId)

        assertEquals(1, result.size)
        assertEquals("guide.md", result.single().filename)
        assertEquals(userId, savedArtifact.captured.uploaderId)
        assertEquals(projectId, savedArtifact.captured.projectId)
        verify(exactly = 1) { uploadedArtifactRepository.findByHashAndProjectId(any(), projectId) }
    }

    @Test
    fun `upload reuses only project matching hash`() {
        val existingArtifact = UploadedArtifact(
            id = artifactId,
            filename = "guide.md",
            hash = "existing-hash",
            uploadedAt = Instant.now(),
            mime = "text/markdown",
            storagePath = "uploads/existing-guide.md",
            uploaderId = UUID.randomUUID(),
            projectId = projectId,
        )
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { validationService.validate(file) } returns Unit
        every { uploadedArtifactRepository.findByHashAndProjectId(any(), projectId) } returns existingArtifact
        every { artifactLinkingService.linkMarkdownImages(any(), any()) } returns emptySet()

        val result = service.upload(authId, listOf(file), projectId)

        assertEquals(artifactId, result.single().id)
        verify(exactly = 0) { storageService.store(any(), any()) }
        verify(exactly = 0) { uploadedArtifactRepository.save(any()) }
    }

    @Test
    fun `upload rejects inaccessible project before processing files`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        val ex = assertFailsWith<ResponseStatusException> {
            service.upload(authId, listOf(file), projectId)
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(exactly = 0) { validationService.validate(any()) }
        verify(exactly = 0) { uploadedArtifactRepository.save(any()) }
    }

    @Test
    fun `listUploads returns project artifacts after project access check`() {
        val artifact = artifact()
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { uploadedArtifactRepository.findAllByProjectId(projectId) } returns listOf(artifact)

        val result = service.listUploads(authId, projectId)

        assertEquals(1, result.size)
        assertEquals(artifactId, result.single().id)
        verify(exactly = 1) { uploadedArtifactRepository.findAllByProjectId(projectId) }
    }

    @Test
    fun `listUploads rejects inaccessible project before repository lookup`() {
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        val ex = assertFailsWith<ResponseStatusException> {
            service.listUploads(authId, projectId)
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(exactly = 0) { uploadedArtifactRepository.findAllByProjectId(any()) }
    }

    @Test
    fun `deleteUpload deletes artifact from requested project`() {
        val artifact = artifact()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) } returns artifact
        every { storageService.delete("uploads/guide.md") } returns Unit
        every { uploadedArtifactRepository.delete(artifact) } returns Unit

        service.deleteUpload(authId, setOf(artifactId), projectId)

        verify(exactly = 1) { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) }
        verify(exactly = 1) { storageService.delete("uploads/guide.md") }
        verify(exactly = 1) { uploadedArtifactRepository.delete(artifact) }
    }

    @Test
    fun `deleteUpload does not delete artifact outside requested project`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) } returns null

        service.deleteUpload(authId, setOf(artifactId), projectId)

        verify(exactly = 1) { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) }
        verify(exactly = 0) { storageService.delete(any()) }
        verify(exactly = 0) { uploadedArtifactRepository.delete(any()) }
    }

    private fun artifact(): UploadedArtifact =
        UploadedArtifact(
            id = artifactId,
            filename = "guide.md",
            hash = "hash",
            uploadedAt = Instant.now(),
            mime = "text/markdown",
            storagePath = "uploads/guide.md",
            uploaderId = userId,
            projectId = projectId,
        )
}

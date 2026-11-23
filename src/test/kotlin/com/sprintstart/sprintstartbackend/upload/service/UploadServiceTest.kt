package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactStatus
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchDeletionFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadFileDeletedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadStartedEvent
import com.sprintstart.sprintstartbackend.upload.model.dto.response.DeleteUploadFailure
import com.sprintstart.sprintstartbackend.upload.model.dto.response.DeleteUploadsResponse
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
import java.security.MessageDigest
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
        verify(exactly = 1) { publisher.publishEvent(match<UploadStartedEvent> { it.projectId == projectId }) }
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

        val result = service.deleteUpload(authId, setOf(artifactId), projectId)

        assertEquals(DeleteUploadsResponse(deletedIds = listOf(artifactId), failed = emptyList()), result)
        verify(exactly = 1) { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) }
        verify(exactly = 1) { storageService.delete("uploads/guide.md") }
        verify(exactly = 1) { uploadedArtifactRepository.delete(artifact) }
    }

    @Test
    fun `deleteUpload does not delete artifact outside requested project`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) } returns null

        val result = service.deleteUpload(authId, setOf(artifactId), projectId)

        assertEquals(emptyList(), result.deletedIds)
        assertEquals(
            listOf(DeleteUploadFailure(artifactId, "Artifact with id $artifactId not found.")),
            result.failed,
        )
        verify(exactly = 1) { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) }
        verify(exactly = 0) { storageService.delete(any()) }
        verify(exactly = 0) { uploadedArtifactRepository.delete(any()) }
    }

    @Test
    fun `deleteUpload reports a storage failure with the generic reason and keeps the raw one in the event`() {
        val brokenId = UUID.randomUUID()
        val broken = artifact(id = brokenId, storagePath = "uploads/broken.md")
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { uploadedArtifactRepository.findByIdAndProjectId(brokenId, projectId) } returns broken
        every { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) } returns artifact()
        every { storageService.delete("uploads/broken.md") } throws IllegalStateException("disk gone: /srv/uploads")
        every { storageService.delete("uploads/guide.md") } returns Unit
        every { uploadedArtifactRepository.delete(any()) } returns Unit

        val result = service.deleteUpload(authId, linkedSetOf(brokenId, artifactId), projectId)

        assertEquals(listOf(artifactId), result.deletedIds)
        assertEquals(listOf(DeleteUploadFailure(brokenId, "Artifact could not be deleted.")), result.failed)
        verify(exactly = 1) {
            publisher.publishEvent(
                match<UploadBatchDeletionFinishedEvent> { event ->
                    event.deleteArtifactOutcomes.map { it.id to it.error } ==
                        listOf(brokenId to "disk gone: /srv/uploads")
                },
            )
        }
    }

    @Test
    fun `upload announces what it stored, with the content hash, and links the markdown it saw`() {
        val savedArtifact = slot<UploadedArtifact>()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { validationService.validate(file) } returns Unit
        every { uploadedArtifactRepository.findByHashAndProjectId(any(), projectId) } returns null
        every { storageService.store(file, any()) } returns "uploads/guide.md"
        every { uploadedArtifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }
        val markdown = slot<List<Pair<UploadedArtifact, String>>>()
        val byFilename = slot<Map<String, UploadedArtifact>>()
        every { artifactLinkingService.linkMarkdownImages(capture(markdown), capture(byFilename)) } returns emptySet()

        service.upload(authId, listOf(file), projectId)

        val expectedHash = sha256Hex("# hello")
        val stored = savedArtifact.captured
        assertEquals(expectedHash, stored.hash)
        assertEquals("uploads/guide.md", stored.storagePath)
        assertEquals(listOf(stored to "# hello"), markdown.captured)
        assertEquals(mapOf("guide.md" to stored), byFilename.captured)

        val uploaded = slot<ArtifactUploadedEvent>()
        val finished = slot<UploadBatchFinishedEvent>()
        verify { publisher.publishEvent(capture(uploaded)) }
        verify { publisher.publishEvent(capture(finished)) }
        assertEquals(stored.id, uploaded.captured.artifactId)
        assertEquals(projectId, uploaded.captured.projectId)
        assertEquals(userId, uploaded.captured.uploaderId)
        assertEquals("uploads/guide.md", uploaded.captured.storagePath)
        assertEquals(expectedHash, uploaded.captured.hash)
        assertEquals("text/markdown", uploaded.captured.mime)
        assertEquals(userId, finished.captured.uploaderId)
        assertEquals(setOf(stored.id), finished.captured.artifactsId)
        assertEquals(
            listOf(UploadArtifactStatus.STORED to stored.id),
            finished.captured.uploadArtifactOperationOutcomes.map { it.status to it.id },
        )
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `upload reports a file that is already there as already uploaded`() {
        val existing = artifact()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { validationService.validate(file) } returns Unit
        every { uploadedArtifactRepository.findByHashAndProjectId(any(), projectId) } returns existing
        every { artifactLinkingService.linkMarkdownImages(any(), any()) } returns emptySet()

        service.upload(authId, listOf(file), projectId)

        val finished = slot<UploadBatchFinishedEvent>()
        verify { publisher.publishEvent(capture(finished)) }
        assertEquals(
            listOf(UploadArtifactStatus.ALREADY_UPLOADED to artifactId),
            finished.captured.uploadArtifactOperationOutcomes.map { it.status to it.id },
        )
    }

    @Test
    fun `upload records a rejected file as failed and carries on with the next one`() {
        val bad = MockMultipartFile("files", "bad.exe", "application/x-msdownload", "MZ".toByteArray())
        val saved = slot<UploadedArtifact>()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { validationService.validate(bad) } throws IllegalArgumentException("not allowed")
        every { validationService.validate(file) } returns Unit
        every { uploadedArtifactRepository.findByHashAndProjectId(any(), projectId) } returns null
        every { storageService.store(file, any()) } returns "uploads/guide.md"
        every { uploadedArtifactRepository.save(capture(saved)) } answers { saved.captured }
        every { artifactLinkingService.linkMarkdownImages(any(), any()) } returns emptySet()

        val result = service.upload(authId, listOf(bad, file), projectId)

        assertEquals(listOf("failed", "ok"), result.map { it.status })
        assertEquals("not allowed", result.first().error)
        assertEquals("bad.exe", result.first().filename)
        val finished = slot<UploadBatchFinishedEvent>()
        verify { publisher.publishEvent(capture(finished)) }
        val outcomes = finished.captured.uploadArtifactOperationOutcomes
        val statuses = outcomes.map { it.status }.toSet()
        assertEquals(setOf(UploadArtifactStatus.FAILED, UploadArtifactStatus.STORED), statuses)
    }

    @Test
    fun `upload for a user without a local projection is a 404`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

        val ex = assertFailsWith<ResponseStatusException> { service.upload(authId, listOf(file), projectId) }

        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `deleteUpload unlinks the images, announces the deletion and the batch`() {
        val artifact = artifact()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) } returns artifact
        every { storageService.delete("uploads/guide.md") } returns Unit
        every { uploadedArtifactRepository.delete(artifact) } returns Unit

        service.deleteUpload(authId, setOf(artifactId), projectId)

        verify(exactly = 1) { linkedImageRepository.deleteAllByMarkdownArtifactId(artifactId) }
        verify(exactly = 1) { linkedImageRepository.deleteAllByImageArtifactId(artifactId) }
        verify(exactly = 1) {
            publisher.publishEvent(match<UploadFileDeletedEvent> { it.uploadArtifactId == artifactId })
        }
        verify(exactly = 1) { publisher.publishEvent(match<UploadStartedEvent> { it.projectId == projectId }) }
        verify(exactly = 1) {
            publisher.publishEvent(match<UploadBatchDeletionFinishedEvent> { it.removerId == userId })
        }
    }

    @Test
    fun `deleteUpload leaves the images and the row alone when the artifact is not in the project`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId) } returns null

        service.deleteUpload(authId, setOf(artifactId), projectId)

        verify(exactly = 0) { linkedImageRepository.deleteAllByMarkdownArtifactId(any()) }
        verify(exactly = 0) { publisher.publishEvent(ofType<UploadFileDeletedEvent>()) }
    }

    private fun artifact(
        id: UUID = artifactId,
        storagePath: String = "uploads/guide.md",
    ): UploadedArtifact =
        UploadedArtifact(
            id = id,
            filename = "guide.md",
            hash = "hash",
            uploadedAt = Instant.now(),
            mime = "text/markdown",
            storagePath = storagePath,
            uploaderId = userId,
            projectId = projectId,
        )
}

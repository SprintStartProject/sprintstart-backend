package com.sprintstart.sprintstartbackend.upload

import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import com.sprintstart.sprintstartbackend.upload.service.ArtifactLinkingService
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import com.sprintstart.sprintstartbackend.upload.service.UploadValidationService
import com.sprintstart.sprintstartbackend.upload.service.storage.ArtifactStorageService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockMultipartFile
import java.util.UUID

class UploadServiceTest {

    private val uploadedArtifactRepository =
        mock<UploadedArtifactRepository>()

    private val userApi =
        mock<UserApi>()

    private val validationService =
        mock<UploadValidationService>()

    private val storageService =
        mock<ArtifactStorageService>()

    private val artifactLinkingService =
        mock<ArtifactLinkingService>()

    private val publisher =
        mock<ApplicationEventPublisher>()

    private val uploadService = UploadService(
        uploadedArtifactRepository =
            uploadedArtifactRepository,

        userApi = userApi,

        validationService = validationService,

        storageService = storageService,

        artifactLinkingService =
            artifactLinkingService,

        publisher = publisher,
    )

    @Test
    fun `should upload markdown file`() {

        val uploaderId = UUID.randomUUID()

        whenever(userApi.exists(uploaderId))
            .thenReturn(true)

        whenever(
            storageService.store(any(), any())
        ).thenReturn("uploads/test.md")

        whenever(
            uploadedArtifactRepository.save(any())
        ).thenAnswer { invocation ->
            invocation.arguments[0] as UploadedArtifact
        }

        val file = MockMultipartFile(
            "files",
            "intro.md",
            "text/markdown",
            "# Intro".toByteArray(),
        )

        val result = uploadService.upload(
            files = listOf(file),
            uploaderId = uploaderId,
        )

        assertEquals(1, result.size)
        assertEquals("ok", result[0].status)

        verify(uploadedArtifactRepository)
            .save(any())
    }

    @Test
    fun `should return existing artifact when hash already exists`() {

        val uploaderId = UUID.randomUUID()

        whenever(userApi.exists(uploaderId))
            .thenReturn(true)

        val existingArtifact = UploadedArtifact(
            filename = "intro.md",
            hash = "hash",
            mime = "text/markdown",
            storagePath = "uploads/test.md",
            uploaderId = uploaderId,
        )

        whenever(
            uploadedArtifactRepository.findByHash(any())
        ).thenReturn(existingArtifact)

        val file = MockMultipartFile(
            "files",
            "intro.md",
            "text/markdown",
            "# Intro".toByteArray(),
        )

        val result = uploadService.upload(
            files = listOf(file),
            uploaderId = uploaderId,
        )

        assertEquals("ok", result[0].status)

        assertEquals(
            existingArtifact.id,
            result[0].id,
        )

        verify(uploadedArtifactRepository, never())
            .save(any())
    }

    @Test
    fun `should return failed status for invalid file`() {

        val uploaderId = UUID.randomUUID()

        whenever(userApi.exists(uploaderId))
            .thenReturn(true)

        doThrow(
            IllegalArgumentException("Invalid file")
        ).whenever(validationService)
            .validate(any())

        val file = MockMultipartFile(
            "files",
            "bad.exe",
            "application/octet-stream",
            "bad".toByteArray(),
        )

        val result = uploadService.upload(
            files = listOf(file),
            uploaderId = uploaderId,
        )

        assertEquals(1, result.size)
        assertEquals("failed", result[0].status)
    }
}
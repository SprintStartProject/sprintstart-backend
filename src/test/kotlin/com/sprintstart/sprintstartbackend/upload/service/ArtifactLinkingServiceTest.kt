package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.model.entity.ArtifactImage
import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.ArtifactImageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.UUID

class ArtifactLinkingServiceTest {
    private val repository =
        Mockito.mock(ArtifactImageRepository::class.java)

    private val service =
        ArtifactLinkingService(
            repository,
            MarkdownImageReferenceExtractor(),
        )

    @Test
    fun `creates link when image exists`() {
        val markdownArtifact =
            artifact("doc.md", "text/markdown")

        val imageArtifact =
            artifact("logo.png", "image/png")

        service.linkMarkdownImages(
            markdownArtifacts = listOf(
                markdownArtifact to "![Logo](logo.png)",
            ),
            uploadedArtifactsByFilename = mapOf(
                "logo.png" to imageArtifact,
            ),
        )

        val captor =
            ArgumentCaptor.forClass(ArtifactImage::class.java)

        Mockito.verify(repository).save(captor.capture())

        val saved = captor.value

        assertEquals(markdownArtifact.id, saved.artifact.id)
        assertEquals(imageArtifact.id, saved.imageArtifact.id)
        assertEquals("logo.png", saved.originalPath)
    }

    @Test
    fun `ignores missing image`() {
        val markdownArtifact =
            artifact("doc.md", "text/markdown")

        service.linkMarkdownImages(
            markdownArtifacts = listOf(
                markdownArtifact to "![Logo](missing.png)",
            ),
            uploadedArtifactsByFilename = emptyMap(),
        )

        Mockito
            .verify(repository, Mockito.never())
            .save(ArgumentMatchers.any())
    }

    @Test
    fun `creates multiple links`() {
        val markdownArtifact =
            artifact("doc.md", "text/markdown")

        val firstImage =
            artifact("one.png", "image/png")

        val secondImage =
            artifact("two.png", "image/png")

        service.linkMarkdownImages(
            markdownArtifacts = listOf(
                markdownArtifact to
                    """
                    ![One](one.png)
                    ![Two](two.png)
                    """.trimIndent(),
            ),
            uploadedArtifactsByFilename = mapOf(
                "one.png" to firstImage,
                "two.png" to secondImage,
            ),
        )

        Mockito
            .verify(repository, Mockito.times(2))
            .save(ArgumentMatchers.any())
    }

    @Test
    fun `normalizes path before lookup`() {
        val markdownArtifact =
            artifact("doc.md", "text/markdown")

        val imageArtifact =
            artifact("logo.png", "image/png")

        service.linkMarkdownImages(
            markdownArtifacts = listOf(
                markdownArtifact to "![Logo](images/logo.png)",
            ),
            uploadedArtifactsByFilename = mapOf(
                "logo.png" to imageArtifact,
            ),
        )

        Mockito.verify(repository).save(ArgumentMatchers.any())
    }

    @Test
    fun `processes multiple markdown files`() {
        val markdownOne =
            artifact("one.md", "text/markdown")

        val markdownTwo =
            artifact("two.md", "text/markdown")

        val imageOne =
            artifact("one.png", "image/png")

        val imageTwo =
            artifact("two.png", "image/png")

        service.linkMarkdownImages(
            markdownArtifacts = listOf(
                markdownOne to "![One](one.png)",
                markdownTwo to "![Two](two.png)",
            ),
            uploadedArtifactsByFilename = mapOf(
                "one.png" to imageOne,
                "two.png" to imageTwo,
            ),
        )

        Mockito
            .verify(repository, Mockito.times(2))
            .save(ArgumentMatchers.any())
    }

    private fun artifact(
        filename: String,
        mime: String,
    ): UploadedArtifact =
        UploadedArtifact(
            filename = filename,
            hash = UUID.randomUUID().toString(),
            mime = mime,
            storagePath = "/tmp/$filename",
            uploaderId = UUID.randomUUID(),
        )
}

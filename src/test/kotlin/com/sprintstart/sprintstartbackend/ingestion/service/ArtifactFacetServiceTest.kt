package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactFacetRepositoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArtifactFacetServiceTest {
    @Test
    fun `extractRepositoryFromSourceId extracts owner and repo correctly`() {
        val standard = "github:SprintStartProject/sprintstart-backend:FILE:README.md"
        assertThat(ArtifactFacetRepositoryImpl.extractRepositoryFromSourceId(standard))
            .isEqualTo("SprintStartProject/sprintstart-backend")

        val issue = "github:owner/repo:ISSUE:42"
        assertThat(ArtifactFacetRepositoryImpl.extractRepositoryFromSourceId(issue))
            .isEqualTo("owner/repo")

        val invalid = "jira:INSTANCE:ISSUE:101"
        assertThat(ArtifactFacetRepositoryImpl.extractRepositoryFromSourceId(invalid)).isNull()

        val empty = ""
        assertThat(ArtifactFacetRepositoryImpl.extractRepositoryFromSourceId(empty)).isNull()
    }

    @Test
    fun `classifyUploadFormat correctly classifies PDF`() {
        val pdfMime = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "document",
            sourceUrl = null,
            sourceId = "uuid",
            mime = "application/pdf",
            language = null,
        )
        assertThat(pdfMime).isEqualTo(UploadFormat.PDF)

        val pdfExt = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "guide.pdf",
            sourceUrl = null,
            sourceId = "uuid",
            mime = null,
            language = null,
        )
        assertThat(pdfExt).isEqualTo(UploadFormat.PDF)
    }

    @Test
    fun `classifyUploadFormat correctly classifies Markdown`() {
        val mdLang = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "README",
            sourceUrl = null,
            sourceId = "uuid",
            mime = null,
            language = "markdown",
        )
        assertThat(mdLang).isEqualTo(UploadFormat.MARKDOWN)

        val mdExt = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "notes.md",
            sourceUrl = null,
            sourceId = "uuid",
            mime = "text/plain",
            language = null,
        )
        assertThat(mdExt).isEqualTo(UploadFormat.MARKDOWN)
    }

    @Test
    fun `classifyUploadFormat correctly classifies Images`() {
        val imgMime = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "diagram",
            sourceUrl = null,
            sourceId = "uuid",
            mime = "image/png",
            language = null,
        )
        assertThat(imgMime).isEqualTo(UploadFormat.IMAGE)

        val imgExt = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "architecture.svg",
            sourceUrl = null,
            sourceId = "uuid",
            mime = null,
            language = null,
        )
        assertThat(imgExt).isEqualTo(UploadFormat.IMAGE)
    }

    @Test
    fun `classifyUploadFormat falls back to Other`() {
        val other = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "archive.zip",
            sourceUrl = null,
            sourceId = "uuid",
            mime = "application/zip",
            language = null,
        )
        assertThat(other).isEqualTo(UploadFormat.OTHER)
    }
}

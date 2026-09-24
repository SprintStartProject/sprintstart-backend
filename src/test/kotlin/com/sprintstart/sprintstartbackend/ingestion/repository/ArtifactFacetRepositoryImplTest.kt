package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.FacetCountResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArtifactFacetRepositoryImplTest {
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
    fun `classifyUploadFormat buckets a row with no mime and an unknown extension as OTHER`() {
        // The facet counts come from this classifier while the filter runs as SQL. The predicate
        // folds null columns to "" for exactly this row, so a plain .txt upload stays reachable
        // through the OTHER filter instead of being counted but unfilterable.
        val other = ArtifactFacetRepositoryImpl.classifyUploadFormat(
            title = "notes.txt",
            sourceUrl = null,
            sourceId = "6f1e2f2c-0000-4000-8000-000000000000",
            mime = null,
            language = null,
        )
        assertThat(other).isEqualTo(UploadFormat.OTHER)
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

    @Test
    fun `languageFacetOptions keeps a selected document language with its real count`() {
        val counted = listOf("Markdown" to 3L, "Kotlin" to 3L, "Plain Text" to 9L)

        val options = languageFacetOptions(counted, selected = listOf("markdown"))

        assertThat(options).containsExactly(
            FacetCountResponse("Kotlin", 3),
            FacetCountResponse("Markdown", 3),
        )
    }
}

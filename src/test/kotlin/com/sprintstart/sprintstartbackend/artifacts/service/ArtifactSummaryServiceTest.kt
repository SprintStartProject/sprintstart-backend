package com.sprintstart.sprintstartbackend.artifacts.service

import com.sprintstart.sprintstartbackend.artifacts.ArtifactSummaryAiClient
import com.sprintstart.sprintstartbackend.artifacts.model.ai.AiArtifactSummaryStreamMessage
import com.sprintstart.sprintstartbackend.artifacts.model.entity.ArtifactSummary
import com.sprintstart.sprintstartbackend.artifacts.model.entity.ArtifactSummaryCitation
import com.sprintstart.sprintstartbackend.artifacts.model.exceptions.ArtifactSummaryAiException
import com.sprintstart.sprintstartbackend.artifacts.repository.ArtifactSummaryRepository
import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.upload.external.UploadApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class ArtifactSummaryServiceTest {
    private val artifactSummaryRepository = mockk<ArtifactSummaryRepository>()
    private val artifactSummaryAiClient = mockk<ArtifactSummaryAiClient>()
    private val artifactIngestionApi = mockk<ArtifactIngestionApi>()
    private val uploadApi = mockk<UploadApi>()

    private val service = ArtifactSummaryService(
        artifactSummaryRepository = artifactSummaryRepository,
        artifactSummaryAiClient = artifactSummaryAiClient,
        artifactIngestionApi = artifactIngestionApi,
        uploadApi = uploadApi,
    )

    private val artifactId = UUID.randomUUID()
    private val citedArtifactId = UUID.randomUUID()

    private fun aiStream(summary: String = "A summary.") = flowOf(
        AiArtifactSummaryStreamMessage(type = "stage", name = "notes", detail = "Extracting notes"),
        AiArtifactSummaryStreamMessage(type = "token", content = summary),
        AiArtifactSummaryStreamMessage(
            type = "citation",
            artifactId = citedArtifactId.toString(),
            filename = "README.md",
            sourceUrl = "https://github.com/example/repo",
        ),
        AiArtifactSummaryStreamMessage(type = "done"),
    )

    @Test
    fun `getSummary serves a cached summary as a single-shot stream when the hash still matches`() = runTest {
        every { uploadApi.getHash(artifactId) } returns null
        every { artifactIngestionApi.exists(artifactId) } returns true
        every { artifactIngestionApi.getHash(artifactId) } returns "hash-1"

        val cached = ArtifactSummary(artifactId = artifactId, summary = "Cached summary.", sourceHash = "hash-1")
        cached.citations = mutableListOf(
            ArtifactSummaryCitation(
                artifactSummary = cached,
                citedArtifactId = citedArtifactId,
                filename = "README.md",
                sourceUrl = null,
            ),
        )
        every { artifactSummaryRepository.findById(artifactId) } returns Optional.of(cached)

        val events = service.getSummary(artifactId).toList()

        assertEquals("token", events[0].type)
        assertEquals("Cached summary.", events[0].content)
        assertEquals("citation", events[1].type)
        assertEquals(citedArtifactId.toString(), events[1].artifactId)
        assertEquals("done", events[2].type)

        verify(exactly = 0) { artifactSummaryAiClient.summarizeStream(any(), any()) }
    }

    @Test
    fun `getSummary streams a fresh summary and caches it when the hash changed`() = runTest {
        every { uploadApi.getHash(artifactId) } returns null
        every { artifactIngestionApi.exists(artifactId) } returns true
        every { artifactIngestionApi.getHash(artifactId) } returns "hash-2"

        val stale = ArtifactSummary(artifactId = artifactId, summary = "Stale summary.", sourceHash = "hash-1")
        every { artifactSummaryRepository.findById(artifactId) } returns Optional.of(stale)
        every { artifactSummaryAiClient.summarizeStream(artifactId, any()) } returns aiStream("Fresh summary.")

        val savedSlot = slot<ArtifactSummary>()
        every { artifactSummaryRepository.save(capture(savedSlot)) } answers { firstArg() }

        val events = service.getSummary(artifactId).toList()

        val tokenText = events.filter { it.type == "token" }.joinToString("") { it.content ?: "" }
        assertEquals("Fresh summary.", tokenText)
        assertEquals("done", events.last().type)

        assertEquals("Fresh summary.", savedSlot.captured.summary)
        assertEquals("hash-2", savedSlot.captured.sourceHash)
        assertEquals(1, savedSlot.captured.citations.size)
        assertEquals(citedArtifactId, savedSlot.captured.citations[0].citedArtifactId)
    }

    @Test
    fun `getSummary streams a fresh summary from an uploaded artifact via its own hash`() = runTest {
        every { uploadApi.getHash(artifactId) } returns "uploaded-hash"
        every { artifactSummaryRepository.findById(artifactId) } returns Optional.empty()
        every { artifactSummaryAiClient.summarizeStream(artifactId, any()) } returns aiStream()
        every { artifactSummaryRepository.save(any()) } answers { firstArg() }

        service.getSummary(artifactId).toList()

        verify(exactly = 0) { artifactIngestionApi.exists(any()) }
    }

    @Test
    fun `getSummary streams without caching when the artifact has no hash on record`() = runTest {
        every { uploadApi.getHash(artifactId) } returns null
        every { artifactIngestionApi.exists(artifactId) } returns true
        every { artifactIngestionApi.getHash(artifactId) } returns null
        every { artifactSummaryAiClient.summarizeStream(artifactId, any()) } returns aiStream()

        val events = service.getSummary(artifactId).toList()

        val tokenText = events.filter { it.type == "token" }.joinToString("") { it.content ?: "" }
        assertEquals("A summary.", tokenText)
        verify(exactly = 0) { artifactSummaryRepository.save(any()) }
    }

    @Test
    fun `getSummary throws 404 when the artifact does not exist anywhere`() = runTest {
        every { uploadApi.getHash(artifactId) } returns null
        every { artifactIngestionApi.exists(artifactId) } returns false

        assertThrows<ResponseStatusException> {
            service.getSummary(artifactId)
        }

        verify(exactly = 0) { artifactSummaryAiClient.summarizeStream(any(), any()) }
    }

    @Test
    fun `getSummary drops citations whose artifact id is not a valid UUID`() = runTest {
        every { uploadApi.getHash(artifactId) } returns null
        every { artifactIngestionApi.exists(artifactId) } returns true
        every { artifactIngestionApi.getHash(artifactId) } returns "hash-1"
        every { artifactSummaryRepository.findById(artifactId) } returns Optional.empty()
        every { artifactSummaryAiClient.summarizeStream(artifactId, any()) } returns flowOf(
            AiArtifactSummaryStreamMessage(type = "token", content = "A summary."),
            AiArtifactSummaryStreamMessage(type = "citation", artifactId = "not-a-uuid", filename = "README.md"),
            AiArtifactSummaryStreamMessage(type = "done"),
        )

        val savedSlot = slot<ArtifactSummary>()
        every { artifactSummaryRepository.save(capture(savedSlot)) } answers { firstArg() }

        val events = service.getSummary(artifactId).toList()

        assertTrue(events.none { it.type == "citation" })
        assertTrue(savedSlot.captured.citations.isEmpty())
    }

    @Test
    fun `getSummary propagates an AI failure without caching anything`() = runTest {
        every { uploadApi.getHash(artifactId) } returns null
        every { artifactIngestionApi.exists(artifactId) } returns true
        every { artifactIngestionApi.getHash(artifactId) } returns "hash-1"
        every { artifactSummaryRepository.findById(artifactId) } returns Optional.empty()
        every { artifactSummaryAiClient.summarizeStream(artifactId, any()) } returns flow {
            throw ArtifactSummaryAiException("AI responded with error: LLM backend unreachable")
        }

        assertThrows<ArtifactSummaryAiException> {
            service.getSummary(artifactId).toList()
        }

        verify(exactly = 0) { artifactSummaryRepository.save(any()) }
    }
}

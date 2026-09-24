package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactAiIndexStatus
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactIngestStatusAiItem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactIngestStatusAiResponse
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.net.ConnectException
import java.net.http.HttpTimeoutException
import java.util.UUID
import kotlin.test.assertFailsWith

class ArtifactAiStatusServiceTest {
    private val artifactRepository = mockk<ArtifactRepository>()
    private val artifactIngestionClient = mockk<ArtifactIngestionClient>()
    private val userApi = mockk<UserApi>()
    private val service = ArtifactAiStatusService(artifactRepository, artifactIngestionClient, userApi)

    private val authId = "user-1"
    private val projectId = UUID.randomUUID()

    init {
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
    }

    @Test
    fun `rejects a user without project access before any lookup`() = runTest {
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        val error = assertFailsWith<ResponseStatusException> {
            service.getAiStatus(authId, projectId, listOf(UUID.randomUUID()))
        }

        assertThat(error.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(exactly = 0) { artifactRepository.findIdsInProject(any(), any()) }
        coVerify(exactly = 0) { artifactIngestionClient.fetchIngestStatus(any()) }
    }

    @Test
    fun `answers an empty request as available and empty without any lookup`() = runTest {
        val result = service.getAiStatus(authId, projectId, emptyList())

        assertThat(result.aiAvailable).isTrue()
        assertThat(result.items).isEmpty()
        verify(exactly = 0) { artifactRepository.findIdsInProject(any(), any()) }
        coVerify(exactly = 0) { artifactIngestionClient.fetchIngestStatus(any()) }
    }

    @Test
    fun `does not ask the AI when every requested id belongs to another project`() = runTest {
        val foreign = UUID.randomUUID()
        every { artifactRepository.findIdsInProject(projectId, listOf(foreign)) } returns emptySet()

        val result = service.getAiStatus(authId, projectId, listOf(foreign))

        assertThat(result.aiAvailable).isTrue()
        assertThat(result.items).isEmpty()
        coVerify(exactly = 0) { artifactIngestionClient.fetchIngestStatus(any()) }
    }

    @Test
    fun `omits foreign ids, collapses duplicates and asks the AI only about visible ones`() = runTest {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val foreign = UUID.randomUUID()
        every { artifactRepository.findIdsInProject(projectId, listOf(first, foreign, second)) } returns
            setOf(second, first)
        coEvery { artifactIngestionClient.fetchIngestStatus(listOf(first, second)) } returns
            aiResponse(aiItem(first, "indexed"), aiItem(second, "processing"))

        val result = service.getAiStatus(authId, projectId, listOf(first, foreign, second, first))

        assertThat(result.aiAvailable).isTrue()
        assertThat(result.items.map { it.artifactId }).containsExactly(first, second)
        assertThat(result.items.map { it.status })
            .containsExactly(ArtifactAiIndexStatus.INDEXED, ArtifactAiIndexStatus.PROCESSING)
    }

    @Test
    fun `reports aiAvailable false with UNKNOWN items when the AI cannot be asked`() = runTest {
        val id = UUID.randomUUID()
        every { artifactRepository.findIdsInProject(projectId, listOf(id)) } returns setOf(id)
        val failures = listOf(
            ConnectException("Connection refused"),
            HttpTimeoutException("request timed out"),
            IngestionResponseException("Failed to read ingest status (HTTP 503): down"),
            SerializationException("Unexpected JSON token"),
        )

        failures.forEach { failure ->
            coEvery { artifactIngestionClient.fetchIngestStatus(listOf(id)) } throws failure

            val result = service.getAiStatus(authId, projectId, listOf(id))

            assertThat(result.aiAvailable).`as`(failure.javaClass.simpleName).isFalse()
            val item = result.items.single()
            assertThat(item.artifactId).isEqualTo(id)
            assertThat(item.status).isEqualTo(ArtifactAiIndexStatus.UNKNOWN)
            assertThat(item.updatedAt).isNull()
            assertThat(item.chunkCount).isNull()
        }
    }

    @Test
    fun `maps AI statuses and degrades unknown, unrecognised and missing ones to UNKNOWN`() = runTest {
        val ids = List(7) { UUID.randomUUID() }
        every { artifactRepository.findIdsInProject(projectId, ids) } returns ids.toSet()
        val aiStatuses = listOf("indexed", "PROCESSING", "failed", "deindexed", "unknown", "reindexing")
        coEvery { artifactIngestionClient.fetchIngestStatus(ids) } returns
            aiResponse(*ids.zip(aiStatuses).map { (id, status) -> aiItem(id, status) }.toTypedArray())

        val result = service.getAiStatus(authId, projectId, ids)

        assertThat(result.aiAvailable).isTrue()
        assertThat(result.items.map { it.status }).containsExactly(
            ArtifactAiIndexStatus.INDEXED,
            ArtifactAiIndexStatus.PROCESSING,
            ArtifactAiIndexStatus.FAILED,
            ArtifactAiIndexStatus.DEINDEXED,
            ArtifactAiIndexStatus.UNKNOWN,
            ArtifactAiIndexStatus.UNKNOWN,
            ArtifactAiIndexStatus.UNKNOWN,
        )
        assertThat(result.items[0].updatedAt).isEqualTo("2026-09-20T10:00:00+00:00")
        assertThat(result.items[0].chunkCount).isEqualTo(4)
        assertThat(result.items.drop(4).map { it.updatedAt to it.chunkCount }).containsOnly(null to null)
    }

    private fun aiItem(id: UUID, status: String) = ArtifactIngestStatusAiItem(
        artifactId = id.toString(),
        status = status,
        updatedAt = "2026-09-20T10:00:00+00:00",
        chunkCount = 4,
    )

    private fun aiResponse(vararg items: ArtifactIngestStatusAiItem) = ArtifactIngestStatusAiResponse(items.toList())
}

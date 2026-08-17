package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.insights.KnowledgeGapsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGap
import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGapsResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.request.SetComponentOwnersRequest
import com.sprintstart.sprintstartbackend.insights.model.entity.ComponentOwner
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGap
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGapSeverity
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGapsScan
import com.sprintstart.sprintstartbackend.insights.model.mapper.AiKnowledgeGapMapper
import com.sprintstart.sprintstartbackend.insights.model.mapper.KnowledgeGapResponseMapper
import com.sprintstart.sprintstartbackend.insights.repository.ComponentOwnerRepository
import com.sprintstart.sprintstartbackend.insights.repository.KnowledgeGapRepository
import com.sprintstart.sprintstartbackend.insights.repository.KnowledgeGapsScanRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class KnowledgeGapsServiceTest {
    private val knowledgeGapRepository = mockk<KnowledgeGapRepository>()

    // Not relaxed: `save` is generic, and a relaxed mock answers it with a stand-in that the
    // compiler-inserted cast to the entity type rejects. The defaults are set in `init` instead,
    // so cases indifferent to the scan record need no stubbing and the rest can override.
    private val knowledgeGapsScanRepository = mockk<KnowledgeGapsScanRepository>()
    private val projectId: UUID = UUID.randomUUID()
    private val componentOwnerRepository = mockk<ComponentOwnerRepository>()
    private val knowledgeGapsAiClient = mockk<KnowledgeGapsAiClient>()
    private val aiKnowledgeGapMapper = AiKnowledgeGapMapper()
    private val knowledgeGapResponseMapper = KnowledgeGapResponseMapper()
    private val userApi = mockk<UserApi>()
    private val artifactIngestionApi = mockk<ArtifactIngestionApi>()

    // Relaxed: TransactionTemplate only needs a manager to hand it a status; the callback
    // runs inline either way.
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private val service = KnowledgeGapsService(
        knowledgeGapRepository = knowledgeGapRepository,
        knowledgeGapsScanRepository = knowledgeGapsScanRepository,
        componentOwnerRepository = componentOwnerRepository,
        knowledgeGapsAiClient = knowledgeGapsAiClient,
        aiKnowledgeGapMapper = aiKnowledgeGapMapper,
        knowledgeGapResponseMapper = knowledgeGapResponseMapper,
        userApi = userApi,
        artifactIngestionApi = artifactIngestionApi,
        refreshTracker = InsightsRefreshTracker(),
        transactionManager = transactionManager,
    )

    init {
        every { knowledgeGapsScanRepository.findById(any()) } returns Optional.empty()
        every { knowledgeGapsScanRepository.save(any<KnowledgeGapsScan>()) } answers { firstArg() }
    }

    private fun buildGap(
        component: String,
        severity: KnowledgeGapSeverity,
    ): KnowledgeGap {
        return KnowledgeGap(
            component = component,
            lastUpdated = Instant.parse("2025-05-01T00:00:00Z"),
            severity = severity,
        )
    }

    private fun buildUser(id: UUID, role: String?) = UserDto(
        id = id,
        username = "jdoe",
        firstname = "John",
        lastname = "Doe",
        avatarUrl = null,
        profileIcon = null,
        projects = emptySet(),
        skills = emptyList(),
        projectRoles = if (role == null) {
            emptyList()
        } else {
            listOf(ProjectRoleDto(roleId = UUID.randomUUID(), name = role, description = ""))
        },
    )

    @Test
    fun `getKnowledgeGaps orders by severity then component`() {
        val lowGap = buildGap("frontend-portal", KnowledgeGapSeverity.LOW)
        val highB = buildGap("payment-service", KnowledgeGapSeverity.HIGH)
        val highA = buildGap("auth-service", KnowledgeGapSeverity.HIGH)
        every { knowledgeGapRepository.findAllByProjectId(projectId) } returns listOf(lowGap, highB, highA)
        every { componentOwnerRepository.findAllByComponentIn(any()) } returns emptyList()
        every { artifactIngestionApi.getFirstIngestedAt(any<Collection<String>>()) } returns emptyMap()

        val overview = service.getKnowledgeGaps(projectId)

        assertEquals(
            listOf("auth-service", "payment-service", "frontend-portal"),
            overview.gaps.map { it.component },
        )
    }

    // A scan that finds nothing writes no gap rows, so the timestamp cannot be derived from them.
    // Without the recorded scan the panel cannot tell "documentation is complete" from "no scan has
    // ever run" -- opposite messages for a PM.
    @Test
    fun `getKnowledgeGaps reports when the last scan ran even though it found nothing`() {
        val scannedAt = Instant.parse("2026-08-16T14:03:35Z")
        every { knowledgeGapRepository.findAllByProjectId(projectId) } returns emptyList()
        every { componentOwnerRepository.findAllByComponentIn(any()) } returns emptyList()
        every { artifactIngestionApi.getFirstIngestedAt(any<Collection<String>>()) } returns emptyMap()
        every { knowledgeGapsScanRepository.findById(projectId) } returns
            Optional.of(KnowledgeGapsScan(projectId = projectId, scannedAt = scannedAt))

        val overview = service.getKnowledgeGaps(projectId)

        assertTrue(overview.gaps.isEmpty())
        assertEquals(scannedAt, overview.refreshedAt)
    }

    @Test
    fun `getKnowledgeGaps reports no scan time before the first scan`() {
        every { knowledgeGapRepository.findAllByProjectId(projectId) } returns emptyList()
        every { componentOwnerRepository.findAllByComponentIn(any()) } returns emptyList()
        every { artifactIngestionApi.getFirstIngestedAt(any<Collection<String>>()) } returns emptyMap()
        every { knowledgeGapsScanRepository.findById(projectId) } returns Optional.empty()

        val overview = service.getKnowledgeGaps(projectId)

        assertEquals(null, overview.refreshedAt)
    }

    @Test
    fun `refreshKnowledgeGaps records the scan even when the AI reports no gaps`() = runTest {
        coEvery { knowledgeGapsAiClient.detectKnowledgeGaps(any()) } returns
            AiKnowledgeGapsResponse(gaps = emptyList())
        every { knowledgeGapRepository.deleteAllByProjectId(projectId) } just Runs
        every { knowledgeGapRepository.deleteAllByProjectIdIsNull() } just Runs
        every { knowledgeGapRepository.saveAll(any<List<KnowledgeGap>>()) } returns emptyList()

        val result = service.refreshKnowledgeGaps(projectId)

        assertEquals(0, result.gapCount)
        val saved = slot<KnowledgeGapsScan>()
        verify { knowledgeGapsScanRepository.save(capture(saved)) }
        assertEquals(projectId, saved.captured.projectId)
    }

    @Test
    fun `getKnowledgeGap maps fields and enriches owners with their project role`() {
        val gap = buildGap("auth-service", KnowledgeGapSeverity.HIGH)
        gap.missingTypes.addAll(listOf("runbook", "adr"))
        gap.presentTypes.add("readme")
        val userId = UUID.randomUUID()
        every { knowledgeGapRepository.findByIdAndProjectId(gap.id, projectId) } returns Optional.of(gap)
        every { componentOwnerRepository.findAllByComponentIn(listOf("auth-service")) } returns
            listOf(ComponentOwner(component = "auth-service", userId = userId))
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(buildUser(userId, "Backend Developer"))
        every { artifactIngestionApi.getFirstIngestedAt("auth-service") } returns
            Instant.parse("2025-01-10T00:00:00Z")

        val detail = service.getKnowledgeGap(projectId, gap.id)

        assertEquals("auth-service", detail.component)
        assertEquals(listOf("runbook", "adr"), detail.missingTypes)
        assertEquals(listOf("readme"), detail.presentTypes)
        assertEquals("high", detail.severity)
        assertEquals(Instant.parse("2025-05-01T00:00:00Z"), detail.lastIngested)
        assertEquals(Instant.parse("2025-01-10T00:00:00Z"), detail.firstIngested)
        assertEquals(1, detail.owners.size)
        assertEquals(userId.toString(), detail.owners.first().id)
        assertEquals("Backend Developer", detail.owners.first().role)
    }

    @Test
    fun `getKnowledgeGap returns empty owners when the component has none`() {
        val gap = buildGap("auth-service", KnowledgeGapSeverity.HIGH)
        every { knowledgeGapRepository.findByIdAndProjectId(gap.id, projectId) } returns Optional.of(gap)
        every { componentOwnerRepository.findAllByComponentIn(listOf("auth-service")) } returns emptyList()
        every { artifactIngestionApi.getFirstIngestedAt("auth-service") } returns null

        val detail = service.getKnowledgeGap(projectId, gap.id)

        assertTrue(detail.owners.isEmpty())
    }

    @Test
    fun `getKnowledgeGap throws 404 when the gap does not exist`() {
        val missingId = UUID.randomUUID()
        every { knowledgeGapRepository.findByIdAndProjectId(missingId, projectId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.getKnowledgeGap(projectId, missingId)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `setComponentOwners replaces the mapping and returns the resolved owners`() {
        val userId = UUID.randomUUID()
        val savedSlot = slot<List<ComponentOwner>>()
        every { componentOwnerRepository.deleteByComponent("auth-service") } just Runs
        every { componentOwnerRepository.saveAll(capture(savedSlot)) } answers { savedSlot.captured.toMutableList() }
        every { componentOwnerRepository.findAllByComponentIn(listOf("auth-service")) } returns
            listOf(ComponentOwner(component = "auth-service", userId = userId))
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(buildUser(userId, null))

        val owners = service.setComponentOwners(
            SetComponentOwnersRequest(component = "auth-service", userIds = listOf(userId, userId)),
        )

        assertEquals(1, savedSlot.captured.size)
        assertEquals("auth-service", savedSlot.captured.first().component)
        assertEquals(1, owners.size)
        assertEquals(userId.toString(), owners.first().id)
        verify(exactly = 1) { componentOwnerRepository.deleteByComponent("auth-service") }
    }

    @Test
    fun `refreshKnowledgeGaps classifies via the AI service and rebuilds the cache`() = runTest {
        val aiResponse = AiKnowledgeGapsResponse(
            gaps = listOf(
                AiKnowledgeGap(
                    component = "auth-service",
                    missingTypes = listOf("runbook", "adr"),
                    presentTypes = listOf("readme"),
                    lastUpdated = "2025-05-01T00:00:00Z",
                    severity = "high",
                ),
            ),
        )
        coEvery { knowledgeGapsAiClient.detectKnowledgeGaps(any()) } returns aiResponse
        every { knowledgeGapRepository.deleteAllByProjectId(projectId) } just Runs
        every { knowledgeGapRepository.deleteAllByProjectIdIsNull() } just Runs
        val savedSlot = slot<List<KnowledgeGap>>()
        every { knowledgeGapRepository.saveAll(capture(savedSlot)) } answers { savedSlot.captured.toMutableList() }

        val result = service.refreshKnowledgeGaps(projectId)

        assertEquals(1, result.gapCount)
        val persisted = savedSlot.captured.first()
        assertEquals("auth-service", persisted.component)
        assertEquals(listOf("readme"), persisted.presentTypes)
    }

    // Every component now yields a row, so the row count alone no longer says whether anything is
    // wrong -- callers that report "nothing to fix" would misfire on a stored covered component.
    @Test
    fun `refreshKnowledgeGaps counts covered components separately from actual gaps`() = runTest {
        val aiResponse = AiKnowledgeGapsResponse(
            gaps = listOf(
                AiKnowledgeGap(
                    component = "auth-service",
                    missingTypes = listOf("runbook"),
                    presentTypes = listOf("readme"),
                    lastUpdated = "2025-05-01T00:00:00Z",
                    severity = "low",
                ),
                AiKnowledgeGap(
                    component = "docs-wiki",
                    missingTypes = emptyList(),
                    presentTypes = listOf("readme", "setup"),
                    lastUpdated = "2025-05-01T00:00:00Z",
                    severity = "covered",
                ),
            ),
        )
        coEvery { knowledgeGapsAiClient.detectKnowledgeGaps(any()) } returns aiResponse
        every { knowledgeGapRepository.deleteAllByProjectId(projectId) } just Runs
        every { knowledgeGapRepository.deleteAllByProjectIdIsNull() } just Runs
        val savedSlot = slot<List<KnowledgeGap>>()
        every { knowledgeGapRepository.saveAll(capture(savedSlot)) } answers { savedSlot.captured.toMutableList() }

        val result = service.refreshKnowledgeGaps(projectId)

        assertEquals(1, result.gapCount)
        assertEquals(2, result.componentCount)
        // The covered component is stored like any other, so the panel can show it.
        assertEquals(listOf("auth-service", "docs-wiki"), savedSlot.captured.map { it.component })
    }

    @Test
    fun `getKnowledgeGaps sorts covered components below every real gap`() {
        val covered = buildGap("docs-wiki", KnowledgeGapSeverity.COVERED)
        val low = buildGap("auth-service", KnowledgeGapSeverity.LOW)
        every { knowledgeGapRepository.findAllByProjectId(projectId) } returns listOf(covered, low)
        every { componentOwnerRepository.findAllByComponentIn(any()) } returns emptyList()
        every { artifactIngestionApi.getFirstIngestedAt(any<Collection<String>>()) } returns emptyMap()

        val overview = service.getKnowledgeGaps(projectId)

        assertEquals(listOf("auth-service", "docs-wiki"), overview.gaps.map { it.component })
    }
}

package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.insights.KnowledgeGapsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGap
import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGapsResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.request.SetComponentOwnersRequest
import com.sprintstart.sprintstartbackend.insights.model.entity.ComponentOwner
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGap
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGapSeverity
import com.sprintstart.sprintstartbackend.insights.model.mapper.AiKnowledgeGapMapper
import com.sprintstart.sprintstartbackend.insights.model.mapper.KnowledgeGapResponseMapper
import com.sprintstart.sprintstartbackend.insights.repository.ComponentOwnerRepository
import com.sprintstart.sprintstartbackend.insights.repository.KnowledgeGapRepository
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
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class KnowledgeGapsServiceTest {
    private val knowledgeGapRepository = mockk<KnowledgeGapRepository>()
    private val componentOwnerRepository = mockk<ComponentOwnerRepository>()
    private val knowledgeGapsAiClient = mockk<KnowledgeGapsAiClient>()
    private val aiKnowledgeGapMapper = AiKnowledgeGapMapper()
    private val knowledgeGapResponseMapper = KnowledgeGapResponseMapper()
    private val userApi = mockk<UserApi>()
    private val artifactIngestionApi = mockk<ArtifactIngestionApi>()

    private val service = KnowledgeGapsService(
        knowledgeGapRepository = knowledgeGapRepository,
        componentOwnerRepository = componentOwnerRepository,
        knowledgeGapsAiClient = knowledgeGapsAiClient,
        aiKnowledgeGapMapper = aiKnowledgeGapMapper,
        knowledgeGapResponseMapper = knowledgeGapResponseMapper,
        userApi = userApi,
        artifactIngestionApi = artifactIngestionApi,
    )

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
        every { knowledgeGapRepository.findAll() } returns listOf(lowGap, highB, highA)
        every { componentOwnerRepository.findAllByComponentIn(any()) } returns emptyList()
        every { artifactIngestionApi.getFirstIngestedAt(any<Collection<String>>()) } returns emptyMap()

        val overview = service.getKnowledgeGaps()

        assertEquals(
            listOf("auth-service", "payment-service", "frontend-portal"),
            overview.gaps.map { it.component },
        )
    }

    @Test
    fun `getKnowledgeGap maps fields and enriches owners with their project role`() {
        val gap = buildGap("auth-service", KnowledgeGapSeverity.HIGH)
        gap.missingTypes.addAll(listOf("runbook", "adr"))
        gap.presentTypes.add("readme")
        val userId = UUID.randomUUID()
        every { knowledgeGapRepository.findById(gap.id) } returns Optional.of(gap)
        every { componentOwnerRepository.findAllByComponentIn(listOf("auth-service")) } returns
            listOf(ComponentOwner(component = "auth-service", userId = userId))
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(buildUser(userId, "Backend Developer"))
        every { artifactIngestionApi.getFirstIngestedAt("auth-service") } returns
            Instant.parse("2025-01-10T00:00:00Z")

        val detail = service.getKnowledgeGap(gap.id)

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
        every { knowledgeGapRepository.findById(gap.id) } returns Optional.of(gap)
        every { componentOwnerRepository.findAllByComponentIn(listOf("auth-service")) } returns emptyList()
        every { artifactIngestionApi.getFirstIngestedAt("auth-service") } returns null

        val detail = service.getKnowledgeGap(gap.id)

        assertTrue(detail.owners.isEmpty())
    }

    @Test
    fun `getKnowledgeGap throws 404 when the gap does not exist`() {
        val missingId = UUID.randomUUID()
        every { knowledgeGapRepository.findById(missingId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.getKnowledgeGap(missingId)
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
        every { knowledgeGapRepository.deleteAll() } just Runs
        val savedSlot = slot<List<KnowledgeGap>>()
        every { knowledgeGapRepository.saveAll(capture(savedSlot)) } answers { savedSlot.captured.toMutableList() }

        val result = service.refreshKnowledgeGaps()

        assertEquals(1, result.gapCount)
        val persisted = savedSlot.captured.first()
        assertEquals("auth-service", persisted.component)
        assertEquals(listOf("readme"), persisted.presentTypes)
    }
}

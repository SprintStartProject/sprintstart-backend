package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.AttestationState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Attestation
import com.sprintstart.sprintstartbackend.onboarding.repository.AttestationRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The through-line: **an attestation is only evidence because somebody else said it.**
 *
 * Every rule here defends that. If a hire could confirm their own work, or somebody outside the
 * project could, or a sent-back request could quietly read like a clean one, then attested evidence
 * would be a formality with a metric attached to it.
 */
class AttestationServiceTest {
    private val attestationRepository: AttestationRepository = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()

    private val now: Instant = Instant.parse("2026-07-27T12:00:00Z")
    private val hireId: UUID = UUID.randomUUID()
    private val attesterId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private val service = AttestationService(
        attestationRepository,
        projectMembershipApi,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun onProject(vararg userIds: UUID) {
        every { projectMembershipApi.getProjectMembers(projectId) } returns
            userIds.map { ProjectMember(it, "Someone", null, null) }
    }

    private fun saves() {
        every { attestationRepository.save(any()) } answers { firstArg() }
    }

    private fun existing(
        state: AttestationState = AttestationState.REQUESTED,
        returnedCount: Int = 0,
        firstResponseAt: Instant? = null,
    ): Attestation {
        val attestation = Attestation(
            hireId = hireId,
            projectId = projectId,
            title = "Facilitated the sprint retro",
            attesterId = attesterId,
            state = state,
            requestedAt = now.minus(Duration.ofDays(2)),
            firstResponseAt = firstResponseAt,
            returnedCount = returnedCount,
        )
        every { attestationRepository.findById(attestation.id) } returns Optional.of(attestation)
        return attestation
    }

    @Test
    fun `a hire cannot confirm their own work`() {
        onProject(hireId)

        val error = assertThrows<ResponseStatusException> {
            service.request(hireId, projectId, "Ran the retro", null, attesterId = hireId)
        }

        assertEquals(400, error.statusCode.value())
    }

    @Test
    fun `somebody outside the project cannot be asked`() {
        onProject(hireId)

        // No standing to say the work met this team's bar, and unauditable if allowed.
        val error = assertThrows<ResponseStatusException> {
            service.request(hireId, projectId, "Ran the retro", null, attesterId)
        }

        assertEquals(400, error.statusCode.value())
    }

    @Test
    fun `a request names the colleague who must answer it`() {
        onProject(hireId, attesterId)
        saves()

        val attestation = service.request(hireId, projectId, "  Ran the retro  ", " ", attesterId)

        assertEquals("Ran the retro", attestation.title)
        assertEquals(attesterId, attestation.attesterId)
        assertEquals(AttestationState.REQUESTED, attestation.state)
        // Blank evidence links are dropped rather than stored: an empty link is worse than none.
        assertNull(attestation.evidenceUrl)
    }

    @Test
    fun `accepting records the acceptance moment`() {
        val attestation = existing()
        saves()

        val result = service.accept(attestation.id, attesterId)

        assertEquals(AttestationState.ACCEPTED, result.state)
        assertEquals(now, result.acceptedAt)
        assertEquals(now, result.firstResponseAt)
    }

    @Test
    fun `only the person who was asked can answer`() {
        val attestation = existing()

        val error = assertThrows<ResponseStatusException> {
            service.accept(attestation.id, UUID.randomUUID())
        }

        assertEquals(403, error.statusCode.value())
    }

    @Test
    fun `sending work back counts as rework and keeps it waiting`() {
        val attestation = existing()
        saves()

        val result = service.sendBack(attestation.id, attesterId, "The actions were not written down")

        // Still REQUESTED: the hire acts on the reason and the same request carries on, exactly as
        // a pull request with changes requested does.
        assertEquals(AttestationState.REQUESTED, result.state)
        assertEquals(1, result.returnedCount)
        assertEquals("The actions were not written down", result.returnReason)
    }

    @Test
    fun `sending back without a reason is refused`() {
        existing()

        // "No, and I won't say why" is not something a hire can act on.
        val error = assertThrows<ResponseStatusException> {
            service.sendBack(UUID.randomUUID(), attesterId, "   ")
        }

        assertEquals(400, error.statusCode.value())
    }

    @Test
    fun `a second pass does not erase how long the first response took`() {
        val firstResponse = now.minus(Duration.ofDays(1))
        val attestation = existing(returnedCount = 1, firstResponseAt = firstResponse)
        saves()

        val result = service.accept(attestation.id, attesterId)

        assertEquals(firstResponse, result.firstResponseAt)
        assertEquals(now, result.acceptedAt)
    }

    @Test
    fun `an answered request cannot be answered again`() {
        val attestation = existing(state = AttestationState.ACCEPTED)

        val error = assertThrows<ResponseStatusException> {
            service.accept(attestation.id, attesterId)
        }

        assertEquals(409, error.statusCode.value())
    }

    @Test
    fun `a hire cannot withdraw work that has already been confirmed`() {
        val attestation = existing(state = AttestationState.ACCEPTED)

        // An accepted contribution is a fact, not a draft.
        val error = assertThrows<ResponseStatusException> {
            service.withdraw(attestation.id, hireId)
        }

        assertEquals(409, error.statusCode.value())
    }

    @Test
    fun `a hire can withdraw a request nobody has answered`() {
        val attestation = existing()
        saves()

        val result = service.withdraw(attestation.id, hireId)

        assertEquals(AttestationState.WITHDRAWN, result.state)
        assertNotNull(result.requestedAt)
    }
}

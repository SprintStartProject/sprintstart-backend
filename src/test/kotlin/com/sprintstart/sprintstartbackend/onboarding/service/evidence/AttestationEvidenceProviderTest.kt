package com.sprintstart.sprintstartbackend.onboarding.service.evidence

import com.sprintstart.sprintstartbackend.onboarding.external.enums.AttestationState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Attestation
import com.sprintstart.sprintstartbackend.onboarding.repository.AttestationRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The through-line: **attested work counts, and is never mistaken for observed work.**
 *
 * This provider is what lets a role nothing observes finish onboarding. The rigor it reports is
 * what stops that from quietly becoming a claim as strong as a merge.
 */
class AttestationEvidenceProviderTest {
    private val attestationRepository: AttestationRepository = mockk()
    private val provider = AttestationEvidenceProvider(attestationRepository)

    private val now: Instant = Instant.parse("2026-07-27T12:00:00Z")
    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    // No GitHub login on purpose: attestation attributes by user id, so it works for exactly the
    // people pull-request evidence cannot see.
    private val member = ProjectMember(hireId, "A Hire", null, null)

    private fun attestations(vararg rows: Attestation) {
        every { attestationRepository.findAllByHireIdAndProjectId(hireId, projectId) } returns rows.toList()
    }

    private fun attestation(
        state: AttestationState,
        acceptedAt: Instant? = null,
        returnedCount: Int = 0,
    ) = Attestation(
        hireId = hireId,
        projectId = projectId,
        title = "Facilitated the sprint retro",
        attesterId = UUID.randomUUID(),
        state = state,
        requestedAt = now.minus(Duration.ofDays(3)),
        firstResponseAt = now.minus(Duration.ofDays(2)),
        acceptedAt = acceptedAt,
        returnedCount = returnedCount,
    )

    @Test
    fun `an accepted attestation is an accepted contribution, attested not observed`() {
        attestations(attestation(AttestationState.ACCEPTED, acceptedAt = now))

        val contribution = provider.contributionsFor(member, projectId).single()

        assertTrue(contribution.isAccepted)
        assertEquals(now, contribution.acceptedAt)
        assertEquals(Rigor.ATTESTED, contribution.rigor)
        assertEquals(ContributionEvidenceKind.ATTESTATION, contribution.kind)
    }

    @Test
    fun `a request nobody has answered is in flight`() {
        attestations(attestation(AttestationState.REQUESTED))

        val contribution = provider.contributionsFor(member, projectId).single()

        assertEquals(ContributionState.IN_FLIGHT, contribution.state)
        assertTrue(contribution.isInFlight)
    }

    @Test
    fun `a withdrawn request is abandoned, waiting on nobody`() {
        attestations(attestation(AttestationState.WITHDRAWN))

        val contribution = provider.contributionsFor(member, projectId).single()

        assertEquals(ContributionState.ABANDONED, contribution.state)
        assertTrue(!contribution.isInFlight)
    }

    @Test
    fun `work sent back carries its rework count into the contribution`() {
        attestations(attestation(AttestationState.ACCEPTED, acceptedAt = now, returnedCount = 2))

        // Autonomy asks whether the last piece of work needed no rework; an attestation that took
        // three passes must not read like one that took none.
        assertEquals(2, provider.contributionsFor(member, projectId).single().returnedCount)
    }

    @Test
    fun `requesting is the submission moment`() {
        val row = attestation(AttestationState.REQUESTED)
        attestations(row)

        // The same thing opening a pull request measures: when the work went to somebody else.
        assertEquals(row.requestedAt, provider.contributionsFor(member, projectId).single().openedAt)
    }
}

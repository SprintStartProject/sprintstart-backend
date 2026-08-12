package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.AttestationState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Attestation
import com.sprintstart.sprintstartbackend.onboarding.repository.AttestationRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID

/**
 * Asking a named person to confirm a hire's work, and their answer.
 *
 * This is the path by which a role whose work nothing observes — a Scrum Master, a PM, an HR hire —
 * can complete onboarding at all. It is deliberately shaped like a code review, because that is the
 * loop it stands in for: submit, wait on somebody, get it back or get it accepted.
 *
 * Two rules do the load-bearing work, both enforced here rather than trusted to callers.
 *
 * **The attester is never the hire.** Self-attestation exists one rung down the evidence ladder and
 * is honestly labelled there; letting it in through this door would put the weakest evidence under
 * the north-star metric while calling it something stronger.
 *
 * **The attester is a member of the same project.** Somebody outside the project has no standing to
 * say the work met this team's bar, and allowing it would make the evidence unauditable.
 *
 * The function count is the five lifecycle operations plus one named guard per rule. Folding the
 * guards back into their callers would hide exactly the rules this class exists to enforce, hence
 * the suppression.
 */
@Suppress("TooManyFunctions")
@Service
class AttestationService(
    private val attestationRepository: AttestationRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Files a hire's request for somebody to confirm a piece of work.
     *
     * @throws ResponseStatusException 400 when the title is blank or the attester is the hire, 404
     * when either party is not a member of the project.
     */
    @Transactional
    fun request(hireId: UUID, projectId: UUID, title: String, evidenceUrl: String?, attesterId: UUID): Attestation {
        requireDistinctPeople(hireId, attesterId)
        requireBothOnProject(hireId, attesterId, projectId)
        return attestationRepository.save(
            Attestation(
                hireId = hireId,
                projectId = projectId,
                title = requireTitle(title),
                evidenceUrl = evidenceUrl?.trim()?.takeIf { it.isNotBlank() },
                attesterId = attesterId,
                requestedAt = clock.instant(),
            ),
        )
    }

    private fun requireTitle(title: String): String {
        if (title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank")
        }
        return title.trim()
    }

    /** The rule that makes this evidence rather than a formality; see the class note. */
    private fun requireDistinctPeople(hireId: UUID, attesterId: UUID) {
        if (attesterId == hireId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "An attestation must be confirmed by somebody other than the person who did the work",
            )
        }
    }

    private fun requireBothOnProject(hireId: UUID, attesterId: UUID, projectId: UUID) {
        val memberIds = projectMembershipApi.getProjectMembers(projectId).map { it.userId }.toSet()
        if (hireId !in memberIds) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $hireId is not a member of project $projectId")
        }
        if (attesterId !in memberIds) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The person you asked is not on this project")
        }
    }

    /** Everything waiting on this person to answer, oldest first — the queue they work through. */
    @Transactional(readOnly = true)
    fun pendingFor(attesterId: UUID): List<Attestation> =
        attestationRepository
            .findAllByAttesterIdAndState(attesterId, AttestationState.REQUESTED)
            .sortedBy { it.requestedAt }

    /** Every attestation this hire has asked for on a project, newest last. */
    @Transactional(readOnly = true)
    fun forHire(hireId: UUID, projectId: UUID): List<Attestation> =
        attestationRepository.findAllByHireIdAndProjectId(hireId, projectId).sortedBy { it.requestedAt }

    /**
     * Confirms the work happened and met the bar. Only the person who was asked may do this.
     *
     * @throws ResponseStatusException 404 when no such attestation exists, 403 when the caller is
     * not the attester, 409 when it is no longer waiting on an answer.
     */
    @Transactional
    fun accept(attestationId: UUID, attesterId: UUID): Attestation {
        val attestation = requirePending(attestationId, attesterId)
        val now = clock.instant()
        attestation.state = AttestationState.ACCEPTED
        attestation.acceptedAt = now
        attestation.firstResponseAt = attestation.firstResponseAt ?: now
        attestation.returnReason = null
        return attestationRepository.save(attestation)
    }

    /**
     * Sends the work back with a reason, which counts as rework.
     *
     * Stays [AttestationState.REQUESTED] rather than moving to a rejected state: the hire is
     * expected to act on the reason and the same request carries on, exactly as a pull request with
     * changes requested does. [Attestation.returnedCount] is what autonomy later reads.
     *
     * @throws ResponseStatusException 400 when no reason is given — "no, and I won't say why" is
     * not something a hire can act on.
     */
    @Transactional
    fun sendBack(attestationId: UUID, attesterId: UUID, reason: String): Attestation {
        if (reason.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Say what needs to change")
        }
        val attestation = requirePending(attestationId, attesterId)
        val now = clock.instant()
        attestation.returnedCount += 1
        attestation.returnReason = reason.trim()
        // Set once and never moved: a second pass must not erase how long the first one took.
        attestation.firstResponseAt = attestation.firstResponseAt ?: now
        return attestationRepository.save(attestation)
    }

    /**
     * Withdraws a request the hire no longer wants answered.
     *
     * @throws ResponseStatusException 404 when no such attestation exists or it is not theirs, 409
     * when it has already been accepted — an accepted contribution is a fact, not a draft.
     */
    @Transactional
    fun withdraw(attestationId: UUID, hireId: UUID): Attestation {
        val attestation = requireOwnedBy(attestationId, hireId)
        if (attestation.state == AttestationState.ACCEPTED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "That work has already been confirmed")
        }
        attestation.state = AttestationState.WITHDRAWN
        return attestationRepository.save(attestation)
    }

    private fun requireOwnedBy(attestationId: UUID, hireId: UUID): Attestation {
        val attestation = findOrThrow(attestationId)
        if (attestation.hireId != hireId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such attestation")
        }
        return attestation
    }

    private fun findOrThrow(attestationId: UUID): Attestation =
        attestationRepository.findById(attestationId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such attestation")

    private fun requirePending(attestationId: UUID, attesterId: UUID): Attestation {
        val attestation = findOrThrow(attestationId)
        if (attestation.attesterId != attesterId) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the person who was asked can answer this",
            )
        }
        if (attestation.state != AttestationState.REQUESTED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "That request is no longer waiting on an answer")
        }
        return attestation
    }
}

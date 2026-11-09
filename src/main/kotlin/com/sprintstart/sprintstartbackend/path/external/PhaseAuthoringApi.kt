package com.sprintstart.sprintstartbackend.path.external

import com.sprintstart.sprintstartbackend.path.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.path.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.path.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.path.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.UpdateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/**
 * The phases of a team member's path and the knowledge checks that close them, as their manager edits
 * them. Only the member's own copy changes; the blueprint it was built from does not.
 *
 * By id and without an access check, as [PathReviewApi]. A call that names a phase that is not there
 * throws a 404 [org.springframework.web.server.ResponseStatusException].
 */
interface PhaseAuthoringApi {
    /** Adds a phase to the member's path. */
    fun createPhase(userId: UserId, request: CreateOnboardingPhaseRequest): CreateOnboardingPhaseResponse

    /** Phase [phaseId]. */
    fun phase(phaseId: PhaseId): GetOnboardingPhaseResponse

    /** Every phase of the member's path. */
    fun phasesOf(userId: UserId): List<GetOnboardingPhasesResponse>

    /** Replaces phase [phaseId]. */
    fun updatePhase(phaseId: PhaseId, request: UpdateOnboardingPhaseRequest): UpdateOnboardingPhaseResponse

    /** Deletes phase [phaseId] and everything on it. */
    fun deletePhase(phaseId: PhaseId)

    /** The knowledge checks of phase [phaseId], with their answers. */
    fun checksOf(phaseId: PhaseId): GetPhaseQuestionsResponse

    /** Replaces the knowledge checks of phase [phaseId]. */
    fun replaceChecks(phaseId: PhaseId, request: UpdatePhaseQuestionsRequest): GetPhaseQuestionsResponse
}

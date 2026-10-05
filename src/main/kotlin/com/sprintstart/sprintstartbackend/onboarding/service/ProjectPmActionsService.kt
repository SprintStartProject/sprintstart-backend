package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.ProjectPmActionsResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingSkipRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Counts pending PM actions for the current members of a project.
 *
 * Onboarding paths belong to users rather than projects, so membership defines the scope, matching
 * the project team's skip and feedback tools. Each request and feedback item counts independently,
 * including requests on non-active steps and feedback without a step. Counts are read on demand so
 * decisions and acknowledgements are reflected on the next request.
 */
@Service
class ProjectPmActionsService(
    private val projectMembershipApi: ProjectMembershipApi,
    private val onboardingSkipRepository: OnboardingSkipRepository,
    private val onboardingFeedbackRepository: OnboardingFeedbackRepository,
) {
    /**
     * Returns the action counts without loading paths or individual actions.
     *
     * @throws ResponseStatusException 404 when the project does not exist.
     */
    @Transactional(readOnly = true)
    fun getPmActions(projectId: UUID): ProjectPmActionsResponse {
        if (!projectMembershipApi.projectExists(projectId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No project found with id: $projectId")
        }
        val memberIds = projectMembershipApi.getProjectMembers(projectId).map { it.userId }.toSet()
        if (memberIds.isEmpty()) {
            return ProjectPmActionsResponse(pendingSkipRequests = 0, unreadFeedback = 0)
        }

        return ProjectPmActionsResponse(
            pendingSkipRequests = onboardingSkipRepository.countByStepPhasePathUserIdInAndStatus(
                memberIds,
                SkipStatus.PENDING,
            ),
            unreadFeedback = onboardingFeedbackRepository.countByUserIdInAndReadFalse(memberIds),
        )
    }
}

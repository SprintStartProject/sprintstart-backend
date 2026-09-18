package com.sprintstart.sprintstartbackend.onboarding.security

import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckQuestionRepository
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Onboarding-scoped authorization predicates for use in `@PreAuthorize` expressions.
 *
 * Registered under the bean name `onboardingAuth` so admin-facing onboarding routes can declare
 * their access rule inline, for example
 * `@PreAuthorize("@onboardingAuth.canManagePhase(authentication, #phaseId)")`. Admin onboarding
 * endpoints address entities (phases, questions) rather than projects, so the project has to be
 * resolved first: an onboarding path records which blueprint it was personalized from, and the
 * blueprint records the owning project. The management decision itself is delegated to
 * [ProjectAuthorization], keeping "who may manage a project" defined in exactly one place.
 *
 * The resolution is fail-closed: when the chain from entity to project is broken (unknown entity,
 * a path without a blueprint, a global blueprint without a project), non-admin callers are denied.
 * Admins are allowed regardless, matching [ProjectAuthorization.canManageProject].
 *
 * Note that every `@WebMvcTest` slice covering a route that uses one of these expressions must
 * provide a mock for this bean, otherwise the expression fails to resolve at request time.
 */
@Component("onboardingAuth")
class OnboardingAuthorization(
    private val projectAuth: ProjectAuthorization,
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val phaseCheckQuestionRepository: PhaseCheckQuestionRepository,
    private val blueprintPathRepository: BlueprintPathRepository,
) {
    /**
     * Checks whether the authenticated principal may manage the project an onboarding phase
     * belongs to.
     *
     * @param authentication The current authentication.
     * @param phaseId Onboarding phase identifier.
     * @return `true` for admins and for the manager of the project whose blueprint produced the
     * phase's path; `false` when the phase or its owning project cannot be resolved.
     */
    @Transactional(readOnly = true)
    fun canManagePhase(authentication: Authentication, phaseId: UUID): Boolean {
        if (projectAuth.isAdmin(authentication)) return true
        val phase = onboardingPhaseRepository.findById(phaseId).orElse(null) ?: return false
        return canManagePath(authentication, phase.path)
    }

    /**
     * Checks whether the authenticated principal may manage the project a knowledge-check
     * question belongs to.
     *
     * @param authentication The current authentication.
     * @param questionId Onboarding check-question identifier.
     * @return `true` for admins and for the manager of the project whose blueprint produced the
     * question's path; `false` when the question or its owning project cannot be resolved.
     */
    @Transactional(readOnly = true)
    fun canManageQuestion(authentication: Authentication, questionId: UUID): Boolean {
        if (projectAuth.isAdmin(authentication)) return true
        val question = phaseCheckQuestionRepository.findById(questionId).orElse(null) ?: return false
        return canManagePath(authentication, question.phase.path)
    }

    /**
     * Resolves the project that owns an onboarding path via the blueprint the path was
     * personalized from, then delegates the management decision to [ProjectAuthorization].
     */
    private fun canManagePath(authentication: Authentication, path: OnboardingPath): Boolean {
        val blueprintId = path.blueprintId ?: return false
        val projectId = blueprintPathRepository.findById(blueprintId).orElse(null)?.projectId ?: return false
        return projectAuth.canManageProject(authentication, projectId)
    }
}

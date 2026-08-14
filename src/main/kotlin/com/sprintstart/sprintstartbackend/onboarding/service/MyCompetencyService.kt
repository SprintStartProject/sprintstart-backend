package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Serves the authenticated user their own durable competency ledger.
 *
 * This is the self-serve equivalent of the PM-only
 * [CompetencyDashboardService.getUserCompetencyStates]. The ledger is global (a proven skill
 * transfers across projects), so this view is not project-scoped. Every ledger row is returned,
 * including level-0 (placed-but-unknown) rows, so the client decides what to surface.
 */
@Service
class MyCompetencyService(
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val competencyRepository: CompetencyRepository,
    private val userApi: UserApi,
) {
    /**
     * Returns the authenticated user's full competency ledger, each row labeled and typed from the
     * competency catalog. Rows whose competency key no longer exists in the catalog are dropped.
     *
     * @param authId External authentication identifier from the JWT subject.
     * @return The user's ledger rows; empty when the user has no ledger yet.
     * @throws ResponseStatusException 404 if no user exists for [authId].
     */
    @Transactional(readOnly = true)
    fun getMyCompetencies(authId: String): List<MyCompetencyResponse> {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
        return getCompetenciesForUser(userId)
    }

    /**
     * Returns [userId]'s full competency ledger, labeled and typed from the catalog.
     *
     * The already-resolved-user counterpart of [getMyCompetencies], for callers that hold a user id
     * rather than an auth subject (e.g. the buddy agent reading the caller's own ledger).
     *
     * @param userId Internal SprintStart user identifier.
     * @return The user's ledger rows; empty when the user has no ledger yet.
     */
    @Transactional(readOnly = true)
    fun getCompetenciesForUser(userId: UUID): List<MyCompetencyResponse> {
        val states = userCompetencyStateRepository.findAllByUserId(userId)
        if (states.isEmpty()) return emptyList()

        val competenciesByKey = competencyRepository
            .findAllByKeyIn(states.map { it.competencyKey })
            .associateBy { it.key }

        return states.mapNotNull { state ->
            competenciesByKey[state.competencyKey]?.let { competency ->
                MyCompetencyResponse(
                    competencyKey = state.competencyKey,
                    label = competency.label,
                    kind = competency.kind,
                    level = state.level,
                    targetLevel = competency.targetLevel,
                    source = state.source,
                    updatedAt = state.updatedAt,
                )
            }
        }
    }
}

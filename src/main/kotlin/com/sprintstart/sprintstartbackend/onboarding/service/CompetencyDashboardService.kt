package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.CompetencyAggregateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.CompetencySourceCounts
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.DashboardProjectResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.DashboardProjectRoleResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.UserCompetencyStateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.UserCompetencySummaryResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * PM-facing team-wide competency signal, sourced from the durable ledger
 * ([com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState]): for every
 * competency, who holds it and at what level, from verified/assessed evidence rather than any
 * self-reported "marked done" state.
 */
@Service
class CompetencyDashboardService(
    private val competencyRepository: CompetencyRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val userApi: UserApi,
) {
    /**
     * Returns, for every live competency, how many users hold it at each level and by which
     * [CompetencySource].
     *
     * A competency with no ledger rows yet still appears, with zero counts -- so a PM sees graph
     * coverage gaps, not just an empty list.
     */
    @Transactional(readOnly = true)
    fun getCompetencyAggregate(): List<CompetencyAggregateResponse> {
        val statesByKey = userCompetencyStateRepository.findAll().groupBy { it.competencyKey }

        return competencyRepository.findAll().map { competency ->
            val engaged = statesByKey[competency.key].orEmpty().filter { it.level > 0 }
            CompetencyAggregateResponse(
                competencyKey = competency.key,
                label = competency.label,
                kind = competency.kind,
                usersEngaged = engaged.size,
                levelCounts = engaged.groupingBy { it.level }.eachCount(),
                sourceCounts = CompetencySourceCounts(
                    assessed = engaged.count { it.source == CompetencySource.ASSESSED },
                    verified = engaged.count { it.source == CompetencySource.VERIFIED },
                    declared = engaged.count { it.source == CompetencySource.DECLARED },
                ),
            )
        }
    }

    /**
     * Returns a paginated, per-user breakdown of each user's full competency ledger.
     *
     * Pagination is delegated straight to [UserApi.searchUsers] -- there is no computed-field sort
     * that would force fetching every user up front.
     */
    @Transactional(readOnly = true)
    fun getUserCompetencySummaries(
        search: String?,
        roleIds: List<UUID>?,
        projectIds: List<UUID>?,
        pageable: Pageable,
    ): Page<UserCompetencySummaryResponse> {
        val usersPage = userApi.searchUsers(search, roleIds, projectIds, pageable)
        val users = usersPage.content
        if (users.isEmpty()) {
            return PageImpl(emptyList(), pageable, usersPage.totalElements)
        }

        val statesByUser = userCompetencyStateRepository
            .findAllByUserIdIn(users.map { it.id })
            .groupBy { it.userId }
        val competenciesByKey = competencyRepository.findAll().associateBy { it.key }

        val summaries = users.map { user ->
            UserCompetencySummaryResponse(
                userId = user.id,
                firstname = user.firstname,
                lastname = user.lastname,
                profileIcon = user.profileIcon,
                roles = user.projectRoles.map { DashboardProjectRoleResponse(id = it.roleId, name = it.name) },
                projects = user.projects.map { DashboardProjectResponse(id = it.projectId, name = it.name) },
                competencies = statesByUser[user.id].orEmpty().mapNotNull { state ->
                    competenciesByKey[state.competencyKey]?.let { competency ->
                        UserCompetencyStateResponse(
                            competencyKey = state.competencyKey,
                            label = competency.label,
                            level = state.level,
                            source = state.source,
                            updatedAt = state.updatedAt,
                        )
                    }
                },
            )
        }
        return PageImpl(summaries, pageable, usersPage.totalElements)
    }

    /**
     * Returns one user's full competency ledger, labeled -- the per-member view the PM member
     * detail page shows.
     *
     * @throws ResponseStatusException 404 if no user matches [userId].
     */
    @Transactional(readOnly = true)
    fun getUserCompetencyStates(userId: UUID): List<UserCompetencyStateResponse> {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }

        val states = userCompetencyStateRepository.findAllByUserId(userId)
        if (states.isEmpty()) return emptyList()

        val competenciesByKey = competencyRepository
            .findAllByKeyIn(states.map { it.competencyKey })
            .associateBy { it.key }

        return states.mapNotNull { state ->
            competenciesByKey[state.competencyKey]?.let { competency ->
                UserCompetencyStateResponse(
                    competencyKey = state.competencyKey,
                    label = competency.label,
                    level = state.level,
                    source = state.source,
                    updatedAt = state.updatedAt,
                )
            }
        }
    }
}

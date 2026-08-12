package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompetencyDashboardServiceTest {
    private val competencyRepository: CompetencyRepository = mockk()
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = CompetencyDashboardService(competencyRepository, userCompetencyStateRepository, userApi)

    private val pageable: Pageable = PageRequest.of(0, 20)

    private fun user(id: UUID, firstname: String, lastname: String): UserDto =
        UserDto(id, "u-$firstname", firstname, lastname, null, null, emptySet(), emptyList())

    @Nested
    inner class GetCompetencyAggregate {
        @Test
        fun `buckets engaged users by level and source, excluding unengaged rows`() {
            every { competencyRepository.findAll() } returns
                listOf(Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL))
            every { userCompetencyStateRepository.findAll() } returns
                listOf(
                    UserCompetencyState(
                        userId = UUID.randomUUID(),
                        competencyKey = "kotlin",
                        level = 3,
                        source = CompetencySource.VERIFIED,
                    ),
                    UserCompetencyState(
                        userId = UUID.randomUUID(),
                        competencyKey = "kotlin",
                        level = 2,
                        source = CompetencySource.DECLARED,
                    ),
                    // Unengaged (level 0) row must not count.
                    UserCompetencyState(
                        userId = UUID.randomUUID(),
                        competencyKey = "kotlin",
                        level = 0,
                        source = CompetencySource.ASSESSED,
                    ),
                )

            val result = service.getCompetencyAggregate()

            assertEquals(1, result.size)
            val kotlin = result[0]
            assertEquals("kotlin", kotlin.competencyKey)
            assertEquals(2, kotlin.usersEngaged)
            assertEquals(mapOf(3 to 1, 2 to 1), kotlin.levelCounts)
            assertEquals(1, kotlin.sourceCounts.verified)
            assertEquals(1, kotlin.sourceCounts.declared)
            assertEquals(0, kotlin.sourceCounts.assessed)
        }

        @Test
        fun `a competency with no ledger rows still appears with zero counts`() {
            every { competencyRepository.findAll() } returns
                listOf(Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL))
            every { userCompetencyStateRepository.findAll() } returns emptyList()

            val result = service.getCompetencyAggregate()

            assertEquals(1, result.size)
            assertEquals(0, result[0].usersEngaged)
            assertTrue(result[0].levelCounts.isEmpty())
        }
    }

    @Nested
    inner class GetUserCompetencySummaries {
        @Test
        fun `echoes the roles and projects the endpoint already filters by`() {
            // The endpoint accepts roleIds/projectIds, so a client can narrow by role -- it must
            // also be able to say which role somebody holds, or the team list has to invent it.
            val userId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val projectId = UUID.randomUUID()
            every { userApi.searchUsers(null, null, null, pageable) } returns PageImpl(
                listOf(
                    UserDto(
                        userId,
                        "u-ada",
                        "Ada",
                        "Lovelace",
                        null,
                        "icon-1",
                        setOf(ProjectDto(projectId, "Apollo", null)),
                        listOf(ProjectRoleDto(roleId, "Backend Developer", "")),
                    ),
                ),
            )
            every { userCompetencyStateRepository.findAllByUserIdIn(listOf(userId)) } returns emptyList()
            every { competencyRepository.findAll() } returns emptyList()

            val summary = service.getUserCompetencySummaries(null, null, null, pageable).content.single()

            assertEquals(listOf(roleId), summary.roles.map { it.id })
            assertEquals(listOf("Backend Developer"), summary.roles.map { it.name })
            assertEquals(listOf(projectId), summary.projects.map { it.id })
            assertEquals("icon-1", summary.profileIcon)
        }

        @Test
        fun `returns an empty page when no users match`() {
            every { userApi.searchUsers("nonexistent", null, null, pageable) } returns PageImpl(emptyList())

            val result = service.getUserCompetencySummaries("nonexistent", null, null, pageable)

            assertTrue(result.isEmpty)
        }

        @Test
        fun `joins each user's ledger with competency labels, dropping unknown keys`() {
            val userId = UUID.randomUUID()
            every { userApi.searchUsers(null, null, null, pageable) } returns
                PageImpl(listOf(user(userId, "f1", "l1")))
            every { userCompetencyStateRepository.findAllByUserIdIn(listOf(userId)) } returns
                listOf(
                    UserCompetencyState(
                        userId = userId,
                        competencyKey = "kotlin",
                        level = 3,
                        source = CompetencySource.VERIFIED,
                    ),
                    // No matching live competency for this key -- should be dropped, not crash.
                    UserCompetencyState(
                        userId = userId,
                        competencyKey = "removed-competency",
                        level = 1,
                        source = CompetencySource.DECLARED,
                    ),
                )
            every { competencyRepository.findAll() } returns
                listOf(Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL))

            val result = service.getUserCompetencySummaries(null, null, null, pageable)

            assertEquals(1, result.content.size)
            val summary = result.content[0]
            assertEquals(userId, summary.userId)
            assertEquals(1, summary.competencies.size)
            assertEquals("kotlin", summary.competencies[0].competencyKey)
            assertEquals("Kotlin", summary.competencies[0].label)
        }
    }

    @Nested
    inner class GetUserCompetencyStates {
        @Test
        fun `throws 404 when the user does not exist`() {
            val userId = UUID.randomUUID()
            every { userApi.exists(userId) } returns false

            val ex = assertThrows<ResponseStatusException> { service.getUserCompetencyStates(userId) }

            assertEquals(404, ex.statusCode.value())
        }

        @Test
        fun `returns the user's labeled ledger, dropping unknown keys`() {
            val userId = UUID.randomUUID()
            every { userApi.exists(userId) } returns true
            every { userCompetencyStateRepository.findAllByUserId(userId) } returns
                listOf(
                    UserCompetencyState(
                        userId = userId,
                        competencyKey = "kotlin",
                        level = 2,
                        source = CompetencySource.VERIFIED,
                    ),
                    UserCompetencyState(
                        userId = userId,
                        competencyKey = "removed-competency",
                        level = 1,
                        source = CompetencySource.DECLARED,
                    ),
                )
            every { competencyRepository.findAllByKeyIn(listOf("kotlin", "removed-competency")) } returns
                listOf(Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL))

            val result = service.getUserCompetencyStates(userId)

            assertEquals(1, result.size)
            assertEquals("Kotlin", result[0].label)
            assertEquals(2, result[0].level)
        }

        @Test
        fun `returns an empty list when the user has no ledger rows`() {
            val userId = UUID.randomUUID()
            every { userApi.exists(userId) } returns true
            every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()

            val result = service.getUserCompetencyStates(userId)

            assertTrue(result.isEmpty())
        }
    }
}

package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class MyCompetencyServiceTest {
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk()
    private val competencyRepository: CompetencyRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = MyCompetencyService(userCompetencyStateRepository, competencyRepository, userApi)

    private val authId = "auth|test-user"
    private val userId = UUID.randomUUID()

    @Test
    fun `returns the user's ledger rows labeled and typed from the catalog`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns listOf(
            UserCompetencyState(
                userId = userId,
                competencyKey = "kotlin",
                level = 3,
                source = CompetencySource.VERIFIED,
            ),
        )
        every { competencyRepository.findAllByKeyIn(listOf("kotlin")) } returns listOf(
            Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL),
        )

        val result = service.getMyCompetencies(authId)

        assertThat(result).hasSize(1)
        assertThat(result[0].competencyKey).isEqualTo("kotlin")
        assertThat(result[0].label).isEqualTo("Kotlin")
        assertThat(result[0].kind).isEqualTo(CompetencyKind.SKILL)
        assertThat(result[0].level).isEqualTo(3)
        assertThat(result[0].source).isEqualTo(CompetencySource.VERIFIED)
    }

    @Test
    fun `returns an empty list when the user has no ledger yet`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()

        assertThat(service.getMyCompetencies(authId)).isEmpty()
    }

    @Test
    fun `drops ledger rows whose competency no longer exists in the catalog`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns listOf(
            UserCompetencyState(
                userId = userId,
                competencyKey = "kotlin",
                level = 3,
                source = CompetencySource.VERIFIED,
            ),
            UserCompetencyState(
                userId = userId,
                competencyKey = "removed",
                level = 2,
                source = CompetencySource.ASSESSED,
            ),
        )
        every { competencyRepository.findAllByKeyIn(listOf("kotlin", "removed")) } returns listOf(
            Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL),
        )

        val result = service.getMyCompetencies(authId)

        assertThat(result.map { it.competencyKey }).containsExactly("kotlin")
    }

    @Test
    fun `throws 404 when the user cannot be resolved`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { service.getMyCompetencies(authId) }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}

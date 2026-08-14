package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.CanonicalAnswerRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class KnowledgeBaseServiceTest {
    private val knowledgeRequestRepository: KnowledgeRequestRepository = mockk()
    private val canonicalAnswerRepository: CanonicalAnswerRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = KnowledgeBaseService(
        knowledgeRequestRepository,
        canonicalAnswerRepository,
        userApi,
    )

    private val authId = "auth|hire"
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    private fun userWith(vararg projects: ProjectDto) = UserDto(
        id = userId,
        username = "hire",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = null,
        projects = projects.toSet(),
        projectRoles = emptyList(),
    )

    @Test
    fun `escalate records an open request owned by the hire`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        val saved = slot<KnowledgeRequest>()
        every { knowledgeRequestRepository.save(capture(saved)) } answers { firstArg() }

        service.escalate(authId, projectId, "  How do we deploy?  ")

        assertThat(saved.captured.hireId).isEqualTo(userId)
        assertThat(saved.captured.projectId).isEqualTo(projectId)
        assertThat(saved.captured.question).isEqualTo("How do we deploy?")
        assertThat(saved.captured.status).isEqualTo(KnowledgeRequestStatus.OPEN)
    }

    @Test
    fun `escalate rejects a blank question`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

        assertThrows<ResponseStatusException> {
            service.escalate(authId, projectId, "   ")
        }.also { assertThat(it.statusCode.value()).isEqualTo(400) }
    }

    @Test
    fun `escalate 404s when the hire is not on the project`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        assertThrows<ResponseStatusException> {
            service.escalate(authId, projectId, "How do we deploy?")
        }.also { assertThat(it.statusCode.value()).isEqualTo(404) }
    }

    @Test
    fun `answering mints a canonical answer and closes the request against it`() {
        val pmAuthId = "auth|pm"
        val pmId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val request = KnowledgeRequest(
            id = requestId,
            projectId = projectId,
            hireId = userId,
            question = "How do we deploy?",
        )

        every { userApi.getUserIdByAuthId(pmAuthId) } returns Optional.of(pmId)
        every { knowledgeRequestRepository.findById(requestId) } returns Optional.of(request)
        val savedAnswer = slot<CanonicalAnswer>()
        every { canonicalAnswerRepository.save(capture(savedAnswer)) } answers { firstArg() }
        val savedRequest = slot<KnowledgeRequest>()
        every { knowledgeRequestRepository.save(capture(savedRequest)) } answers { firstArg() }

        val result = service.answer(pmAuthId, requestId, "Run ./deploy.sh from main.", questionOverride = null)

        // The durable answer inherits the request's question and the PM's authorship.
        assertThat(savedAnswer.captured.question).isEqualTo("How do we deploy?")
        assertThat(savedAnswer.captured.answer).isEqualTo("Run ./deploy.sh from main.")
        assertThat(savedAnswer.captured.authorId).isEqualTo(pmId)
        // The request is closed and linked to it.
        assertThat(savedRequest.captured.status).isEqualTo(KnowledgeRequestStatus.ANSWERED)
        assertThat(savedRequest.captured.canonicalAnswerId).isEqualTo(result.id)
        assertThat(savedRequest.captured.answeredBy).isEqualTo(pmId)
    }

    @Test
    fun `search scopes to the caller's projects and ranks by term overlap`() {
        val deployAnswer = CanonicalAnswer(
            projectId = projectId,
            question = "How do we deploy?",
            answer = "Run deploy.sh",
            authorId = UUID.randomUUID(),
        )
        val unrelated = CanonicalAnswer(
            projectId = projectId,
            question = "Where is the changelog?",
            answer = "In CHANGELOG.md",
            authorId = UUID.randomUUID(),
        )
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        every { canonicalAnswerRepository.findAllByProjectIdIn(listOf(projectId)) } returns
            listOf(unrelated, deployAnswer)

        val results = service.searchForUser(userId, "how do we deploy")

        assertThat(results).containsExactly(deployAnswer)
    }

    @Test
    fun `search returns nothing when the caller is on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())

        assertThat(service.searchForUser(userId, "deploy")).isEmpty()
    }
}

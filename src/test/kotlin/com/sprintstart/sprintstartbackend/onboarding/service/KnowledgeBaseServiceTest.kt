package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.repository.CanonicalAnswerRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class KnowledgeBaseServiceTest {
    private val knowledgeRequestRepository: KnowledgeRequestRepository = mockk()
    private val canonicalAnswerRepository: CanonicalAnswerRepository = mockk()
    private val userApi: UserApi = mockk()
    private val onboardingPathRepository: OnboardingPathRepository = mockk()

    // The real reader, not a mock: the point of extracting it was that one rule answers "where is
    // this person", so a test that stubs the answer would stop covering the thing that broke.
    private val service = KnowledgeBaseService(
        knowledgeRequestRepository,
        canonicalAnswerRepository,
        userApi,
        OnboardingPositionReader(onboardingPathRepository),
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

    @Nested
    inner class ListOpen {
        private fun openRequest(hireId: UUID) = KnowledgeRequest(
            projectId = projectId,
            hireId = hireId,
            question = "How do we deploy?",
        )

        private fun pathFor(hireId: UUID, stepStatus: StepStatus): OnboardingPath {
            val path = OnboardingPath(userId = hireId)
            val phase = OnboardingPhase(
                path = path,
                position = 0,
                title = "Getting started",
                description = "The first week",
            )
            path.phases += phase
            phase.steps += OnboardingStep(
                phase = phase,
                position = 0,
                title = "Set up your machine",
                description = "Desc",
                type = StepType.DOCUMENT,
                estimatedMinutes = 30,
                expectedOutcome = "Outcome",
                status = stepStatus,
            )
            return path
        }

        @Test
        fun `each open question carries who asked it and where they are`() {
            every {
                knowledgeRequestRepository
                    .findAllByProjectIdAndStatusOrderByCreatedAtAsc(projectId, KnowledgeRequestStatus.OPEN)
            } returns listOf(openRequest(userId))
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
            every { onboardingPathRepository.findByUserIdIn(listOf(userId)) } returns
                listOf(pathFor(userId, StepStatus.IN_PROGRESS))

            val hire = service.listOpen(projectId).single().hire

            assertThat(hire?.userId).isEqualTo(userId)
            assertThat(hire?.displayName).isEqualTo("Sam Hire")
            assertThat(hire?.currentPhase).isEqualTo("Getting started")
            assertThat(hire?.currentStep).isEqualTo("Set up your machine")
            assertThat(hire?.progressPercentage).isEqualTo(0.0)
        }

        @Test
        fun `a hire with no onboarding path is reported as having no position, not as an error`() {
            every {
                knowledgeRequestRepository
                    .findAllByProjectIdAndStatusOrderByCreatedAtAsc(projectId, KnowledgeRequestStatus.OPEN)
            } returns listOf(openRequest(userId))
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
            every { onboardingPathRepository.findByUserIdIn(listOf(userId)) } returns emptyList()

            val hire = service.listOpen(projectId).single().hire

            assertThat(hire?.displayName).isEqualTo("Sam Hire")
            assertThat(hire?.currentPhase).isNull()
            assertThat(hire?.currentStep).isNull()
            assertThat(hire?.progressPercentage).isEqualTo(0.0)
        }

        @Test
        fun `a question whose asker cannot be resolved still appears in the queue`() {
            val ghost = UUID.randomUUID()
            every {
                knowledgeRequestRepository
                    .findAllByProjectIdAndStatusOrderByCreatedAtAsc(projectId, KnowledgeRequestStatus.OPEN)
            } returns listOf(openRequest(ghost))
            every { userApi.getUsersByIds(listOf(ghost)) } returns emptyList()
            every { onboardingPathRepository.findByUserIdIn(listOf(ghost)) } returns emptyList()

            val response = service.listOpen(projectId).single()

            assertThat(response.hireId).isEqualTo(ghost)
            assertThat(response.hire).isNull()
        }

        @Test
        fun `the queue is enriched per distinct asker, not per question`() {
            every {
                knowledgeRequestRepository
                    .findAllByProjectIdAndStatusOrderByCreatedAtAsc(projectId, KnowledgeRequestStatus.OPEN)
            } returns listOf(openRequest(userId), openRequest(userId), openRequest(userId))
            // Stubbed with the distinct id exactly once: three questions from one hire that looked up
            // the user three times would not match this, and would fail rather than merely be slow.
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
            every { onboardingPathRepository.findByUserIdIn(listOf(userId)) } returns
                listOf(pathFor(userId, StepStatus.WAITING))

            val responses = service.listOpen(projectId)

            assertThat(responses).hasSize(3)
            assertThat(responses.map { it.hire?.displayName }).containsOnly("Sam Hire")
            verify(exactly = 1) { userApi.getUsersByIds(listOf(userId)) }
            verify(exactly = 1) { onboardingPathRepository.findByUserIdIn(listOf(userId)) }
        }

        @Test
        fun `an empty queue reads nothing beyond the queue itself`() {
            every {
                knowledgeRequestRepository
                    .findAllByProjectIdAndStatusOrderByCreatedAtAsc(projectId, KnowledgeRequestStatus.OPEN)
            } returns emptyList()

            assertThat(service.listOpen(projectId)).isEmpty()
            verify(exactly = 0) { userApi.getUsersByIds(any()) }
        }
    }

    @Test
    fun `a hire reading their own escalations is told nothing new about themselves`() {
        val request = KnowledgeRequest(projectId = projectId, hireId = userId, question = "How do we deploy?")
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { knowledgeRequestRepository.findAllByHireIdOrderByCreatedAtDesc(userId) } returns listOf(request)
        every { canonicalAnswerRepository.findAllById(emptyList()) } returns emptyList()

        assertThat(service.listMine(authId).single().hire).isNull()
    }
}

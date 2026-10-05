package com.sprintstart.sprintstartbackend.onboarding.service

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSkip
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/** Exercises the count queries against persisted actions, not stubbed count results. */
@ActiveProfiles("test")
@DataJpaTest
@Import(ProjectPmActionsService::class, CryptoConfiguration::class)
class ProjectPmActionsServiceTest {
    @Autowired
    private lateinit var service: ProjectPmActionsService

    @Autowired
    private lateinit var entityManager: EntityManager

    @MockkBean
    private lateinit var projectMembershipApi: ProjectMembershipApi

    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { projectMembershipApi.projectExists(projectId) } returns true
    }

    @Test
    fun `counts each action for members with both feedback and skips across all steps`() {
        val memberId = UUID.randomUUID()
        members(projectId, memberId)
        val steps = stepsFor(memberId)
        skip(steps[0])
        skip(steps[1])
        skip(steps[0], SkipStatus.ACCEPTED)
        skip(steps[1], SkipStatus.DENIED)
        feedback(memberId, steps[0])
        feedback(memberId, steps[0])
        feedback(memberId)
        feedback(memberId, steps[1], read = true)

        val summary = service.getPmActions(projectId)

        assertThat(summary.pendingSkipRequests).isEqualTo(2)
        assertThat(summary.unreadFeedback).isEqualTo(3)
        assertThat(summary.total).isEqualTo(5)
    }

    @Test
    fun `uses current selected project membership including members without a path`() {
        val firstMember = UUID.randomUUID()
        val secondMember = UUID.randomUUID()
        val memberWithoutPath = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        every { projectMembershipApi.projectExists(otherProjectId) } returns true
        members(projectId, firstMember, memberWithoutPath)
        members(otherProjectId, secondMember)
        skip(stepsFor(firstMember)[1])
        feedback(firstMember)
        feedback(memberWithoutPath)
        skip(stepsFor(secondMember)[0])
        feedback(secondMember)
        feedback(secondMember)
        feedback(secondMember)
        val outsider = UUID.randomUUID()
        skip(stepsFor(outsider)[0])
        feedback(outsider)

        val first = service.getPmActions(projectId)
        val second = service.getPmActions(otherProjectId)

        assertThat(first.pendingSkipRequests).isEqualTo(1)
        assertThat(first.unreadFeedback).isEqualTo(2)
        assertThat(first.total).isEqualTo(3)
        assertThat(second.pendingSkipRequests).isEqualTo(1)
        assertThat(second.unreadFeedback).isEqualTo(3)
        assertThat(second.total).isEqualTo(4)

        members(projectId, memberWithoutPath)
        assertThat(service.getPmActions(projectId).total).isEqualTo(1)
    }

    @Test
    fun `accepted and denied requests and acknowledged feedback leave the next count`() {
        val memberId = UUID.randomUUID()
        members(projectId, memberId)
        val steps = stepsFor(memberId)
        val accepted = skip(steps[0])
        val denied = skip(steps[1])
        val acknowledged = feedback(memberId)
        assertThat(service.getPmActions(projectId).total).isEqualTo(3)

        accepted.status = SkipStatus.ACCEPTED
        accepted.resolvedAt = Instant.now()
        denied.status = SkipStatus.DENIED
        denied.resolvedAt = Instant.now()
        acknowledged.read = true
        entityManager.flush()
        entityManager.clear()

        val summary = service.getPmActions(projectId)
        assertThat(summary.pendingSkipRequests).isZero()
        assertThat(summary.unreadFeedback).isZero()
        assertThat(summary.total).isZero()
    }

    @Test
    fun `an empty project has zero actions even when other users have actions`() {
        members(projectId)
        val outsider = UUID.randomUUID()
        skip(stepsFor(outsider)[0])
        feedback(outsider)

        val summary = service.getPmActions(projectId)

        assertThat(summary.pendingSkipRequests).isZero()
        assertThat(summary.unreadFeedback).isZero()
        assertThat(summary.total).isZero()
    }

    @Test
    fun `a missing project returns 404 rather than a misleading zero count`() {
        every { projectMembershipApi.projectExists(projectId) } returns false

        assertThatThrownBy { service.getPmActions(projectId) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            }
        verify(exactly = 0) { projectMembershipApi.getProjectMembers(any()) }
    }

    private fun members(id: UUID, vararg userIds: UUID) {
        every { projectMembershipApi.getProjectMembers(id) } returns userIds.map {
            ProjectMember(userId = it, displayName = "Member", githubLogin = null, joinedAt = null)
        }
    }

    private fun stepsFor(userId: UUID): List<OnboardingStep> {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Setup", description = "Setup")
        val steps = listOf(StepStatus.IN_PROGRESS, StepStatus.WAITING).mapIndexed { index, status ->
            OnboardingStep(
                phase = phase,
                position = index,
                title = "Step $index",
                description = "Setup step",
                type = StepType.TASK,
                estimatedMinutes = 10,
                expectedOutcome = "Ready",
                status = status,
            )
        }
        path.phases.add(phase)
        phase.steps.addAll(steps)
        entityManager.persist(path)
        return steps
    }

    private fun skip(step: OnboardingStep, status: SkipStatus = SkipStatus.PENDING): OnboardingSkip {
        val skip = OnboardingSkip(step = step, status = status, reason = "Already familiar")
        entityManager.persist(skip)
        return skip
    }

    private fun feedback(userId: UUID, step: OnboardingStep? = null, read: Boolean = false): OnboardingFeedback {
        val feedback = OnboardingFeedback(userId = userId, step = step, message = "Needs clarification", read = read)
        entityManager.persist(feedback)
        return feedback
    }
}

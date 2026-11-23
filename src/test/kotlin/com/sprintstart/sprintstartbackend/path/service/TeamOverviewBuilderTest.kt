package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingSkip
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TeamOverviewBuilderTest {
    private val reader = mockk<OnboardingPositionReader>()
    private val builder = TeamOverviewBuilder(reader)
    private val skillId = UUID.randomUUID()
    private val user = UserDto(
        id = UUID.randomUUID(),
        username = "sam",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = "icon",
        projects = emptySet(),
        projectRoles = emptyList(),
        skills = listOf(UserSkillDto(skillId, "Kotlin", "SENIOR")),
    )

    @Test
    fun `a person without a path has no progress and no position`() {
        val row = builder.build(user, null)

        assertThat(row.userId).isEqualTo(user.id.toString())
        assertThat(row.firstname).isEqualTo("Sam")
        assertThat(row.lastname).isEqualTo("Hire")
        assertThat(row.profileIcon).isEqualTo("icon")
        assertThat(row.progressPercentage).isEqualTo(0.0)
        assertThat(row.currentPhase).isNull()
        assertThat(row.currentStep).isNull()
        assertThat(row.hasFeedback).isFalse()
        val skill = row.skills.single()
        assertThat(skill.id).isEqualTo(skillId.toString())
        assertThat(skill.name).isEqualTo("Kotlin")
        assertThat(skill.level).isEqualTo("SENIOR")
        assertThat(skill.roleId).isNull()
    }

    @Test
    fun `a path with no active step has progress but no position`() {
        val path = mockk<OnboardingPath>()
        every { reader.progressOf(path) } returns 100.0
        every { reader.activeStepIn(path) } returns null

        val row = builder.build(user, path)

        assertThat(row.progressPercentage).isEqualTo(100.0)
        assertThat(row.currentPhase).isNull()
        assertThat(row.currentStep).isNull()
    }

    @Test
    fun `the active step is reported with the phase and the latest skip request`() {
        val path = mockk<OnboardingPath>()
        val phase = mockk<OnboardingPhase> { every { title } returns "Setup" }
        val skipStep = mockk<OnboardingStep> { every { id } returns UUID.randomUUID() }
        val resolvedAt = Instant.parse("2026-10-02T10:00:00Z")
        val skip = mockk<OnboardingSkip> {
            every { id } returns UUID.randomUUID()
            every { step } returns skipStep
            every { reason } returns "already know it"
            every { status } returns SkipStatus.DENIED
            every { reviewComment } returns "please do it"
            every { this@mockk.resolvedAt } returns resolvedAt
        }
        val startedAt = Instant.parse("2026-10-01T09:00:00Z")
        val step = mockk<OnboardingStep> {
            every { id } returns UUID.randomUUID()
            every { title } returns "Install tools"
            every { this@mockk.startedAt } returns startedAt
            every { skips } returns mutableListOf(skip)
        }
        every { reader.progressOf(path) } returns 40.0
        every { reader.activeStepIn(path) } returns ActiveStep(phase, step)

        val row = builder.build(user, path)

        assertThat(row.progressPercentage).isEqualTo(40.0)
        assertThat(row.currentPhase?.title).isEqualTo("Setup")
        val current = row.currentStep!!
        assertThat(current.id).isEqualTo(step.id.toString())
        assertThat(current.title).isEqualTo("Install tools")
        assertThat(current.startedAt).isEqualTo(startedAt)
        val request = current.skip!!
        assertThat(request.id).isEqualTo(skip.id.toString())
        assertThat(request.stepId).isEqualTo(skipStep.id.toString())
        assertThat(request.reason).isEqualTo("already know it")
        assertThat(request.status).isEqualTo("DENIED")
        assertThat(request.reviewComment).isEqualTo("please do it")
        assertThat(request.reviewedAt).isEqualTo(resolvedAt)
    }

    @Test
    fun `an active step without a skip request reports none`() {
        val path = mockk<OnboardingPath>()
        val phase = mockk<OnboardingPhase> { every { title } returns "Setup" }
        val step = mockk<OnboardingStep> {
            every { id } returns UUID.randomUUID()
            every { title } returns "Install tools"
            every { startedAt } returns null
            every { skips } returns mutableListOf()
        }
        every { reader.progressOf(path) } returns 0.0
        every { reader.activeStepIn(path) } returns ActiveStep(phase, step)

        assertThat(builder.build(user, path).currentStep!!.skip).isNull()
    }
}

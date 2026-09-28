package com.sprintstart.sprintstartbackend.onboarding.security

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.PhaseCheckQuestionRepository
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingAuthorizationTest {
    private val projectAuth: ProjectAuthorization = mockk()
    private val onboardingPhaseRepository: OnboardingPhaseRepository = mockk()
    private val phaseCheckQuestionRepository: PhaseCheckQuestionRepository = mockk()
    private val blueprintPathRepository: BlueprintPathRepository = mockk()

    private val onboardingAuth = OnboardingAuthorization(
        projectAuth,
        onboardingPhaseRepository,
        phaseCheckQuestionRepository,
        blueprintPathRepository,
    )

    private val authentication: Authentication = mockk()
    private val projectId = UUID.randomUUID()
    private val blueprintId = UUID.randomUUID()
    private val path = OnboardingPath(userId = UUID.randomUUID(), blueprintId = blueprintId)
    private val phase = OnboardingPhase(path = path, position = 0, title = "Setup", description = "d")
    private val question = PhaseCheckQuestion(
        phase = phase,
        position = 0,
        type = CheckQuestionType.MULTIPLE_CHOICE,
        question = "q",
    )

    @Test
    fun `canManagePhase allows an admin without resolving the project`() {
        every { projectAuth.isAdmin(authentication) } returns true

        assertTrue(onboardingAuth.canManagePhase(authentication, phase.id))
    }

    @Test
    fun `canManagePhase allows the manager of the project the phase's blueprint belongs to`() {
        every { projectAuth.isAdmin(authentication) } returns false
        every { onboardingPhaseRepository.findById(phase.id) } returns Optional.of(phase)
        every { blueprintPathRepository.findById(blueprintId) } returns
            Optional.of(BlueprintPath(blueprintKey = UUID.randomUUID(), title = "t", projectId = projectId))
        every { projectAuth.canManageProject(authentication, projectId) } returns true

        assertTrue(onboardingAuth.canManagePhase(authentication, phase.id))
    }

    @Test
    fun `canManagePhase denies a non-manager of the project`() {
        every { projectAuth.isAdmin(authentication) } returns false
        every { onboardingPhaseRepository.findById(phase.id) } returns Optional.of(phase)
        every { blueprintPathRepository.findById(blueprintId) } returns
            Optional.of(BlueprintPath(blueprintKey = UUID.randomUUID(), title = "t", projectId = projectId))
        every { projectAuth.canManageProject(authentication, projectId) } returns false

        assertFalse(onboardingAuth.canManagePhase(authentication, phase.id))
    }

    @Test
    fun `canManagePhase denies a non-admin when the phase does not exist`() {
        every { projectAuth.isAdmin(authentication) } returns false
        every { onboardingPhaseRepository.findById(phase.id) } returns Optional.empty()

        assertFalse(onboardingAuth.canManagePhase(authentication, phase.id))
    }

    @Test
    fun `canManagePhase denies a non-admin when the path was not personalized from a blueprint`() {
        val legacyPath = OnboardingPath(userId = UUID.randomUUID(), blueprintId = null)
        val legacyPhase = OnboardingPhase(path = legacyPath, position = 0, title = "Setup", description = "d")
        every { projectAuth.isAdmin(authentication) } returns false
        every { onboardingPhaseRepository.findById(legacyPhase.id) } returns Optional.of(legacyPhase)

        assertFalse(onboardingAuth.canManagePhase(authentication, legacyPhase.id))
    }

    @Test
    fun `canManagePhase denies a non-admin when the blueprint is global`() {
        every { projectAuth.isAdmin(authentication) } returns false
        every { onboardingPhaseRepository.findById(phase.id) } returns Optional.of(phase)
        every { blueprintPathRepository.findById(blueprintId) } returns
            Optional.of(BlueprintPath(blueprintKey = UUID.randomUUID(), title = "t"))

        assertFalse(onboardingAuth.canManagePhase(authentication, phase.id))
    }

    @Test
    fun `canManageQuestion allows an admin without resolving the project`() {
        every { projectAuth.isAdmin(authentication) } returns true

        assertTrue(onboardingAuth.canManageQuestion(authentication, question.id))
    }

    @Test
    fun `canManageQuestion allows the manager of the project the question's blueprint belongs to`() {
        every { projectAuth.isAdmin(authentication) } returns false
        every { phaseCheckQuestionRepository.findById(question.id) } returns Optional.of(question)
        every { blueprintPathRepository.findById(blueprintId) } returns
            Optional.of(BlueprintPath(blueprintKey = UUID.randomUUID(), title = "t", projectId = projectId))
        every { projectAuth.canManageProject(authentication, projectId) } returns true

        assertTrue(onboardingAuth.canManageQuestion(authentication, question.id))
    }

    @Test
    fun `canManageQuestion denies a non-admin when the question does not exist`() {
        every { projectAuth.isAdmin(authentication) } returns false
        every { phaseCheckQuestionRepository.findById(question.id) } returns Optional.empty()

        assertFalse(onboardingAuth.canManageQuestion(authentication, question.id))
    }

    @Test
    fun `canManageQuestion denies a non-manager of the project`() {
        every { projectAuth.isAdmin(authentication) } returns false
        every { phaseCheckQuestionRepository.findById(question.id) } returns Optional.of(question)
        every { blueprintPathRepository.findById(blueprintId) } returns
            Optional.of(BlueprintPath(blueprintKey = UUID.randomUUID(), title = "t", projectId = projectId))
        every { projectAuth.canManageProject(authentication, projectId) } returns false

        assertFalse(onboardingAuth.canManageQuestion(authentication, question.id))
    }
}

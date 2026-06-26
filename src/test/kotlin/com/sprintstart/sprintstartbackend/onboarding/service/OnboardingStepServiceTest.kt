package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSkip
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OnboardingStepServiceTest {
    private val onboardingPhaseRepository: OnboardingPhaseRepository = mockk()
    private val onboardingStepRepository: OnboardingStepRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = OnboardingStepService(onboardingPhaseRepository, onboardingStepRepository, userApi)

    private val userId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makePhase(): OnboardingPhase {
        val path = OnboardingPath(userId = userId)
        return OnboardingPhase(id = phaseId, path = path, position = 0, title = "Phase", description = "Desc")
    }

    private fun makeStep(position: Int = 0, status: StepStatus = StepStatus.WAITING): OnboardingStep =
        OnboardingStep(
            id = stepId,
            phase = makePhase(),
            position = position,
            title = "Step",
            description = "Desc",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Outcome",
            status = status,
        )

    private fun makeCreateRequest(position: Int = 0) = CreateOnboardingStepRequest(
        position = position,
        title = "Step",
        description = "Desc",
        type = StepType.DOCUMENT,
        estimatedMinutes = 30,
        expectedOutcome = "Outcome",
    )

    private fun makeUpdateRequest(position: Int = 0) =
        UpdateOnboardingStepRequest(
            position = position,
            title = "Updated Step",
            description = "Updated Desc",
            type = StepType.VIDEO,
            estimatedMinutes = 15,
            expectedOutcome = "New Outcome",
        )

    private fun makeSkip(step: OnboardingStep, status: SkipStatus = SkipStatus.PENDING) = OnboardingSkip(
        step = step,
        status = status,
        reason = "Not relevant",
        resolvedAt = java.time.Instant.now(),
    )

    @Nested
    inner class GetOnboardingStepsForMe {
        @Test
        fun `returns steps for authenticated user`() {
            val step = makeStep()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingStepRepository.findAllByPhaseIdAndPhasePathUserId(phaseId, userId)
            } returns mutableListOf(step)

            val result = service.getOnboardingStepsForMe(authId, phaseId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingStepsForMe(authId, phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class CreateOnboardingStepForMe {
        @Test
        fun `creates step at valid position`() {
            val phase = makePhase()
            val step = makeStep()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { onboardingStepRepository.countByPhaseId(phase.id) } returns 0
            every {
                onboardingStepRepository.findByPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(phase.id, 0)
            } returns mutableListOf()
            every { onboardingStepRepository.save(any()) } returns step

            val result = service.createOnboardingStepForMe(authId, phaseId, makeCreateRequest())

            assertEquals(stepId, result.id)
        }

        @Test
        fun `throws 400 when position out of range`() {
            val phase = makePhase()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { onboardingStepRepository.countByPhaseId(phase.id) } returns 1

            assertThrows<ResponseStatusException> {
                service.createOnboardingStepForMe(authId, phaseId, makeCreateRequest(5))
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when phase not found for user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createOnboardingStepForMe(authId, phaseId, makeCreateRequest())
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingStepForMe {
        @Test
        fun `returns step for authenticated user`() {
            val step = makeStep()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)

            val result = service.getOnboardingStepForMe(authId, stepId)

            assertEquals(stepId, result.id)
        }

        @Test
        fun `throws 404 when step not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingStepForMe(authId, stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class UpdateOnboardingStepForMe {
        @Test
        fun `updates step fields`() {
            val step = makeStep(0, StepStatus.WAITING)
            val request = makeUpdateRequest(0)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)
            every { onboardingStepRepository.countByPhaseId(step.phase.id) } returns 1
            every {
                onboardingStepRepository.findByPhaseIdAndPositionBetween(any(), any(), any())
            } returns mutableListOf()

            val result = service.updateOnboardingStepForMe(authId, stepId, request)

            assertEquals("Updated Step", result.title)
            assertEquals(StepStatus.WAITING, result.status)
            assertNull(result.completedAt)
            assertNull(result.skip)
        }
    }

    @Nested
    inner class CompleteOnboardingStepForMe {
        @Test
        fun `completes waiting step`() {
            val step = makeStep(0, StepStatus.WAITING)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)

            val result = service.completeOnboardingStepForMe(authId, stepId)

            assertEquals(StepStatus.FINISHED, result.status)
            assertNotNull(result.completedAt)
            assertNull(result.skip)
        }

        @Test
        fun `removes pending skip when completing step`() {
            val step = makeStep(0, StepStatus.WAITING)
            step.skips += makeSkip(step, SkipStatus.PENDING)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)

            val result = service.completeOnboardingStepForMe(authId, stepId)

            assertEquals(0, step.skips.size)
            assertNull(result.skip)
        }

        @Test
        fun `throws 403 when step is not waiting`() {
            val step = makeStep(0, StepStatus.FINISHED)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)

            assertThrows<ResponseStatusException> {
                service.completeOnboardingStepForMe(authId, stepId)
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingStepForMe {
        @Test
        fun `deletes step and shifts subsequent steps`() {
            val step = makeStep(1)
            val laterStep = makeStep(2)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)
            every {
                onboardingStepRepository.findAllByPhaseIdAndPositionGreaterThan(step.phase.id, 1)
            } returns mutableListOf(laterStep)
            every { onboardingStepRepository.delete(step) } just runs

            service.deleteOnboardingStepForMe(authId, stepId)

            assertEquals(1, laterStep.position)
            verify(exactly = 1) { onboardingStepRepository.delete(step) }
        }

        @Test
        fun `throws 404 when step not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingStepForMe(authId, stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingStepsByPhaseId {
        @Test
        fun `returns all steps for a phase`() {
            every { onboardingStepRepository.findAllByPhaseId(phaseId) } returns mutableListOf(makeStep())

            val result = service.getOnboardingStepsByPhaseId(phaseId)

            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class GetOnboardingStepById {
        @Test
        fun `returns step by id`() {
            val step = makeStep()
            every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)

            val result = service.getOnboardingStepById(stepId)

            assertEquals(stepId, result.id)
        }

        @Test
        fun `throws 404 when step not found`() {
            every { onboardingStepRepository.findById(stepId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingStepById(stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingStepById {
        @Test
        fun `deletes step and shifts subsequent siblings`() {
            val step = makeStep(1)
            val laterStep = makeStep(2)

            every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
            every {
                onboardingStepRepository.findAllByPhaseIdAndPositionGreaterThan(step.phase.id, 1)
            } returns mutableListOf(laterStep)
            every { onboardingStepRepository.delete(step) } just runs

            service.deleteOnboardingStepById(stepId)

            assertEquals(1, laterStep.position)
            verify(exactly = 1) { onboardingStepRepository.delete(step) }
        }

        @Test
        fun `throws 404 when step not found`() {
            every { onboardingStepRepository.findById(stepId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingStepById(stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }
}

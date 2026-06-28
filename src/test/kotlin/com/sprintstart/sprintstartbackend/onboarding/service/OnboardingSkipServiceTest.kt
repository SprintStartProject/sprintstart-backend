package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSkip
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.UpdateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingSkipRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OnboardingSkipServiceTest {
    private val onboardingSkipRepository: OnboardingSkipRepository = mockk()
    private val onboardingStepRepository: OnboardingStepRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = OnboardingSkipService(onboardingSkipRepository, onboardingStepRepository, userApi)

    private val userId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val skipId = UUID.randomUUID()
    private val authId = "auth|skip-user"

    private fun makeStep(status: StepStatus = StepStatus.WAITING): OnboardingStep {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Phase", description = "Desc")

        return OnboardingStep(
            id = stepId,
            phase = phase,
            position = 0,
            title = "Step",
            description = "Desc",
            type = StepType.DOCUMENT,
            estimatedMinutes = 20,
            expectedOutcome = "Outcome",
            status = status,
        )
    }

    private fun makeSkip(
        step: OnboardingStep,
        status: SkipStatus = SkipStatus.PENDING,
        resolvedAt: Instant? = null,
    ): OnboardingSkip {
        val skip = OnboardingSkip(
            id = skipId,
            step = step,
            status = status,
            reason = "Need to skip",
            reviewComment = null,
            createdAt = Instant.now(),
            resolvedAt = resolvedAt,
        )
        step.skips += skip
        return skip
    }

    @Nested
    inner class GetAllSkipsForMe {
        @Test
        fun `returns skips for authenticated user`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingSkipRepository.findAllByStepPhasePathUserIdOrderByCreatedAtAsc(userId) } returns
                mutableListOf(skip)

            val result = service.getAllSkipsForMe(authId)

            assertEquals(1, result.size)
            assertEquals(skipId, result.first().id)
        }
    }

    @Nested
    inner class GetSkipByIdForMe {
        @Test
        fun `returns owned skip`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingSkipRepository.findByIdAndStepPhasePathUserId(skipId, userId) } returns Optional.of(skip)

            val result = service.getSkipByIdForMe(authId, skipId)

            assertEquals(skipId, result.id)
            assertEquals("Need to skip", result.reason)
        }
    }

    @Nested
    inner class CreateOnboardingSkipForMe {
        @Test
        fun `creates pending skip for waiting step`() {
            val step = makeStep()
            val request = CreateOnboardingSkipRequest(reason = "Need to skip")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)
            every { onboardingSkipRepository.save(any()) } answers { firstArg() }

            val result = service.createOnboardingSkipForMe(authId, stepId, request)

            assertEquals(SkipStatus.PENDING, result.status)
            assertEquals("Need to skip", result.reason)
            assertNull(result.reviewedAt)
        }

        @Test
        fun `throws 400 when step already has a pending skip`() {
            val step = makeStep()
            makeSkip(step)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)

            assertThrows<ResponseStatusException> {
                service.createOnboardingSkipForMe(authId, stepId, CreateOnboardingSkipRequest("Need to skip"))
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 400 when step is not waiting`() {
            val step = makeStep(StepStatus.FINISHED)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)

            assertThrows<ResponseStatusException> {
                service.createOnboardingSkipForMe(authId, stepId, CreateOnboardingSkipRequest("Need to skip"))
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    @Nested
    inner class UpdateOnboardingSkipForMe {
        @Test
        fun `updates reason for pending skip and returns step response`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingSkipRepository.findByIdAndStepPhasePathUserId(skipId, userId) } returns Optional.of(skip)

            val result = service.updateOnboardingSkipForMe(authId, skipId, UpdateOnboardingSkipRequest("Updated"))

            assertEquals("Updated", skip.reason)
            assertEquals(step.id, result.id)
            assertNotNull(result.skip)
            assertEquals("Updated", result.skip.reason)
        }

        @Test
        fun `throws 400 when skip is no longer pending`() {
            val step = makeStep()
            val skip = makeSkip(step, SkipStatus.ACCEPTED, Instant.now())
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingSkipRepository.findByIdAndStepPhasePathUserId(skipId, userId) } returns Optional.of(skip)

            assertThrows<ResponseStatusException> {
                service.updateOnboardingSkipForMe(authId, skipId, UpdateOnboardingSkipRequest("Updated"))
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    @Nested
    inner class ReviewSkip {
        @Test
        fun `accepting a skip marks step as skipped`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { onboardingSkipRepository.findById(skipId) } returns Optional.of(skip)

            val result = service.acceptSkipById(skipId, ReviewOnboardingSkipRequest("Approved"))

            assertEquals(SkipStatus.ACCEPTED, result.status)
            assertEquals(StepStatus.SKIPPED, step.status)
            assertNotNull(step.completedAt)
            assertEquals("Approved", result.reviewComment)
        }

        @Test
        fun `denying a skip returns step to waiting`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { onboardingSkipRepository.findById(skipId) } returns Optional.of(skip)

            val result = service.denySkipById(skipId, ReviewOnboardingSkipRequest("No"))

            assertEquals(SkipStatus.DENIED, result.status)
            assertEquals(StepStatus.WAITING, step.status)
            assertNull(step.completedAt)
            assertEquals("No", result.reviewComment)
        }

        @Test
        fun `accepting a non-pending skip throws 400`() {
            val step = makeStep()
            val skip = makeSkip(step, SkipStatus.DENIED, Instant.now())
            every { onboardingSkipRepository.findById(skipId) } returns Optional.of(skip)

            assertThrows<ResponseStatusException> {
                service.acceptSkipById(skipId, ReviewOnboardingSkipRequest("Approved"))
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteSkip {
        @Test
        fun `deleting pending skip removes it from the step collection`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { onboardingSkipRepository.findById(skipId) } returns Optional.of(skip)

            service.deleteSkipById(skipId)

            assertEquals(0, step.skips.size)
            assertEquals(StepStatus.WAITING, step.status)
        }

        @Test
        fun `deleting owned pending skip removes it from the step collection`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingSkipRepository.findByIdAndStepPhasePathUserId(skipId, userId) } returns Optional.of(skip)

            service.deleteSkipByIdForMe(authId, skipId)

            assertEquals(0, step.skips.size)
        }

        @Test
        fun `deleting accepted skip throws 400`() {
            val step = makeStep(StepStatus.SKIPPED)
            val skip = makeSkip(step, SkipStatus.ACCEPTED, Instant.now())
            every { onboardingSkipRepository.findById(skipId) } returns Optional.of(skip)

            assertThrows<ResponseStatusException> {
                service.deleteSkipById(skipId)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `deleting owned denied skip throws 400`() {
            val step = makeStep()
            val skip = makeSkip(step, SkipStatus.DENIED, Instant.now())
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingSkipRepository.findByIdAndStepPhasePathUserId(skipId, userId) } returns Optional.of(skip)

            assertThrows<ResponseStatusException> {
                service.deleteSkipByIdForMe(authId, skipId)
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    @Nested
    inner class AdminReads {
        @Test
        fun `returns skips by user id`() {
            val step = makeStep()
            val skip = makeSkip(step)
            every { userApi.exists(userId) } returns true
            every { onboardingSkipRepository.findAllByStepPhasePathUserIdOrderByCreatedAtAsc(userId) } returns
                mutableListOf(skip)

            val result = service.getAllSkipsByUserId(userId)

            assertEquals(1, result.size)
            assertEquals(skipId, result.first().id)
        }

        @Test
        fun `throws 404 when user does not exist`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> {
                service.getAllSkipsByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }
}

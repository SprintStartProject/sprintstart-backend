package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.request.feedback.CreateOnboardingFeedbackRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OnboardingFeedbackServiceTest {
    private val onboardingFeedbackRepository: OnboardingFeedbackRepository = mockk()
    private val onboardingStepRepository: OnboardingStepRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = OnboardingFeedbackService(onboardingFeedbackRepository, onboardingStepRepository, userApi)

    private val userId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val feedbackId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makeStep(): OnboardingStep {
        val path = OnboardingPath(userId = userId)
        val phase =
            OnboardingPhase(id = UUID.randomUUID(), path = path, position = 0, title = "Phase", description = "Desc")
        return OnboardingStep(
            id = stepId,
            phase = phase,
            position = 0,
            title = "Step",
            description = "Desc",
            type = com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Outcome",
            status = com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus.WAITING,
        )
    }

    private fun makeFeedback(step: OnboardingStep? = null): OnboardingFeedback =
        OnboardingFeedback(
            id = feedbackId,
            userId = userId,
            step = step,
            helpful = true,
            message = "Great step!",
        )

    private fun makeCreateRequest(stepId: UUID? = null) = CreateOnboardingFeedbackRequest(
        stepId = stepId,
        helpful = true,
        message = "Great step!",
    )

    @Nested
    inner class GetAllFeedbackForMe {
        @Test
        fun `returns feedback for authenticated user`() {
            val feedback = makeFeedback()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingFeedbackRepository.findAllByUserIdOrderByCreatedAtAsc(userId) } returns
                mutableListOf(feedback)

            val result = service.getAllFeedbackForMe(authId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getAllFeedbackForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetFeedbackByStepIdForMe {
        @Test
        fun `returns feedback for step owned by user`() {
            val step = makeStep()
            val feedback = makeFeedback(step)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)
            every { onboardingFeedbackRepository.findAllByStepIdAndUserIdOrderByCreatedAtAsc(stepId, userId) } returns
                mutableListOf(feedback)

            val result = service.getFeedbackByStepIdForMe(authId, stepId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getFeedbackByStepIdForMe(authId, stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when step not owned by user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getFeedbackByStepIdForMe(authId, stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class CreateFeedbackForMe {
        @Test
        fun `creates feedback with step`() {
            val step = makeStep()
            val request = makeCreateRequest(stepId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)

            val result = service.createFeedbackForMe(authId, request)

            assertNotNull(result)
            assertEquals(1, step.feedback.size)
        }

        @Test
        fun `creates feedback without step`() {
            val request = makeCreateRequest(null)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingFeedbackRepository.save(any()) } returns makeFeedback()

            val result = service.createFeedbackForMe(authId, request)

            assertNotNull(result)
            verify(exactly = 1) { onboardingFeedbackRepository.save(any()) }
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createFeedbackForMe(authId, makeCreateRequest())
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when step not owned by user`() {
            val request = makeCreateRequest(stepId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createFeedbackForMe(authId, request)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetAllFeedback {
        @Test
        fun `returns all feedback for admins`() {
            every { onboardingFeedbackRepository.findAllByOrderByCreatedAtAsc() } returns mutableListOf(makeFeedback())

            val result = service.getAllFeedback()

            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class GetAllFeedbackByUserId {
        @Test
        fun `returns feedback for given user`() {
            every { userApi.exists(userId) } returns true
            every { onboardingFeedbackRepository.findAllByUserIdOrderByCreatedAtAsc(userId) } returns
                mutableListOf(makeFeedback())

            val result = service.getAllFeedbackByUserId(userId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> {
                service.getAllFeedbackByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetAllFeedbackByStepId {
        @Test
        fun `returns feedback for given step`() {
            every { onboardingStepRepository.existsById(stepId) } returns true
            every { onboardingFeedbackRepository.findAllByStepIdOrderByCreatedAtAsc(stepId) } returns
                mutableListOf(makeFeedback())

            val result = service.getAllFeedbackByStepId(stepId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when step not found`() {
            every { onboardingStepRepository.existsById(stepId) } returns false

            assertThrows<ResponseStatusException> {
                service.getAllFeedbackByStepId(stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class MarkFeedbackAsRead {
        @Test
        fun `marks feedback as read`() {
            val feedback = makeFeedback().also { it.read = false }
            every { onboardingFeedbackRepository.findById(feedbackId) } returns Optional.of(feedback)

            val result = service.markFeedbackAsRead(feedbackId)

            assertTrue(feedback.read)
            assertTrue(result.read)
        }

        @Test
        fun `throws 404 when feedback not found`() {
            every { onboardingFeedbackRepository.findById(feedbackId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.markFeedbackAsRead(feedbackId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }
}

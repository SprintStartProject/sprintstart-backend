package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.request.feedback.CreateOnboardingFeedbackRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.ModulePageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
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
    private val modulePageRepository: ModulePageRepository = mockk()
    private val userApi: UserApi = mockk()
    private val contentQualityService: ContentQualityService = mockk(relaxed = true)
    private val service = OnboardingFeedbackService(
        onboardingFeedbackRepository,
        modulePageRepository,
        userApi,
        contentQualityService,
    )

    private val userId = UUID.randomUUID()
    private val pageId = UUID.randomUUID()
    private val feedbackId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makePage(): ModulePage {
        val module = CompetencyModule(
            competencyKey = "deploy-runbook",
            projectId = UUID.randomUUID(),
            version = 1,
            status = ModuleStatus.ACTIVE,
            title = "Deploying",
        )
        return ModulePage(
            id = pageId,
            module = module,
            kind = ModulePageKind.LESSON,
            title = "How it works",
            position = 0,
        )
    }

    private fun makeFeedback(page: ModulePage? = null): OnboardingFeedback =
        OnboardingFeedback(
            id = feedbackId,
            userId = userId,
            page = page,
            helpful = true,
            message = "Great page!",
        )

    private fun makeCreateRequest(pageId: UUID? = null, helpful: Boolean? = true) = CreateOnboardingFeedbackRequest(
        pageId = pageId,
        helpful = helpful,
        message = "Great page!",
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
        fun `returns this user's feedback on a page`() {
            val page = makePage()
            val feedback = makeFeedback(page)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { modulePageRepository.findById(pageId) } returns Optional.of(page)
            every { onboardingFeedbackRepository.findAllByPageIdAndUserIdOrderByCreatedAtAsc(pageId, userId) } returns
                mutableListOf(feedback)

            val result = service.getFeedbackByPageIdForMe(authId, pageId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getFeedbackByPageIdForMe(authId, pageId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when the page does not exist`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { modulePageRepository.findById(pageId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getFeedbackByPageIdForMe(authId, pageId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class CreateFeedbackForMe {
        @Test
        fun `creates feedback about a page`() {
            val page = makePage()
            val request = makeCreateRequest(pageId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { modulePageRepository.findById(pageId) } returns Optional.of(page)
            every { onboardingFeedbackRepository.save(any()) } answers { firstArg() }

            val result = service.createFeedbackForMe(authId, request)

            assertNotNull(result)
            verify(exactly = 1) { onboardingFeedbackRepository.save(any()) }
            verify(exactly = 0) { contentQualityService.checkAndTriggerRegeneration(any()) }
        }

        @Test
        fun `triggers the content-quality check when feedback on a page is unhelpful`() {
            val page = makePage()
            every { onboardingFeedbackRepository.save(any()) } answers { firstArg() }
            val request = makeCreateRequest(pageId, helpful = false)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { modulePageRepository.findById(pageId) } returns Optional.of(page)

            service.createFeedbackForMe(authId, request)

            verify(exactly = 1) { contentQualityService.checkAndTriggerRegeneration(page) }
        }

        @Test
        fun `does not trigger the content-quality check when feedback names no page`() {
            val request = makeCreateRequest(null, helpful = false)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingFeedbackRepository.save(any()) } returns makeFeedback()

            service.createFeedbackForMe(authId, request)

            verify(exactly = 0) { contentQualityService.checkAndTriggerRegeneration(any()) }
        }

        @Test
        fun `creates feedback with no page`() {
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
        fun `throws 404 when the page does not exist`() {
            val request = makeCreateRequest(pageId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { modulePageRepository.findById(pageId) } returns Optional.empty()

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
        fun `returns everybody's feedback on a page`() {
            every { modulePageRepository.existsById(pageId) } returns true
            every { onboardingFeedbackRepository.findAllByPageIdOrderByCreatedAtAsc(pageId) } returns
                mutableListOf(makeFeedback())

            val result = service.getAllFeedbackByPageId(pageId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when no page has that id`() {
            every { modulePageRepository.existsById(pageId) } returns false

            assertThrows<ResponseStatusException> {
                service.getAllFeedbackByPageId(pageId)
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

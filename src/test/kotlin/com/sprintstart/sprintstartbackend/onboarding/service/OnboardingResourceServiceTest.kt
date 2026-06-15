package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingResourceRepository
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

class OnboardingResourceServiceTest {
    private val onboardingResourceRepository: OnboardingResourceRepository = mockk()
    private val onboardingStepRepository: OnboardingStepRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = OnboardingResourceService(onboardingResourceRepository, onboardingStepRepository, userApi)

    private val userId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val resourceId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makeStep(): OnboardingStep {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Phase", description = "Desc")
        return OnboardingStep(
            id = stepId,
            phase = phase,
            position = 0,
            title = "Step",
            description = "Desc",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Outcome",
            status = StepStatus.WAITING,
        )
    }

    private fun makeResource(): OnboardingResource =
        OnboardingResource(
            id = resourceId,
            step = makeStep(),
            title = "Resource",
            description = "Desc",
            url = "https://example.com",
        )

    @Nested
    inner class GetOnboardingResourcesForMe {
        @Test
        fun `returns resources for authenticated user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingResourceRepository.findByStepIdAndStepPhasePathUserId(stepId, userId)
            } returns mutableListOf(makeResource())

            val result = service.getOnboardingResourcesForMe(authId, stepId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingResourcesForMe(authId, stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class CreateOnboardingResourceForMe {
        @Test
        fun `creates resource`() {
            val step = makeStep()
            val resource = makeResource()
            val request =
                CreateOnboardingResourceRequest(title = "Resource", description = "Desc", url = "https://example.com")

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)
            every { onboardingResourceRepository.save(any()) } returns resource

            val result = service.createOnboardingResourceForMe(authId, stepId, request)

            assertEquals(resourceId, result.id)
        }

        @Test
        fun `throws 404 when step not found for user`() {
            val request =
                CreateOnboardingResourceRequest(title = "Resource", description = "Desc", url = "https://example.com")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createOnboardingResourceForMe(authId, stepId, request)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingResourceForMe {
        @Test
        fun `returns resource for authenticated user`() {
            val resource = makeResource()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingResourceRepository.findByIdAndStepPhasePathUserId(resourceId, userId)
            } returns Optional.of(resource)

            val result = service.getOnboardingResourceForMe(authId, resourceId)

            assertEquals(resourceId, result.id)
        }

        @Test
        fun `throws 404 when resource not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingResourceRepository.findByIdAndStepPhasePathUserId(resourceId, userId)
            } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingResourceForMe(authId, resourceId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class UpdateOnboardingResourceForMe {
        @Test
        fun `updates and saves resource`() {
            val resource = makeResource()
            val request =
                UpdateOnboardingResourceRequest(
                    title = "Updated",
                    description = "Updated Desc",
                    url = "https://new.example.com",
                )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingResourceRepository.findByIdAndStepPhasePathUserId(resourceId, userId)
            } returns Optional.of(resource)
            every { onboardingResourceRepository.save(resource) } returns resource

            val result = service.updateOnboardingResourceForMe(authId, resourceId, request)

            assertEquals("Updated", result.title)
            assertEquals("https://new.example.com", result.url)
        }

        @Test
        fun `throws 404 when resource not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingResourceRepository.findByIdAndStepPhasePathUserId(resourceId, userId)
            } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.updateOnboardingResourceForMe(
                    authId,
                    resourceId,
                    UpdateOnboardingResourceRequest("t", "d", "u"),
                )
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingResourceForMe {
        @Test
        fun `deletes resource for authenticated user`() {
            val resource = makeResource()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingResourceRepository.findByIdAndStepPhasePathUserId(resourceId, userId)
            } returns Optional.of(resource)
            every { onboardingResourceRepository.delete(resource) } just runs

            service.deleteOnboardingResourceForMe(authId, resourceId)

            verify(exactly = 1) { onboardingResourceRepository.delete(resource) }
        }

        @Test
        fun `throws 404 when resource not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingResourceRepository.findByIdAndStepPhasePathUserId(resourceId, userId)
            } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingResourceForMe(authId, resourceId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class CreateOnboardingResourceForStepId {
        @Test
        fun `creates resource for step by stepId`() {
            val step = makeStep()
            val resource = makeResource()
            val request =
                CreateOnboardingResourceRequest(title = "Resource", description = "Desc", url = "https://example.com")

            every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
            every { onboardingResourceRepository.save(any()) } returns resource

            val result = service.createOnboardingResourceForStepId(stepId, request)

            assertEquals(resourceId, result.id)
        }

        @Test
        fun `throws 404 when step not found`() {
            val request =
                CreateOnboardingResourceRequest(title = "Resource", description = "Desc", url = "https://example.com")
            every { onboardingStepRepository.findById(stepId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createOnboardingResourceForStepId(stepId, request)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingResourcesByStepId {
        @Test
        fun `returns all resources for step`() {
            every { onboardingResourceRepository.findAllByStepId(stepId) } returns mutableListOf(makeResource())

            val result = service.getOnboardingResourcesByStepId(stepId)

            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class GetOnboardingResourceById {
        @Test
        fun `returns resource by id`() {
            val resource = makeResource()
            every { onboardingResourceRepository.findById(resourceId) } returns Optional.of(resource)

            val result = service.getOnboardingResourceById(resourceId)

            assertEquals(resourceId, result.id)
        }

        @Test
        fun `throws 404 when resource not found`() {
            every { onboardingResourceRepository.findById(resourceId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingResourceById(resourceId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class UpdateOnboardingResourceById {
        @Test
        fun `updates and saves resource`() {
            val resource = makeResource()
            val request =
                UpdateOnboardingResourceRequest(
                    title = "Updated",
                    description = "Updated Desc",
                    url = "https://new.example.com",
                )

            every { onboardingResourceRepository.findById(resourceId) } returns Optional.of(resource)
            every { onboardingResourceRepository.save(resource) } returns resource

            val result = service.updateOnboardingResourceById(resourceId, request)

            assertEquals("Updated", result.title)
        }

        @Test
        fun `throws 404 when resource not found`() {
            every { onboardingResourceRepository.findById(resourceId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.updateOnboardingResourceById(resourceId, UpdateOnboardingResourceRequest("t", "d", "u"))
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingResourceById {
        @Test
        fun `deletes resource by id`() {
            val resource = makeResource()
            every { onboardingResourceRepository.findById(resourceId) } returns Optional.of(resource)
            every { onboardingResourceRepository.delete(resource) } just runs

            service.deleteOnboardingResourceById(resourceId)

            verify(exactly = 1) { onboardingResourceRepository.delete(resource) }
        }

        @Test
        fun `throws 404 when resource not found`() {
            every { onboardingResourceRepository.findById(resourceId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingResourceById(resourceId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }
}

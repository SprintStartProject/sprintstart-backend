package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingAiPathEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Blueprint
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.repository.BlueprintRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class OnboardingPersonalizationServiceTest {
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val blueprintRepository: BlueprintRepository = mockk()
    private val blueprintService: BlueprintService = mockk()
    private val userApi: UserApi = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val service = OnboardingPersonalizationService(
        onboardingAiClient,
        onboardingPathRepository,
        blueprintRepository,
        blueprintService,
        userApi,
        transactionManager,
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|test-user"
    private val profile = UserOnboardingProfile(
        id = userId,
        workingArea = WorkingArea.BACKEND_DEV,
        experience = "junior",
    )

    @Nested
    inner class Personalize {
        @Test
        fun `throws 404 when user profile not found`() {
            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.empty()

            val ex = assertThrows<ResponseStatusException> { service.personalize(authId) }

            assertEquals(404, ex.statusCode.value())
        }

        @Test
        fun `calls ensureScopesExist with global and area derived from working area`() = runTest {
            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
            coEvery { blueprintService.ensureScopesExist(listOf("global", "area:backend")) } just runs
            every { blueprintRepository.findByScopeAndStatus(any(), BlueprintStatus.ACTIVE) } returns null
            every { onboardingPathRepository.deleteByUserId(userId) } just runs
            every { onboardingAiClient.generatePath(any(), any(), any()) } returns flowOf(
                OnboardingAiPathEvent(type = "done"),
            )

            service.personalize(authId).toList()

            coVerify(exactly = 1) { blueprintService.ensureScopesExist(listOf("global", "area:backend")) }
        }

        @Test
        fun `loads active blueprints after ensuring they exist`() = runTest {
            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
            coEvery { blueprintService.ensureScopesExist(any()) } just runs
            every { blueprintRepository.findByScopeAndStatus(any(), BlueprintStatus.ACTIVE) } returns null
            every { onboardingPathRepository.deleteByUserId(userId) } just runs
            every { onboardingAiClient.generatePath(any(), any(), any()) } returns flowOf(
                OnboardingAiPathEvent(type = "done"),
            )

            service.personalize(authId).toList()

            verify(exactly = 1) {
                blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE)
            }
            verify(exactly = 1) {
                blueprintRepository.findByScopeAndStatus("area:backend", BlueprintStatus.ACTIVE)
            }
        }

        @Test
        fun `passes active blueprints to AI client`() = runTest {
            val bp = Blueprint(scope = "global", version = "1", status = BlueprintStatus.ACTIVE)
            val blueprintSlot = slot<List<BlueprintSchema>>()

            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
            coEvery { blueprintService.ensureScopesExist(any()) } just runs
            every { blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE) } returns bp
            every {
                blueprintRepository.findByScopeAndStatus("area:backend", BlueprintStatus.ACTIVE)
            } returns null
            every { onboardingPathRepository.deleteByUserId(userId) } just runs
            every {
                onboardingAiClient.generatePath(any(), any(), capture(blueprintSlot))
            } returns flowOf(OnboardingAiPathEvent(type = "done"))

            service.personalize(authId).toList()

            assertEquals(1, blueprintSlot.captured.size)
            assertEquals("global", blueprintSlot.captured[0].scope)
        }

        @Test
        fun `maps stage events from AI client`() = runTest {
            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
            coEvery { blueprintService.ensureScopesExist(any()) } just runs
            every { blueprintRepository.findByScopeAndStatus(any(), BlueprintStatus.ACTIVE) } returns null
            every { onboardingPathRepository.deleteByUserId(userId) } just runs
            every { onboardingAiClient.generatePath(any(), any(), any()) } returns flowOf(
                OnboardingAiPathEvent(type = "stage", name = "retrieve", detail = "Retrieving documents"),
                OnboardingAiPathEvent(type = "done"),
            )

            val events = service.personalize(authId).toList()

            assertEquals(2, events.size)
            assertEquals("stage", events[0].type)
            assertEquals("retrieve", events[0].name)
            assertEquals("Retrieving documents", events[0].detail)
            assertEquals("done", events[1].type)
        }

        @Test
        fun `maps error events from AI client`() = runTest {
            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
            coEvery { blueprintService.ensureScopesExist(any()) } just runs
            every { blueprintRepository.findByScopeAndStatus(any(), BlueprintStatus.ACTIVE) } returns null
            every { onboardingPathRepository.deleteByUserId(userId) } just runs
            every { onboardingAiClient.generatePath(any(), any(), any()) } returns flowOf(
                OnboardingAiPathEvent(type = "error", message = "LLM unavailable"),
            )

            val events = service.personalize(authId).toList()

            assertEquals(1, events.size)
            assertEquals("error", events[0].type)
            assertEquals("LLM unavailable", events[0].message)
        }

        @Test
        fun `maps path event and persists the generated path`() = runTest {
            val path = OnboardingPath(workingArea = "backend", experience = "junior")
            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
            coEvery { blueprintService.ensureScopesExist(any()) } just runs
            every { blueprintRepository.findByScopeAndStatus(any(), BlueprintStatus.ACTIVE) } returns null
            every { onboardingPathRepository.deleteByUserId(userId) } just runs
            every { onboardingAiClient.generatePath(any(), any(), any()) } returns flowOf(
                OnboardingAiPathEvent(type = "path", path = path),
            )
            every { onboardingPathRepository.save(any()) } answers { firstArg() }

            val events = service.personalize(authId).toList()

            assertEquals(1, events.size)
            assertEquals("path", events[0].type)
            assertEquals(userId, events[0].path?.userId)
            verify(exactly = 1) { onboardingPathRepository.save(any()) }
        }

        @Test
        fun `deletes existing path before generating new one`() = runTest {
            every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
            coEvery { blueprintService.ensureScopesExist(any()) } just runs
            every { blueprintRepository.findByScopeAndStatus(any(), BlueprintStatus.ACTIVE) } returns null
            every { onboardingPathRepository.deleteByUserId(userId) } just runs
            every { onboardingAiClient.generatePath(any(), any(), any()) } returns flowOf(
                OnboardingAiPathEvent(type = "done"),
            )

            service.personalize(authId).toList()

            verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
        }
    }
}

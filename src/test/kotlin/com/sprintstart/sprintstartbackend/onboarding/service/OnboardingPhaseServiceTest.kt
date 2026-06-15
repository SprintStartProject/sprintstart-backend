package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
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

class OnboardingPhaseServiceTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val onboardingPhaseRepository: OnboardingPhaseRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = OnboardingPhaseService(onboardingPathRepository, onboardingPhaseRepository, userApi)

    private val userId = UUID.randomUUID()
    private val pathId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makePath() = OnboardingPath(id = pathId, userId = userId)

    private fun makePhase(position: Int = 0, path: OnboardingPath = makePath()) =
        OnboardingPhase(id = phaseId, path = path, position = position, title = "Phase", description = "Desc")

    @Nested
    inner class GetOnboardingPhasesForMe {
        @Test
        fun `returns phases for authenticated user`() {
            val path = makePath()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
            every { onboardingPhaseRepository.findAllByPathId(path.id) } returns mutableListOf(makePhase(0, path))

            val result = service.getOnboardingPhasesForMe(authId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPhasesForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when path not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPhasesForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingPhaseForMe {
        @Test
        fun `returns phase for authenticated user`() {
            val phase = makePhase()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)

            val result = service.getOnboardingPhaseForMe(authId, phaseId)

            assertEquals(phaseId, result.id)
        }

        @Test
        fun `throws 404 when phase not found for user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPhaseForMe(authId, phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class CreateOnboardingPhaseForMe {
        @Test
        fun `creates phase at valid position`() {
            val path = makePath()
            val request = CreateOnboardingPhaseRequest(position = 0, title = "New Phase", description = "Desc")
            val phase = makePhase(0, path)

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
            every { onboardingPhaseRepository.countByPathId(path.id) } returns 0
            every {
                onboardingPhaseRepository.findByPathIdAndPositionGreaterThanEqualOrderByPositionDesc(path.id, 0)
            } returns mutableListOf()
            every { onboardingPhaseRepository.save(any()) } returns phase

            val result = service.createOnboardingPhaseForMe(authId, request)

            assertEquals(phaseId, result.id)
        }

        @Test
        fun `throws 400 when position is out of range`() {
            val path = makePath()
            val request = CreateOnboardingPhaseRequest(position = 5, title = "Phase", description = "Desc")

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
            every { onboardingPhaseRepository.countByPathId(path.id) } returns 2

            assertThrows<ResponseStatusException> {
                service.createOnboardingPhaseForMe(authId, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    @Nested
    inner class UpdateOnboardingPhaseForMe {
        @Test
        fun `updates phase fields`() {
            val path = makePath()
            val phase = makePhase(0, path)
            val request = UpdateOnboardingPhaseRequest(position = 0, title = "Updated", description = "Updated Desc")

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every { onboardingPhaseRepository.countByPathId(path.id) } returns 1
            every { onboardingPhaseRepository.findByPathIdAndPositionBetween(any(), any(), any()) } returns
                mutableListOf()

            val result = service.updateOnboardingPhaseForMe(authId, phaseId, request)

            assertEquals("Updated", result.title)
            assertEquals("Updated Desc", result.description)
        }

        @Test
        fun `throws 404 when phase not found for user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.updateOnboardingPhaseForMe(authId, phaseId, UpdateOnboardingPhaseRequest(0, "t", "d"))
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingPhaseForMe {
        @Test
        fun `deletes phase and shifts subsequent phases`() {
            val path = makePath()
            val phase = makePhase(1, path)
            val laterPhase = makePhase(2, path)

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.of(phase)
            every {
                onboardingPhaseRepository.findAllByPathIdAndPositionGreaterThan(path.id, 1)
            } returns mutableListOf(laterPhase)
            every { onboardingPhaseRepository.saveAll(any<List<OnboardingPhase>>()) } returns mutableListOf(laterPhase)
            every { onboardingPhaseRepository.delete(phase) } just runs

            service.deleteOnboardingPhaseForMe(authId, phaseId)

            assertEquals(1, laterPhase.position)
            verify(exactly = 1) { onboardingPhaseRepository.delete(phase) }
        }

        @Test
        fun `throws 404 when phase not found for user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPhaseRepository.findByIdAndPathUserId(phaseId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingPhaseForMe(authId, phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingPhasesForUser {
        @Test
        fun `returns all phases for given userId`() {
            val path = makePath()
            every { onboardingPhaseRepository.findAllByPathUserId(userId) } returns mutableListOf(makePhase(0, path))

            val result = service.getOnboardingPhasesForUser(userId)

            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class CreateOnboardingPhaseForUserId {
        @Test
        fun `creates phase for user by userId`() {
            val path = makePath()
            val request = CreateOnboardingPhaseRequest(position = 0, title = "Phase", description = "Desc")
            val phase = makePhase(0, path)

            every { onboardingPathRepository.findByUserId(userId) } returns Optional.of(path)
            every { onboardingPhaseRepository.countByPathId(path.id) } returns 0
            every {
                onboardingPhaseRepository.findByPathIdAndPositionGreaterThanEqualOrderByPositionDesc(path.id, 0)
            } returns mutableListOf()
            every { onboardingPhaseRepository.save(any()) } returns phase

            val result = service.createOnboardingPhaseForUserId(userId, request)

            assertEquals(phaseId, result.id)
        }

        @Test
        fun `throws 404 when path not found for userId`() {
            val request = CreateOnboardingPhaseRequest(position = 0, title = "Phase", description = "Desc")
            every { onboardingPathRepository.findByUserId(userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createOnboardingPhaseForUserId(userId, request)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingPhaseById {
        @Test
        fun `returns phase by id`() {
            val phase = makePhase()
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)

            val result = service.getOnboardingPhaseById(phaseId)

            assertEquals(phaseId, result.id)
        }

        @Test
        fun `throws 404 when phase not found`() {
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPhaseById(phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingPhaseById {
        @Test
        fun `deletes phase and shifts subsequent siblings`() {
            val path = makePath()
            val phase = makePhase(1, path)
            val laterPhase = makePhase(2, path)

            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)
            every {
                onboardingPhaseRepository.findAllByPathIdAndPositionGreaterThan(path.id, 1)
            } returns mutableListOf(laterPhase)
            every { onboardingPhaseRepository.saveAll(any<List<OnboardingPhase>>()) } returns mutableListOf(laterPhase)
            every { onboardingPhaseRepository.delete(phase) } just runs

            service.deleteOnboardingPhaseById(phaseId)

            assertEquals(1, laterPhase.position)
            verify(exactly = 1) { onboardingPhaseRepository.delete(phase) }
        }

        @Test
        fun `throws 404 when phase not found`() {
            every { onboardingPhaseRepository.findById(phaseId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingPhaseById(phaseId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }
}

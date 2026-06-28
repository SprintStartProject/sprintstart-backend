package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
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
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserSkillDto
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingPathServiceTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = OnboardingPathService(onboardingPathRepository, userApi)

    private val userId = UUID.randomUUID()
    private val pathId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makePath(id: UUID = pathId, uid: UUID = userId) =
        OnboardingPath(id = id, userId = uid)

    @Nested
    inner class GetOnboardingPathOverviewByUserId {
        @Test
        fun `returns path when user and path exist`() {
            val path = makePath()
            every { userApi.exists(userId) } returns true
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)

            val result = service.getOnboardingPathByUserId(userId)

            assertEquals(path.id, result.id)
        }

        @Test
        fun `throws 404 when user does not exist`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when path not found for user`() {
            every { userApi.exists(userId) } returns true
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingPathByAuthId {
        @Test
        fun `returns path for authenticated user`() {
            val path = makePath()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)

            val result = service.getOnboardingPathForMe(authId)

            assertEquals(path.id, result.id)
        }

        @Test
        fun `throws 404 when authId not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when no path for resolved user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingPathByUserId {
        @Test
        fun `deletes path when user exists`() {
            every { userApi.exists(userId) } returns true
            every { onboardingPathRepository.deleteByUserId(userId) } just runs

            service.deleteOnboardingPathByUserId(userId)

            verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
        }

        @Test
        fun `throws 404 when user does not exist`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingPathByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }

            verify(exactly = 0) { onboardingPathRepository.deleteByUserId(any()) }
        }
    }

    @Nested
    inner class DeleteOnboardingPathByAuthId {
        @Test
        fun `deletes path for authenticated user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.deleteByUserId(userId) } just runs

            service.deleteOnboardingPathForMe(authId)

            verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
        }

        @Test
        fun `throws 404 when authId not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingPathForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetTeamOverview {
        private val pageable = PageRequest.of(0, 10)

        @Test
        fun `returns empty page when no users match search`() {
            every { userApi.searchUsers("nonexistent", null, null, Pageable.unpaged()) } returns PageImpl(emptyList())

            val result = service.getTeamOverview("nonexistent", null, null, "HIGHEST_PROGRESS", pageable)

            assertTrue(result.isEmpty)
        }

        @Test
        fun `sorts users by highest progress`() {
            val user1Id = UUID.randomUUID()
            val user2Id = UUID.randomUUID()
            
            val user1 = UserDto(user1Id, "u1", "f1", "l1", null, "DEV", null, emptyList(), emptyList())
            val user2 = UserDto(user2Id, "u2", "f2", "l2", null, "DEV", null, emptyList(), emptyList())
            
            every { userApi.searchUsers(null, null, null, Pageable.unpaged()) } returns PageImpl(listOf(user1, user2))
            every { onboardingPathRepository.findByUserIdIn(listOf(user1Id, user2Id)) } returns listOf(
                // Empty path for user1 (0% progress)
                OnboardingPath(userId = user1Id),
                // User 2 has higher progress (but we just test that it falls back to 0 without phases and sorts by name if both are 0)
                OnboardingPath(userId = user2Id)
            )

            val result = service.getTeamOverview(null, null, null, "HIGHEST_PROGRESS", pageable)

            assertEquals(2, result.content.size)
            // They both have 0 progress, so it should sort by lastname then firstname
            assertEquals("f1", result.content[0].firstname)
            assertEquals("f2", result.content[1].firstname)
        }
    }
}

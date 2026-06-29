package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.UserApiService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class UserApiServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userApi: UserApi = UserApiService(userRepository)

    @Test
    fun `exists should return true when user exists`() {
        // given
        val userId = UUID.randomUUID()

        every {
            userRepository.existsById(userId)
        } returns true

        // when
        val result = userApi.exists(userId)

        // then
        verify(exactly = 1) {
            userRepository.existsById(userId)
        }

        assertThat(result).isTrue()
    }

    @Test
    fun `exists should return false when user does not exists`() {
        // given
        val userId = UUID.randomUUID()

        every {
            userRepository.existsById(userId)
        } returns false

        // when
        val result = userApi.exists(userId)

        // then
        verify(exactly = 1) {
            userRepository.existsById(userId)
        }

        assertThat(result).isFalse()
    }

    @Test
    fun `getOnboardingProfileByAuthId returns profile when user exists`() {
        val userId = UUID.randomUUID()
        val authId = "auth|test-user"
        val user = User(
            id = userId,
            authId = authId,
            username = "testuser",
            email = null,
            firstname = "Test",
            lastname = "User",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every { userRepository.findByAuthId(authId) } returns Optional.of(user)

        val result = userApi.getOnboardingProfileByAuthId(authId)

        assertThat(result).isPresent
        assertThat(result.get().id).isEqualTo(userId)
        assertThat(result.get().workingArea).isEqualTo(WorkingArea.BACKEND_DEV)
    }

    @Test
    fun `getOnboardingProfileByAuthId returns empty when user not found`() {
        val authId = "auth|unknown"

        every { userRepository.findByAuthId(authId) } returns Optional.empty()

        val result = userApi.getOnboardingProfileByAuthId(authId)

        assertThat(result).isEmpty
    }
}

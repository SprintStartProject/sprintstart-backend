package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.UserApi
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
    fun `getUserIdByAuthId should return user id when auth id exists`() {
        // given
        val userId = UUID.randomUUID()
        every {
            userRepository.findIdByAuthId("auth-1")
        } returns Optional.of(userId)

        // when
        val result = userApi.getUserIdByAuthId("auth-1")

        // then
        verify(exactly = 1) {
            userRepository.findIdByAuthId("auth-1")
        }
        assertThat(result).contains(userId)
    }

    @Test
    fun `getUserIdByAuthId should return empty when auth id does not exist`() {
        // given
        every {
            userRepository.findIdByAuthId("missing-auth")
        } returns Optional.empty()

        // when
        val result = userApi.getUserIdByAuthId("missing-auth")

        // then
        verify(exactly = 1) {
            userRepository.findIdByAuthId("missing-auth")
        }
        assertThat(result).isEmpty()
    }
}

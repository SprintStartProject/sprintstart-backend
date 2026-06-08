package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.UserApiService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
    fun `existsByAuthId should return true when user exists`() {
        val authId = "keycloak-id-1"

        every {
            userRepository.existsByAuthId(authId)
        } returns true

        val result = userApi.existsByAuthId(authId)

        verify(exactly = 1) {
            userRepository.existsByAuthId(authId)
        }

        assertThat(result).isTrue()
    }

    @Test
    fun `existsByAuthId should return false when user does not exist`() {
        val authId = "keycloak-id-2"

        every {
            userRepository.existsByAuthId(authId)
        } returns false

        val result = userApi.existsByAuthId(authId)

        verify(exactly = 1) {
            userRepository.existsByAuthId(authId)
        }

        assertThat(result).isFalse()
    }
}

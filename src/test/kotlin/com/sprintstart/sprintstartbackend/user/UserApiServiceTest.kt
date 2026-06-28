package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.UserApiService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
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
    fun `searchUsers should return page of users`() {
        val user = com.sprintstart.sprintstartbackend.user.model.entity.User(
            id = UUID.randomUUID(), 
            authId = "auth1",
            username = "alice",
            firstname = "Alice",
            lastname = "Test",
            workingArea = com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea.BACKEND_DEV,
            email = "alice@test.com"
        )
        val page = PageImpl(listOf(user))
        
        // Use any() for Specification since it's an inline lambda creation
        every { userRepository.findAll(any<Specification<com.sprintstart.sprintstartbackend.user.model.entity.User>>(), any<Pageable>()) } returns page

        val result = userApi.searchUsers("search", null, null, Pageable.unpaged())

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].id).isEqualTo(user.id)
    }

    @Test
    fun `getUsersByIds should return users`() {
        val userId = UUID.randomUUID()
        val user = com.sprintstart.sprintstartbackend.user.model.entity.User(
            id = userId, 
            authId = "auth1",
            username = "alice",
            firstname = "Alice",
            lastname = "Test",
            workingArea = com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea.BACKEND_DEV,
            email = "alice@test.com"
        )
        
        every { userRepository.findAllById(listOf(userId)) } returns listOf(user)

        val result = userApi.getUsersByIds(listOf(userId))

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(userId)
    }
}

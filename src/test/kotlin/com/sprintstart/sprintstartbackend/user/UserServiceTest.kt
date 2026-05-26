package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userService: UserService = UserService(userRepository)

    @Test
    fun `createUser should save and return created user`() {
        // give
        val request = CreateUserRequest(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val savedUserSlot = slot<User>()

        every {
            userRepository.save(capture(savedUserSlot))
        } answers {
            savedUserSlot.captured
        }

        // when
        val response = userService.createUser(request)

        // then
        verify(exactly = 1) {
            userRepository.save(any())
        }

        val savedUser = savedUserSlot.captured

        assertThat(savedUser.id).isNotNull()
        assertThat(savedUser.username).isEqualTo("max_backend")
        assertThat(savedUser.firstname).isEqualTo("Max")
        assertThat(savedUser.lastname).isEqualTo("Backend")
        assertThat(savedUser.primaryRole).isEqualTo(Role.NO_ROLE)
        assertThat(savedUser.secondaryRole).isEqualTo(Role.NO_ROLE)
        assertThat(savedUser.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)

        // Testing the mapping entity -> dto
        assertThat(response.id).isEqualTo(savedUser.id)
        assertThat(response.username).isEqualTo(savedUser.username)
        assertThat(response.firstname).isEqualTo(savedUser.firstname)
        assertThat(response.lastname).isEqualTo(savedUser.lastname)
        assertThat(response.primaryRole).isEqualTo(savedUser.primaryRole)
        assertThat(response.secondaryRole).isEqualTo(savedUser.secondaryRole)
        assertThat(response.workingArea).isEqualTo(savedUser.workingArea)
    }

    // Todo: getAllUsers should return mapped users
    //       getAllUsers should return empty list
    //       getUserById should return user when found
    //       getUserById should throw NOT_FOUND when missing
    //       updateUserById should update and return user
    //       updateUserById should throw NOT_FOUND when missing
    //       deleteUserById should delete existing user
    //       deleteUserById should throw NOT_FOUND when missing
}

package com.sprintstart.sprintstartbackend.user.config

import com.sprintstart.sprintstartbackend.user.api.enums.Roles
import com.sprintstart.sprintstartbackend.user.api.enums.WorkingAreas
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.UUID

@Component
internal class DevUserSeeder (
    private val userRepository: UserRepository,
): ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val defaultUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        if (!userRepository.existsById(defaultUserId)) {
            userRepository.save(
                User(
                    id = defaultUserId,
                    username = "Default-User",
                    firstname = "Default",
                    lastname = "User",
                    primaryRole = Roles.EXISTING_MEMBER,
                    workingArea = WorkingAreas.BACKEND_DEV
                )
            )
        }
    }
}
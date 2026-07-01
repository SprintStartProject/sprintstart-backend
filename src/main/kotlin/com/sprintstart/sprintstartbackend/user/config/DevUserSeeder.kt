package com.sprintstart.sprintstartbackend.user.config

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Seeds a default development user when the application starts.
 *
 * This runner checks whether a predefined default user already exists in the database.
 * If the user does not exist, it creates and saves one with fixed development values.
 *
 * This class is intended for development or local setup purposes and should be reviewed
 * before being used in production environments.
 *
 * @property userRepository Repository used to check for and persist user entities.
 * @property projectRoleRepository Repository used to find or create the dev user's project role.
 */
@Component
@ConditionalOnProperty(prefix = "sprintstart.dev-user", name = ["enabled"], havingValue = "true")
class DevUserSeeder(
    private val userRepository: UserRepository,
    private val projectRoleRepository: ProjectRoleRepository,
) : ApplicationRunner {
    /**
     * Runs the development user seeding logic after the application context has started.
     *
     * A fixed UUID is used to make the default user deterministic across application starts.
     * If no user with this UUID exists, a new default user is created and stored with a
     * "Backend Developer" project role so it has a usable onboarding scope.
     *
     * @param args Application startup arguments provided by Spring Boot.
     */
    override fun run(args: ApplicationArguments) {
        val defaultUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        if (!userRepository.existsById(defaultUserId)) {
            val defaultRole = projectRoleRepository.findByName("Backend Developer")
                ?: projectRoleRepository.save(
                    ProjectRole(name = "Backend Developer", description = "Builds and maintains backend services"),
                )

            userRepository.save(
                User(
                    id = defaultUserId,
                    authId = "dev-user-subject",
                    username = "Default-User",
                    email = "dev.user@sprintstart.de",
                    firstname = "Default",
                    lastname = "User",
                    projectRoles = mutableSetOf(defaultRole),
                ),
            )
        }
    }
}

package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class AdminProjectMapperTest {
    @Test
    fun `toProjectUserResponse reports the roles the user actually holds`() {
        val user = user()
        user.projectRoles.add(ProjectRole(name = "Developer", description = "Writes code"))
        user.projectRoles.add(ProjectRole(name = "Architect", description = "Designs"))
        val assignment = ProjectUserAssignment(user = user, project = project())

        val response = assignment.toProjectUserResponse()

        assertThat(response.projectRoles).containsExactly("Architect", "Developer")
    }

    @Test
    fun `toProjectUserResponse ignores the never-written assignment roles`() {
        val user = user()
        val assignment = ProjectUserAssignment(user = user, project = project())
        // Roles are assigned to the user, never to the membership row, so
        // anything sitting here must not reach the response.
        assignment.projectRoles.add(ProjectRole(name = "Stale", description = "Unused"))

        val response = assignment.toProjectUserResponse()

        assertThat(response.projectRoles).isEmpty()
    }

    private fun project(id: UUID = UUID.randomUUID()) = Project(
        id = id,
        name = "SprintStart Frontend",
        description = "Frontend web application",
    )

    private fun user(id: UUID = UUID.randomUUID()) = User(
        id = id,
        authId = "auth-$id",
        username = "max.mustermann",
        email = "max.mustermann@example.com",
        firstname = "Max",
        lastname = "Mustermann",
    )
}

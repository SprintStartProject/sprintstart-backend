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
    fun `toProjectUserResponse reports the roles the user holds on this project`() {
        val assignment = ProjectUserAssignment(user = user(), project = project())
        assignment.projectRoles.add(ProjectRole(name = "Developer", description = "Writes code"))
        assignment.projectRoles.add(ProjectRole(name = "Architect", description = "Designs"))

        val response = assignment.toProjectUserResponse()

        assertThat(response.projectRoles).containsExactly("Architect", "Developer")
    }

    @Test
    fun `toProjectUserResponse exposes the same roles with ids for removal`() {
        val assignment = ProjectUserAssignment(user = user(), project = project())
        val developer = ProjectRole(name = "Developer", description = "Writes code")
        assignment.projectRoles.add(developer)

        val response = assignment.toProjectUserResponse()

        assertThat(response.projectRoleRefs.map { it.id }).containsExactly(developer.id)
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

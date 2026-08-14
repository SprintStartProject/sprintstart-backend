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

    // The companion test — "ignores the never-written assignment roles" — is gone with its subject.
    // It guarded against a stale per-assignment role collection leaking into the response; that
    // collection no longer exists, so there is no second place a role can be written and nothing
    // left to leak. The invariant it checked is now held by the model rather than by a test.

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

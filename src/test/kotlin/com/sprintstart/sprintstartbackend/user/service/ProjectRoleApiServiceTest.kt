package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.response.user.ProjectRoleSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The published role surface: what crosses the module line, and in what shape.
 *
 * Entities never do. The catalogue crosses as a detail DTO because a caller offering a choice needs
 * the description; what somebody holds crosses as the short one, because a caller resolving a role
 * they already chose does not.
 */
class ProjectRoleApiServiceTest {
    private val projectRoleService: ProjectRoleService = mockk(relaxed = true)
    private val service = ProjectRoleApiService(projectRoleService)

    @Test
    fun `the catalogue crosses the boundary with the text that explains each role`() {
        val role = ProjectRole(name = "Backend Developer", description = "Builds the services")
        every { projectRoleService.getAllRoles() } returns listOf(role)

        val published = service.getAllProjectRoles()

        assertThat(published).singleElement().satisfies({
            assertThat(it.id).isEqualTo(role.id)
            assertThat(it.name).isEqualTo("Backend Developer")
            assertThat(it.description).isEqualTo("Builds the services")
        })
    }

    @Test
    fun `what somebody holds crosses as id and name only`() {
        val userId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val role = ProjectRoleSummary(id = UUID.randomUUID(), name = "Backend Developer")
        every { projectRoleService.getRolesForUserOnProject(userId, projectId) } returns listOf(role)

        val held = service.getRolesOnProject(userId, projectId)

        assertThat(held).singleElement().satisfies({
            assertThat(it.id).isEqualTo(role.id)
            assertThat(it.name).isEqualTo("Backend Developer")
        })
    }

    @Test
    fun `the writes are the project-scoped forms, never the ones that reach every project`() {
        val userId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val roleId = UUID.randomUUID()

        service.assignRoleOnProject(userId, projectId, roleId)
        service.unassignRoleOnProject(userId, projectId, roleId)

        verify { projectRoleService.assignRoleToUser(userId, projectId, roleId) }
        verify { projectRoleService.unassignRoleFromUser(userId, projectId, roleId) }
    }
}

package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

/**
 * The module boundary's own behaviour, which the callers on the other side cannot see.
 *
 * `getProjectManagerId` answers the same empty for a project that does not exist and for one with
 * nobody managing it, and a caller refusing to remove "the manager" must not read that empty as a
 * match. The two writes are delegations, pinned so they keep going to the service that owns the
 * rules rather than growing their own.
 */
class ProjectMembershipApiServiceTest {
    private val projectUserAssignmentRepository: ProjectUserAssignmentRepository = mockk()
    private val projectRepository: ProjectRepository = mockk()
    private val adminProjectService: AdminProjectService = mockk(relaxed = true)
    private val service = ProjectMembershipApiService(
        projectUserAssignmentRepository = projectUserAssignmentRepository,
        projectRepository = projectRepository,
        adminProjectService = adminProjectService,
    )

    @Test
    fun `a project nobody manages has no manager id`() {
        val project = project()
        every { projectRepository.findById(project.id) } returns Optional.of(project)

        assertThat(service.getProjectManagerId(project.id)).isEmpty
    }

    @Test
    fun `a project that does not exist has no manager id, rather than failing`() {
        val projectId = UUID.randomUUID()
        every { projectRepository.findById(projectId) } returns Optional.empty()

        assertThat(service.getProjectManagerId(projectId)).isEmpty
    }

    @Test
    fun `a managed project answers the manager's user id`() {
        val manager = user()
        val project = project().apply { this.manager = manager }
        every { projectRepository.findById(project.id) } returns Optional.of(project)

        assertThat(service.getProjectManagerId(project.id)).contains(manager.id)
    }

    @Test
    fun `adding members goes to the service that owns the rules`() {
        val projectId = UUID.randomUUID()
        val userIds = setOf(UUID.randomUUID(), UUID.randomUUID())

        service.addMembers(projectId, userIds)

        verify { adminProjectService.assignUsers(projectId, AssignProjectUsersRequest(userIds = userIds)) }
    }

    @Test
    fun `removing a member goes to the service that owns the rules`() {
        val projectId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        service.removeMember(projectId, userId)

        verify { adminProjectService.removeUser(projectId, userId) }
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

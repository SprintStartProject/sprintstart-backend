package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceApi
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.request.project.SetProjectManagerRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class ProjectManagerServiceTest {
    private val projectRepository: ProjectRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val assignmentRepository: ProjectUserAssignmentRepository = mockk()
    private val projectSourceApi: ProjectSourceApi = mockk()
    private val service = ProjectManagerService(
        projectRepository = projectRepository,
        userRepository = userRepository,
        assignmentRepository = assignmentRepository,
        projectSourceApi = projectSourceApi,
    )

    @Test
    fun `setManager assigns the manager and adds a membership row`() {
        val project = project()
        val manager = user(username = "erika").apply { roles.add(Role.PM) }
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findById(manager.id) } returns Optional.of(manager)
        every { assignmentRepository.findByProjectIdAndUserId(project.id, manager.id) } returns null
        every { assignmentRepository.save(any()) } answers { firstArg() }
        every { assignmentRepository.findAllByProjectId(project.id) } returns emptyList()
        every { projectSourceApi.findSourcesByProjectId(project.id) } returns emptyList()

        val result = service.setManager(project.id, SetProjectManagerRequest(managerUserId = manager.id))

        assertThat(project.manager).isEqualTo(manager)
        assertThat(result.manager?.id).isEqualTo(manager.id)
        verify(exactly = 1) { assignmentRepository.save(any()) }
    }

    @Test
    fun `setManager does not duplicate an existing membership row`() {
        val project = project()
        val manager = user(username = "erika").apply { roles.add(Role.ADMIN) }
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findById(manager.id) } returns Optional.of(manager)
        every { assignmentRepository.findByProjectIdAndUserId(project.id, manager.id) } returns
            ProjectUserAssignment(user = manager, project = project)
        every { assignmentRepository.findAllByProjectId(project.id) } returns emptyList()
        every { projectSourceApi.findSourcesByProjectId(project.id) } returns emptyList()

        service.setManager(project.id, SetProjectManagerRequest(managerUserId = manager.id))

        verify(exactly = 0) { assignmentRepository.save(any()) }
    }

    @Test
    fun `setManager rejects users without a management role`() {
        val project = project()
        val member = user(username = "max").apply { roles.add(Role.USER) }
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findById(member.id) } returns Optional.of(member)

        val exception = assertThrows<ResponseStatusException> {
            service.setManager(project.id, SetProjectManagerRequest(managerUserId = member.id))
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(project.manager).isNull()
    }

    @Test
    fun `setManager returns 404 when the user does not exist`() {
        val project = project()
        val missingUserId = UUID.randomUUID()
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findById(missingUserId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.setManager(project.id, SetProjectManagerRequest(managerUserId = missingUserId))
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `setManager replaces a previously assigned manager`() {
        val previousManager = user(username = "old").apply { roles.add(Role.PM) }
        val project = project().apply { manager = previousManager }
        val newManager = user(username = "new").apply { roles.add(Role.PM) }
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findById(newManager.id) } returns Optional.of(newManager)
        every { assignmentRepository.findByProjectIdAndUserId(project.id, newManager.id) } returns null
        every { assignmentRepository.save(any()) } answers { firstArg() }
        every { assignmentRepository.findAllByProjectId(project.id) } returns emptyList()
        every { projectSourceApi.findSourcesByProjectId(project.id) } returns emptyList()

        service.setManager(project.id, SetProjectManagerRequest(managerUserId = newManager.id))

        assertThat(project.manager).isEqualTo(newManager)
    }

    @Test
    fun `clearManager removes the manager assignment`() {
        val manager = user(username = "erika").apply { roles.add(Role.PM) }
        val project = project().apply { this.manager = manager }
        every { projectRepository.findById(project.id) } returns Optional.of(project)

        service.clearManager(project.id)

        assertThat(project.manager).isNull()
    }

    @Test
    fun `getManagerCandidates returns only users holding a management role`() {
        val projectManager = user(username = "erika").apply { roles.add(Role.PM) }
        val admin = user(username = "adam").apply { roles.add(Role.ADMIN) }
        every { userRepository.findAllByRoleIn(listOf(Role.PM, Role.ADMIN)) } returns
            listOf(admin, projectManager)

        val result = service.getManagerCandidates()

        assertThat(result.map { it.username }).containsExactly("adam", "erika")
    }

    @Test
    fun `getManagedProjects returns only the projects a manager is assigned to`() {
        val managed = project(name = "SprintStart Frontend")
        every { projectRepository.findAllByManagerAuthId("auth-manager") } returns listOf(managed)
        every { assignmentRepository.findAllByProjectIdIn(listOf(managed.id)) } returns listOf(
            ProjectUserAssignment(user = user(username = "max"), project = managed),
        )

        val result = service.getManagedProjects(authId = "auth-manager", isAdmin = false)

        assertThat(result).singleElement()
        assertThat(result.single().memberCount).isEqualTo(1)
        verify(exactly = 0) { projectRepository.findAllWithManager() }
    }

    @Test
    fun `getManagedProjects returns every project for admins`() {
        val first = project(name = "SprintStart Frontend")
        val second = project(name = "SprintStart Backend")
        every { projectRepository.findAllWithManager() } returns listOf(first, second)
        every { assignmentRepository.findAllByProjectIdIn(listOf(first.id, second.id)) } returns emptyList()

        val result = service.getManagedProjects(authId = "auth-admin", isAdmin = true)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.memberCount }).containsExactly(0, 0)
        verify(exactly = 0) { projectRepository.findAllByManagerAuthId(any()) }
    }

    private fun project(
        id: UUID = UUID.randomUUID(),
        name: String = "SprintStart Frontend",
    ) = Project(
        id = id,
        name = name,
        description = "Frontend web application",
    )

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "max.mustermann",
    ) = User(
        id = id,
        authId = "auth-$id",
        username = username,
        email = "$username@example.com",
        firstname = "Max",
        lastname = "Mustermann",
    )
}

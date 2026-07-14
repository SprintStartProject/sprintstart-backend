package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceApi
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceDto
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.CreateAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.PatchAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class AdminProjectServiceTest {
    private val projectRepository: ProjectRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val assignmentRepository: ProjectUserAssignmentRepository = mockk()
    private val projectSourceApi: ProjectSourceApi = mockk()
    private val service = AdminProjectService(
        projectRepository = projectRepository,
        userRepository = userRepository,
        assignmentRepository = assignmentRepository,
        projectSourceApi = projectSourceApi,
    )

    @Test
    fun `getAllProjects returns empty list when no projects exist`() {
        every { projectRepository.findAll() } returns emptyList()

        val result = service.getAllProjects()

        assertThat(result).isEmpty()
        verify(exactly = 0) { assignmentRepository.findAllByProjectIdIn(any()) }
        verify(exactly = 0) { projectSourceApi.findSourcesByProjectId(any()) }
    }

    @Test
    fun `getAllProjects returns projects with sources and assigned user summaries`() {
        val frontendProject = project(name = "SprintStart Frontend")
        val backendProject = project(name = "SprintStart Backend")
        val frontendUser = user(username = "alice").apply { roles.add(Role.USER) }
        val backendUser = user(username = "bob").apply { roles.add(Role.ADMIN) }
        val frontendAssignment = ProjectUserAssignment(user = frontendUser, project = frontendProject)
        val backendAssignment = ProjectUserAssignment(user = backendUser, project = backendProject)

        every { projectRepository.findAll() } returns listOf(frontendProject, backendProject)
        every {
            assignmentRepository.findAllByProjectIdIn(listOf(frontendProject.id, backendProject.id))
        } returns listOf(frontendAssignment, backendAssignment)
        every { projectSourceApi.findSourcesByProjectId(frontendProject.id) } returns listOf(
            projectSource(name = "Frontend GitHub Repo"),
        )
        every { projectSourceApi.findSourcesByProjectId(backendProject.id) } returns listOf(
            projectSource(name = "Backend GitHub Repo"),
        )

        val result = service.getAllProjects()

        assertThat(result.map { it.name }).containsExactly("SprintStart Frontend", "SprintStart Backend")
        assertThat(result[0].sources.map { it.name }).containsExactly("Frontend GitHub Repo")
        assertThat(result[0].users.map { it.username }).containsExactly("alice")
        assertThat(result[1].sources.map { it.name }).containsExactly("Backend GitHub Repo")
        assertThat(result[1].users.map { it.username }).containsExactly("bob")
    }

    @Test
    fun `getProjectById returns sources and project-specific users`() {
        val project = project()
        val user = user().apply { roles.add(Role.USER) }
        val assignment = ProjectUserAssignment(user = user, project = project)
        assignment.projectRoles.add(ProjectRole(name = "MANAGER", description = "Manages the project"))
        val source = ProjectSourceDto(
            id = UUID.randomUUID().toString(),
            name = "Frontend GitHub Repo",
            type = "GITHUB",
            status = "CONNECTED",
        )

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { projectSourceApi.findSourcesByProjectId(project.id) } returns listOf(source)
        every { assignmentRepository.findAllByProjectId(project.id) } returns listOf(assignment)

        val result = service.getProjectById(project.id)

        assertThat(result.id).isEqualTo(project.id)
        assertThat(result.sources.map { it.type }).containsExactly("GITHUB")
        assertThat(result.users).hasSize(1)
        assertThat(result.users.single().roles).containsExactly(Role.USER)
        assertThat(result.users.single().projectRoles).containsExactly("MANAGER")
    }

    @Test
    fun `getProjectById throws 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.getProjectById(projectId)
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(exactly = 0) { projectSourceApi.findSourcesByProjectId(any()) }
        verify(exactly = 0) { assignmentRepository.findAllByProjectId(any()) }
    }

    @Test
    fun `createProject saves project when name is available`() {
        val request = CreateAdminProjectRequest(
            name = " SprintStart Frontend ",
            description = "Frontend web application",
        )
        every { projectRepository.findByName("SprintStart Frontend") } returns null
        every { projectRepository.save(any()) } answers { firstArg() }

        val result = service.createProject(request)

        assertThat(result.name).isEqualTo("SprintStart Frontend")
        assertThat(result.description).isEqualTo("Frontend web application")
        verify(exactly = 1) {
            projectRepository.save(match { it.name == "SprintStart Frontend" })
        }
    }

    @Test
    fun `createProject throws 400 when name is blank`() {
        val ex = assertThrows<ResponseStatusException> {
            service.createProject(CreateAdminProjectRequest(name = "   "))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(exactly = 0) { projectRepository.findByName(any()) }
        verify(exactly = 0) { projectRepository.save(any()) }
    }

    @Test
    fun `createProject throws 400 when name already exists`() {
        val existingProject = project(name = "SprintStart Frontend")
        every { projectRepository.findByName("SprintStart Frontend") } returns existingProject

        val ex = assertThrows<ResponseStatusException> {
            service.createProject(CreateAdminProjectRequest(name = "SprintStart Frontend"))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `patchProject updates provided fields and returns current sources and users`() {
        val project = project(name = "SprintStart Frontend")
        val user = user().apply { roles.add(Role.USER) }
        val assignment = ProjectUserAssignment(user = user, project = project)
        val request = PatchAdminProjectRequest(
            name = " SprintStart Backend ",
            description = "Backend service",
        )

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { projectRepository.findByName("SprintStart Backend") } returns null
        every { projectSourceApi.findSourcesByProjectId(project.id) } returns listOf(projectSource())
        every { assignmentRepository.findAllByProjectId(project.id) } returns listOf(assignment)

        val result = service.patchProject(project.id, request)

        assertThat(project.name).isEqualTo("SprintStart Backend")
        assertThat(project.description).isEqualTo("Backend service")
        assertThat(result.name).isEqualTo("SprintStart Backend")
        assertThat(result.description).isEqualTo("Backend service")
        assertThat(result.sources.map { it.type }).containsExactly("GITHUB")
        assertThat(result.users.map { it.username }).containsExactly(user.username)
    }

    @Test
    fun `patchProject preserves omitted fields`() {
        val project = project(name = "SprintStart Frontend")
        val request = PatchAdminProjectRequest(description = "Updated frontend web application")

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { projectSourceApi.findSourcesByProjectId(project.id) } returns emptyList()
        every { assignmentRepository.findAllByProjectId(project.id) } returns emptyList()

        val result = service.patchProject(project.id, request)

        assertThat(result.name).isEqualTo("SprintStart Frontend")
        assertThat(result.description).isEqualTo("Updated frontend web application")
        verify(exactly = 0) { projectRepository.findByName(any()) }
    }

    @Test
    fun `patchProject allows unchanged project name`() {
        val project = project(name = "SprintStart Frontend")
        val request = PatchAdminProjectRequest(name = " SprintStart Frontend ")

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { projectRepository.findByName("SprintStart Frontend") } returns project
        every { projectSourceApi.findSourcesByProjectId(project.id) } returns emptyList()
        every { assignmentRepository.findAllByProjectId(project.id) } returns emptyList()

        val result = service.patchProject(project.id, request)

        assertThat(result.name).isEqualTo("SprintStart Frontend")
    }

    @Test
    fun `patchProject throws 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.patchProject(projectId, PatchAdminProjectRequest(description = "Updated"))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(exactly = 0) { projectRepository.findByName(any()) }
        verify(exactly = 0) { projectSourceApi.findSourcesByProjectId(any()) }
        verify(exactly = 0) { assignmentRepository.findAllByProjectId(any()) }
    }

    @Test
    fun `patchProject throws 400 when name is blank`() {
        val project = project()
        every { projectRepository.findById(project.id) } returns Optional.of(project)

        val ex = assertThrows<ResponseStatusException> {
            service.patchProject(project.id, PatchAdminProjectRequest(name = "   "))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(exactly = 0) { projectRepository.findByName(any()) }
        verify(exactly = 0) { projectSourceApi.findSourcesByProjectId(any()) }
        verify(exactly = 0) { assignmentRepository.findAllByProjectId(any()) }
    }

    @Test
    fun `patchProject throws 400 when requested name belongs to another project`() {
        val project = project(name = "SprintStart Frontend")
        val existingProject = project(name = "SprintStart Backend")

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { projectRepository.findByName("SprintStart Backend") } returns existingProject

        val ex = assertThrows<ResponseStatusException> {
            service.patchProject(project.id, PatchAdminProjectRequest(name = "SprintStart Backend"))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(exactly = 0) { projectSourceApi.findSourcesByProjectId(any()) }
        verify(exactly = 0) { assignmentRepository.findAllByProjectId(any()) }
    }

    @Test
    fun `assignUsers creates only missing assignments and returns project users`() {
        val project = project()
        val existingUser = user(username = "alice").apply { roles.add(Role.USER) }
        val newUser = user(username = "bob").apply { roles.add(Role.ADMIN) }
        val existingAssignment = ProjectUserAssignment(user = existingUser, project = project)
        val savedAssignments = mutableListOf<ProjectUserAssignment>()

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findAllById(setOf(existingUser.id, newUser.id)) } returns listOf(existingUser, newUser)
        every { assignmentRepository.findAllByProjectId(project.id) } returnsMany listOf(
            listOf(existingAssignment),
            listOf(existingAssignment, ProjectUserAssignment(user = newUser, project = project)),
        )
        every { assignmentRepository.saveAll(any<List<ProjectUserAssignment>>()) } answers {
            savedAssignments.addAll(firstArg())
            firstArg<List<ProjectUserAssignment>>()
        }

        val result = service.assignUsers(
            project.id,
            AssignProjectUsersRequest(userIds = setOf(existingUser.id, newUser.id)),
        )

        assertThat(savedAssignments).hasSize(1)
        assertThat(savedAssignments.single().user.id).isEqualTo(newUser.id)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(existingUser.id, newUser.id)
    }

    @Test
    fun `assignUsers does not create duplicate assignment when user is already assigned`() {
        val project = project()
        val existingUser = user(username = "alice").apply { roles.add(Role.USER) }
        val existingAssignment = ProjectUserAssignment(user = existingUser, project = project)

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findAllById(setOf(existingUser.id)) } returns listOf(existingUser)
        every { assignmentRepository.findAllByProjectId(project.id) } returnsMany listOf(
            listOf(existingAssignment),
            listOf(existingAssignment),
        )

        val result = service.assignUsers(
            project.id,
            AssignProjectUsersRequest(userIds = setOf(existingUser.id)),
        )

        assertThat(result.map { it.id }).containsExactly(existingUser.id)
        verify(exactly = 0) { assignmentRepository.saveAll(any<List<ProjectUserAssignment>>()) }
    }

    @Test
    fun `assignUsers throws 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.assignUsers(projectId, AssignProjectUsersRequest(userIds = setOf(userId)))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(exactly = 0) { userRepository.findAllById(any<Set<UUID>>()) }
        verify(exactly = 0) { assignmentRepository.findAllByProjectId(any()) }
    }

    @Test
    fun `assignUsers throws 404 when a requested user is missing`() {
        val project = project()
        val missingUserId = UUID.randomUUID()
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { userRepository.findAllById(setOf(missingUserId)) } returns emptyList()

        val ex = assertThrows<ResponseStatusException> {
            service.assignUsers(project.id, AssignProjectUsersRequest(userIds = setOf(missingUserId)))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getProjectUsers returns assigned users for project`() {
        val project = project()
        val user = user().apply { roles.add(Role.USER) }
        val assignment = ProjectUserAssignment(user = user, project = project)

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { assignmentRepository.findAllByProjectId(project.id) } returns listOf(assignment)

        val result = service.getProjectUsers(project.id)

        assertThat(result.map { it.id }).containsExactly(user.id)
        assertThat(result.single().roles).containsExactly(Role.USER)
    }

    @Test
    fun `getProjectUsers throws 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.getProjectUsers(projectId)
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(exactly = 0) { assignmentRepository.findAllByProjectId(any()) }
    }

    @Test
    fun `removeUser deletes existing assignment`() {
        val project = project()
        val user = user()
        val assignment = ProjectUserAssignment(user = user, project = project)
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { assignmentRepository.findByProjectIdAndUserId(project.id, user.id) } returns assignment
        every { assignmentRepository.delete(assignment) } just runs

        service.removeUser(project.id, user.id)

        verify(exactly = 1) { assignmentRepository.delete(assignment) }
    }

    @Test
    fun `removeUser throws 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.removeUser(projectId, userId)
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(exactly = 0) { assignmentRepository.findByProjectIdAndUserId(any(), any()) }
        verify(exactly = 0) { assignmentRepository.delete(any()) }
    }

    @Test
    fun `removeUser throws 404 when assignment does not exist`() {
        val project = project()
        val userId = UUID.randomUUID()
        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { assignmentRepository.findByProjectIdAndUserId(project.id, userId) } returns null

        val ex = assertThrows<ResponseStatusException> {
            service.removeUser(project.id, userId)
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `deleteProject removes assignments before project`() {
        val project = project()
        val assignment = ProjectUserAssignment(user = user(), project = project)
        val deletedAssignments = slot<Iterable<ProjectUserAssignment>>()

        every { projectRepository.findById(project.id) } returns Optional.of(project)
        every { assignmentRepository.findAllByProjectId(project.id) } returns listOf(assignment)
        every { assignmentRepository.deleteAll(capture(deletedAssignments)) } just runs
        every { projectRepository.delete(project) } just runs

        val result = service.deleteProject(project.id)

        assertThat(result.deleted).isTrue()
        assertThat(deletedAssignments.captured.toList()).containsExactly(assignment)
        verify(exactly = 1) { projectRepository.delete(project) }
    }

    @Test
    fun `deleteProject throws 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        every { projectRepository.findById(projectId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.deleteProject(projectId)
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(exactly = 0) { assignmentRepository.findAllByProjectId(any()) }
        verify(exactly = 0) { assignmentRepository.deleteAll(any<Iterable<ProjectUserAssignment>>()) }
        verify(exactly = 0) { projectRepository.delete(any()) }
    }

    private fun projectSource(
        id: String = UUID.randomUUID().toString(),
        name: String = "Frontend GitHub Repo",
        type: String = "GITHUB",
        status: String = "CONNECTED",
    ) = ProjectSourceDto(
        id = id,
        name = name,
        type = type,
        status = status,
    )

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

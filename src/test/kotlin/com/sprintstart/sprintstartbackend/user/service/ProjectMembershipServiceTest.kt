package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.request.project.TransferProjectUserRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
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

/**
 * Covers the move a project manager may perform themselves: the caller must manage both projects,
 * and the user is never left unassigned in between.
 */
class ProjectMembershipServiceTest {
    private val projectRepository: ProjectRepository = mockk()
    private val assignmentRepository: ProjectUserAssignmentRepository = mockk()
    private val adminProjectService: AdminProjectService = mockk()
    private val service = ProjectMembershipService(
        projectRepository = projectRepository,
        assignmentRepository = assignmentRepository,
        adminProjectService = adminProjectService,
    )

    private val managerAuthId = "auth-pm"

    @Test
    fun `transferUser moves the assignment to the target project`() {
        val source = project(name = "Backend")
        val target = project(name = "Frontend")
        val member = user(username = "max")
        val sourceAssignment = ProjectUserAssignment(user = member, project = source)
        val saved = slot<ProjectUserAssignment>()
        every { projectRepository.findById(target.id) } returns Optional.of(target)
        every { projectRepository.findById(source.id) } returns Optional.of(source)
        every { assignmentRepository.findByProjectIdAndUserId(source.id, member.id) } returns sourceAssignment
        every { assignmentRepository.findByProjectIdAndUserId(target.id, member.id) } returns null
        every { assignmentRepository.delete(sourceAssignment) } just runs
        every { assignmentRepository.flush() } just runs
        every { assignmentRepository.save(capture(saved)) } answers { firstArg() }
        every { adminProjectService.getProjectUsers(target.id) } returns emptyList()

        service.transferUser(
            authId = managerAuthId,
            isAdmin = false,
            targetProjectId = target.id,
            request = TransferProjectUserRequest(userId = member.id, sourceProjectId = source.id),
        )

        verify(exactly = 1) { assignmentRepository.delete(sourceAssignment) }
        assertThat(saved.captured.project.id).isEqualTo(target.id)
        assertThat(saved.captured.user.id).isEqualTo(member.id)
    }

    @Test
    fun `transferUser only removes the source assignment when the user is already in the target`() {
        val source = project()
        val target = project()
        val member = user()
        val sourceAssignment = ProjectUserAssignment(user = member, project = source)
        every { projectRepository.findById(target.id) } returns Optional.of(target)
        every { projectRepository.findById(source.id) } returns Optional.of(source)
        every { assignmentRepository.findByProjectIdAndUserId(source.id, member.id) } returns sourceAssignment
        every { assignmentRepository.findByProjectIdAndUserId(target.id, member.id) } returns
            ProjectUserAssignment(user = member, project = target)
        every { assignmentRepository.delete(sourceAssignment) } just runs
        every { assignmentRepository.flush() } just runs
        every { adminProjectService.getProjectUsers(target.id) } returns emptyList()

        service.transferUser(
            authId = managerAuthId,
            isAdmin = false,
            targetProjectId = target.id,
            request = TransferProjectUserRequest(userId = member.id, sourceProjectId = source.id),
        )

        verify(exactly = 1) { assignmentRepository.delete(sourceAssignment) }
        verify(exactly = 0) { assignmentRepository.save(any()) }
    }

    @Test
    fun `transferUser rejects a source project the caller does not manage`() {
        val target = project(manager = user(username = "erika"))
        val source = project(manager = user(username = "foreign"))
        every { projectRepository.findById(target.id) } returns Optional.of(target)
        every { projectRepository.findById(source.id) } returns Optional.of(source)

        val exception = assertThrows<ResponseStatusException> {
            service.transferUser(
                authId = managerAuthId,
                isAdmin = false,
                targetProjectId = target.id,
                request = TransferProjectUserRequest(userId = UUID.randomUUID(), sourceProjectId = source.id),
            )
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(exactly = 0) { assignmentRepository.delete(any()) }
    }

    @Test
    fun `transferUser rejects identical source and target projects`() {
        val projectId = UUID.randomUUID()

        val exception = assertThrows<ResponseStatusException> {
            service.transferUser(
                authId = managerAuthId,
                isAdmin = false,
                targetProjectId = projectId,
                request = TransferProjectUserRequest(userId = UUID.randomUUID(), sourceProjectId = projectId),
            )
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `transferUser rejects moving a user that manages the source project`() {
        val sourceManager = user(username = "erika", authId = managerAuthId)
        val source = project(manager = sourceManager)
        val target = project(manager = sourceManager)
        every { projectRepository.findById(target.id) } returns Optional.of(target)
        every { projectRepository.findById(source.id) } returns Optional.of(source)
        every { assignmentRepository.findByProjectIdAndUserId(source.id, sourceManager.id) } returns
            ProjectUserAssignment(user = sourceManager, project = source)

        val exception = assertThrows<ResponseStatusException> {
            service.transferUser(
                authId = managerAuthId,
                isAdmin = false,
                targetProjectId = target.id,
                request = TransferProjectUserRequest(userId = sourceManager.id, sourceProjectId = source.id),
            )
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.CONFLICT)
        verify(exactly = 0) { assignmentRepository.delete(any()) }
    }

    @Test
    fun `transferUser returns 404 when the user is not assigned to the source project`() {
        val source = project()
        val target = project()
        val userId = UUID.randomUUID()
        every { projectRepository.findById(target.id) } returns Optional.of(target)
        every { projectRepository.findById(source.id) } returns Optional.of(source)
        every { assignmentRepository.findByProjectIdAndUserId(source.id, userId) } returns null

        val exception = assertThrows<ResponseStatusException> {
            service.transferUser(
                authId = managerAuthId,
                isAdmin = false,
                targetProjectId = target.id,
                request = TransferProjectUserRequest(userId = userId, sourceProjectId = source.id),
            )
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    private fun project(
        id: UUID = UUID.randomUUID(),
        name: String = "SprintStart Frontend",
        manager: User? = user(username = "erika", authId = managerAuthId),
    ) = Project(
        id = id,
        name = name,
        description = "Frontend web application",
        manager = manager,
    )

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "max.mustermann",
        authId: String = "auth-$username",
    ) = User(
        id = id,
        authId = authId,
        username = username,
        email = "$username@example.com",
        firstname = "Max",
        lastname = "Mustermann",
    )
}

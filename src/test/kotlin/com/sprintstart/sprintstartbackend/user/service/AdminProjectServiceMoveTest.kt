package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceApi
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.events.UserMovedToProjectEvent
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID

/**
 * Covers [AdminProjectService.assignUsers] moving a regular user out of their other projects.
 *
 * Kept apart from [AdminProjectServiceTest], which covers the rest of the service.
 */
class AdminProjectServiceMoveTest {
    private val projectRepository: ProjectRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val assignmentRepository: ProjectUserAssignmentRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val service = AdminProjectService(
        projectRepository = projectRepository,
        userRepository = userRepository,
        assignmentRepository = assignmentRepository,
        projectSourceApi = mockk<ProjectSourceApi>(),
        githubRepositoryApi = mockk<GithubRepositoryApi>(),
        jiraInstanceApi = mockk<JiraInstanceApi>(),
        eventPublisher = eventPublisher,
    )

    /**
     * A regular user belongs to exactly one project. Roles sit on the assignment, so deleting the
     * old assignment is what makes the old project's roles disappear.
     */
    @Test
    fun `assignUsers moves a regular user out of their previous project`() {
        val oldProject = project("Old")
        val target = project("Target")
        val user = member(Role.USER, of = listOf(oldProject))
        val oldAssignment = user.projectAssignments.single()
        val moves = stubAssignment(target, user)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        verify(exactly = 1) { assignmentRepository.deleteAll(listOf(oldAssignment)) }
        assertThat(moves.single().userId).isEqualTo(user.id)
        assertThat(moves.single().projectId).isEqualTo(target.id)
        assertThat(moves.single().previousProjectIds).containsExactly(oldProject.id)
    }

    @Test
    fun `assignUsers reports every project a regular user leaves`() {
        val first = project("First")
        val second = project("Second")
        val target = project("Target")
        val user = member(Role.USER, of = listOf(first, second))
        val moves = stubAssignment(target, user)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        assertThat(moves.single().previousProjectIds).containsExactlyInAnyOrder(first.id, second.id)
    }

    @Test
    fun `assignUsers moves an HR user like a regular user`() {
        val oldProject = project("Old")
        val target = project("Target")
        val user = member(Role.HR, of = listOf(oldProject))
        val moves = stubAssignment(target, user)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        verify(exactly = 1) { assignmentRepository.deleteAll(any<Iterable<ProjectUserAssignment>>()) }
        assertThat(moves.single().previousProjectIds).containsExactly(oldProject.id)
    }

    @Test
    fun `assignUsers keeps the other memberships of a PM`() {
        val target = project("Target")
        val user = member(Role.PM, of = listOf(project("Old")))
        val moves = stubAssignment(target, user)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        verify(exactly = 0) { assignmentRepository.deleteAll(any<Iterable<ProjectUserAssignment>>()) }
        verify(exactly = 1) { assignmentRepository.saveAll(match<List<ProjectUserAssignment>> { it.size == 1 }) }
        assertThat(moves).isEmpty()
    }

    @Test
    fun `assignUsers keeps the other memberships of an admin`() {
        val target = project("Target")
        val user = member(Role.ADMIN, of = listOf(project("Old")))
        val moves = stubAssignment(target, user)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        verify(exactly = 0) { assignmentRepository.deleteAll(any<Iterable<ProjectUserAssignment>>()) }
        assertThat(moves).isEmpty()
    }

    /** A user who lost the PM role can still be a project's manager and then stays a member, as in `removeUser`. */
    @Test
    fun `assignUsers keeps a membership in a project the user still manages`() {
        val managed = project("Managed")
        val other = project("Other")
        val target = project("Target")
        val user = member(Role.USER, of = listOf(managed, other))
        managed.manager = user
        val otherAssignment = user.projectAssignments.single { it.id.projectId == other.id }
        val moves = stubAssignment(target, user)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        verify(exactly = 1) { assignmentRepository.deleteAll(listOf(otherAssignment)) }
        assertThat(moves.single().previousProjectIds).containsExactly(other.id)
    }

    @Test
    fun `assignUsers does not move a user who is already in the target project`() {
        val target = project("Target")
        val user = member(Role.USER, of = listOf(target))
        val moves = stubAssignment(target, user, alreadyMember = true)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        verify(exactly = 0) { assignmentRepository.deleteAll(any<Iterable<ProjectUserAssignment>>()) }
        verify(exactly = 0) { assignmentRepository.saveAll(any<List<ProjectUserAssignment>>()) }
        assertThat(moves).isEmpty()
    }

    @Test
    fun `assignUsers publishes no move for a user who had no other membership`() {
        val target = project("Target")
        val user = member(Role.USER, of = emptyList())
        val moves = stubAssignment(target, user)

        service.assignUsers(target.id, AssignProjectUsersRequest(userIds = setOf(user.id)))

        verify(exactly = 0) { assignmentRepository.deleteAll(any<Iterable<ProjectUserAssignment>>()) }
        verify(exactly = 1) { assignmentRepository.saveAll(any<List<ProjectUserAssignment>>()) }
        assertThat(moves).isEmpty()
    }

    /** A user holding [role] who is already a member of each project in [of]. */
    private fun member(role: Role, of: List<Project>): User {
        val user = user(username = "member-${UUID.randomUUID()}").apply { roles.add(role) }
        of.forEach { user.projectAssignments.add(ProjectUserAssignment(user = user, project = it)) }
        return user
    }

    /**
     * Stubs assigning [joining] to [target] and returns the list every [UserMovedToProjectEvent]
     * published during the call is captured into.
     */
    private fun stubAssignment(
        target: Project,
        joining: User,
        alreadyMember: Boolean = false,
    ): List<UserMovedToProjectEvent> {
        val moves = mutableListOf<UserMovedToProjectEvent>()
        val existing = if (alreadyMember) {
            listOf(ProjectUserAssignment(user = joining, project = target))
        } else {
            emptyList()
        }
        every { projectRepository.findById(target.id) } returns Optional.of(target)
        every { userRepository.findAllById(setOf(joining.id)) } returns listOf(joining)
        every { assignmentRepository.findAllByProjectId(target.id) } returns existing
        every { assignmentRepository.saveAll(any<List<ProjectUserAssignment>>()) } answers { firstArg() }
        every { assignmentRepository.deleteAll(any<Iterable<ProjectUserAssignment>>()) } just runs
        every { eventPublisher.publishEvent(capture(moves)) } just runs
        return moves
    }

    private fun project(name: String) = Project(id = UUID.randomUUID(), name = name, description = "Test project")

    private fun user(username: String) = User(
        id = UUID.randomUUID(),
        authId = "auth-$username",
        username = username,
        email = "$username@example.com",
        firstname = "Max",
        lastname = "Mustermann",
    )
}

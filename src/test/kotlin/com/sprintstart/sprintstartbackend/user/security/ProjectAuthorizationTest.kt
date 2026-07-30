package com.sprintstart.sprintstartbackend.user.security

import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.Optional
import java.util.UUID

class ProjectAuthorizationTest {
    private val projectRepository: ProjectRepository = mockk()
    private val assignmentRepository: ProjectUserAssignmentRepository = mockk()
    private val userRepository: UserRepository = mockk()

    private val projectAuthorization = ProjectAuthorization(
        projectRepository = projectRepository,
        assignmentRepository = assignmentRepository,
        userRepository = userRepository,
    )

    private val projectId: UUID = UUID.randomUUID()
    private val managerAuthId = "auth-manager"
    private val otherAuthId = "auth-other"

    @Test
    fun `isAdmin is true only for the admin authority`() {
        assertThat(projectAuthorization.isAdmin(authentication(otherAuthId, "ROLE_ADMIN"))).isTrue()
        assertThat(projectAuthorization.isAdmin(authentication(otherAuthId, "ROLE_PM"))).isFalse()
        assertThat(projectAuthorization.isAdmin(authentication(otherAuthId))).isFalse()
    }

    @Test
    fun `isManager is true when the caller is the assigned manager`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)

        assertThat(projectAuthorization.isManager(authentication(managerAuthId), projectId)).isTrue()
        assertThat(projectAuthorization.isManager(authentication(otherAuthId), projectId)).isFalse()
    }

    @Test
    fun `isManager is false when the project has no manager or does not exist`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.empty()

        assertThat(projectAuthorization.isManager(authentication(managerAuthId), projectId)).isFalse()
    }

    @Test
    fun `canManageProject grants admins access to projects they do not manage`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)

        val admin = authentication(otherAuthId, "ROLE_ADMIN")

        assertThat(projectAuthorization.canManageProject(admin, projectId)).isTrue()
    }

    @Test
    fun `canManageProject denies project managers on projects they do not manage`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)

        val foreignManager = authentication(otherAuthId, "ROLE_PM")

        assertThat(projectAuthorization.canManageProject(foreignManager, projectId)).isFalse()
    }

    @Test
    fun `canAccessProject grants access to assigned members`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)
        val member = user(otherAuthId)
        every { userRepository.findByAuthId(otherAuthId) } returns Optional.of(member)
        every { assignmentRepository.findByProjectIdAndUserId(projectId, member.id) } returns assignment(member)

        assertThat(projectAuthorization.canAccessProject(authentication(otherAuthId), projectId)).isTrue()
    }

    @Test
    fun `canAccessProject denies users who neither manage nor belong to the project`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)
        val outsider = user(otherAuthId)
        every { userRepository.findByAuthId(otherAuthId) } returns Optional.of(outsider)
        every { assignmentRepository.findByProjectIdAndUserId(projectId, outsider.id) } returns null

        assertThat(projectAuthorization.canAccessProject(authentication(otherAuthId), projectId)).isFalse()
    }

    @Test
    fun `canAccessProject grants the manager access without a membership row`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)

        assertThat(projectAuthorization.canAccessProject(authentication(managerAuthId), projectId)).isTrue()
    }

    @Test
    fun `canAccessProject denies unknown users`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)
        every { userRepository.findByAuthId(otherAuthId) } returns Optional.empty()

        assertThat(projectAuthorization.canAccessProject(authentication(otherAuthId), projectId)).isFalse()
    }

    private fun authentication(authId: String, vararg authorities: String): Authentication {
        return UsernamePasswordAuthenticationToken(
            authId,
            "n/a",
            authorities.map { SimpleGrantedAuthority(it) },
        )
    }

    private fun user(authId: String) = User(
        authId = authId,
        username = "max.mustermann",
        email = "max.mustermann@example.com",
        firstname = "Max",
        lastname = "Mustermann",
    )

    private fun assignment(user: User) = ProjectUserAssignment(
        user = user,
        project = Project(id = projectId, name = "SprintStart Frontend"),
    )
}

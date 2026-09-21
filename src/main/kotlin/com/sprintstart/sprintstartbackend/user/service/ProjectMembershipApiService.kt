package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

/**
 * Read-only adapter over project assignments for other modules.
 *
 * Deliberately thin: it maps assignments to a boundary type and nothing else. Anything that needs
 * to interpret these facts — what counts as a stall, how long a first response may take — belongs
 * to the module that cares, not here.
 */
@Service
internal class ProjectMembershipApiService(
    private val projectUserAssignmentRepository: ProjectUserAssignmentRepository,
    private val projectRepository: ProjectRepository,
) : ProjectMembershipApi {
    @Transactional(readOnly = true)
    override fun getProjectMembers(projectId: UUID): List<ProjectMember> {
        return projectUserAssignmentRepository.findAllByProjectId(projectId).map { assignment ->
            val user = assignment.user
            ProjectMember(
                userId = user.id,
                displayName = "${user.firstname} ${user.lastname}".trim().ifBlank { user.username },
                githubLogin = user.githubLogin,
                joinedAt = assignment.assignedAt,
                jiraDisplayName = user.jiraDisplayName,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getProjectManagerId(projectId: UUID): Optional<UUID> {
        val project = projectRepository
            .findById(projectId)
            .orElse(null)

        return Optional.ofNullable(project?.manager?.id)
    }
}

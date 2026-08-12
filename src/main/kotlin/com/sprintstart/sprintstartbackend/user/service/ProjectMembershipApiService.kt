package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val projectRoleRepository: ProjectRoleRepository,
) : ProjectMembershipApi {
    @Transactional(readOnly = true)
    override fun onboardingTrackKeysInUse(): Set<String> {
        return projectRoleRepository.findAll().mapNotNull { it.onboardingTrackKey }.toSet()
    }

    @Transactional(readOnly = true)
    override fun getProjectMembers(projectId: UUID): List<ProjectMember> {
        return projectUserAssignmentRepository.findAllByProjectId(projectId).map { assignment ->
            val user = assignment.user
            ProjectMember(
                userId = user.id,
                displayName = "${user.firstname} ${user.lastname}".trim().ifBlank { user.username },
                githubLogin = user.githubLogin,
                joinedAt = assignment.assignedAt,
                onboardingTrackKey = trackKeyFor(assignment),
                jiraDisplayName = user.jiraDisplayName,
            )
        }
    }

    /**
     * The single onboarding track this assignment's roles agree on, or null.
     *
     * Reads the roles held **on this project**, which is now the only place roles live — so the
     * grain this always wanted is finally the grain the data has. Somebody who ships code here and
     * runs delivery elsewhere gets each project's own vocabulary.
     *
     * Disagreement resolves to null, not to a winner. Somebody holding two roles with different
     * tracks *on the same project* is a real situation (a PM who also ships code), and picking one
     * arbitrarily would put the wrong vocabulary in front of them. Null lets onboarding fall back to
     * its default, which is the same answer they got before tracks existed. An assignment with no
     * roles at all resolves to null for the same reason, and the setup ladder reports it.
     */
    private fun trackKeyFor(assignment: ProjectUserAssignment): String? =
        assignment.user.projectRoles
            .mapNotNull { it.onboardingTrackKey }
            .distinct()
            .singleOrNull()
}

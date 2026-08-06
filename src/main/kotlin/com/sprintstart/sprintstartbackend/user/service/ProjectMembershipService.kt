package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.request.project.TransferProjectUserRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Application service for the membership change a project manager may perform themselves: moving one
 * of their users from one of their projects into another.
 *
 * Route-level authorization ([com.sprintstart.sprintstartbackend.user.security.ProjectAuthorization])
 * decides whether the caller manages the project in the path; this service checks the second project
 * involved in a move. Together they mean a manager can shuffle their own people between their own
 * projects but can neither reach into a foreign project nor pull an unrelated user out of the
 * directory.
 *
 * Adding a user to a project for the first time and removing them from one altogether both stay
 * administrator-only on [com.sprintstart.sprintstartbackend.user.controller.AdminProjectController].
 * A manager who dropped their last mapping to a user would no longer manage that user and could not
 * undo the change, so a manager only ever gets the atomic move. Project metadata and the raw
 * assignment writes remain owned by [AdminProjectService].
 */
@Service
class ProjectMembershipService(
    private val projectRepository: ProjectRepository,
    private val assignmentRepository: ProjectUserAssignmentRepository,
    private val adminProjectService: AdminProjectService,
) {
    /**
     * Moves one user from a source project into a target project.
     *
     * The removal and the assignment happen in the same transaction, so the user is never left
     * without a project the caller can reach. Project roles need no handling: they hang off the
     * user rather than off the membership, so they follow the move by themselves. A user that is
     * already assigned to the target project is only removed from the source, which makes repeating
     * the request harmless.
     *
     * @param authId External authentication identifier of the requesting user.
     * @param isAdmin Whether the requesting user holds the admin role.
     * @param targetProjectId Identifier of the project the user is moved into.
     * @param request Identifiers of the user and of the project the user is moved out of.
     * @return The assigned-user list of the target project after the move.
     * @throws ResponseStatusException When source and target are the same project (400), the caller
     * does not manage the source project (403), the user is not assigned to the source project
     * (404), or the user manages the source project (409).
     */
    @Transactional
    @Tracked("Transferring user between projects")
    fun transferUser(
        authId: String,
        isAdmin: Boolean,
        targetProjectId: UUID,
        request: TransferProjectUserRequest,
    ): List<ProjectUserResponse> {
        ensureDifferentProjects(request.sourceProjectId, targetProjectId)

        val targetProject = findProject(targetProjectId)
        val sourceProject = findProject(request.sourceProjectId)
        ensureManages(authId, isAdmin, sourceProject)

        val sourceAssignment = findTransferableAssignment(sourceProject, request.userId)
        assignmentRepository.delete(sourceAssignment)
        assignmentRepository.flush()

        val existingTargetAssignment = assignmentRepository
            .findByProjectIdAndUserId(targetProject.id, request.userId)
        if (existingTargetAssignment == null) {
            assignmentRepository.save(
                ProjectUserAssignment(user = sourceAssignment.user, project = targetProject),
            )
        }

        return adminProjectService.getProjectUsers(targetProject.id)
    }

    /**
     * Verifies that a transfer actually moves the user between two different projects.
     *
     * @param sourceProjectId Identifier of the project the user is moved out of.
     * @param targetProjectId Identifier of the project the user is moved into.
     * @throws ResponseStatusException When both identifiers are the same.
     */
    private fun ensureDifferentProjects(sourceProjectId: UUID, targetProjectId: UUID) {
        if (sourceProjectId == targetProjectId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Source and target project must differ",
            )
        }
    }

    /**
     * Verifies that the caller manages the given project.
     *
     * @param authId External authentication identifier of the requesting user.
     * @param isAdmin Whether the requesting user holds the admin role.
     * @param project The project to check.
     * @throws ResponseStatusException When the caller does not manage the project.
     */
    private fun ensureManages(authId: String, isAdmin: Boolean, project: Project) {
        if (isAdmin || project.manager?.authId == authId) {
            return
        }
        throw ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Caller does not manage project with id ${project.id}",
        )
    }

    /**
     * Returns the assignment that may be moved out of the given project.
     *
     * The project's own manager is refused: moving them away would leave the project managed by
     * someone who is not a member of it, which is the administrator's decision to make.
     *
     * @param project The project the user is moved out of.
     * @param userId Identifier of the user being moved.
     * @return The assignment to move.
     * @throws ResponseStatusException When the user is not assigned to the project (404) or manages
     * it (409).
     */
    private fun findTransferableAssignment(project: Project, userId: UUID): ProjectUserAssignment {
        if (project.manager?.id == userId) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "User with id $userId manages project with id ${project.id}. " +
                    "Clear the project manager first.",
            )
        }

        return assignmentRepository.findByProjectIdAndUserId(project.id, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User with id $userId is not assigned to project with id ${project.id}",
            )
    }

    /**
     * Finds a project by its unique identifier.
     *
     * @param id The unique identifier of the project to be retrieved.
     * @return The project associated with the given id.
     * @throws ResponseStatusException When no project is found with the given id.
     */
    private fun findProject(id: UUID): Project {
        return projectRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project with id $id not found") }
    }
}

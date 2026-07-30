package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceApi
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.mapper.toAdminDetailResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toManagedResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toManagerResponse
import com.sprintstart.sprintstartbackend.user.model.request.project.SetProjectManagerRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ManagedProjectResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectManagerResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Application service for project manager assignments.
 *
 * A project has at most one manager, held as a foreign key on the project itself. That foreign key
 * — not the global `PM` role — decides which projects a user may manage. Project metadata and
 * member assignments remain owned by [AdminProjectService].
 */
@Service
class ProjectManagerService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val assignmentRepository: ProjectUserAssignmentRepository,
    private val projectSourceApi: ProjectSourceApi,
) {
    /**
     * Assigns a project manager, replacing any previously assigned manager.
     *
     * The manager is also assigned to the project as a member if they are not one already, keeping
     * member-facing project lists consistent. Access itself does not depend on that row.
     *
     * @param projectId Project identifier.
     * @param request Manager assignment payload.
     * @return The updated project.
     * @throws ResponseStatusException When the project or user does not exist, or the user holds
     * neither the `PM` nor the `ADMIN` role.
     */
    @Transactional
    @Tracked("Assigning project manager")
    fun setManager(projectId: UUID, request: SetProjectManagerRequest): AdminProjectDetailResponse {
        val project = findProject(projectId)
        val manager = userRepository
            .findById(request.managerUserId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User with id ${request.managerUserId} not found",
                )
            }

        if (Role.PM !in manager.roles && Role.ADMIN !in manager.roles) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "User with id ${manager.id} must hold the PM or ADMIN role to manage a project",
            )
        }

        project.manager = manager
        ensureMembership(project, manager)

        return project.toAdminDetailResponse(
            sources = projectSourceApi.findSourcesByProjectId(project.id),
            assignments = assignmentRepository.findAllByProjectId(project.id),
        )
    }

    /**
     * Removes the manager assignment from a project.
     *
     * The former manager keeps their membership assignment and therefore their read access.
     *
     * @param projectId Project identifier.
     * @throws ResponseStatusException When no project exists for [projectId].
     */
    @Transactional
    @Tracked("Clearing project manager")
    fun clearManager(projectId: UUID) {
        findProject(projectId).manager = null
    }

    /**
     * Returns all users eligible to be assigned as a project manager.
     *
     * @return Users holding the `PM` or `ADMIN` role.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving project manager candidates")
    fun getManagerCandidates(): List<ProjectManagerResponse> {
        return userRepository
            .findAllByRoleIn(listOf(Role.PM, Role.ADMIN))
            .map { it.toManagerResponse() }
    }

    /**
     * Returns the projects the given user may manage.
     *
     * Admins may manage every project; everyone else only the projects they are assigned to as
     * manager.
     *
     * @param authId External authentication identifier of the requesting user.
     * @param isAdmin Whether the requesting user holds the admin role.
     * @return The manageable projects.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving managed projects")
    fun getManagedProjects(authId: String, isAdmin: Boolean): List<ManagedProjectResponse> {
        val projects = if (isAdmin) {
            projectRepository.findAllWithManager()
        } else {
            projectRepository.findAllByManagerAuthId(authId)
        }
        if (projects.isEmpty()) {
            return emptyList()
        }

        val assignmentsByProject = assignmentRepository
            .findAllByProjectIdIn(projects.map { it.id })
            .groupBy { it.project.id }

        return projects.map { project ->
            project.toManagedResponse(memberCount = assignmentsByProject[project.id].orEmpty().size)
        }
    }

    /**
     * Assigns a user to a project if no assignment exists yet.
     *
     * @param project The project to assign to.
     * @param user The user to assign.
     */
    private fun ensureMembership(project: Project, user: User) {
        val existing = assignmentRepository.findByProjectIdAndUserId(project.id, user.id)
        if (existing == null) {
            assignmentRepository.save(ProjectUserAssignment(user = user, project = project))
        }
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

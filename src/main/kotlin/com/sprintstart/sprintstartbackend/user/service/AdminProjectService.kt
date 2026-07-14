package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceApi
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.mapper.toAdminDetailResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toAdminListResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toProjectUserResponse
import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.CreateAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.PatchAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectListResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.DeleteProjectResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Application service for administrative project management.
 *
 * Project metadata and user assignments are owned by the user module. Connected source
 * summaries are read through [ProjectSourceApi] so connector persistence stays behind
 * explicit module-facing APIs.
 */
@Service
class AdminProjectService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val assignmentRepository: ProjectUserAssignmentRepository,
    private val projectSourceApi: ProjectSourceApi,
) {
    /**
     * Returns all projects with source and assigned-user summaries.
     *
     * @return All projects visible to administrators.
     */
    @Transactional(readOnly = true)
    fun getAllProjects(): List<AdminProjectListResponse> {
        val projects = projectRepository.findAll()
        if (projects.isEmpty()) {
            return emptyList()
        }

        val assignmentsByProject = assignmentRepository
            .findAllByProjectIdIn(projects.map { it.id })
            .groupBy { it.project.id }

        return projects.map { project ->
            project.toAdminListResponse(
                sources = projectSourceApi.findSourcesByProjectId(project.id),
                assignments = assignmentsByProject[project.id].orEmpty(),
            )
        }
    }

    /**
     * Returns one project with detailed assigned-user information.
     *
     * @param id Project identifier.
     * @return The project detail response.
     * @throws ResponseStatusException When no project exists for [id].
     */
    @Transactional(readOnly = true)
    fun getProjectById(id: UUID): AdminProjectDetailResponse {
        val project = findProject(id)
        return project.toAdminDetailResponse(
            sources = projectSourceApi.findSourcesByProjectId(project.id),
            assignments = assignmentRepository.findAllByProjectId(project.id),
        )
    }

    /**
     * Creates a project with basic metadata.
     *
     * User assignments are intentionally handled by the project-user mapping endpoint.
     *
     * @param request Project creation payload.
     * @return The created project.
     * @throws ResponseStatusException When the name is blank or already in use.
     */
    @Transactional
    fun createProject(request: CreateAdminProjectRequest): AdminProjectDetailResponse {
        val name = validatedName(request.name)
        ensureProjectNameAvailable(name)

        val project = projectRepository.save(
            Project(
                name = name,
                description = request.description,
            ),
        )

        return project.toAdminDetailResponse(
            sources = emptyList(),
            assignments = emptyList(),
        )
    }

    /**
     * Partially updates project metadata.
     *
     * Omitted fields remain unchanged and user assignments are not modified.
     *
     * @param id Project identifier.
     * @param request Partial project update payload.
     * @return The updated project.
     * @throws ResponseStatusException When no project exists for [id] or the requested name is invalid.
     */
    @Transactional
    fun patchProject(id: UUID, request: PatchAdminProjectRequest): AdminProjectDetailResponse {
        val project = findProject(id)

        request.name?.let { requestedName ->
            val name = validatedName(requestedName)
            ensureProjectNameAvailable(name, project.id)
            project.name = name
        }
        request.description?.let { project.description = it }

        return project.toAdminDetailResponse(
            sources = projectSourceApi.findSourcesByProjectId(project.id),
            assignments = assignmentRepository.findAllByProjectId(project.id),
        )
    }

    /**
     * Deletes a project and its local user assignments.
     *
     * Connected-source records are owned by their connector modules and are not deleted here.
     *
     * @param id Project identifier.
     * @return Deletion confirmation DTO.
     * @throws ResponseStatusException When no project exists for [id].
     */
    @Transactional
    fun deleteProject(id: UUID): DeleteProjectResponse {
        val project = findProject(id)
        val assignments = assignmentRepository.findAllByProjectId(project.id)
        assignmentRepository.deleteAll(assignments)
        projectRepository.delete(project)

        return DeleteProjectResponse(id = id)
    }

    /**
     * Returns all users assigned to a project with project-specific roles.
     *
     * @param projectId Project identifier.
     * @return Assigned users for the project.
     * @throws ResponseStatusException When no project exists for [projectId].
     */
    @Transactional(readOnly = true)
    fun getProjectUsers(projectId: UUID): List<ProjectUserResponse> {
        findProject(projectId)
        return assignmentRepository
            .findAllByProjectId(projectId)
            .map { it.toProjectUserResponse() }
    }

    /**
     * Assigns one or more users to a project.
     *
     * Existing assignments are left unchanged, making repeated assignment requests idempotent.
     *
     * @param projectId Project identifier.
     * @param request User assignment payload.
     * @return The full assigned-user list after the operation.
     * @throws ResponseStatusException When the project or any requested user does not exist.
     */
    @Transactional
    fun assignUsers(projectId: UUID, request: AssignProjectUsersRequest): List<ProjectUserResponse> {
        val project = findProject(projectId)
        val users = userRepository.findAllById(request.userIds)
        val foundUserIds = users.map { it.id }.toSet()
        val missingUserIds = request.userIds - foundUserIds
        if (missingUserIds.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User(s) with id(s) $missingUserIds not found")
        }

        val existingAssignmentIds = assignmentRepository
            .findAllByProjectId(project.id)
            .map { it.user.id }
            .toSet()
        val newAssignments = users
            .filter { it.id !in existingAssignmentIds }
            .map { ProjectUserAssignment(user = it, project = project) }

        if (newAssignments.isNotEmpty()) {
            assignmentRepository.saveAll(newAssignments)
        }

        return assignmentRepository
            .findAllByProjectId(project.id)
            .map { it.toProjectUserResponse() }
    }

    /**
     * Removes one user assignment from a project.
     *
     * @param projectId Project identifier.
     * @param userId User identifier.
     * @throws ResponseStatusException When the project or assignment does not exist.
     */
    @Transactional
    fun removeUser(projectId: UUID, userId: UUID) {
        findProject(projectId)
        val assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User with id $userId is not assigned to project with id $projectId",
            )

        assignmentRepository.delete(assignment)
    }

    private fun findProject(id: UUID): Project {
        return projectRepository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project with id $id not found") }
    }

    private fun validatedName(name: String): String {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name must not be blank")
        }
        return trimmedName
    }

    private fun ensureProjectNameAvailable(name: String, currentProjectId: UUID? = null) {
        val existingProject = projectRepository.findByName(name)
        if (existingProject != null && existingProject.id != currentProjectId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Project with name '$name' already exists")
        }
    }
}

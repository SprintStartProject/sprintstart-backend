package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ProjectRoleService(
    private val projectRoleRepository: ProjectRoleRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getAllRoles(): List<ProjectRole> {
        return projectRoleRepository.findAll()
    }

    @Transactional
    fun createRole(request: CreateProjectRoleRequest): ProjectRole {
        val role = ProjectRole(
            name = request.name,
            description = request.description,
        )
        return projectRoleRepository.save(role)
    }

    @Transactional
    fun deleteRole(roleId: UUID) {
        if (!projectRoleRepository.existsById(roleId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found")
        }
        projectRoleRepository.deleteById(roleId)
    }

    @Transactional
    fun assignRoleToUser(userId: UUID, roleId: UUID) {
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found") }
        val role = projectRoleRepository.findById(roleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found") }

        user.projectRoles.add(role)
        userRepository.save(user)
    }

    @Transactional
    fun unassignRoleFromUser(userId: UUID, roleId: UUID) {
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found") }
        user.projectRoles.removeIf { it.id == roleId }
        userRepository.save(user)
    }
}

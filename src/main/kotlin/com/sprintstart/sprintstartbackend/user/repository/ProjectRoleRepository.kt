package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProjectRoleRepository : JpaRepository<ProjectRole, UUID> {
    fun findByName(name: String): ProjectRole?
}

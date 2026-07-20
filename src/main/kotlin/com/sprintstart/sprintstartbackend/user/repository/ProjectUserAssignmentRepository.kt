package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignmentId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProjectUserAssignmentRepository : JpaRepository<ProjectUserAssignment, ProjectUserAssignmentId> {
    @Query(
        """
            SELECT DISTINCT a
            FROM ProjectUserAssignment a
            JOIN FETCH a.user u
            LEFT JOIN FETCH u.roles
            LEFT JOIN FETCH a.projectRoles
            WHERE a.project.id = :projectId
        """,
    )
    fun findAllByProjectId(@Param("projectId") projectId: UUID): List<ProjectUserAssignment>

    @Query(
        """
            SELECT DISTINCT a
            FROM ProjectUserAssignment a
            JOIN FETCH a.user u
            LEFT JOIN FETCH u.roles
            LEFT JOIN FETCH a.projectRoles
            WHERE a.project.id IN :projectIds
        """,
    )
    fun findAllByProjectIdIn(@Param("projectIds") projectIds: Collection<UUID>): List<ProjectUserAssignment>

    @Query(
        """
            SELECT DISTINCT a
            FROM ProjectUserAssignment a
            JOIN FETCH a.user u
            LEFT JOIN FETCH u.roles
            LEFT JOIN FETCH a.projectRoles
            WHERE a.project.id = :projectId AND a.user.id = :userId
        """,
    )
    fun findByProjectIdAndUserId(
        @Param("projectId") projectId: UUID,
        @Param("userId") userId: UUID,
    ): ProjectUserAssignment?
}

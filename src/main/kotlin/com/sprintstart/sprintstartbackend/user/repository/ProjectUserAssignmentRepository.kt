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

    /**
     * Every assignment currently holding a role, so deleting the role can let go of it first.
     *
     * `V4` does declare `ON DELETE CASCADE` on this table's role FK, so a real Postgres schema would
     * clean up on its own. Two reasons not to lean on that: the entity mapping declares no
     * cascade, so schema-built-from-entities contexts (the whole test suite) would hit a
     * constraint violation instead; and a cascade fires behind Hibernate's back, leaving already
     * loaded assignments holding a role the database has dropped. Clearing it explicitly makes the
     * behaviour identical everywhere and visible in code.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM ProjectUserAssignment a
            JOIN FETCH a.user u
            LEFT JOIN FETCH a.projectRoles r
            WHERE :roleId IN (SELECT r2.id FROM a.projectRoles r2)
        """,
    )
    fun findAllHoldingRole(@Param("roleId") roleId: UUID): List<ProjectUserAssignment>
}

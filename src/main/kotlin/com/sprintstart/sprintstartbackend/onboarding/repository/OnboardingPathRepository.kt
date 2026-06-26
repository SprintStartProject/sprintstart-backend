package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OnboardingPathRepository : JpaRepository<OnboardingPath, UUID> {
    fun findOnboardingPathByUserId(userId: UUID): Optional<OnboardingPath>

    fun deleteByUserId(userId: UUID)

    fun existsByUserId(userId: UUID): Boolean

    fun findByUserId(userId: UUID): Optional<OnboardingPath>

    @org.springframework.data.jpa.repository.Query(
        value = """
        SELECT CAST(u.id AS uuid)
        FROM sprintstart_users u
        LEFT JOIN onboarding_paths p ON p.user_id = u.id
        WHERE (:search IS NULL OR LOWER(u.firstname) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.lastname) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (COALESCE(:projectIds) IS NULL OR u.project_id IN (:projectIds))
          AND (COALESCE(:roleIds) IS NULL OR EXISTS (SELECT 1 FROM user_project_roles ur WHERE ur.user_id = u.id AND ur.role_id IN (:roleIds)))
        ORDER BY 
          CASE WHEN :sortBy = 'LONGEST_STEP' THEN 
            (SELECT s.started_at FROM onboarding_phases ph JOIN onboarding_steps s ON s.phase_id = ph.id WHERE ph.path_id = p.id AND s.status = 'WAITING' ORDER BY ph.position ASC, s.position ASC LIMIT 1)
          END ASC NULLS LAST,
          CASE WHEN :sortBy = 'SHORTEST_STEP' THEN 
            (SELECT s.started_at FROM onboarding_phases ph JOIN onboarding_steps s ON s.phase_id = ph.id WHERE ph.path_id = p.id AND s.status = 'WAITING' ORDER BY ph.position ASC, s.position ASC LIMIT 1)
          END DESC NULLS LAST,
          CASE WHEN :sortBy = 'HIGHEST_PROGRESS' THEN 
            COALESCE(
               (SELECT COUNT(s.id) FILTER (WHERE s.status IN ('FINISHED', 'SKIPPED'))::double precision / NULLIF(COUNT(s.id), 0)::double precision
                FROM onboarding_phases ph JOIN onboarding_steps s ON s.phase_id = ph.id WHERE ph.path_id = p.id), 
               0.0
            )
          END DESC,
          CASE WHEN :sortBy = 'LOWEST_PROGRESS' THEN 
            COALESCE(
               (SELECT COUNT(s.id) FILTER (WHERE s.status IN ('FINISHED', 'SKIPPED'))::double precision / NULLIF(COUNT(s.id), 0)::double precision
                FROM onboarding_phases ph JOIN onboarding_steps s ON s.phase_id = ph.id WHERE ph.path_id = p.id), 
               0.0
            )
          END ASC,
          u.lastname ASC, u.firstname ASC
    """,
        countQuery = """
        SELECT count(u.id)
        FROM sprintstart_users u
        WHERE (:search IS NULL OR LOWER(u.firstname) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.lastname) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (COALESCE(:projectIds) IS NULL OR u.project_id IN (:projectIds))
          AND (COALESCE(:roleIds) IS NULL OR EXISTS (SELECT 1 FROM user_project_roles ur WHERE ur.user_id = u.id AND ur.role_id IN (:roleIds)))
    """,
        nativeQuery = true,
    )
    fun findUserIdsForOverview(
        @org.springframework.data.repository.query.Param("search") search: String?,
        @org.springframework.data.repository.query.Param("roleIds") roleIds: List<UUID>?,
        @org.springframework.data.repository.query.Param("projectIds") projectIds: List<UUID>?,
        @org.springframework.data.repository.query.Param("sortBy") sortBy: String,
        pageable: org.springframework.data.domain.Pageable,
    ): org.springframework.data.domain.Page<UUID>
}

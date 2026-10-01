package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.ProjectAnalysisRun
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Persistence access for the finished project analyses of a project.
 */
interface ProjectAnalysisRunRepository : JpaRepository<ProjectAnalysisRun, UUID> {
    /** A project's runs, newest first, as many as [pageable] asks for. */
    fun findByProjectIdOrderByCreatedAtDesc(projectId: UUID, pageable: Pageable): List<ProjectAnalysisRun>

    /**
     * Deletes all but the newest [keep] runs of a project.
     *
     * A run is stored on every press of the button, so without a bound one busy project would grow
     * its history without end; nothing reads further back than the newest few.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            DELETE FROM project_analysis_runs
            WHERE project_id = :projectId
              AND id NOT IN (
                  SELECT id FROM project_analysis_runs
                  WHERE project_id = :projectId
                  ORDER BY created_at DESC
                  LIMIT :keep
              )
        """,
        nativeQuery = true,
    )
    fun deleteAllButNewest(
        @Param("projectId") projectId: UUID,
        @Param("keep") keep: Int,
    )
}

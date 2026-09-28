package com.sprintstart.sprintstartbackend.connectors.jira.repository

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface JiraInstanceRepository : JpaRepository<JiraInstance, String> {
    @Query(
        """
        SELECT i FROM JiraInstance i
        JOIN i.projectIds p
        WHERE p = :projectId
        """,
    )
    fun findByProjectId(projectId: UUID): List<JiraInstance>

    /**
     * Loads a Jira instance with its lazy [JiraInstance.jiraProjectKeys] and
     * [JiraInstance.projectIds] element collections eagerly initialized.
     *
     * The update/check ingestion runs execute on a background coroutine with no active
     * Hibernate session, so a plain [findById] returns a detached instance whose lazy
     * collections throw `LazyInitializationException` when read. Fetching them here (both are
     * sets, so a single fetch join is safe) means the returned entity is safe to use after the
     * session closes.
     */
    @EntityGraph(attributePaths = ["jiraProjectKeys", "projectIds"])
    @Query("SELECT i FROM JiraInstance i WHERE i.instanceUrl = :instanceUrl")
    fun findByInstanceUrlWithCollections(instanceUrl: String): JiraInstance?
}

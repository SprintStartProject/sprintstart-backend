package com.sprintstart.sprintstartbackend.connectors.jira.repository

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstanceConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
internal interface JiraInstanceConfigRepository : JpaRepository<JiraInstanceConfig, String> {
    fun findAllByNextSyncAtIsLessThanEqual(due: Instant): List<JiraInstanceConfig>
}

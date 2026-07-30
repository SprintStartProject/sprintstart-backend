package com.sprintstart.sprintstartbackend.connectors.jira.repository

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraIssue
import org.springframework.data.jpa.repository.JpaRepository

internal interface JiraIssueRepository : JpaRepository<JiraIssue, String>

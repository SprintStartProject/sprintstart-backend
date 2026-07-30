package com.sprintstart.sprintstartbackend.connectors.jira.model.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "jira_issues")
internal class JiraIssue(
    @Id
    var id: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id")
    var instance: JiraInstance,
)

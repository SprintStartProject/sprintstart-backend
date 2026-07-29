package com.sprintstart.sprintstartbackend.ingestion.listener.jira

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingCompleteEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.JiraArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.JiraArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class JiraIssueListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
    private val ingestionRunService: IngestionRunService,
    private val jiraArtifactProviderService: JiraArtifactProviderService,
    private val mapper: JiraArtifactMapper,
) {
    @EventListener
    fun on(event: JiraResourceFetchingStartedEvent) {
        ingestionRunLifeCycleService.updateRunStatus(
            transactionId = event.transactionId,
            status = IngestionRunStatus.RUNNING,
        )
    }

    @EventListener
    fun on(event: JiraIssueFetchedEvent) {
        jiraArtifactProviderService.persistArtifact(mapper.toCommand(event))
    }

    @EventListener
    fun on(event: JiraIssueDeletedEvent) {
        jiraArtifactProviderService.deleteFileArtifact(event)
    }

    @EventListener
    fun on(event: JiraResourceFetchingFailedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.JIRA,
            status = IngestionRunStatus.FAILED,
            failureReason = event.reason,
        )
    }

    @EventListener
    fun on(event: JiraResourceFetchingCompleteEvent) {
        val ingestionRun = ingestionRunService.findRunByTransactionId(event.transactionId)
            ?: return

        ingestionRunLifeCycleService.finishRun(ingestionRun)
    }
}

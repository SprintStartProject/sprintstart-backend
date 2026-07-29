package com.sprintstart.sprintstartbackend.ingestion.listener.jira

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial.JiraInstanceConnectionCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial.JiraInstanceConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial.JiraInstanceConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class JiraInstanceConnectionListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
    private val ingestionRunService: IngestionRunService,
) {
    @EventListener
    fun on(event: JiraInstanceConnectionInitiatedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.JIRA,
            status = IngestionRunStatus.CONNECTED,
        )
    }

    @EventListener
    fun on(event: JiraInstanceConnectionCompletedEvent) {
        val ingestionRun = ingestionRunService.findRunByTransactionId(event.transactionId)
            ?: return

        ingestionRunLifeCycleService.finishRun(ingestionRun)
    }

    @EventListener
    fun on(event: JiraInstanceConnectionInitiationFailedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.JIRA,
            status = IngestionRunStatus.FAILED,
            failureReason = event.reason,
        )
    }
}

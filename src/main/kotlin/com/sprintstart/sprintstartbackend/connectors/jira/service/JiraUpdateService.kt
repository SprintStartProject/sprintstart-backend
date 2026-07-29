package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.update.JiraInstanceUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.update.JiraInstanceUpdateStartedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.UpdateJiraInstanceResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceRepository
import com.sprintstart.sprintstartbackend.connectors.jira.service.internal.JiraIssueService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class JiraUpdateService(
    private val instanceRepository: JiraInstanceRepository,
    private val issueService: JiraIssueService,
    private val eventPublisher: ApplicationEventPublisher,
    private val applicationScope: CoroutineScope,
) {
    @Tracked("Updating all Jira instances")
    fun updateAllJiraInstances(): List<UpdateJiraInstanceResponse> =
        instanceRepository.findAll().map { instance ->
            updateJiraInstance(instance.instanceUrl, performUpdate = true)
        }

    @Tracked("Updating a Jira instance")
    fun updateJiraInstance(instanceUrl: String, performUpdate: Boolean): UpdateJiraInstanceResponse {
        val transactionId = UUID.randomUUID()

        eventPublisher.publishEvent(JiraInstanceUpdateStartedEvent(transactionId, instanceUrl))

        val instance = try {
            instanceRepository.findById(instanceUrl).orElseThrow {
                JiraInstanceNotConnectedException(instanceUrl)
            }
        } catch (e: Exception) {
            eventPublisher.publishEvent(
                JiraInstanceUpdateFailedEvent(transactionId, instanceUrl, e.message ?: "Unknown error"),
            )
            throw e
        }

        eventPublisher.publishEvent(JiraResourceFetchingStartedEvent(transactionId))

        applicationScope.launch {
            if (performUpdate) {
                issueService.updateInstance(instance, transactionId)
            } else {
                issueService.checkInstanceForUpdates(instance, transactionId)
            }
        }

        return UpdateJiraInstanceResponse(transactionId)
    }
}

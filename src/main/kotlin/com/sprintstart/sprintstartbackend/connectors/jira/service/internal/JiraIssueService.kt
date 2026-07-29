package com.sprintstart.sprintstartbackend.connectors.jira.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.jira.JiraClient
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingCompleteEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredentialsId
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraIssue
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraCredentialsRepository
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceRepository
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraIssueRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
internal class JiraIssueService(
    private val jiraClient: JiraClient,
    private val instanceRepository: JiraInstanceRepository,
    private val credentialsRepository: JiraCredentialsRepository,
    private val issueRepository: JiraIssueRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val jqlDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * Fetches and ingests all issues of multiple Jira projects for a specific Jira instance.
     *
     * @param instance The Jira instance containing the details of the projects whose issues need to be fetched.
     * @param credentialsId The credentials identifier used to authenticate with the Jira instance.
     * @param transactionId The unique transaction identifier associated with this operation.
     */
    @Tracked("Fetching & ingesting all jira instance's issues of a list of projects")
    suspend fun searchAndIngestAllIssuesOfProjects(
        instance: JiraInstance,
        credentialsId: JiraCredentialsId,
        transactionId: UUID,
    ) {
        instance.jiraProjectKeys.forEach {
            searchAndIngestAllIssuesOfProject(instance, credentialsId, it, transactionId)
        }
    }

    /**
     * Fetches all issues of a specified project from a given Jira instance and ingests them into the system.
     *
     * @param instance The Jira instance containing the details of the project whose issues need to be fetched.
     * @param credentialsId The identifier for the Jira credentials to use for authentication.
     * @param projectKey The unique key of the Jira project whose issues are to be fetched.
     * @param transactionId The unique identifier for the transaction context in which this operation is performed.
     */
    @Tracked("Fetching & ingesting all jira instance's issues of a single project")
    suspend fun searchAndIngestAllIssuesOfProject(
        instance: JiraInstance,
        credentialsId: JiraCredentialsId,
        projectKey: String,
        transactionId: UUID,
    ) {
        val credentials = fetchCredentials(credentialsId, transactionId)
        val issues = fetchIssues(instance.instanceUrl, credentials, "project=\"$projectKey\"", transactionId) ?: return

        if (issues.isEmpty()) {
            return
        }

        processAndIngestIssues(instance, issues, transactionId)
    }

    /**
     * Checks a Jira instance for updates and modifies its connection status accordingly without applying the updates.
     *
     * If no new or updated issues are found, the instance status is updated to UP_TO_DATE.
     * Otherwise, the instance status is updated to OUT_OF_DATE.
     *
     * @param instance The JiraInstance object to be checked for updates.
     * @param transactionId A unique identifier for the transaction associated with the update-check process.
     */
    @Tracked("Fetching a Jira instance for updates without applying the updates")
    suspend fun checkInstanceForUpdates(instance: JiraInstance, transactionId: UUID) = withContext(Dispatchers.IO) {
        instance.status = ConnectionState.UPDATING
        instanceRepository.save(instance)

        val newAndUpdatedIssues = fetchNewAndUpdatedIssues(instance.instanceUrl, transactionId)

        if (newAndUpdatedIssues.isEmpty()) {
            instance.status = ConnectionState.UP_TO_DATE
            instanceRepository.save(instance)
            return@withContext
        }

        instance.status = ConnectionState.OUT_OF_DATE
        instanceRepository.save(instance)
    }

    /**
     * Updates the specified Jira instance by fetching new and updated issues and processing them.
     *
     * This method changes the status of the instance to UPDATING during the process of fetching
     * and processing updates. Once the updates are completed, the instance status is set to UP_TO_DATE.
     *
     * @param instance The Jira instance to be updated.
     * @param transactionId A unique identifier for the ongoing transaction.
     */
    @Tracked("Fetching & applying updates to a Jira instance")
    suspend fun updateInstance(instance: JiraInstance, transactionId: UUID) = withContext(Dispatchers.IO) {
        instance.status = ConnectionState.UPDATING
        instanceRepository.save(instance)

        val newAndUpdatedIssues = fetchNewAndUpdatedIssues(instance.instanceUrl, transactionId)

        if (newAndUpdatedIssues.isNotEmpty()) {
            processAndIngestIssues(instance, newAndUpdatedIssues, transactionId)
        }

        instance.status = ConnectionState.UP_TO_DATE
        instanceRepository.save(instance)
    }

    /**
     * Fetches a list of new and updated issues from a Jira instance based on the given parameters.
     *
     * @param instanceUrl The URL of the Jira instance to fetch issues from.
     * @param transactionId A unique identifier representing the current transaction.
     * @return A list of `JiraIssueResponse` objects containing information about the new and updated issues.
     */
    private suspend fun fetchNewAndUpdatedIssues(
        instanceUrl: String,
        transactionId: UUID,
    ): List<JiraIssueResponse> {
        val instance = instanceRepository.findById(instanceUrl).orElse(null) ?: return emptyList()
        val credentialsId = JiraCredentialsId(instance.updateCredentialUserEmail, instance.updateCredentialName)
        val credentials = fetchCredentials(credentialsId, transactionId)

        val jql = buildString {
            append("project in (${instance.jiraProjectKeys.joinToString(", ") { "\"$it\"" }}) ")
            append("AND (created >= \"${formatJqlDate(instance.lastUpdate)}\" ")
            append("OR updated >= \"${formatJqlDate(instance.lastUpdate)}\") ")
        }

        return fetchIssues(instanceUrl, credentials, jql, transactionId) ?: emptyList()
    }

    /**
     * Fetches a list of Jira issues based on the provided JQL query.
     *
     * @param instanceUrl The URL of the Jira instance to connect to.
     * @param credentials The credentials required to authenticate with the Jira instance.
     * @param jql The Jira Query Language (JQL) string used to filter issues.
     * @param transactionId The unique transaction identifier for tracking the operation.
     * @return A list of JiraIssueResponse objects representing the fetched issues, or null if an error occurs.
     * @throws Exception If an error occurs during the fetching of Jira issues.
     */
    private suspend fun fetchIssues(
        instanceUrl: String,
        credentials: JiraCredential,
        jql: String,
        transactionId: UUID,
    ): List<JiraIssueResponse>? = try {
        jiraClient.searchIssues(instanceUrl, credentials, jql)
    } catch (e: Exception) {
        eventPublisher.publishEvent(JiraResourceFetchingFailedEvent(transactionId, e.message ?: "Unknown error"))
        throw e
    }

    /**
     * Retrieves the Jira credentials associated with the given credentials ID. If the credentials
     * are not found, a `JiraResourceFetchingFailedEvent` is published and a `JiraCredentialNotFoundException`
     * is thrown.
     *
     * @param credentialsId The ID of the credentials to fetch.
     * @param transactionId The unique transaction identifier associated with the operation.
     * @return The JiraCredential object associated with the provided credentials ID.
     * @throws JiraCredentialNotFoundException If the credentials are invalid or not found.
     */
    private fun fetchCredentials(
        credentialsId: JiraCredentialsId,
        transactionId: UUID,
    ): JiraCredential = credentialsRepository.findById(credentialsId).orElse(null) ?: run {
        eventPublisher.publishEvent(JiraResourceFetchingFailedEvent(transactionId, "Invalid credentials"))
        throw JiraCredentialNotFoundException(credentialsId.userEmail, credentialsId.name)
    }

    /**
     * Processes and ingests a list of Jira issues by upserting the tracking entity and
     * publishing corresponding events for artifact creation or update.
     *
     * Unlike the previous delete-and-recreate approach, this method simply saves the
     * `JiraIssue` entity (which acts as an upsert by its @Id) and publishes a
     * `JiraIssueFetchedEvent` with a proper per-issue source URL and the instance's
     * project associations. The artifact provider service handles update-in-place logic.
     *
     * @param instance The Jira instance to which the issues belong.
     * @param issues The list of Jira issues to be processed and ingested.
     * @param transactionId The unique identifier for the transaction during which the issues are processed.
     */
    private fun processAndIngestIssues(
        instance: JiraInstance,
        issues: List<JiraIssueResponse>,
        transactionId: UUID,
    ) {
        for (issue in issues) {
            val issueEntity = JiraIssue(issue.id, instance)
            issueRepository.save(issueEntity)

            val sourceUrl = "${instance.instanceUrl}/browse/${issue.key}"

            eventPublisher.publishEvent(
                JiraIssueFetchedEvent(
                    transactionId = transactionId,
                    instanceId = instance.instanceUrl,
                    instanceUrl = instance.instanceUrl,
                    sourceUrl = sourceUrl,
                    issue = issue,
                    projectIds = instance.projectIds,
                ),
            )
        }

        eventPublisher.publishEvent(JiraResourceFetchingCompleteEvent(transactionId))
    }

    /**
     * Formats the given `Instant` into a string representation suitable for JQL (Jira Query Language) queries.
     *
     * The formatted date is in the "yyyy-MM-dd HH:mm" pattern and is adjusted to UTC time zone.
     *
     * @param instant The `Instant` representing the date and time to be formatted.
     * @return A string representation of the given `Instant` in "yyyy-MM-dd HH:mm" format in UTC time zone.
     */
    private fun formatJqlDate(instant: Instant): String =
        jqlDateFormatter.format(instant.atZone(ZoneOffset.UTC))
}

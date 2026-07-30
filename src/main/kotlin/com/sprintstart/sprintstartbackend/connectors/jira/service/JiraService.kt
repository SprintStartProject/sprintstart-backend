package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.jira.JiraClient
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial.JiraInstanceConnectionCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial.JiraInstanceConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial.JiraInstanceConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.ConnectJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraInstanceDto
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.toDto
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredentialsId
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstanceConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceUnavailableException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraCredentialsRepository
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceConfigRepository
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceRepository
import com.sprintstart.sprintstartbackend.connectors.jira.service.internal.JiraIssueService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
internal class JiraService(
    private val credentialsRepository: JiraCredentialsRepository,
    private val instanceRepository: JiraInstanceRepository,
    private val configRepository: JiraInstanceConfigRepository,
    private val jiraClient: JiraClient,
    private val applicationScope: CoroutineScope,
    private val jiraIssueService: JiraIssueService,
    private val eventPublisher: ApplicationEventPublisher,
    private val jiraInstanceConfigService: JiraInstanceConfigService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Retrieves all connected Jira instances as a list of DTOs.
     *
     * @return a list of JiraInstanceDto representing all connected Jira instances.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all connected Jira instances")
    fun getInstances(): List<JiraInstanceDto> = instanceRepository.findAll().map { it.toDto() }

    /**
     * Retrieves a list of Jira instances associated with the specified project.
     *
     * @param projectId the unique identifier of the project for which connected Jira instances should be retrieved.
     * @return a list of JiraInstanceDto representing the connected Jira instances associated with the given project.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving connected Jira instances for project")
    fun getInstances(projectId: UUID): List<JiraInstanceDto> =
        instanceRepository.findByProjectId(projectId).map { it.toDto() }

    /**
     * Updates the status of a Jira instance identified by its ID.
     *
     * @param instanceId The unique identifier of the Jira instance to be updated.
     * @param newStatus The new status to set for the Jira instance's sourceEnabled property.
     */
    @Transactional
    @Tracked("Patching Jira instance status")
    fun patchInstance(instanceId: String, newStatus: Boolean) {
        val instance = instanceRepository.findById(instanceId).orElseThrow {
            JiraInstanceNotConnectedException(instanceId)
        }
        instance.sourceEnabled = newStatus
    }

    /**
     * Connects a Jira Cloud instance if it is not already connected. If an instance is found for the provided
     * URL, it ensures that the specified project is added to the instance. Otherwise, it creates and connects
     * a new Jira Cloud instance.
     *
     * @param request The request containing the details required to connect the Jira Cloud instance,
     * such as the instance URL, display name, and project ID.
     * @return A UUID representing the transaction ID of the connection process.
     */
    @Transactional
    @Tracked("Connecting Jira Cloud instance if not already connected")
    suspend fun connectInstanceIfNeeded(request: ConnectJiraInstanceRequest): UUID {
        val transactionId = UUID.randomUUID()
        eventPublisher.publishEvent(JiraInstanceConnectionInitiatedEvent(transactionId, request.displayName))

        val instance = instanceRepository.findById(request.url)

        if (instance.isPresent) {
            logger.info("Jira instance already connected: ${request.url}")
            addProjectIfMissing(instance.get(), request.projectId)
            eventPublisher.publishEvent(JiraInstanceConnectionCompletedEvent(transactionId))
            return transactionId
        }

        return connectNewInstance(request, transactionId)
    }

    /**
     * Adds a project ID to the given Jira instance if it is not already present.
     *
     * @param instance The JiraInstance to which the project ID should be added.
     * @param projectId The UUID of the project to add if it is missing.
     */
    private fun addProjectIfMissing(instance: JiraInstance, projectId: UUID) {
        if (!instance.projectIds.contains(projectId)) {
            instance.projectIds.add(projectId)
            instanceRepository.save(instance)
        }
    }

    /**
     * Establishes a connection to a new Jira instance by validating the provided details,
     * fetching project information, and saving the instance configuration.
     * If the connection fails during any step, appropriate events are published, and exceptions are thrown.
     *
     * @param request Represents the connection request including Jira instance details
     *                such as URL, display name, user email, and token name.
     * @param transactionId A unique identifier for tracking the current transaction.
     * @return The transaction ID associated with the connection process.
     * @throws JiraCredentialNotFoundException If the provided credentials are not found.
     * @throws JiraInstanceUnavailableException If the specified Jira instance is not reachable.
     * @throws Exception If an error occurs during retrieval of project details or subsequent steps.
     */
    private suspend fun connectNewInstance(
        request: ConnectJiraInstanceRequest,
        transactionId: UUID,
    ): UUID {
        val credentials = credentialsRepository
            .findById(JiraCredentialsId(request.userEmail, request.tokenName))
            .orElseThrow {
                eventPublisher.publishEvent(
                    JiraInstanceConnectionInitiationFailedEvent(transactionId, "Invalid credentials"),
                )
                JiraCredentialNotFoundException(request.userEmail, request.tokenName)
            }

        if (!jiraClient.checkInstanceCapabilities(request.url)) {
            eventPublisher.publishEvent(
                JiraInstanceConnectionInitiationFailedEvent(transactionId, "Instance is not available"),
            )
            throw JiraInstanceUnavailableException(request.url)
        }

        val projects = try {
            jiraClient.searchProjects(request.url, credentials)
        } catch (e: Exception) {
            eventPublisher.publishEvent(
                JiraInstanceConnectionInitiationFailedEvent(transactionId, e.message ?: "Unknown error"),
            )
            throw e
        }

        val projectKeys = projects.map { it.key }
        val instance = JiraInstance(
            instanceUrl = request.url,
            displayName = request.displayName,
            lastUpdate = Instant.now(),
            projectIds = mutableSetOf(request.projectId),
            jiraProjectKeys = projectKeys.toMutableSet(),
            status = ConnectionState.UP_TO_DATE,
            updateCredentialName = request.tokenName,
            updateCredentialUserEmail = request.userEmail,
        )
        val config = JiraInstanceConfig(instance = instance)
        config.nextSyncAt = jiraInstanceConfigService.calculateNextSyncAt(config.schedule)

        instanceRepository.save(instance)
        configRepository.save(config)

        eventPublisher.publishEvent(JiraInstanceConnectionCompletedEvent(transactionId))

        applicationScope.launch {
            jiraIssueService.searchAndIngestAllIssuesOfProjects(
                instance,
                JiraCredentialsId(request.userEmail, request.tokenName),
                transactionId,
            )
        }

        return transactionId
    }
}

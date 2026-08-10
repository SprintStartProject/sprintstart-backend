package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraSourceInstanceDto
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Repository-backed implementation of the Jira module API exposed to other modules.
 *
 * The counterpart to
 * [com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryApiService] for
 * Jira.
 */
@Service
internal class JiraInstanceApiService(
    private val instanceRepository: JiraInstanceRepository,
) : JiraInstanceApi {
    @Transactional(readOnly = true)
    override fun getSourceInstances(projectId: UUID?): List<JiraSourceInstanceDto> {
        val instances =
            if (projectId != null) {
                instanceRepository.findByProjectId(projectId)
            } else {
                instanceRepository.findAll()
            }

        return instances
            .sortedWith(compareBy({ it.displayName }, { it.instanceUrl }))
            .map { it.toSourceInstanceDto() }
    }

    @Transactional(readOnly = true)
    override fun getInstanceRefsByProject(projectId: UUID): List<String> =
        instanceRepository.findByProjectId(projectId).map { it.instanceUrl }

    @Transactional
    override fun removeProjectFromAllInstances(projectId: UUID) {
        val instances = instanceRepository.findByProjectId(projectId)
        instances.forEach { it.projectIds.remove(projectId) }
        instanceRepository.saveAll(instances)
    }

    private fun JiraInstance.toSourceInstanceDto(): JiraSourceInstanceDto =
        JiraSourceInstanceDto(
            instanceUrl = instanceUrl,
            displayName = displayName,
            status = toSourceStatus(),
            enabled = sourceEnabled,
            lastUpdate = lastUpdate,
            jiraProjectKeys = jiraProjectKeys.toSet(),
        )
}

/**
 * Maps a Jira instance to the stable source-status vocabulary shared by the connector overview and
 * the ingestion status APIs.
 *
 * A disabled source always reports `DISABLED`, regardless of its underlying connection state, so a
 * paused instance is not shown as actively connected. Mirrors the GitHub connector's
 * `toSourceStatus`, keeping the mapping owned by the Jira module.
 */
internal fun JiraInstance.toSourceStatus(): String {
    if (!sourceEnabled) {
        return "DISABLED"
    }

    return when (status) {
        ConnectionState.UP_TO_DATE -> "CONNECTED"
        ConnectionState.UPDATING -> "UPDATING"
        ConnectionState.OUT_OF_DATE -> "OUT_OF_DATE"
        ConnectionState.FAILED -> "FAILED"
    }
}

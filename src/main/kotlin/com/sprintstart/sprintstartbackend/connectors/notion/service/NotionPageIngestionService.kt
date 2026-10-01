package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.NotionBlockParser
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionFailure
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionFailureStage
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionOutcome
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionResult
import com.sprintstart.sprintstartbackend.ingestion.external.NotionArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/** Ingests one connected Notion page as one canonical PAGE artifact. */
@Service
internal class NotionPageIngestionService(
    private val notionClient: NotionClient,
    private val parser: NotionBlockParser,
    private val credentialPersistenceService: NotionCredentialPersistenceService,
    private val connectionPersistenceService: NotionPageConnectionPersistenceService,
    private val artifactMapper: NotionPageArtifactMapper,
    private val ingestionApi: NotionArtifactIngestionApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun ingest(projectId: UUID, connectionId: UUID): NotionIngestionResult {
        val runId = UUID.randomUUID()
        val connection = withContext(Dispatchers.IO) {
            connectionPersistenceService.requireConnection(projectId, connectionId)
        }
        if (!connection.sourceEnabled) {
            return failedResult(runId, connectionId, NotionIngestionFailureStage.FETCHING, SOURCE_DISABLED_MESSAGE)
        }

        ingestionApi.startRun(runId, connection.id, connection.pageUrl)
        return try {
            ingestStartedRun(runId, projectId, connection)
        } catch (exception: CancellationException) {
            markFailedAndPropagate(runId, exception)
        } catch (exception: InterruptedException) {
            markFailedAndPropagate(runId, exception)
        }
    }

    private suspend fun ingestStartedRun(
        runId: UUID,
        projectId: UUID,
        connection: NotionPageConnection,
    ): NotionIngestionResult {
        return try {
            val token = executeStep(NotionIngestionFailureStage.FETCHING, FETCHING_FAILURE_MESSAGE) {
                withContext(Dispatchers.IO) {
                    credentialPersistenceService.requireToken(
                        authId = connection.credentialAuthId,
                        name = connection.credentialName,
                    )
                }
            }
            val page = executeStep(NotionIngestionFailureStage.FETCHING, FETCHING_FAILURE_MESSAGE) {
                notionClient.getPage(token, connection.pageId)
            }
            if (page.inTrash || page.parent.type in DATA_SOURCE_PARENT_TYPES) {
                return failRun(
                    runId,
                    connection.id,
                    NotionIngestionFailureStage.FETCHING,
                    UNSUPPORTED_PAGE_MESSAGE,
                )
            }

            val lastEditedTime = executeStep(NotionIngestionFailureStage.FETCHING, INVALID_PAGE_MESSAGE) {
                Instant.parse(page.lastEditedTime)
            }
            if (connection.contentHash != null && connection.lastEditedTime == lastEditedTime) {
                return finishUnchanged(runId, projectId, connection, page, lastEditedTime)
            }

            val blockTree = executeStep(NotionIngestionFailureStage.FETCHING, FETCHING_FAILURE_MESSAGE) {
                notionClient.getBlockTree(token = token, blockId = page.id)
            }
            val parsed = executeStep(NotionIngestionFailureStage.PARSING, PARSING_FAILURE_MESSAGE) {
                parser.parse(blockTree)
            }
            val command = executeStep(NotionIngestionFailureStage.PARSING, MAPPING_FAILURE_MESSAGE) {
                artifactMapper.toCommand(connection, page, parsed)
            }
            val writeResult = executeStep(NotionIngestionFailureStage.PERSISTENCE, PERSISTENCE_FAILURE_MESSAGE) {
                ingestionApi.persistPage(runId, projectId, command)
            }

            executeStep(NotionIngestionFailureStage.PERSISTENCE, SYNC_STATE_FAILURE_MESSAGE) {
                connectionPersistenceService.recordSuccessfulSync(
                    projectId = projectId,
                    connectionId = connection.id,
                    pageTitle = command.title,
                    pageUrl = command.sourceUrl,
                    lastEditedTime = command.lastEditedTime,
                    contentHash = writeResult.contentHash,
                )
                ingestionApi.finishRun(runId, successfulItemCount = 1)
            }

            NotionIngestionResult(
                runId = runId,
                connectionId = connection.id,
                outcome = writeResult.outcome.toIngestionOutcome(),
            )
        } catch (exception: NotionIngestionStepException) {
            failRun(
                runId,
                connection.id,
                exception.stage,
                exception.failureMessage,
                exception.stepCause,
            )
        }
    }

    private suspend fun <T> executeStep(
        stage: NotionIngestionFailureStage,
        failureMessage: String,
        operation: suspend () -> T,
    ): T {
        return try {
            operation()
        } catch (exception: CancellationException) {
            propagate(exception)
        } catch (exception: InterruptedException) {
            propagate(exception)
        } catch (exception: RuntimeException) {
            failStep(stage, failureMessage, exception)
        }
    }

    private fun propagate(exception: Exception): Nothing {
        throw exception
    }

    private fun failStep(
        stage: NotionIngestionFailureStage,
        failureMessage: String,
        cause: RuntimeException,
    ): Nothing {
        throw NotionIngestionStepException(stage, failureMessage, cause)
    }

    private fun finishUnchanged(
        runId: UUID,
        projectId: UUID,
        connection: NotionPageConnection,
        page: NotionApiPageResponse,
        lastEditedTime: Instant,
    ): NotionIngestionResult {
        return try {
            connectionPersistenceService.recordSuccessfulSync(
                projectId = projectId,
                connectionId = connection.id,
                pageTitle = page.titleOr(connection.pageTitle),
                pageUrl = page.url,
                lastEditedTime = lastEditedTime,
                contentHash = requireNotNull(connection.contentHash),
            )
            ingestionApi.finishRun(runId, successfulItemCount = 1)
            NotionIngestionResult(runId, connection.id, NotionIngestionOutcome.UNCHANGED)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: RuntimeException) {
            failRun(
                runId,
                connection.id,
                NotionIngestionFailureStage.PERSISTENCE,
                SYNC_STATE_FAILURE_MESSAGE,
                exception,
            )
        }
    }

    private fun NotionArtifactWriteOutcome.toIngestionOutcome(): NotionIngestionOutcome {
        return when (this) {
            NotionArtifactWriteOutcome.CREATED -> NotionIngestionOutcome.CREATED
            NotionArtifactWriteOutcome.UPDATED -> NotionIngestionOutcome.UPDATED
            NotionArtifactWriteOutcome.UNCHANGED -> NotionIngestionOutcome.UNCHANGED
        }
    }

    private fun failRun(
        runId: UUID,
        connectionId: UUID,
        stage: NotionIngestionFailureStage,
        message: String,
        cause: RuntimeException? = null,
    ): NotionIngestionResult {
        if (cause != null) {
            logger.warn("Notion ingestion failed at {} for connection {}", stage, connectionId, cause)
        }
        markRunFailed(runId)
        return failedResult(runId, connectionId, stage, message)
    }

    private fun failedResult(
        runId: UUID,
        connectionId: UUID,
        stage: NotionIngestionFailureStage,
        message: String,
    ): NotionIngestionResult {
        return NotionIngestionResult(
            runId = runId,
            connectionId = connectionId,
            outcome = NotionIngestionOutcome.FAILED,
            failure = NotionIngestionFailure(stage, message),
        )
    }

    private fun markRunFailed(runId: UUID) {
        try {
            ingestionApi.failRun(runId, TERMINAL_FAILURE_MESSAGE)
        } catch (@Suppress("SwallowedException") exception: RuntimeException) {
            logger.error("Unable to mark Notion ingestion run {} as failed", runId, exception)
        }
    }

    private fun markFailedAndPropagate(runId: UUID, exception: Exception): Nothing {
        if (exception is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        markRunFailed(runId)
        throw exception
    }

    private companion object {
        val DATA_SOURCE_PARENT_TYPES = setOf("data_source_id", "database_id")
        const val SOURCE_DISABLED_MESSAGE = "Notion source is disabled"
        const val FETCHING_FAILURE_MESSAGE = "Notion page content could not be fetched"
        const val UNSUPPORTED_PAGE_MESSAGE = "Notion page is unavailable for page ingestion"
        const val INVALID_PAGE_MESSAGE = "Notion returned invalid page metadata"
        const val PARSING_FAILURE_MESSAGE = "Notion page blocks could not be parsed"
        const val MAPPING_FAILURE_MESSAGE = "Notion page could not be mapped to an artifact"
        const val PERSISTENCE_FAILURE_MESSAGE = "Notion page artifact could not be persisted"
        const val SYNC_STATE_FAILURE_MESSAGE = "Notion page sync state could not be persisted"
        const val TERMINAL_FAILURE_MESSAGE = "Notion page ingestion terminated before completion"
    }
}

private class NotionIngestionStepException(
    val stage: NotionIngestionFailureStage,
    val failureMessage: String,
    val stepCause: RuntimeException,
) : RuntimeException(stepCause)

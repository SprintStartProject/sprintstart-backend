package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceInvalidResponseException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePage
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactIngestionApi
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotEnabledException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceIngestionException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionFailure
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionFailureStage
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.parser.ConfluenceStorageFormatParser
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/** Fetches, filters, parses, and submits one complete Confluence page ingestion batch. */
@Service
internal class ConfluencePageIngestionService(
    private val connectionService: ConfluenceConnectionRuntimeService,
    private val confluenceClient: ConfluenceClient,
    private val parser: ConfluenceStorageFormatParser,
    private val pageMapper: ConfluencePageArtifactMapper,
    private val ingestionApi: ConfluenceArtifactIngestionApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun ingest(projectId: UUID, connectionId: UUID): ConfluenceIngestionResult {
        val connection = connectionService.getConnectionForIngestion(projectId, connectionId)
        requireEnabled(connection)
        refreshSpaceMetadata(connection)
        val runId = UUID.randomUUID()
        ingestionApi.startRun(runId, connection.id, "${connection.baseUrl}|${connection.spaceId}")

        try {
            return ingestRun(connection, projectId, runId)
        } catch (exception: CancellationException) {
            markFailedAndPropagate(runId, exception)
        } catch (exception: InterruptedException) {
            markFailedAndPropagateInterruption(runId, exception)
        } catch (exception: RuntimeException) {
            markRunFailed(runId)
            throw sanitizedTerminalException(exception)
        }
    }

    private suspend fun ingestRun(
        connection: ConfluenceConnectionIngestionSnapshot,
        project: UUID,
        run: UUID,
    ): ConfluenceIngestionResult {
        val collected = collect(connection)
        val persisted = ingestionApi.persistBatch(
            ConfluenceArtifactBatchCommand(
                runId = run,
                projectId = project,
                artifacts = collected.commands,
                failures = collected.failures.map { it.toArtifactFailure() },
            ),
        )
        val persistenceFailures = persisted.persistenceFailures.map { it.toIngestionFailure() }
        val successfulItemCount = persisted.created + persisted.updated + persisted.unchanged
        ingestionApi.finishRun(run, successfulItemCount)
        return ConfluenceIngestionResult(
            runId = run,
            connectionId = connection.id,
            discovered = collected.discovered,
            eligible = collected.eligible,
            filtered = collected.filtered,
            created = persisted.created,
            updated = persisted.updated,
            unchanged = persisted.unchanged,
            failed = persisted.failed,
            failures = collected.failures + persistenceFailures,
            status = resultStatus(successfulItemCount, persisted.failed),
        )
    }

    /** Fetches the space's pages and maps the ones the connection lets through, recording what could not be. */
    private suspend fun collect(connection: ConfluenceConnectionIngestionSnapshot): Collected {
        val fetched = confluenceClient.getPages(connection.baseUrl, connection.credentials, connection.spaceId)
        val pages = fetched.successfulPages.distinctBy { page -> page.id }
        val eligiblePages = pages.filter { page -> connection.allowsPage(page.id) }
        val failures = fetched.failures.map { it.toIngestionFailure() }.toMutableList()
        val commands = mapEligiblePages(connection, eligiblePages, buildRelationshipIndex(pages), failures)
        return Collected(
            discovered = fetched.successfulPages.size + fetched.failures.size,
            eligible = eligiblePages.size,
            filtered = pages.size - eligiblePages.size,
            commands = commands,
            failures = failures,
        )
    }

    private class Collected(
        val discovered: Int,
        val eligible: Int,
        val filtered: Int,
        val commands: List<ConfluencePageArtifactCommand>,
        val failures: List<ConfluenceIngestionFailure>,
    )

    private fun mapEligiblePages(
        connection: ConfluenceConnectionIngestionSnapshot,
        pages: List<ConfluencePage>,
        relationshipIndex: RelationshipIndex,
        failures: MutableList<ConfluenceIngestionFailure>,
    ): List<ConfluencePageArtifactCommand> {
        return pages.mapNotNull { page ->
            if (page.id in relationshipIndex.selfParentPageIds) {
                failures += ConfluenceIngestionFailure(
                    pageId = page.id,
                    stage = ConfluenceIngestionFailureStage.HIERARCHY,
                    message = SELF_PARENT_FAILURE_MESSAGE,
                )
                return@mapNotNull null
            }
            val parsedBody = recording(failures, page.id, ConfluenceIngestionFailureStage.PARSING) {
                parser.parse(page.storage.value)
            } ?: return@mapNotNull null
            recording(failures, page.id, ConfluenceIngestionFailureStage.MAPPING) {
                pageMapper.toCommand(
                    connection = connection,
                    page = page,
                    parsedBody = parsedBody,
                    relationships = relationshipIndex.byPageId[page.id].orEmpty(),
                )
            }
        }
    }

    /** Runs [block] for one page; a failure is recorded against the page at [stage], and yields null. */
    private inline fun <T> recording(
        failures: MutableList<ConfluenceIngestionFailure>,
        page: String,
        stage: ConfluenceIngestionFailureStage,
        block: () -> T,
    ): T? =
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (@Suppress("SwallowedException") exception: RuntimeException) {
            failures += ConfluenceIngestionFailure(pageId = page, stage = stage, message = failureMessage(stage))
            null
        }

    private fun requireEnabled(connection: ConfluenceConnectionIngestionSnapshot) {
        if (!connection.sourceEnabled) {
            throw ConfluenceConnectionNotEnabledException(connection.spaceName ?: "No name specified.")
        }
    }

    /** Keeps the cached space name (and key) in sync with Confluence; refresh failures never abort the run. */
    private suspend fun refreshSpaceMetadata(connection: ConfluenceConnectionIngestionSnapshot) {
        try {
            val space = confluenceClient.getSpace(connection.baseUrl, connection.credentials, connection.spaceId)
            connectionService.updateSpaceMetadata(connection.id, space.name, space.key)
        } catch (exception: CancellationException) {
            throw exception
        } catch (@Suppress("SwallowedException") exception: RuntimeException) {
            logger.warn("Unable to refresh Confluence space metadata for connection {}", connection.id, exception)
        }
    }

    private fun sanitizedTerminalException(exception: RuntimeException): RuntimeException {
        if (exception is ConfluenceClientException || exception is ConfluenceInvalidResponseException) {
            return exception
        }
        return ConfluenceIngestionException()
    }

    private fun markRunFailed(runId: UUID) {
        try {
            ingestionApi.failRun(runId, TERMINAL_FAILURE_MESSAGE)
        } catch (@Suppress("SwallowedException") exception: RuntimeException) {
            logger.error("Unable to mark Confluence ingestion run {} as failed", runId)
        }
    }

    private fun markFailedAndPropagate(runId: UUID, exception: CancellationException): Nothing {
        markRunFailed(runId)
        throw exception
    }

    private fun markFailedAndPropagateInterruption(runId: UUID, exception: InterruptedException): Nothing {
        Thread.currentThread().interrupt()
        markRunFailed(runId)
        throw exception
    }

    private companion object {
        const val TERMINAL_FAILURE_MESSAGE = "Confluence page ingestion terminated before persistence"
    }
}

package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceInvalidResponseException
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageEditCommand
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Reads who saved which version of a page, without letting a missing history stop the page's ingestion. */
@Component
internal class ConfluencePageHistoryReader(
    private val confluenceClient: ConfluenceClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The [page] with who saved which of its versions, or with no history when Confluence would not say.
     *
     * The history is evidence about people, not part of the page, so a page whose history cannot be read is
     * still ingested and keeps the history recorded before.
     */
    suspend fun withHistory(
        connection: ConfluenceConnectionIngestionSnapshot,
        page: ConfluencePageArtifactCommand,
    ): ConfluencePageArtifactCommand {
        val pageId = page.metadata.pageId
        val edits = try {
            confluenceClient
                .getPageEdits(connection.baseUrl, connection.credentials, pageId)
                .map { ConfluencePageEditCommand(it.number, it.authorAccountId, it.editedAt) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ConfluenceClientException) {
            logger.warn("Unable to read the versions of Confluence page {}", pageId, exception)
            null
        } catch (exception: ConfluenceInvalidResponseException) {
            logger.warn("Unreadable versions of Confluence page {}", pageId, exception)
            null
        }
        return page.copy(edits = edits)
    }
}

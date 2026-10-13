package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files

import java.util.UUID

/**
 * One file of a Bitbucket repository that was fetched and should be ingested.
 *
 * The payload is carried on the event rather than fetched later by the listener: the content is
 * read from the local clone, and the clone may have moved on to a newer revision by the time a
 * listener reacts.
 *
 * @property transactionId The ingestion run this file belongs to.
 * @property repositoryId The connected repository the file was read from.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property path The repository-relative path of the file.
 * @property content The file's text content.
 * @property sourceUrl A link to the file at the exact revision it was read at.
 */
data class BitbucketFileFetchedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val path: String,
    val content: String,
    val sourceUrl: String,
)

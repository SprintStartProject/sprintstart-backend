package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events

import java.util.UUID

/**
 * Announces that connecting a Bitbucket repository was accepted and ingestion will begin.
 *
 * Published before the credential is resolved, so it carries the requested coordinates rather than
 * a repository id: at this point nothing has confirmed the repository exists. The ingestion side
 * records the run under these coordinates and resolves the id when the collectors report in.
 *
 * @property transactionId The ingestion run this connection reports under.
 * @property workspace The requested Bitbucket workspace.
 * @property slug The requested repository slug.
 */
data class BitbucketRepositoryConnectionInitiatedEvent(
    val transactionId: UUID,
    val workspace: String,
    val slug: String,
)

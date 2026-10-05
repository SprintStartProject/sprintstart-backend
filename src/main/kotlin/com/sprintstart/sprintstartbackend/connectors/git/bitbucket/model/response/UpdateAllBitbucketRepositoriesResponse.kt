package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response

import java.util.UUID

/**
 * The outcome of a batch update of the connected Bitbucket repositories.
 *
 * Keyed by `workspace/slug` rather than by connection id, because that is how a caller addresses the
 * repository everywhere else in this API. Each value is the transaction id of that repository's
 * update — the id its progress events report under — so a caller can follow one repository out of
 * the batch. Repositories whose source is disabled are absent: they were skipped, not updated.
 *
 * @property transactionIdsByRepository One transaction id per repository that was updated.
 */
data class UpdateAllBitbucketRepositoriesResponse(
    val transactionIdsByRepository: Map<String, UUID>,
)

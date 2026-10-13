package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response

import java.util.UUID

/**
 * The resulting project links of one repository connection after a link or unlink.
 *
 * @property repositoryId The connection whose links changed.
 * @property projectIds Every project the connection is linked to afterwards.
 */
data class BitbucketRepositoryProjectLinkResponse(
    val repositoryId: UUID,
    val projectIds: Set<UUID>,
)

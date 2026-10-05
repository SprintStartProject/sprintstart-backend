package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions

import java.util.UUID

/**
 * Raised when a repository connection is addressed by id but cannot be used by the caller.
 *
 * An unknown connection and one the caller has no way of seeing answer with the same message: both
 * name only the id that was sent, so a caller cannot tell "this does not exist" apart from "you may
 * not see this".
 *
 * @property repositoryId The connection id the caller sent.
 */
data class BitbucketRepositoryConnectionNotFoundException(
    val repositoryId: UUID,
) : RuntimeException("Repository connection with id $repositoryId not found")

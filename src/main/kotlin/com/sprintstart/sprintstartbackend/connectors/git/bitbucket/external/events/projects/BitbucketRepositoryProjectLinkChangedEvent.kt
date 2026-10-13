package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.projects

import java.util.UUID

/**
 * Emitted when a connected repository is linked to, or unlinked from, a project.
 *
 * The connector owns which projects a repository belongs to, but the same membership is also
 * carried by every artifact of that repository and by every chunk in the AI index — and retrieval
 * is fail-closed on it. A link that stops at the connection row leaves the repository listed in the
 * new project while none of its content is findable there; an unlink leaves it findable in a
 * project it was just removed from.
 *
 * Carries the single project that changed rather than the resulting set, and identifies the
 * repository by its coordinates rather than its id, because the artifact source ids the ingestion
 * side re-scopes by are built from the coordinates, not the id.
 *
 * @property workspace The workspace of the repository whose project links changed.
 * @property slug The slug of the repository whose project links changed.
 * @property projectId The project that was linked or unlinked.
 * @property linked `true` when the project was added, `false` when it was removed.
 */
data class BitbucketRepositoryProjectLinkChangedEvent(
    val workspace: String,
    val slug: String,
    val projectId: UUID,
    val linked: Boolean,
)

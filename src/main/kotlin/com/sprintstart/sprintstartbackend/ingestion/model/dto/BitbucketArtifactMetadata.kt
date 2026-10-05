package com.sprintstart.sprintstartbackend.ingestion.model.dto

import java.util.UUID

/**
 * Repository identity carried by every Bitbucket artifact's metadata.
 *
 * Deliberately a distinct type from [GithubArtifactMetadata] rather than a shared one: metadata is
 * deserialized back through Jackson's DEDUCTION (see [ArtifactMetadata]), which infers the subtype
 * from the field set alone. A type shared with GitHub's would make every Bitbucket artifact read
 * back as GitHub's and vice versa.
 *
 * The field names differ from GitHub's on purpose — `workspace`/`slug` instead of a duplicated
 * `repositoryFullName` — so a JSON payload can only ever match one subtype and deduction stays
 * unambiguous.
 *
 * @property repositoryId The connected repository's internal id, the join key to the projects the
 *           artifact belongs to.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 */
data class BitbucketArtifactMetadata(
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
) : ArtifactMetadata

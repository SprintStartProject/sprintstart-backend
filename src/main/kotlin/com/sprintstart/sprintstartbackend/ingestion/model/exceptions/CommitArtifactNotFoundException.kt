package com.sprintstart.sprintstartbackend.ingestion.model.exceptions

/**
 * A commit's changed files were recorded before the commit artifact they belong to was stored.
 *
 * The artifact is persisted immediately before its files are recorded, so this means that step was
 * skipped or failed, not that the commit is unknown.
 */
class CommitArtifactNotFoundException(
    sourceId: String,
) : RuntimeException("Commit artifact $sourceId not found, so its changed files cannot be recorded")

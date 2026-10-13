package com.sprintstart.sprintstartbackend.connectors.git.utils

/**
 * Consumes the files produced by [GitIngestionEngine].
 *
 * A sink is how a connector adapts the engine's provider-neutral output to its own events and its
 * own storage. Implementing it is the whole of a connector's involvement in file ingestion: the
 * engine handles cloning, revision tracking, diffing, reading and hashing, and never learns which
 * provider it worked for.
 *
 * Changes arrive in batches so that a caller writing to a database can group them, rather than
 * paying one round trip per file. Batch sizes are bounded by `sprintstart.git.ingest.batch-size`.
 *
 * Implementations must tolerate being called from several coroutines simultaneously: the engine
 * reads files concurrently and reports failures from those readers as they happen.
 */
interface GitFileSink {
    /**
     * Receives one batch of changes.
     *
     * A batch is never empty, and a file appears at most once across all batches of one ingest.
     *
     * @param changes The changes to record, in no guaranteed order.
     */
    suspend fun onBatch(changes: List<GitFileChange>)

    /**
     * Receives a file that belongs to the revision but could not be ingested.
     *
     * Reporting these is what keeps a failed file distinguishable from one the repository does not
     * contain. A file skipped here is absent from the index even though the revision still holds it.
     *
     * @param relativePath The path that failed.
     * @param reason A human-readable explanation, suitable for an event payload.
     */
    suspend fun onFailure(relativePath: String, reason: String)
}

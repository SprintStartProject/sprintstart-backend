package com.sprintstart.sprintstartbackend.shared.git

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Contains the following application.yml config parameters
 *
 * ```yaml
 * sprintstart:
 *     git:
 *         cache-path: ...
 *         ingest:
 *             parallelism: ...
 *             batch-size: ...
 *             max-file-size-bytes: ...
 * ```
 *
 * This is a bean of its own rather than a nested class of the application's root configuration,
 * because it is shared infrastructure: the connectors and the Git plumbing behind them are the only
 * things that need it, and routing it through the root configuration would make each of them depend
 * on every unrelated setting in the application. It is registered alongside the root configuration
 * in `@EnableConfigurationProperties`.
 *
 * @property cachePath Root directory holding the clones of all connectors. Clones are placed under
 *           `<cache-path>/<host>/<namespace>/<name>`, so repositories that share a namespace and a
 *           name across providers cannot collide on one directory.
 * @property ingest Bounds on how a repository is read.
 */
@ConfigurationProperties(prefix = "sprintstart.git")
data class GitConfig(
    val cachePath: String = "/repos",
    val ingest: GitIngestConfig = GitIngestConfig(),
)

/**
 * Bounds on reading a repository's working tree.
 *
 * `parallelism` is deliberately allowed to exceed the processor count. Reading and hashing files is
 * dominated by file and disk waits rather than by CPU, so a small multiple of the cores keeps more
 * of that latency overlapped. `batchSize` bounds how many files are held in memory at once, since
 * each buffered entry carries the file's full content. `maxFileSizeBytes` caps a single read: an
 * extension list cannot identify every binary file, so an explicit size limit is what stops a
 * generated bundle from being read into memory at all.
 *
 * @property parallelism How many files to read concurrently. `0` derives it from the processor count.
 * @property batchSize How many changed files are handed to a sink in one batch.
 * @property maxFileSizeBytes Largest file that is read. Larger files are reported as skipped.
 */
data class GitIngestConfig(
    val parallelism: Int = 0,
    val batchSize: Int = 32,
    val maxFileSizeBytes: Long = 1_048_576,
)

package com.sprintstart.sprintstartbackend.github.util

import com.sprintstart.sprintstartbackend.github.util.OnDiskOperations.Companion.exec
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.Path
import java.time.Instant

/**
 * Factory for Git CLI operations and a runner for executing them.
 *
 * Each factory method returns a configured [ProcessBuilder] without executing it, keeping
 * construction and execution separate. Callers pass the result to [exec] along with the
 * target repository path to run the command.
 *
 * Example usage:
 *
 * ```kotlin
 * val ops = OnDiskOperations()
 * OnDiskOperations.exec(repoPath, ops.gitStatus())
 * ```
 *
 * Requires the `git` CLI to be present on the container's PATH. All commands are run against
 * the repository at the path provided to [exec] — none of the factory methods encode a path
 * themselves.
 */
@Service
class OnDiskOperations {
    /** Checks the working tree state. Used to verify cache validity. */
    fun gitStatus() = ProcessBuilder("git", "status")

    /**
     * Clones [remoteUri] into [localFsPath].
     *
     * [remoteUri] is expected to contain an inline auth token
     * (`https://<token>@github.com/...`). Never pass this URI to a logger — use a sanitized
     * version with the token replaced by `***` instead.
     */
    fun gitClone(remoteUri: String, localFsPath: String) = ProcessBuilder("git", "clone", remoteUri, localFsPath)

    /** Downloads new commits from `origin` into `FETCH_HEAD` without modifying the working tree. */
    fun gitFetch() = ProcessBuilder("git", "fetch", "origin")

    /** Fast-forwards the local branch to `FETCH_HEAD` after a [gitFetch]. */
    fun gitMerge() = ProcessBuilder("git", "merge", "FETCH_HEAD")

    /**
     * Resolves `HEAD` to its full 40-character commit SHA.
     *
     * Output includes a trailing newline — call `.trim()` on the result of [exec] before
     * storing or comparing the SHA.
     */
    fun gitRevParse() = ProcessBuilder("git", "rev-parse", "HEAD")

    /** Lists remote-tracking branches so callers can repair a clone with an invalid local HEAD. */
    fun gitRemoteBranches() =
        ProcessBuilder("git", "for-each-ref", "refs/remotes/origin", "--format=%(refname:short)")

    /** Checks out [branch] from the corresponding `origin/[branch]` remote-tracking ref. */
    fun gitCheckoutRemoteBranch(branch: String) =
        ProcessBuilder("git", "checkout", "-B", branch, "refs/remotes/origin/$branch")

    /**
     * Lists the names of files that changed between [previousSha] and [currentSha].
     *
     * Output is one relative file path per line. Passes `--name-only` so file contents
     * are not included — callers read file contents directly from disk after diffing.
     */
    fun gitDiffCmp(previousSha: String, currentSha: String) =
        ProcessBuilder("git", "diff", "$previousSha..$currentSha", "--name-only")

    /**
     * Constructs a `git log` command that retrieves all commits with a custom format.
     */
    fun gitCommits() = ProcessBuilder("git", "log", "--pretty=format:%cI - %H - %an - %s")

    /**
     * Constructs a `git log` command that retrieves commit hashes created after the specified timestamp.
     *
     * @param datetime The point in time after which commits should be retrieved. Accepts an [Instant] value.
     * @return A [ProcessBuilder] configured to execute the `git log` command with the `--after` option.
     */
    fun gitCommitsAfter(datetime: Instant) =
        ProcessBuilder("git", "log", "--pretty=format:%cI - %H - %an - %s", "--after=$datetime")

    companion object {
        /**
         * Executes [op] in the context of the repository at [path] and returns stdout.
         *
         * Stderr is merged into stdout via [ProcessBuilder.redirectErrorStream] so all output
         * is captured in one stream. Throws [RuntimeException] with the full command and exit
         * code if the process exits non-zero, making failures easy to trace in logs.
         *
         * @param path Absolute path to the local repository root
         * @param op   A [ProcessBuilder] produced by one of the factory methods on this class
         * @return Captured stdout (and stderr) of the process as a string
         * @throws RuntimeException if the process exits with a non-zero exit code
         */
        fun exec(path: Path, op: ProcessBuilder): String {
            val process = op.directory(path.toFile()).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val command = op.command().joinToString(" ") { sanitizeCommandPart(it) }
                val details = output.trim()
                val suffix = if (details.isBlank()) "" else ": $details"
                throw RuntimeException("$command failed (exit $exitCode)$suffix")
            }

            return output
        }

        private fun sanitizeCommandPart(value: String): String =
            runCatching {
                val uri = URI(value)
                val userInfo = uri.userInfo ?: return value
                val username = userInfo.substringBefore(':')
                val sanitizedUserInfo = if (username.isBlank()) "***" else "$username:***"
                URI(
                    uri.scheme,
                    sanitizedUserInfo,
                    uri.host,
                    uri.port,
                    uri.path,
                    uri.query,
                    uri.fragment,
                ).toASCIIString()
            }.getOrDefault(value)
    }
}

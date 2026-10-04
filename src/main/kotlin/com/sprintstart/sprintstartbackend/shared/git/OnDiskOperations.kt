package com.sprintstart.sprintstartbackend.shared.git

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
 *
 * Every operation here works on any hosted Git repository — GitHub and Bitbucket clones are
 * cloned, diffed, and read with the same commands. Provider-specific concerns, such as the
 * clone URL or the API used for discovery, deliberately live outside this class.
 */
@Service
@Suppress("TooManyFunctions")
class OnDiskOperations {
    /** Checks the working tree state. Used to verify cache validity. */
    fun gitStatus() = ProcessBuilder("git", "status")

    /**
     * Clones [remoteUri] into [localFsPath].
     *
     * [remoteUri] is expected to contain credentials inline
     * (`https://<username>:<secret>@<host>/<namespace>/<name>.git`). The exact user name and
     * secret are provider-specific and are supplied by the caller. Never pass this URI to a
     * logger — use a sanitized version with the secret replaced by `***` instead.
     */
    fun gitClone(remoteUri: String, localFsPath: String) = ProcessBuilder("git", "clone", remoteUri, localFsPath)

    /**
     * Points `origin` at [remoteUri].
     *
     * Used to refresh a cached clone after the stored credential rotated: `git clone` bakes the
     * token into the clone's remote URL once, and nothing else ever rewrites it. Like [gitClone],
     * [remoteUri] is expected to contain credentials inline and must never be logged.
     */
    fun gitSetRemoteUrl(remoteUri: String) = ProcessBuilder("git", "remote", "set-url", "origin", remoteUri)

    /** Downloads new commits from `origin` into `FETCH_HEAD` without modifying the working tree. */
    fun gitFetch() = ProcessBuilder("git", "fetch", "origin")

    /** Fast-forwards the local branch to `FETCH_HEAD` after a [gitFetch]. */
    fun gitMerge() = ProcessBuilder("git", "merge", "FETCH_HEAD")

    /**
     * Resolves current `HEAD` to its full 40-character commit SHA.
     *
     * Output includes a trailing newline — call `.trim()` on the result of [exec] before
     * storing or comparing the SHA.
     */
    fun gitRevParse() = ProcessBuilder("git", "rev-parse", "HEAD")

    /**
     * Checks that [revision] exists in the clone, without printing anything.
     *
     * `git cat-file -e` exits non-zero for an unknown revision, which [exec] turns into a
     * `RuntimeException` — callers testing for existence catch that rather than parsing output.
     */
    fun gitRevisionExists(revision: String) = ProcessBuilder("git", "cat-file", "-e", revision)

    /**
     * Resolves remote `HEAD` to its full 40-character commit SHA.
     *
     * Output includes a trailing newline — call `.trim()` on the result of [exec] before
     * storing or comparing the SHA.
     */
    fun gitLsRemote() = ProcessBuilder("git", "ls-remote", "origin", "HEAD")

    /** Lists remote-tracking branches so callers can repair a clone with an invalid local HEAD. */
    fun gitRemoteBranches() =
        ProcessBuilder("git", "for-each-ref", "refs/remotes/origin", "--format=%(refname:short)")

    /** Checks out [branch] from the corresponding `origin/[branch]` remote-tracking ref. */
    fun gitCheckoutRemoteBranch(branch: String) =
        ProcessBuilder("git", "checkout", "-B", branch, "refs/remotes/origin/$branch")

    /**
     * Lists the names of files that changed between [previousSha] and [currentSha].
     *
     * Output is NUL-terminated (`-z`), one relative file path per entry. Passes `--name-only`
     * so file contents are not included — callers read file contents directly from disk after
     * diffing. `-z` disables Git's quoting of unusual names, so non-ASCII, whitespace and
     * newline characters survive as literal path bytes.
     */
    fun gitDiffCmp(previousSha: String, currentSha: String) =
        ProcessBuilder("git", "diff", "-z", "--name-only", "$previousSha..$currentSha")

    /**
     * Lists every tracked file of the working tree, each terminated by NUL.
     *
     * This reads the index instead of the filesystem, which is both cheaper and more precise than
     * walking the directory: untracked files, ignored build output and the contents of `.git` are
     * excluded without any filtering, and `.git` is never descended into at all. `-z` terminates
     * each path with NUL rather than a newline, so file names containing newlines stay readable.
     */
    fun gitListFiles() = ProcessBuilder("git", "ls-files", "-z")

    /**
     * Constructs a `git log` command that reads commit metadata in [GIT_LOG_FORMAT].
     *
     * @param sinceRevision When set, commits reachable from it are excluded by using the
     *        `sinceRevision..HEAD` range. When `null`, the entire history is read.
     */
    fun gitLog(sinceRevision: String?) =
        ProcessBuilder(
            buildList {
                add("git")
                add("log")
                if (sinceRevision != null) add("$sinceRevision..HEAD")
                add("--pretty=format:$GIT_LOG_FORMAT")
            },
        )

    /**
     * Discards local changes and moves the checked out branch onto `FETCH_HEAD`.
     *
     * Used after [gitFetch] to advance a clone that is only ever read. Unlike a merge this cannot
     * stop halfway on a rewritten history or leave conflict markers in the working tree — which
     * matters, because a conflicted checkout still passes a validity check while serving file
     * contents that are not the revision's.
     */
    fun gitResetHard() = ProcessBuilder("git", "reset", "--hard", "FETCH_HEAD")

    /**
     * Lists every tracked file of the working tree, each terminated by NUL.
     *
     * This reads the index instead of the filesystem, which is both cheaper and more precise than
     * walking the directory: untracked files, ignored build output and the contents of `.git` are
     * excluded without any filtering, and `.git` is never descended into at all. `-z` terminates
     * each path with NUL rather than a newline, so file names containing newlines stay readable.
     */
    fun gitListFiles() = ProcessBuilder("git", "ls-files", "-z")

    /**
     * Constructs a `git log` command that reads commit metadata in [GIT_LOG_FORMAT].
     *
     * @param sinceRevision When set, commits reachable from it are excluded by using the
     *        `sinceRevision..HEAD` range. When `null`, the entire history is read.
     */
    fun gitLog(sinceRevision: String?) =
        ProcessBuilder(
            buildList {
                add("git")
                add("log")
                if (sinceRevision != null) add("$sinceRevision..HEAD")
                add("--pretty=format:$GIT_LOG_FORMAT")
            },
        )

    /**
     * Discards local changes and moves the checked out branch onto `FETCH_HEAD`.
     *
     * Used after [gitFetch] to advance a clone that is only ever read. Unlike a merge this cannot
     * stop halfway on a rewritten history or leave conflict markers in the working tree — which
     * matters, because a conflicted checkout still passes a validity check while serving file
     * contents that are not the revision's.
     */
    fun gitResetHard() = ProcessBuilder("git", "reset", "--hard", "FETCH_HEAD")

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

        /**
         * Record format of [gitLog]: SHA, author name, commit time and subject.
         *
         * Fields are separated by the ASCII unit separator rather than a printable character,
         * because a printable one can occur inside an author name or a subject and would then split
         * the record. The subject is last and single-line, so it cannot truncate a later field.
         */
        const val GIT_LOG_FORMAT = "%H%x1f%an%x1f%cI%x1f%s"

        /**
         * Sanitizes a command part by masking sensitive information such as user credentials in a URI.
         *
         * If the input string represents a URI containing user information (e.g., `username:password`),
         * the method replaces the password with a placeholder (`***`). If the username is absent or
         * empty, the entire user info is replaced with `***`. Other parts of the URI remain unchanged.
         *
         * @param value The input string potentially containing sensitive information in URI form.
         * @return The sanitized string with sensitive information masked, or the original string
         *         if it does not contain user information.
         */
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

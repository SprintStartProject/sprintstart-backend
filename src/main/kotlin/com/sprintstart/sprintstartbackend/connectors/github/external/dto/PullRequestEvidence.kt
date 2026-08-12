package com.sprintstart.sprintstartbackend.connectors.github.external.dto

/**
 * Real, observed state of one pull request, gathered on demand for artifact verification.
 *
 * [checksPassed] is `null` when GitHub reports no combined CI status (e.g. no checks configured
 * or still pending) -- only an explicit `SUCCESS`/`FAILURE`/`ERROR` rollup maps to `true`/`false`.
 */
data class PullRequestEvidence(
    val title: String,
    val body: String,
    val state: String,
    val filesChanged: List<String>,
    /**
     * The diffs of the changed files, budgeted — see [ChangedFileDiff].
     *
     * ⚠️ **Without this the judge could only see filenames**, which cannot tell a real fix from a
     * whitespace edit to the right file. A hire who touched `AuthService.kt` and a hire who fixed
     * the bug in it were indistinguishable evidence.
     *
     * Empty when GitHub would not answer, which the judge is told is **not** the same as an empty
     * diff: a network failure must not fail somebody's work.
     */
    val fileDiffs: List<ChangedFileDiff> = emptyList(),
    /**
     * How many changed files have no diff here, because the budget ran out before them.
     *
     * ⚠️ **Load-bearing, not a statistic.** A judge shown a partial diff and not told it is partial
     * will read absence as proof the work was not done. This is the number that lets it say "what I
     * can see is consistent" instead of failing a hire for the part it was never shown.
     */
    val omittedFileCount: Int = 0,
    val checksPassed: Boolean?,
    val commitMessages: List<String>,
    /**
     * GitHub login of whoever opened the pull request, lower-cased, or `null` when GitHub reports
     * no author (e.g. a deleted account). Artifact verification compares this against the
     * submitting user's declared GitHub login -- without it, a hire could pass a task with
     * somebody else's pull request.
     */
    val authorLogin: String?,
)

/**
 * One changed file's diff, as much of it as the budget allowed.
 *
 * [truncated] says the patch was cut, so the judge can tell "this file changed a little" from "this
 * file changed more than I was shown". Without the flag a cut patch reads as a complete small one,
 * which is the same lie as an omitted file reading as an unchanged one.
 *
 * [patch] is null when GitHub reported none — a binary file, or one it considers too large to
 * inline. Real work sometimes has no readable diff, and saying so beats implying nothing happened.
 */
data class ChangedFileDiff(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val patch: String?,
    val truncated: Boolean = false,
)

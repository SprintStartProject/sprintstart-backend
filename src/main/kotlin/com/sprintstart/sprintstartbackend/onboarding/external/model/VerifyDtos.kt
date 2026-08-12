package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request for the AI service's `/onboarding/verify` endpoint, `type = "knowledge"`.
 *
 * `exact`/`attest` are graded locally in Kotlin (see `VerificationService`), so [canonicalAnswer]
 * is never populated by this client.
 */
@Serializable
data class GradeKnowledgeRequest(
    val type: String = "knowledge",
    val question: String,
    val answer: String,
    @SerialName("attempt_no") val attemptNo: Int,
    val rubric: String,
    val evidence: String = "",
    @SerialName("canonical_answer") val canonicalAnswer: String? = null,
)

/**
 * Repo/world-state evidence the backend has already, deterministically gathered for one PR --
 * mirrors `sprintstart-ai`'s `ArtifactEvidenceSchema` field-for-field. The AI service never
 * re-derives any of this, it only judges whether the content satisfies a rubric.
 */
@Serializable
data class ArtifactEvidenceDto(
    @SerialName("pr_title") val prTitle: String = "",
    @SerialName("pr_body") val prBody: String = "",
    @SerialName("pr_state") val prState: String = "",
    @SerialName("files_changed") val filesChanged: List<String> = emptyList(),
    @SerialName("checks_passed") val checksPassed: Boolean? = null,
    @SerialName("commit_messages") val commitMessages: List<String> = emptyList(),
    /**
     * The diffs, budgeted backend-side.
     *
     * ⚠️ **The judge could previously only see filenames**, which cannot separate a fix from a
     * whitespace edit to the right file — "claims are not evidence; changed files and commits are"
     * was as far as it could go, and a filename is a weak kind of evidence to rest that on.
     */
    @SerialName("file_diffs") val fileDiffs: List<FileDiffDto> = emptyList(),
    /**
     * How many changed files were left out of [fileDiffs] by the budget.
     *
     * ⚠️ **Sent so the judge cannot mistake a partial diff for a complete one.** Absence of shown
     * evidence is not evidence of absence, and a model not told it is looking at part of a change
     * will fail a hire for the part it was never given.
     */
    @SerialName("omitted_file_count") val omittedFileCount: Int = 0,
)

/** One changed file's diff, as much of it as the budget allowed. */
@Serializable
data class FileDiffDto(
    val path: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    /** Null when GitHub reported no patch — a binary or over-large file, not an empty change. */
    val patch: String? = null,
    /** Whether [patch] was cut short, so a trimmed diff is not read as a small one. */
    val truncated: Boolean = false,
)

/** Request for the AI service's `/onboarding/verify` endpoint, `type = "artifact"`. */
@Serializable
data class GradeArtifactRequest(
    val type: String = "artifact",
    val question: String,
    val rubric: String,
    @SerialName("artifact_evidence") val artifactEvidence: ArtifactEvidenceDto,
)

@Serializable
data class GradeResult(
    val passed: Boolean,
    val score: Double = 0.0,
    val feedback: String = "",
    val hint: String? = null,
)

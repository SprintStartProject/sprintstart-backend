package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IngestedIssue
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.SourceId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.CandidatePoolState
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.starterwork.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.starterwork.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.starterwork.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkCandidateResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.starterwork.repository.StarterWorkTaskProposalRepository
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * The open issues a PM can browse, and putting one of them into the starter-work pool.
 */
internal class StarterWorkCandidates(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val artifactIngestionApi: ArtifactIngestionApi,
) {
    /**
     * The open issues in a project's corpus, each marked with where it stands in the pool.
     *
     * Deterministic and LLM-free: the same material mining reads, shown to a person instead.
     *
     * Assigned issues are returned, and so are issues already pooled or removed — filtering is
     * the client's to do. [CandidatePoolState] and the three-valued `hasAssignee` carry that, so an
     * absence from the list never has to stand in for "somebody else has this".
     *
     * Ordered newest-changed first; ties fall back to the source id so the order is stable.
     */
    @Transactional(readOnly = true)
    fun listCandidates(projectId: ProjectId): List<StarterWorkCandidateResponse> {
        val poolStateBySourceId = starterWorkTaskProposalRepository
            .findAllByStatusIn(listOf(ProposalStatus.LIVE, ProposalStatus.REJECTED, ProposalStatus.STALE))
            .associate { it.sourceId to it.status.toPoolState() }

        return artifactIngestionApi
            .getOpenIssues(projectId.value)
            .mapNotNull { issue ->
                // An issue with no title is dropped rather than shown as an empty row.
                val title = issue.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val body = issue.body?.takeIf { it.isNotBlank() }
                StarterWorkCandidateResponse(
                    sourceId = issue.sourceId,
                    tracker = issue.tracker,
                    title = title,
                    excerpt = body?.take(CANDIDATE_EXCERPT_CHARS),
                    excerptTruncated = (body?.length ?: 0) > CANDIDATE_EXCERPT_CHARS,
                    labels = issue.labels,
                    sourceUrl = issue.sourceUrl,
                    hasAssignee = issue.hasAssignee,
                    poolState = poolStateBySourceId[issue.sourceId] ?: CandidatePoolState.AVAILABLE,
                    updatedAtSource = issue.updatedAtSource,
                )
            }.sortedWith(
                compareByDescending<StarterWorkCandidateResponse> { it.updatedAtSource }
                    .thenBy { it.sourceId },
            )
    }

    private fun ProposalStatus.toPoolState(): CandidatePoolState =
        when (this) {
            ProposalStatus.LIVE -> CandidatePoolState.IN_POOL
            ProposalStatus.REJECTED -> CandidatePoolState.REMOVED
            // A stale row appears here only when its issue reopened; promoting it revives the row, so it is
            // offered as AVAILABLE.
            ProposalStatus.STALE -> CandidatePoolState.AVAILABLE
        }

    /**
     * Puts one browsed corpus issue into the pool, live and reviewed.
     *
     * The same landing place as [StarterWorkTaskProposalService.createTask]: somebody looked at this
     * issue and vouched for it.
     * This is not an approval gate in front of mining — mining keeps landing tasks live without
     * anybody's involvement, and this only adds a way in for an issue mining did not pick.
     *
     * The title and link come from the ingested issue, so the pool cannot disagree with the tracker
     * about what an issue is called.
     *
     * Refuses an issue already in the pool or removed from it (409 — rejection is sticky,
     * and promotion must not undo it), and one closed at its source (409). An assigned
     * issue is accepted: mining skips those, and a person promoting one can see who holds it.
     *
     * @throws ResponseStatusException 404 if no issue with that source id is ingested; 400 if the
     * ingested issue has no title; 409 if it is already pooled, was removed, or is closed.
     */
    @Transactional
    fun promoteCandidate(request: PromoteStarterWorkCandidateRequest): StarterWorkTaskProposalResponse {
        val sourceId = SourceId(request.sourceId.trim())
        val issue = loadPromotableIssue(sourceId)
        val title = titleOf(sourceId, issue)
        settleExistingProposal(sourceId, request)?.let { return it.toResponse() }

        val proposal = starterWorkTaskProposalRepository.save(
            StarterWorkTaskProposal(
                sourceId = sourceId.value,
                title = title,
                summary = request.summary?.trim()?.takeIf { it.isNotBlank() },
                // Nothing judged this issue, so there is no rationale to state.
                rationale = null,
                sourceUrl = issue.sourceUrl,
                competencyKeys = request.competencyKeys.toMutableList(),
                status = ProposalStatus.LIVE,
                reviewed = true,
                decidedAt = Instant.now(),
            ),
        )
        return proposal.toResponse()
    }

    /** The ingested issue, if there is one and it is still work somebody could be handed. */
    private fun loadPromotableIssue(sourceId: SourceId): IngestedIssue {
        val issue = artifactIngestionApi.getIssue(sourceId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No ingested issue found with source id: $sourceId",
            )
        // Only a definite "CLOSED" refuses — an uncaptured state is unknown, never "closed".
        if (CLOSED_STATE.equals(issue.state, ignoreCase = true)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Issue $sourceId is closed at its source and is not work to hand a newcomer",
            )
        }
        return issue
    }

    private fun titleOf(sourceId: SourceId, issue: IngestedIssue): String =
        issue.title?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Issue $sourceId has no title and cannot be put in the pool",
            )

    /**
     * Handles an issue that already has a proposal: revives a stale one, refuses the rest.
     *
     * The rejected branch is the load-bearing one: rejection is sticky, so promotion must not
     * become the accidental way to undo it.
     *
     * A stale row is the opposite case. [loadPromotableIssue] has already established that this
     * issue is open, so the row is out of date rather than unwanted, and reviving it is both what
     * the caller asked for and what the next reconciliation pass would do anyway. Reviving rather
     * than inserting is not a preference either — `sourceId` is unique per proposal, so a second
     * row for the same issue cannot exist.
     *
     * A revived row lands in the same state a fresh promotion does: live *and reviewed*, because
     * this endpoint's whole premise is that somebody looked at the issue and vouched for it. Coming
     * back still carrying the unreviewed demotion would contradict the act that revived it.
     *
     * What the promoter sent is applied; what they left out is kept. Both fields are optional and
     * default to empty, so reading silence as "clear this" would let a promotion that said nothing
     * destroy competency keys mining had already worked out.
     *
     * @return the revived proposal, or null when the issue has no proposal at all.
     */
    private fun settleExistingProposal(
        sourceId: SourceId,
        request: PromoteStarterWorkCandidateRequest,
    ): StarterWorkTaskProposal? {
        val existing = starterWorkTaskProposalRepository.findBySourceId(sourceId.value) ?: return null
        return when (existing.status) {
            ProposalStatus.STALE -> {
                existing.status = ProposalStatus.LIVE
                existing.reviewed = true
                request.summary
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { existing.summary = it }
                request.competencyKeys.takeIf { it.isNotEmpty() }?.let {
                    existing.competencyKeys.clear()
                    existing.competencyKeys.addAll(it)
                }
                existing.decidedAt = Instant.now()
                starterWorkTaskProposalRepository.save(existing)
            }
            ProposalStatus.LIVE -> throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Issue $sourceId is already in the starter-work pool",
            )
            ProposalStatus.REJECTED -> throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Issue $sourceId was removed from the starter-work pool and is not re-addable",
            )
        }
    }

    private companion object {
        /** How the ingestion mappers spell "finished at the source", for GitHub and Jira alike. */
        const val CLOSED_STATE = "CLOSED"

        /**
         * How much of a browsed issue's body a list row carries. The response flags when
         * something was cut, so a truncated body is never mistaken for a short one.
         */
        const val CANDIDATE_EXCERPT_CHARS = 600
    }
}

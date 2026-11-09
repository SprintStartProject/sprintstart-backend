package com.sprintstart.sprintstartbackend.starterwork.external

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.starterwork.external.model.PoolReconciliation
import com.sprintstart.sprintstartbackend.starterwork.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkCandidateResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse

/** What a manager decides about the starter-work pool from team mode. */
interface StarterWorkReviewApi {
    /** Vouches for [taskId], lifting the demotion an unreviewed task gets; 409 when it was rejected. */
    fun markReviewed(taskId: StarterWorkTaskId): StarterWorkTaskProposalResponse

    /** The open issues from [projectId]'s repositories that are neither pooled nor removed, newest-changed first. */
    fun promotableIssuesOn(projectId: ProjectId): List<StarterWorkCandidateResponse>

    /** Pools an ingested issue nobody mined; 409 when it is pooled, was removed or is closed. */
    fun promoteCandidate(request: PromoteStarterWorkCandidateRequest): StarterWorkTaskProposalResponse

    /** Takes [taskId] out of the pool for good, so mining never brings it back. */
    fun reject(taskId: StarterWorkTaskId, reason: String?): StarterWorkTaskProposalResponse

    /** Flags [taskId] as a good first task ("Task 0") or clears the flag; 409 when it is not live. */
    fun setTaskZeroEligibility(taskId: StarterWorkTaskId, eligible: Boolean): StarterWorkTaskProposalResponse

    /** Compares every live and stale task against its source now and writes back what changed. */
    fun reconcile(): PoolReconciliation
}

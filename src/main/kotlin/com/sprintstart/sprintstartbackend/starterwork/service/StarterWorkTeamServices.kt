package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.starterwork.external.OrientationAuthoringApi
import com.sprintstart.sprintstartbackend.starterwork.external.StarterWorkReviewApi
import com.sprintstart.sprintstartbackend.starterwork.external.enums.CandidatePoolState
import com.sprintstart.sprintstartbackend.starterwork.external.model.PoolReconciliation
import com.sprintstart.sprintstartbackend.starterwork.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.starterwork.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.OrientationPacketResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkCandidateResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse
import org.springframework.stereotype.Service

/** [StarterWorkReviewApi] over the proposal service, the scope and the reconciler. */
@Service
class StarterWorkReviewService(
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val starterWorkScope: StarterWorkScope,
    private val starterWorkPoolReconciler: StarterWorkPoolReconciler,
) : StarterWorkReviewApi {
    override fun promotableIssuesOn(projectId: ProjectId): List<StarterWorkCandidateResponse> =
        starterWorkScope.onProject(
            starterWorkTaskProposalService
                .listCandidates(projectId.value)
                .filter { it.poolState == CandidatePoolState.AVAILABLE },
            projectId.value,
        ) { it.sourceId }

    override fun markReviewed(taskId: StarterWorkTaskId): StarterWorkTaskProposalResponse =
        starterWorkTaskProposalService.markReviewed(taskId.value)

    override fun promoteCandidate(request: PromoteStarterWorkCandidateRequest): StarterWorkTaskProposalResponse =
        starterWorkTaskProposalService.promoteCandidate(request)

    override fun reject(taskId: StarterWorkTaskId, reason: String?): StarterWorkTaskProposalResponse =
        starterWorkTaskProposalService.reject(taskId.value, reason)

    override fun setTaskZeroEligibility(taskId: StarterWorkTaskId, eligible: Boolean): StarterWorkTaskProposalResponse =
        starterWorkTaskProposalService.setTaskZeroEligibility(taskId.value, eligible)

    override fun reconcile(): PoolReconciliation = starterWorkPoolReconciler.reconcile()
}

/** [OrientationAuthoringApi] over the orientation service. */
@Service
class OrientationAuthoringService(
    private val taskOrientationService: TaskOrientationService,
) : OrientationAuthoringApi {
    override fun packetForAuthoring(taskId: StarterWorkTaskId, projectId: ProjectId): MyOrientationResponse =
        taskOrientationService.getForAuthoring(taskId.value, projectId.value)

    override fun authorPacket(
        taskId: StarterWorkTaskId,
        projectId: ProjectId,
        request: AuthorOrientationRequest,
    ): OrientationPacketResponse = taskOrientationService.authorPacket(taskId.value, projectId.value, request)

    override fun revertToAi(taskId: StarterWorkTaskId, projectId: ProjectId) =
        taskOrientationService.revertToAi(taskId.value, projectId.value)
}

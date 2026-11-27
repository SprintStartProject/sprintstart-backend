package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.external.InterviewAiClient
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewRequest
import com.sprintstart.sprintstartbackend.offboarding.model.mapper.toAiFile
import com.sprintstart.sprintstartbackend.offboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.model.response.InterviewPlanResponse
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service

/**
 * Plans the exit interview of a departure: which questions only the leaving people can answer.
 *
 * The risk map decides deterministically which files are at risk and why; the AI service turns the
 * riskiest of them into questions and leaves out what the project's records already answer. Only
 * files, their findings and the risk map's reasons are sent, never a person's name.
 */
@Service
class InterviewPlanService(
    private val risks: OffboardingRiskService,
    private val ai: InterviewAiClient,
) {
    /**
     * The interview plan for a departure.
     *
     * @param projectId The project whose evidence is read.
     * @param request Who leaves and how to weigh the evidence.
     * @param maxFiles How many of the riskiest files the plan is made from, at most [MAX_FILES].
     * @throws com.sprintstart.sprintstartbackend.offboarding.model.exception.InterviewAiException when the
     *         AI service fails.
     */
    suspend fun plan(projectId: ProjectId, request: RiskRequest, maxFiles: Int): InterviewPlanResponse {
        val files = risks.riskMap(projectId, request).files.take(maxFiles.coerceAtMost(MAX_FILES))
        if (files.isEmpty()) return InterviewPlanResponse(emptyList(), emptyList(), 0)
        val plan = ai.planInterview(AiInterviewRequest(projectId.value.toString(), files.map { it.toAiFile() }))
        return plan.toResponse(files.size)
    }

    private companion object {
        /** Each file costs model time, and the plan asks about a handful of components anyway. */
        const val MAX_FILES = 40
    }
}

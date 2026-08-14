package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse

fun StarterWorkTaskProposal.toResponse(): StarterWorkTaskProposalResponse =
    StarterWorkTaskProposalResponse(
        id = id,
        sourceId = sourceId,
        title = title,
        summary = summary,
        rationale = rationale,
        sourceUrl = sourceUrl,
        competencyKeys = competencyKeys.toList(),
        status = status,
        taskZeroEligible = taskZeroEligible,
    )

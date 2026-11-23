package com.sprintstart.sprintstartbackend.starterwork.model.mapper

import com.sprintstart.sprintstartbackend.starterwork.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse

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
        reviewed = reviewed,
        sourceHasAssignee = sourceHasAssignee,
        sourceCheckedAt = sourceCheckedAt,
    )

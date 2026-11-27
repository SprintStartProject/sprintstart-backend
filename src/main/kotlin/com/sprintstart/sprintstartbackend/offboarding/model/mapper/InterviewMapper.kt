package com.sprintstart.sprintstartbackend.offboarding.model.mapper

import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewFile
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewQuestion
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.InterviewPlanResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.InterviewQuestionResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileRisk

/** A file at risk as the AI service reads it: its findings and reasons, and nobody's name. */
fun FileRisk.toAiFile(): AiInterviewFile =
    AiInterviewFile(
        repository = repository,
        path = path,
        level = level.name,
        score = score,
        kind = kind?.name,
        hotspot = hotspot,
        abandonedShare = abandonedShare,
        coupledWith = coupling.partners.map { it.path },
        reasons = reasons,
    )

/** The plan as the API answers it, made from [files] files at risk. */
fun AiInterviewResponse.toResponse(files: Int): InterviewPlanResponse =
    InterviewPlanResponse(
        questions = questions.map { it.toResponse() },
        answeredByRecords = answeredByRecords.map { it.toResponse() },
        files = files,
    )

private fun AiInterviewQuestion.toResponse() =
    InterviewQuestionResponse(
        component = component,
        text = text,
        type = type,
        triggers = triggers,
        files = files,
        priority = priority,
        coverage = coverage,
        recordAnswer = recordAnswer,
        sources = sources,
    )

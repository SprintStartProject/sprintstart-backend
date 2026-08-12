package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification
import com.sprintstart.sprintstartbackend.onboarding.model.entity.VerificationAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.SubmitVerificationAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.VerificationResponse

fun Verification.toResponse(): VerificationResponse =
    VerificationResponse(
        id = id,
        moduleId = moduleId,
        type = type,
        prompt = prompt,
        competencyKey = competencyKey,
        level = level,
    )

fun VerificationAttempt.toSubmitResponse(): SubmitVerificationAttemptResponse =
    SubmitVerificationAttemptResponse(
        attemptId = id,
        moduleId = verification.moduleId,
        passed = passed,
        score = score,
        feedback = feedback,
        hint = hint,
        attemptNo = attemptNo,
    )

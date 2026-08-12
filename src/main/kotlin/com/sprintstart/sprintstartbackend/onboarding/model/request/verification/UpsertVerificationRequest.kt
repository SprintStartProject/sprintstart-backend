package com.sprintstart.sprintstartbackend.onboarding.model.request.verification

import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import java.util.UUID

data class UpsertVerificationRequest(
    val type: VerificationType,
    val prompt: String,
    val rubric: String? = null,
    val canonicalAnswer: String? = null,
    val repositoryConnectionId: UUID? = null,
    val competencyKey: String,
    val level: String,
)

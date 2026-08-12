package com.sprintstart.sprintstartbackend.onboarding.model.response.verification

import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import java.util.UUID

/**
 * A graded check's config as shown via the API.
 *
 * `rubric`/`canonicalAnswer` are deliberately never included here -- revealing either would let a
 * learner read off the answer.

 */
data class VerificationResponse(
    val id: UUID,
    val moduleId: UUID,
    val type: VerificationType,
    val prompt: String,
    val competencyKey: String,
    val level: String,
)

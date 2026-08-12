package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Lifecycle of a [com.sprintstart.sprintstartbackend.onboarding.model.entity.SkillAssessmentSession].
 *
 * A session starts [IN_PROGRESS] and flips to [COMPLETED] once the AI interviewer returns a final
 * placement (`done=true`) and the ledger has been written. There is no cancelled/abandoned state
 * in this phase — an [IN_PROGRESS] session with no recent activity is simply resumed by `/start`.
 */
enum class SkillAssessmentSessionStatus { IN_PROGRESS, COMPLETED }

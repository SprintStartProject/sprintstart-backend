package com.sprintstart.sprintstartbackend.onboarding.model.response.assessment

/**
 * Whether the authenticated user has ever completed a skill-assessment interview.
 *
 * A hint, not a door. The assessment is optional and its output is only a prior for matching
 * ([com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource.ASSESSED], which
 * proof always outranks) — completing it never unlocks the product and skipping it never blocks
 * the first week. This flag lets a surface *offer* the assessment to someone who has not taken it;
 * it is no longer the target of a redirect that gates the app. Source of truth is a COMPLETED
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.SkillAssessmentSession].
 */
data class GetAssessmentStatusResponse(
    val completed: Boolean,
)

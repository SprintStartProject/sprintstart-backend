package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import org.springframework.stereotype.Component

/**
 * Keeps `Competency.area` from fragmenting into near-duplicates of itself.
 *
 * The area is free text, because a fixed taxonomy cannot fit a codebase nobody has seen. The cost of
 * free text is that "auth", "Authentication" and "auth " are three groups describing one subject, and
 * a grouping that fragments is worse than no grouping — it looks organised and is not. So the
 * constraint lives at the write instead of in the type: an incoming area that differs from one
 * already in use only by case or surrounding whitespace **is** that area, and is stored with the
 * spelling already in use.
 *
 * ⚠️ First writer wins the spelling, deliberately. Letting a later write restyle every existing row
 * would mean one generation run silently renaming a PM's chosen wording. It matters because
 * generation runs on ingestion, proposing areas with nobody in the loop to tidy up.
 */
@Component
class CompetencyAreaNormalizer(
    private val competencyRepository: CompetencyRepository,
) {
    /**
     * Resolves [raw] to the area that should be stored.
     *
     * @param raw What the caller asked for — a PM's typing or a generated value.
     * @return The matching area already in use, else [raw] trimmed, else null when [raw] is blank or
     * absent. Blank maps to null because clearing the field is how somebody ungroups a competency.
     */
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
        return competencyRepository
            .findDistinctAreas()
            .firstOrNull { it.equals(trimmed, ignoreCase = true) }
            ?: trimmed
    }
}

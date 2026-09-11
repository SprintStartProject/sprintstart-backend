package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.SkillDto
import java.util.UUID

/**
 * Module-facing read access to skills.
 *
 * Exposes the small slice of skill data other modules may consume without reaching into this
 * module's repositories. Implemented by `SkillService`.
 */
interface SkillsApi {
    /**
     * Returns the active skills matching the given ids.
     *
     * Retired skills and unknown ids are silently omitted from the result, so callers never
     * receive a skill that can no longer be assigned. The result may therefore be smaller than
     * the requested id set — or empty.
     *
     * @param skillIds Ids of the skills to resolve.
     * @return The matching skills in `ACTIVE` status, mapped to [SkillDto]s.
     */
    fun getSkillsByIds(skillIds: Set<UUID>): Set<SkillDto>
}

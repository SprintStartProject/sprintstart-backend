package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.enums.SkillLevel

/**
 * One skill a user has said they have, at the level they said.
 *
 * The name rather than the skill id, because the reader matches it against its own vocabulary:
 * the id is meaningless outside the user module.
 */
data class DeclaredSkill(
    val name: String,
    val level: SkillLevel,
)

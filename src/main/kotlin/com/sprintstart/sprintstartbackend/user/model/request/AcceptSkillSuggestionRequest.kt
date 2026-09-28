package com.sprintstart.sprintstartbackend.user.model.request

import java.util.UUID

data class AcceptSkillSuggestionRequest(
    val skillId: UUID? = null,
    val name: String? = null,
    val category: String? = null,
)

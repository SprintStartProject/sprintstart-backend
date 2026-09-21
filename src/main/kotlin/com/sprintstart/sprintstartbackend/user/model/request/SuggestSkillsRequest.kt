package com.sprintstart.sprintstartbackend.user.model.request

import java.util.UUID

data class SuggestSkillsRequest(
    val projectId: UUID? = null,
    val industry: String? = null,
)

package com.sprintstart.sprintstartbackend.user.model.response.skill

import java.util.UUID

data class SkillSuggestionItemResponse(
    val skillId: UUID? = null,
    val name: String,
    val category: String? = null,
    val reason: String = "",
    val confidence: String = "medium",
    val isNew: Boolean = false,
    val chunkIds: List<String> = emptyList(),
)

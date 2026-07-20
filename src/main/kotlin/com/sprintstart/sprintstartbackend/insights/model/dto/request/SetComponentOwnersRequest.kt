package com.sprintstart.sprintstartbackend.insights.model.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.util.UUID

@Schema(description = "Assigns the owners of a component, replacing any previous assignment.")
data class SetComponentOwnersRequest(
    @field:Schema(description = "Component identifier, an owner/repo string.")
    @field:NotBlank
    val component: String,
    @field:Schema(description = "Users to set as owners of the component. An empty list clears the owners.")
    val userIds: List<UUID>,
)

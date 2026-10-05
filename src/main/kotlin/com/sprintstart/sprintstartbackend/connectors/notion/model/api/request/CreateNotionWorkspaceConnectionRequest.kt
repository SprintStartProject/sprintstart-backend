package com.sprintstart.sprintstartbackend.connectors.notion.model.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateNotionWorkspaceConnectionRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val credentialName: String,
)

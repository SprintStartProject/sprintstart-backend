package com.sprintstart.sprintstartbackend.connectors.core.models.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class PatchSourcesRequest(
    @NotEmpty
    val sources: List<PatchSourceRequest>,
)

data class PatchSourceRequest(
    @NotBlank
    val sourceId: String,
    @Pattern(regexp = "included|excluded")
    val status: String,
)

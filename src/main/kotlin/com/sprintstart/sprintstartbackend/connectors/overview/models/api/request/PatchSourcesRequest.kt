package com.sprintstart.sprintstartbackend.connectors.overview.models.api.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class PatchSourcesRequest(
    @NotEmpty
    @Valid
    val sources: List<PatchSourceRequest>,
)

data class PatchSourceRequest(
    @NotBlank
    val sourceId: String,
    @NotNull
    var enabled: Boolean,
)

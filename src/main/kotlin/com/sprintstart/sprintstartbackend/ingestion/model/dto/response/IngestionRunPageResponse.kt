package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description =
        "One page of ingestion runs together with pagination metadata. Used by run-history views " +
            "that need filtering and real \"load more\" paging instead of a single truncated list.",
)
data class IngestionRunPageResponse(
    @field:Schema(description = "Ingestion runs contained in this page, newest first.")
    val items: List<IngestionRunResponse>,
    @field:Schema(description = "Pagination metadata for the returned page.")
    val page: PageMetadata,
)

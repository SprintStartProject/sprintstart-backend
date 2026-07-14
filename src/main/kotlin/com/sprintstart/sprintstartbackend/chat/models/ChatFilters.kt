package com.sprintstart.sprintstartbackend.chat.models

import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import jakarta.validation.constraints.PastOrPresent
import org.hibernate.validator.constraints.UniqueElements
import java.time.Instant

/**
 * Collects all filters provided by the frontend.
 */
data class ChatFilters(
    @field:UniqueElements
    var sourceSystems: List<SourceSystem>?,
    @field:PastOrPresent
    var from: Instant?,
    @field:PastOrPresent
    var to: Instant?,
)

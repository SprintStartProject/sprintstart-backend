package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

data class PageMetadata(
    val number: Long,
    val size: Long,
    val totalElements: Long,
    val totalPages: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)
